package baaahs.gl.render

import baaahs.Color
import baaahs.Pixels
import baaahs.TestModelSurface
import baaahs.fixtures.Fixture
import baaahs.gadgets.Slider
import baaahs.geom.Vector3F
import baaahs.gl.GlBase
import baaahs.gl.GlContext
import baaahs.gl.glsl.GlslAnalyzer
import baaahs.gl.glsl.GlslProgram
import baaahs.gl.patch.AutoWirer
import baaahs.glsl.Shaders.cylindricalProjection
import baaahs.model.ModelInfo
import baaahs.plugin.Plugins
import baaahs.shows.FakeShowPlayer
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.*

class RenderEngineTest {
    // assumeTrue() doesn't work in js runners; instead, bail manually.
    // TODO: Do something better.
//    @BeforeTest
//    fun verifyGlslAvailable() = assumeTrue(GlslBase.manager.available)

    fun glslAvailable(): Boolean {
        val available = GlBase.manager.available
        if (!available) {
            println("WARNING: OpenGL not available, skipping test!")
        }
        return available
    }

    private lateinit var glContext: GlContext
    private lateinit var renderEngine: RenderEngine
    private lateinit var fakeShowPlayer: FakeShowPlayer

    @BeforeTest
    fun setUp() {
        if (glslAvailable()) {
            glContext = GlBase.manager.createContext()
            renderEngine = RenderEngine(glContext, ModelInfoForTest)
            fakeShowPlayer = FakeShowPlayer(glContext)
        }
    }

    @AfterTest
    fun tearDown() {
        if (glslAvailable()) {
            glContext.release()
            renderEngine.release()
        }
    }

    // TODO: Need to swap out uvShader for simpler test version to get this passing.

    @Test
    fun testSimpleRendering() {
        if (!glslAvailable()) return

        val program =
            /**language=glsl*/
            """
            uniform float time;
            void main() {
                gl_FragColor = vec4(gl_FragCoord.xy, 0.5, 1.);
            }
            """.trimIndent()

        val glslProgram = compileAndBind(program)
        val fixtureRenderPlan = renderEngine.addFixture(fakeSurface()).apply { this.program = glslProgram }

        renderEngine.draw()

        expectColor(listOf(
            Color(0f, .1f, .5f),
            Color(.2f, .3f, .5f),
            Color(.4f, .5f, .5f)
        )) { fixtureRenderPlan.pixels.toList() }
    }

    @Test
    fun testRenderingWithUniform() {
        if (!glslAvailable()) return

        val program =
            /**language=glsl*/
            """
            uniform float time;
            
            // SPARKLEMOTION GADGET: Slider {}
            uniform float blue;
            
            void main() {
                gl_FragColor = vec4(gl_FragCoord.xy, blue, 1.);
            }
            """.trimIndent()

        val glslProgram = compileAndBind(program)
        val fixtureRenderPlan = renderEngine.addFixture(fakeSurface()).apply { this.program = glslProgram }

        fakeShowPlayer.getGadget<Slider>("glsl_in_blue").value = .1f
        fakeShowPlayer.drawFrame()

        expectColor(listOf(
            Color(0f, .1f, .1f),
            Color(.2f, .3f, .1f),
            Color(.4f, .5f, .1f)
        )) { fixtureRenderPlan.pixels.toList() }

        fakeShowPlayer.getGadget<Slider>("glsl_in_blue").value = .2f
        fakeShowPlayer.drawFrame()

        expectColor(listOf(
            Color(0f, .1f, .2f),
            Color(.2f, .3f, .2f),
            Color(.4f, .5f, .2f)
        )) { fixtureRenderPlan.pixels.toList() }
    }

    @Test
    fun testRenderingWithMultipleFixtures() {
        if (!glslAvailable()) return

        val program =
            /**language=glsl*/
            """
            uniform float time;
            void main() {
                gl_FragColor = vec4(gl_FragCoord.xy, 0.5, 1.);
            }
            """.trimIndent()

        val glslProgram = compileAndBind(program)

        val fixtureRenderPlan1 = renderEngine.addFixture(fakeSurface("s1")).apply { this.program = glslProgram }
        val fixtureRenderPlan2 = renderEngine.addFixture(fakeSurface("s2", 2)).apply { this.program = glslProgram }
        val fixtureRenderPlan3 = renderEngine.addFixture(fakeSurface("s3")).apply { this.program = glslProgram }

        renderEngine.draw()

        expectColor(listOf(
            Color(0f, .1f, .5f),
            Color(.2f, .3f, .5f),
            Color(.4f, .5f, .5f)
        )) { fixtureRenderPlan1.pixels.toList() }

        expectColor(listOf(
            Color(.6f, .6f, .5f),
            Color(.651f, .651f, .5f)
        )) { fixtureRenderPlan2.pixels.toList() }

        expect(listOf(
            Color(1f, 1f, .5f),
            Color(1f, 1f, .5f),
            Color(1f, 1f, .5f)
        )) { fixtureRenderPlan3.pixels.toList() }
    }

