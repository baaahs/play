package baaahs

import baaahs.geom.Vector3F
import baaahs.gl.GlContext
import baaahs.io.Fs
import baaahs.model.Model
import baaahs.models.SheepModel
import baaahs.net.Network
import baaahs.net.TestNetwork
import baaahs.plugin.Plugins
import baaahs.shows.FakeGlContext
import baaahs.sim.FakeDmxUniverse
import baaahs.sim.FakeFs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.test.expect

@Suppress("UNCHECKED_CAST")
fun <T> nuffin(): T = null as T

fun MutableList<String>.assertEmpty() {
    expect(emptyList<String>()) { this }
    this.clear()
}

fun MutableList<String>.assertContents(vararg s: String) {
    expect(s.toList()) { this }
    this.clear()
}

var json = Json { serializersModule = Gadget.serialModule }

fun <T> serializationRoundTrip(serializer: KSerializer<T>, obj: T): T {
    val jsonString = json.encodeToString(serializer, obj)
    return json.decodeFromString(serializer, jsonString)
}

fun <T: Any?> toBeSpecified(): T = error("override me!")

class TestPlatformModule(
    model: SheepModel = SheepModel()
) : PlatformModule {
    override val model: Model<*> = model
    override val network: Network = TestNetwork(1_000_000)
    override val dmxUniverse: Dmx.Universe = FakeDmxUniverse()
    override val beatSource: BeatSource = StubBeatSource()
    override val clock: Clock = FakeClock()
    override val fs: Fs = FakeFs()
    override val firmwareDaddy: FirmwareDaddy = PermissiveFirmwareDaddy()
    override val soundAnalyzer: SoundAnalyzer = StubSoundAnalyzer()
    override val glContext: GlContext = FakeGlContext()
    override val plugins: Plugins = Plugins.safe()
    override val pinkyMainDispatcher: CoroutineDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }
}

class FakeClock(var time: Time = 0.0) : Clock {
    override fun now(): Time = time
}

class StubBeatSource : BeatSource {
    override fun getBeatData(): BeatData = BeatData(0.0, 0, confidence = 0f)
}

class StubSoundAnalyzer : SoundAnalyzer {
    override val frequencies = floatArrayOf()

    override fun listen(analysisListener: SoundAnalyzer.AnalysisListener) {
    }

    override fun unlisten(analysisListener: SoundAnalyzer.AnalysisListener) {
    }
}

class TestModelSurface(
    override val name: String,
    override val expectedPixelCount: Int? = 1,
    val vertices: Collection<Vector3F> = emptyList()
) : Model.Surface {
    override val description = name

    override fun allVertices(): Collection<Vector3F> = vertices

    override val faces: List<Model.Face> = emptyList()
    override val lines: List<Model.Line> = emptyList()
}

expect fun assumeTrue(boolean: Boolean)