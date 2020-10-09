package baaahs

import baaahs.fixtures.FixtureManager
import baaahs.gl.GlContext
import baaahs.gl.patch.AutoWirer
import baaahs.gl.render.RenderEngine
import baaahs.io.Fs
import baaahs.mapper.Storage
import baaahs.model.Model
import baaahs.model.ModelInfo
import baaahs.net.FragmentingUdpLink
import baaahs.net.Network
import baaahs.plugin.Plugins
import baaahs.proto.Ports
import baaahs.show.Show
import baaahs.show.live.OpenShow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val pinkyModule = module {
    single { AutoWirer(get()) }
    single<ModelInfo> { get<Model<*>>() }
    single(named("non-fragmenting")) { get<Network>().link("pinky") }
    single<Network.Link> { FragmentingUdpLink(get(named("non-fragmenting"))) }
    single { RenderEngine(get(), get()) }
    single { StageManager(get(), get(), get(), get(), get(), get(), get(), get(), get(named("pinkyMainContext"))) }

    single {
        { newShow: Show, newShowState: ShowState?, openShow: OpenShow ->
            ShowRunner(newShow, newShowState, openShow, get(), get(), get(), get())
        }
    }

    single { get<Network.Link>().startHttpServer(Ports.PINKY_UI_TCP) }
    single { PubSub.Server(get(), get(named("pinkyMainContext"))) }
    single { Storage(get(), get()) }
    single { FixtureManager(get()) }
    single { MovingHeadManager(get(), get(), get<Model<*>>().movingHeads) }
    single<Job>(named("pinkyJob")) { SupervisorJob() }
    single(named("pinkyMainContext")) { get<CoroutineDispatcher>(named("pinkyMainDispatcher")) + get<Job>(named("pinkyJob")) }
    single {
        Pinky(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            null,
            null,
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(named("pinkyMainContext"))
        )
    }
}


interface PlatformModule {
    val model: Model<*>
    val network: Network
    val dmxUniverse: Dmx.Universe
    val beatSource: BeatSource
    val clock: Clock
    val fs: Fs
    val firmwareDaddy: FirmwareDaddy
    val soundAnalyzer: SoundAnalyzer
    val glContext: GlContext
    val plugins: Plugins
    val pinkyMainDispatcher: CoroutineDispatcher

    fun getModule(): Module = module {
        single { model }
        single { network }
        single { dmxUniverse }
        single { beatSource }
        single { clock }
        single { fs }
        single { firmwareDaddy }
        single { soundAnalyzer }
        single { glContext }
        single { plugins }
        single(named("pinkyMainDispatcher")) { pinkyMainDispatcher }
    }
}