    @Ignore @Test // TODO: Per-surface uniform control TBD
    fun testRenderingSurfacesWithDifferentBufferValues() {
        if (!glslAvailable()) return

        val program =
            /**language=glsl*/
            """
            // SPARKLEMOTION GADGET: Slider {name: "Blue", initialValue: 1.0, minValue: 0.0, maxValue: 1.0}
            uniform float blue;

            uniform float time;
            void main() {
                gl_FragColor = vec4(gl_FragCoord.xy, blue, 1.);
            }
            """.trimIndent()

        val glslProgram = compileAndBind(program)

        val fixtureRenderPlan1 = renderEngine.addFixture(fakeSurface("s1")).apply { this.program = glslProgram }
        val fixtureRenderPlan2 = renderEngine.addFixture(fakeSurface("s2")).apply { this.program = glslProgram }

        // TODO: yuck, let's not do this [first part]
//        fixtureRenderPlan1.uniforms.updateFrom(arrayOf(1f, 1f, 1f, 1f, 1f, 1f, .2f))
//        fixtureRenderPlan2.uniforms.updateFrom(arrayOf(1f, 1f, 1f, 1f, 1f, 1f, .3f))

        renderEngine.draw()

        expectColor(listOf(
            Color(0f, .1f, .2f),
            Color(.2f, .3f, .2f),
            Color(.4f, .503f, .2f)
        )) { fixtureRenderPlan1.pixels.toList() }

        // Interpolation between vertex 0 and the surface's center.
        expectColor(listOf(
            Color(.6f, .6f, .3f),
            Color(.651f, .651f, .3f),
            Color(.7f, .7f, .3f)
        )) { fixtureRenderPlan2.pixels.toList() }
    }

    @Test
    fun mapFixturePanelsToRects_shouldWrapAsNecessary() {

        // ....
        // xxx.
        expect(listOf(
            Quad.Rect(1f, 0f, 2f, 3f)
        )) { RenderEngine.mapFixturePixelsToRects(4, 4, createSurface("A", 3)) }

        // ...x
        // xxxx
        // xx..
        expect(listOf(
            Quad.Rect(0f, 3f, 1f, 4f),
            Quad.Rect(1f, 0f, 2f, 4f),
            Quad.Rect(2f, 0f, 3f, 2f)
        )) { RenderEngine.mapFixturePixelsToRects(3, 4, createSurface("A", 7)) }
    }

    private fun fakeSurface(name: String = "xyz", pixelCount: Int = 3): Fixture {
        return Fixture(
            TestModelSurface(name),
            pixelCount,
            /**
             * e.g.:
             *  Vector3F(0f, .1f, 0f),
             *  Vector3F(.2f, .3f, 0f),
             *  Vector3F(.4f, .5f, 0f)
             */
            (0 until pixelCount).map { i ->
                val offset = i * .1f
                Vector3F(0f + offset, .1f + offset, 0f)
            }
        )
    }

    private fun createSurface(name: String, pixelCount: Int): Fixture {
        return Fixture(
            TestModelSurface(name), pixelCount,
            (0 until pixelCount).map { Vector3F(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) }
        )
    }

    private fun compileAndBind(program: String): GlslProgram {
        val autoWirer = AutoWirer(Plugins.safe())
        val shader = GlslAnalyzer(Plugins.safe()).import(program)
        return autoWirer
            .autoWire(cylindricalProjection, shader)
            .acceptSuggestedLinkOptions()
            .resolve()
            .openForPreview(autoWirer)!!
            .compile(glContext) { id, dataSource ->
            dataSource.createFeed(fakeShowPlayer, autoWirer.plugins, id)
        }
    }

    // More forgiving color equality checking, allows each channel to be off by one.
    fun expectColor(expected: List<Color>, actualFn: () -> List<Color>) {
        val actual = actualFn()
        val nearlyEqual = expected.zip(actual) { exp, act ->
            val diff = exp - act
            (diff.redI <= 1 && diff.greenI <= 1 && diff.blueI <= 1)
        }.all { it }
        if (!nearlyEqual) {
            expect(expected, actualFn)
        }
    }

    operator fun Color.minus(other: Color) =
        Color(abs(redI - other.redI), abs(greenI - other.greenI), abs(blueI - other.blueI), abs(alphaI - other.alphaI))

    val ModelInfoForTest = ModelInfo.Empty

}

val FixtureRenderPlan.pixels: Pixels
    get() = renderResult as Pixels