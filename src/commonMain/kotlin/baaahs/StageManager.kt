package baaahs

import baaahs.app.ui.CommonIcons
import baaahs.fixtures.FixtureManager
import baaahs.fixtures.RenderPlan
import baaahs.gl.Toolchain
import baaahs.gl.render.RenderManager
import baaahs.io.Fs
import baaahs.io.FsServerSideSerializer
import baaahs.io.PubSubRemoteFsServerBackend
import baaahs.io.RemoteFsSerializer
import baaahs.mapper.Storage
import baaahs.model.ModelInfo
import baaahs.show.DataSource
import baaahs.show.Show
import baaahs.show.buildEmptyShow
import baaahs.ui.Icon
import baaahs.util.Clock
import com.soywiz.klock.DateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlin.coroutines.CoroutineContext

class StageManager(
    toolchain: Toolchain,
    private val renderManager: RenderManager,
    private val pubSub: PubSub.Server,
    private val storage: Storage,
    private val fixtureManager: FixtureManager,
    private val clock: Clock,
    modelInfo: ModelInfo,
    private val coroutineContext: CoroutineContext
) : BaseShowPlayer(toolchain, modelInfo) {
    val facade = Facade()
    private var showRunner: ShowRunner? = null
    private val gadgets: MutableMap<String, GadgetInfo> = mutableMapOf()
    var lastUserInteraction = DateTime.now()

    private val fsSerializer = FsServerSideSerializer()

    init {
        PubSubRemoteFsServerBackend(pubSub, fsSerializer)
    }

    @Suppress("unused")
    private val clientData =
        pubSub.state(Topics.createClientData(fsSerializer), ClientData(storage.fs.rootFile))

    private val showProblems = pubSub.publish(Topics.showProblems, emptyList()) {}

    private val showEditSession = ShowEditSession(fsSerializer)
    private val showEditorStateChannel: PubSub.Channel<ShowEditorState?> =
        pubSub.publish(
            ShowEditorState.createTopic(toolchain.plugins, fsSerializer),
            showEditSession.getShowEditState()
        ) { incoming ->
            val newShow = incoming?.show
            val newShowState = incoming?.showState
            val newIsUnsaved = incoming?.isUnsaved ?: false
            switchTo(
                newShow, newShowState, showEditSession.showFile,
                newIsUnsaved, fromClientUpdate = true
            )
        }

    private var gadgetsChangedJobEnqueued: Boolean = false

    override fun <T : Gadget> registerGadget(id: String, gadget: T, controlledDataSource: DataSource?) {
        val topic =
            PubSub.Topic("/gadgets/$id", GadgetDataSerializer)
        val channel = pubSub.publish(topic, gadget.state) { updated ->
            CoroutineScope(coroutineContext).launch {
                gadget.state.putAll(updated)
                lastUserInteraction = DateTime.now()

                if (!gadgetsChangedJobEnqueued) {
                    onGadgetChange()
                    gadgetsChangedJobEnqueued = false
                }
            }
        }
        val gadgetChannelListener: (Gadget) -> Unit = { channel.onChange(it.state) }
        gadget.listen(gadgetChannelListener)
        gadgets[id] = GadgetInfo(gadget, controlledDataSource, topic, channel, gadgetChannelListener)
        super.registerGadget(id, gadget, controlledDataSource)
    }

    fun onGadgetChange() {
        showRunner?.onSelectedPatchesChanged()

        // Start housekeeping early -- as soon as we see a change -- in hopes of avoiding jank.
        if (showRunner?.housekeeping() == true) facade.notifyChanged()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Gadget> useGadget(id: String): T {
        return (gadgets[id]?.gadget
            ?: error("no such gadget \"$id\" among [${gadgets.keys.sorted()}]")) as T
    }

    fun switchTo(
        newShow: Show?,
        newShowState: ShowState? = null,
        file: Fs.File? = null,
        isUnsaved: Boolean = file == null,
        fromClientUpdate: Boolean = false
    ) {
        val newShowRunner = newShow?.let {
            val openShow = openShow(newShow, newShowState)
            ShowRunner(newShow, newShowState, openShow, clock, renderManager, fixtureManager) { problems ->
                this.showProblems.onChange(problems)
            }
        }

        showRunner?.release()
        releaseUnused()

        showRunner = newShowRunner
        showEditSession.show = newShowRunner?.show
        showEditSession.showFile = file
        showEditSession.showIsUnsaved = isUnsaved

        updateRunningShowPath(file)

        notifyOfShowChanges(fromClientUpdate)
    }

    private fun updateRunningShowPath(file: Fs.File?) {
        GlobalScope.launch {
            storage.updateConfig {
                copy(runningShowPath = file?.fullPath)
            }
        }
    }

    internal fun notifyOfShowChanges(fromClientUpdate: Boolean = false) {
        if (!fromClientUpdate) {
            showEditorStateChannel.onChange(showEditSession.getShowEditState())
        }

        facade.notifyChanged()
    }

    fun renderAndSendNextFrame(dontProcrastinate: Boolean = true) {
        showRunner?.let { showRunner ->
            // Unless otherwise instructed, = generate and send the next frame right away,
            // then perform any housekeeping tasks immediately afterward, to avoid frame lag.
            if (dontProcrastinate) housekeeping()

            if (showRunner.renderNextFrame()) {
                fixtureManager.sendFrame()
            }

            if (!dontProcrastinate) housekeeping()
        }
    }

    private fun housekeeping() {
        if (showRunner!!.housekeeping()) facade.notifyChanged()
    }

    fun shutDown() {
        showRunner?.release()
        showEditorStateChannel.unsubscribe()
    }

    inner class ShowEditSession(remoteFsSerializer: RemoteFsSerializer) {
        var show: Show? = null
        var showFile: Fs.File? = null
        var showIsUnsaved: Boolean = false

        init {
            val commands = Topics.Commands(SerializersModule {
                include(remoteFsSerializer.serialModule)
                include(toolchain.plugins.serialModule)
            })
            pubSub.listenOnCommandChannel(commands.newShow) { command, _ -> handleNewShow(command) }
            pubSub.listenOnCommandChannel(commands.switchToShow) { command, _ -> handleSwitchToShow(command.file) }
            pubSub.listenOnCommandChannel(commands.saveShow) { command, _ -> handleSaveShow() }
            pubSub.listenOnCommandChannel(commands.saveAsShow) { command, _ ->
                val saveAsFile = storage.resolve(command.file.fullPath)
                handleSaveAsShow(saveAsFile)
                updateRunningShowPath(saveAsFile)
            }
        }

        private suspend fun handleNewShow(command: NewShowCommand) {
            switchTo(command.template ?: buildEmptyShow())
        }

        private suspend fun handleSwitchToShow(file: Fs.File?) {
            if (file != null) {
                switchTo(storage.loadShow(file), file = file, isUnsaved = false)
            } else {
                switchTo(null, null, null)
            }
        }

        private suspend fun handleSaveShow() {
            showFile?.let { showFile ->
                show?.let { show -> saveShow(showFile, show) }
            }
        }

        private suspend fun handleSaveAsShow(showAsFile: Fs.File) {
            show?.let { show -> saveShow(showAsFile, show) }
        }

        private suspend fun saveShow(file: Fs.File, show: Show) {
            storage.saveShow(file, show)
            showFile = file
            showIsUnsaved = false
            notifyOfShowChanges()
        }

        fun getShowEditState(): ShowEditorState? {
            return showRunner?.let { showRunner ->
                show?.withState(showRunner.getShowState(), showIsUnsaved, showFile)
            }
        }
    }

    inner class Facade : baaahs.ui.Facade() {
        val currentShow: Show?
            get() = this@StageManager.showRunner?.show

        val currentRenderPlan: RenderPlan?
            get() = this@StageManager.fixtureManager.currentRenderPlan
    }
}

interface RefCounted {
    fun inUse(): Boolean
    fun use()
    fun release()
    fun onFullRelease()
}

class RefCounter : RefCounted {
    var refCount: Int = 0

    override fun inUse(): Boolean = refCount == 0

    override fun use() {
        refCount++
    }

    override fun release() {
        refCount--

        if (!inUse()) onFullRelease()
    }

    override fun onFullRelease() {
    }
}

@Serializable
data class ClientData(
    val fsRoot: Fs.File
)

@Serializable
class NewShowCommand(
    val template: Show? = null
) {
    @Serializable
    class Response
}

@Serializable
class SwitchToShowCommand(
    val file: Fs.File?
) {
    @Serializable
    class Response
}

@Serializable
class SaveShowCommand {
    @Serializable
    class Response
}

@Serializable
class SaveAsShowCommand(val file: Fs.File) {
    @Serializable
    class Response
}

@Serializable
data class ShowProblem(
    val title: String,
    val message: String? = null,
    val severity: Severity = Severity.ERROR,
    val id: String = randomId("error")
)

fun Collection<ShowProblem>.severity() = maxOfOrNull { it.severity }

enum class Severity(val icon: Icon) {
    INFO(CommonIcons.Info),
    WARN(CommonIcons.Warning),
    ERROR(CommonIcons.Error)
}