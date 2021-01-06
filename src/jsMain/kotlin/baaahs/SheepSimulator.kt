package baaahs

import baaahs.client.WebClient
import baaahs.geom.Matrix4
import baaahs.geom.Vector3F
import baaahs.gl.GlBase
import baaahs.gl.RootToolchain
import baaahs.gl.render.RenderManager
import baaahs.mapper.MappingSession
import baaahs.mapper.MappingSession.SurfaceData.PixelData
import baaahs.mapper.Storage
import baaahs.model.Model
import baaahs.net.Network
import baaahs.plugin.PluginContext
import baaahs.plugin.Plugins
import baaahs.plugin.beatlink.BeatLinkPlugin
import baaahs.proto.Ports
import baaahs.shows.BakedInShaders
import baaahs.sim.*
import baaahs.util.JsClock
import baaahs.util.LoggerConfig
import baaahs.visualizer.SurfaceGeometry
import baaahs.visualizer.SwirlyPixelArranger
import baaahs.visualizer.Visualizer
import baaahs.visualizer.VizPixels
import decodeQueryParams
import kotlinx.coroutines.*
import three.js.Vector3

class SheepSimulator(val model: Model) {
    @Suppress("unused")
    val facade = Facade()

    private val queryParams = decodeQueryParams(document.location!!)
    val network = FakeNetwork()
    val clock = JsClock
    private val dmxUniverse = FakeDmxUniverse()
    val visualizer = Visualizer(model, clock)
    private val mapperFs = FakeFs("Temporary Mapping Files")
    private val fs = MergedFs(BrowserSandboxFs("Browser Data"), mapperFs, "Browser Data")
    val filesystems = listOf(fs)
    private val bridgeClient: BridgeClient = BridgeClient("${window.location.hostname}:${Ports.SIMULATOR_BRIDGE_TCP}")
    init {
        window.asDynamic().simulator = this
        window.asDynamic().LoggerConfig = LoggerConfig
//  TODO      GlslBase.plugins.add(SoundAnalysisPlugin(bridgeClient.soundAnalyzer))
    }

    init {
        GlobalScope.launch {
            BakedInShaders.all.forEach { show ->
                try {
                    val file = fs.resolve("shaders", "${show.name}.glsl")
                    if (!fs.exists(file)) {
                        fs.saveFile(file, show.src)
                    }
                } catch (e: Exception) {
                    console.warn("huh? failed:", e)
                }
            }
        }
    }

    val pluginContext = PluginContext(clock)
    val plugins = Plugins.safe(pluginContext) +
            BeatLinkPlugin.Builder(bridgeClient.beatSource)
    private val pinky = Pinky(
        model,
        network,
        dmxUniverse,
        clock,
        fs,
        PermissiveFirmwareDaddy(),
        bridgeClient.soundAnalyzer,
        renderManager = RenderManager(model) { GlBase.manager.createContext() },
        plugins = plugins,
        pinkyMainDispatcher = Dispatchers.Main
    )
    private val brains: MutableList<Brain> = mutableListOf()

    val pinkyAddress: Network.Address get() = pinky.address

    fun start() = doRunBlocking {
        val simSurfaces = prepareSurfaces()

        GlobalScope.launch {
            pinky.startAndRun()
        }

        val launcher = Launcher(document.getElementById("launcher")!!)
        launcher.add("Web UI") { createWebClient() }

        launcher.add("Mapper") {
            val mapperUi = JsMapperUi(visualizer)
            val mediaDevices = FakeMediaDevices(visualizer)
            val mapper = Mapper(network, model, mapperUi, mediaDevices, pinky.address, clock)
            mapperScope.launch { mapper.start() }

            mapperUi
        }

        launcher.add("Admin UI") {
            AdminUi(network, pinky.address, model, clock)
        }

        simSurfaces.forEach { simSurface ->
            val vizPanel = visualizer.addSurface(simSurface.surfaceGeometry)
            vizPanel.vizPixels = VizPixels(vizPanel, simSurface.pixelPositions)

            val brain = Brain(simSurface.brainId.uuid, network, vizPanel.vizPixels ?: NullPixels, clock)
            brains.add(brain)

            brainScope.launch { randomDelay(1000); brain.run() }
        }
        pinky.pixelCount = simSurfaces.sumBy { it.pixelPositions.size }

        model.movingHeads.forEach { movingHead ->
            visualizer.addMovingHead(movingHead, dmxUniverse)
        }

//        val users = storage.users.transaction { store -> store.getAll() }
//        println("users = ${users}")

        facade.notifyChanged()

        doRunBlocking {
            delay(200000L)
        }
    }

    fun createWebClient(): WebClient {
        return WebClient(network, pinkyAddress, RootToolchain(plugins))
    }

    private fun prepareSurfaces(): List<SimSurface> {
        val pixelDensity = queryParams.getOrElse("pixelDensity") { "0.2" }.toFloat()
        val pixelSpacing = queryParams.getOrElse("pixelSpacing") { "3" }.toFloat()
        val pixelArranger = SwirlyPixelArranger(pixelDensity, pixelSpacing)
        var totalPixels = 0

        val simSurfaces = model.allSurfaces.sortedBy(Model.Surface::name).mapIndexed { index, surface ->
            //            if (panel.name != "17L") return@forEachIndexed

            val surfaceGeometry = SurfaceGeometry(surface)
            val pixelPositions = pixelArranger.arrangePixels(surfaceGeometry)
            totalPixels += pixelPositions.size
            SimSurface(surface, surfaceGeometry, pixelPositions, BrainId("brain//$index"))
        }

        doRunBlocking {
            val mappingSessionPath = Storage(mapperFs, plugins).saveSession(
                MappingSession(clock.now(), simSurfaces.map { simSurface ->
                    MappingSession.SurfaceData(
                        simSurface.brainId.uuid, simSurface.surface.name,
                        simSurface.pixelPositions.map {
                            PixelData(Vector3F(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()), null, null)
                        }, null, null, null
                    )
                }, Matrix4(doubleArrayOf()), null, notes = "Simulated pixels")
            )
            mapperFs.renameFile(
                mappingSessionPath,
                fs.resolve("mapping", model.name, "simulated", mappingSessionPath.name))
        }
        return simSurfaces
    }

    class SimSurface(
        val surface: Model.Surface,
        val surfaceGeometry: SurfaceGeometry,
        val pixelPositions: Array<Vector3>,
        val brainId: BrainId
    )

    object NullPixels : Pixels {
        override val size = 0

        override fun get(i: Int): Color = Color.BLACK
        override fun set(i: Int, color: Color) {}
        override fun set(colors: Array<Color>) {}
    }

    private val brainScope = CoroutineScope(Dispatchers.Main)
    private val mapperScope = CoroutineScope(Dispatchers.Main)

    inner class Facade : baaahs.ui.Facade() {
        val pinky: Pinky.Facade
            get() = this@SheepSimulator.pinky.facade
        val network: FakeNetwork.Facade
            get() = this@SheepSimulator.network.facade
        val visualizer: Visualizer.Facade
            get() = this@SheepSimulator.visualizer.facade
        val brains: List<Brain.Facade>
            get() = this@SheepSimulator.brains.map { it.facade }
    }
}