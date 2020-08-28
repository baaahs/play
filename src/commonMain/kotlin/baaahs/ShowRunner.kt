package baaahs

import baaahs.gl.glsl.CompilationException
import baaahs.gl.glsl.GlslException
import baaahs.gl.patch.AutoWirer
import baaahs.gl.patch.ContentType
import baaahs.gl.render.ModelRenderer
import baaahs.glsl.GuruMeditationError
import baaahs.show.ShaderChannel
import baaahs.show.Show
import baaahs.show.live.ShowContext

class ShowRunner(
    val show: Show,
    initialShowState: ShowState? = null,
    private val showContext: ShowContext,
    private val showPlayer: ShowPlayer,
//    private val beatSource: BeatSource,
//    private val dmxUniverse: Dmx.Universe,
//    private val movingHeadManager: MovingHeadManager,
    internal val clock: Clock,
    private val modelRenderer: ModelRenderer,
    private val surfaceManager: SurfaceManager,
    private val autoWirer: AutoWirer
) {
    private var showState: ShowState = initialShowState ?: show.defaultShowState()
    private var nextShowState: ShowState? = showState
    internal var currentRenderPlan: RenderPlan? = null

    // TODO: Get beat sync working again.
//    // Continuous from [0.0 ... 3.0] (0 is first beat in a measure, 3 is last)
//    val currentBeat: Float
//        get() = beatSource.getBeatData().beatWithinMeasure(clock)

//    private fun getDmxBuffer(baseChannel: Int, channelCount: Int): Dmx.Buffer =
//        dmxUniverse.writer(baseChannel, channelCount)
//
//    // TODO: Get moving heads working again.
//    fun getMovingHeadBuffer(movingHead: MovingHead): MovingHead.Buffer {
//        val baseChannel = Config.DMX_DEVICES[movingHead.name] ?: error("no DMX device for ${movingHead.name}")
//        val movingHeadBuffer = Shenzarpy(getDmxBuffer(baseChannel, 16))
//
//        movingHeadManager.listen(movingHead) { updated ->
//            println("Moving head ${movingHead.name} moved to ${updated.x} ${updated.y}")
//            movingHeadBuffer.pan = updated.x / 255f
//            movingHeadBuffer.tilt = updated.y / 255f
//        }
//
//        return movingHeadBuffer
//    }

    fun getShowState(): ShowState {
        return showState
    }

    fun switchTo(newShowState: ShowState) {
        this.showState = newShowState
        nextShowState = newShowState
    }

    /** @return `true` if a frame was rendered and should be sent to fixtures. */
    fun renderNextFrame(): Boolean {
        val renderPlan = currentRenderPlan
        renderPlan?.render(modelRenderer)
        return renderPlan != null
    }

    fun housekeeping(): Boolean {
        var remapSurfaces = surfaceManager.requiresRemap()

        // Maybe switch to a new show.
        nextShowState?.let { newShowState ->
            currentRenderPlan = prepareRenderPlan(newShowState)
            nextShowState = null
            remapSurfaces = true
        }

        if (remapSurfaces) {
            surfaceManager.clearRenderPlans()

            currentRenderPlan?.let {
                surfaceManager.remap(it)
            }
        }

        return remapSurfaces
    }

    private fun prepareRenderPlan(newShowState: ShowState): RenderPlan {
        try {
            val activePatchHolders = newShowState.getActivePatchHolders(show)
            val linkedPatches = autoWirer.merge(showContext, *activePatchHolders.toTypedArray())
                .mapValues { (_, portDiagram) ->
                    portDiagram.resolvePatch(ShaderChannel.Main, ContentType.ColorStream)
                }
            val glslContext = modelRenderer.gl
            val activeDataSources = mutableSetOf<String>()
            val programs = linkedPatches.mapNotNull { (_, linkedPatch) ->
                linkedPatch?.let { it to it.createProgram(glslContext, showPlayer) }
            }
            return RenderPlan(programs)
        } catch (e: GlslException) {
            logger.error("Error preparing program", e)
            if (e is CompilationException) {
                e.source?.let { logger.info { it } }
            }
            return GuruMeditationError.createRenderPlan(modelRenderer.gl)
        }
    }


    fun shutDown() {
        // TODO gadgetManager.clear()
    }

    fun release() {
        showContext.release()
    }

    data class SurfacesChanges(val added: Collection<SurfaceReceiver>, val removed: Collection<SurfaceReceiver>)

    interface SurfaceReceiver {
        val surface: Surface
        fun send(pixels: Pixels)
    }

    companion object {
        val logger = Logger("ShowRunner")
    }
}