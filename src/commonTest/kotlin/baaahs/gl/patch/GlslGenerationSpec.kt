package baaahs.gl.patch

import baaahs.fixtures.PixelLocationDataSource
import baaahs.gl.kexpect
import baaahs.gl.override
import baaahs.gl.patch.ContentType.Companion.Color
import baaahs.gl.testToolchain
import baaahs.glsl.Shaders.cylindricalProjection
import baaahs.plugin.CorePlugin
import baaahs.plugin.core.FixtureInfoDataSource
import baaahs.plugin.core.MovingHeadParams
import baaahs.show.ShaderChannel
import baaahs.show.mutable.MutableDataSourcePort
import baaahs.show.mutable.MutablePatch
import baaahs.show.mutable.MutableShaderChannel
import baaahs.show.mutable.editor
import ch.tutteli.atrium.api.fluent.en_GB.containsExactly
import ch.tutteli.atrium.api.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.fail

object GlslGenerationSpec : Spek({
    describe("Generation of GLSL from patches") {
        val shaderText by value {
            /**language=glsl*/
            """
                // This Shader's Name
                // Other stuff.
                
                uniform float blueness;
                uniform vec2  resolution;
                uniform float time;
                int someGlobalVar;
                const int someConstVar = 123;
                
                int anotherFunc(int i) { return i; }
                
                void main( void ) {
                    vec2 uv = gl_FragCoord.xy / resolution.xy;
                    someGlobalVar = anotherFunc(someConstVar);
                    gl_FragColor = vec4(uv.xy, blueness, 1.);
                }
            """.trimIndent()
        }
        val glslAnalyzer by value { testToolchain.glslAnalyzer }
        val mainShader by value { testToolchain.import(shaderText) }
        val mutablePatch by value { MutablePatch { } }
        val resultContentType by value { Color }
        val linkedPatch by value {
            mutablePatch.openForPreview(testToolchain, resultContentType)
                ?: fail("openForPreview returned null, maybe no shaders on mutablePatch?")
        }
        val glsl by value { linkedPatch.toGlsl().trim() }

        context("with screen coordinates for preview") {
            beforeEachTest {
                mutablePatch.addShaderInstance(mainShader) {
                    link("fragCoord", CorePlugin.RasterCoordinateDataSource())
                    link("resolution", CorePlugin.ResolutionDataSource())
                    link("time", CorePlugin.TimeDataSource())
                    link(
                        "blueness",
                        CorePlugin.SliderDataSource("Blueness", 0f, 0f, 1f, null)
                    )
                    shaderChannel = ShaderChannel.Main.editor()
                }
            }

            it("generates GLSL") {
                kexpect(glsl).toBe(
                    /**language=glsl*/
                    """
                        #ifdef GL_ES
                        precision mediump float;
                        #endif

                        // SparkleMotion-generated GLSL

                        layout(location = 0) out vec4 sm_result;

                        // Data source: Blueness Slider
                        uniform float in_bluenessSlider;

                        // Data source: Resolution
                        uniform vec2 in_resolution;

                        // Data source: Time
                        uniform float in_time;

                        // Shader: This Shader's Name; namespace: p0
                        // This Shader's Name

                        vec4 p0_thisShaderSName_gl_FragColor = vec4(0., 0., 0., 1.);

                        #line 7
                        int p0_thisShaderSName_someGlobalVar;

                        #line 8
                        const int p0_thisShaderSName_someConstVar = 123;

                        #line 10
                        int p0_thisShaderSName_anotherFunc(int i) { return i; }

                        #line 12
                        void p0_thisShaderSName_main( void ) {
                            vec2 uv = gl_FragCoord.xy / in_resolution.xy;
                            p0_thisShaderSName_someGlobalVar = p0_thisShaderSName_anotherFunc(p0_thisShaderSName_someConstVar);
                            p0_thisShaderSName_gl_FragColor = vec4(uv.xy, in_bluenessSlider, 1.);
                        }


                        #line 10001
                        void main() {
                          // Invoke This Shader's Name
                          p0_thisShaderSName_main();

                          sm_result = p0_thisShaderSName_gl_FragColor;
                        }
                    """.trimIndent()
                )
            }
        }

        context("with param input ports") {
            override(shaderText) {
                /**language=glsl*/
                """
                    uniform vec2  resolution;
                    vec2 anotherFunc(vec2 fragCoord) { return fragCoord; }
                    
                    // @param fragCoord uv-coordinate
                    void main(vec2 fragCoord) {
                        vec2 uv = anotherFunc(fragCoord) / resolution.xy;
                        gl_FragColor = vec4(uv.xy, 0., 1.);
                    }
                """.trimIndent()
            }

            beforeEachTest {
                mutablePatch.addShaderInstance(mainShader) {
                    link("resolution", CorePlugin.ResolutionDataSource())
                    link("fragCoord", CorePlugin.RasterCoordinateDataSource())
                    shaderChannel = ShaderChannel.Main.editor()
                }
            }

            it("generates GLSL") {
                kexpect(glsl).toBe(
                    /**language=glsl*/
                    """
                        #ifdef GL_ES
                        precision mediump float;
                        #endif

                        // SparkleMotion-generated GLSL

                        layout(location = 0) out vec4 sm_result;

                        // Data source: Resolution
                        uniform vec2 in_resolution;

                        // Shader: Untitled Shader; namespace: p0
                        // Untitled Shader

                        vec4 p0_untitledShader_gl_FragColor = vec4(0., 0., 0., 1.);

                        #line 2
                        vec2 p0_untitledShader_anotherFunc(vec2 fragCoord) { return fragCoord; }

                        #line 5
                        void p0_untitledShader_main(vec2 fragCoord) {
                            vec2 uv = p0_untitledShader_anotherFunc(fragCoord) / in_resolution.xy;
                            p0_untitledShader_gl_FragColor = vec4(uv.xy, 0., 1.);
                        }


                        #line 10001
                        void main() {
                          // Invoke Untitled Shader
                          p0_untitledShader_main(gl_FragCoord.xy);

                          sm_result = p0_untitledShader_gl_FragColor;
                        }
                    """.trimIndent()
                )
            }
        }

        context("with a ShaderToy paint shader") {
            override(shaderText) {
                /**language=glsl*/
                """
                    // This Shader's Name
                    // Other stuff.
                    
                    uniform float blueness;
                    int someGlobalVar;
                    const int someConstVar = 123;
                    
                    int anotherFunc(int i) { return i; }
                    
                    void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
                        vec2 uv = fragCoord.xy / iResolution.xy;
                        someGlobalVar = anotherFunc(someConstVar) + iTime * 0.;
                        fragColor = vec4(uv.xy, blueness, 1.);
                    }
                """.trimIndent()
            }

            beforeEachTest {
                mutablePatch.addShaderInstance(mainShader) {
                    link(
                        "blueness",
                        CorePlugin.SliderDataSource("Blueness", 0f, 0f, 1f, null)
                    )
                    link("iResolution", CorePlugin.ResolutionDataSource())
                    link("iTime", CorePlugin.TimeDataSource())
                    link("fragCoord", CorePlugin.RasterCoordinateDataSource())
                    shaderChannel = ShaderChannel.Main.editor()
                }
            }

            it("generates GLSL") {
                kexpect(glsl).toBe(
                    /**language=glsl*/
                    """
                        #ifdef GL_ES
                        precision mediump float;
                        #endif

                        // SparkleMotion-generated GLSL

                        layout(location = 0) out vec4 sm_result;

                        // Data source: Blueness Slider
                        uniform float in_bluenessSlider;

                        // Data source: Resolution
                        uniform vec2 in_resolution;

                        // Data source: Time
                        uniform float in_time;

                        // Shader: This Shader's Name; namespace: p0
                        // This Shader's Name

                        vec4 p0_thisShaderSName_fragColor = vec4(0., 0., 0., 1.);

                        #line 5
                        int p0_thisShaderSName_someGlobalVar;

                        #line 6
                        const int p0_thisShaderSName_someConstVar = 123;

                        #line 8
                        int p0_thisShaderSName_anotherFunc(int i) { return i; }

                        #line 10
                        void p0_thisShaderSName_mainImage( out vec4 fragColor, in vec2 fragCoord ) {
                            vec2 uv = fragCoord.xy / in_resolution.xy;
                            p0_thisShaderSName_someGlobalVar = p0_thisShaderSName_anotherFunc(p0_thisShaderSName_someConstVar) + in_time * 0.;
                            fragColor = vec4(uv.xy, in_bluenessSlider, 1.);
                        }


                        #line 10001
                        void main() {
                          // Invoke This Shader's Name
                          p0_thisShaderSName_mainImage(p0_thisShaderSName_fragColor, gl_FragCoord.xy);

                          sm_result = p0_thisShaderSName_fragColor;
                        }
                    """.trimIndent()
                )
            }
        }

        describe("with projection shader") {
            beforeEachTest {
                mutablePatch.apply {
                    addShaderInstance(cylindricalProjection) {
                        link("pixelLocation", PixelLocationDataSource())
                        link("modelInfo", CorePlugin.ModelInfoDataSource())
                        shaderChannel = ShaderChannel.Main.editor()
                    }

                    addShaderInstance(mainShader) {
                        link("gl_FragCoord", ShaderChannel.Main.toMutable())
                        link("resolution", CorePlugin.ResolutionDataSource())
                        link("time", CorePlugin.TimeDataSource())
                        link("blueness", CorePlugin.SliderDataSource("Blueness", 0f, 0f, 1f, null))
                        shaderChannel = ShaderChannel.Main.editor()
                    }
                }
            }

            it("generates GLSL") {
                kexpect(glsl).toBe(
                    /**language=glsl*/
                    """
                        #ifdef GL_ES
                        precision mediump float;
                        #endif

                        // SparkleMotion-generated GLSL

                        layout(location = 0) out vec4 sm_result;

                        struct ModelInfo {
                            vec3 center;
                            vec3 extents;
                        };
                        
                        // Data source: Blueness Slider
                        uniform float in_bluenessSlider;

                        // Data source: Model Info
                        uniform ModelInfo in_modelInfo;

                        // Data source: Pixel Location
                        uniform sampler2D ds_pixelLocation_texture;
                        vec3 ds_pixelLocation_getPixelCoords(vec2 rasterCoord) {
                            return texelFetch(ds_pixelLocation_texture, ivec2(rasterCoord.xy), 0).xyz;
                        }
                        vec3 in_pixelLocation;

                        // Data source: Resolution
                        uniform vec2 in_resolution;

                        // Data source: Time
                        uniform float in_time;

                        // Shader: Cylindrical Projection; namespace: p0
                        // Cylindrical Projection

                        vec2 p0_cylindricalProjectioni_result = vec2(0.);

                        #line 10
                        const float p0_cylindricalProjection_PI = 3.141592654;

                        #line 14
                        vec2 p0_cylindricalProjection_main(vec3 pixelLocation) {
                            vec3 pixelOffset = pixelLocation - in_modelInfo.center;
                            vec3 normalDelta = normalize(pixelOffset);
                            float theta = atan(abs(normalDelta.z), normalDelta.x); // theta in range [-π,π]
                            if (theta < 0.0) theta += (2.0f * p0_cylindricalProjection_PI);                 // theta in range [0,2π)
                            float u = theta / (2.0f * p0_cylindricalProjection_PI) * 2.;                    // u in range [0,1)
                            float v = (pixelOffset.y + in_modelInfo.extents.y / 2.0f) / in_modelInfo.extents.y;
                            return vec2(u, v);
                        }

                        // Shader: This Shader's Name; namespace: p1
                        // This Shader's Name

                        vec4 p1_thisShaderSName_gl_FragColor = vec4(0., 0., 0., 1.);

                        #line 7
                        int p1_thisShaderSName_someGlobalVar;

                        #line 8
                        const int p1_thisShaderSName_someConstVar = 123;

                        #line 10
                        int p1_thisShaderSName_anotherFunc(int i) { return i; }

                        #line 12
                        void p1_thisShaderSName_main( void ) {
                            vec2 uv = p0_cylindricalProjectioni_result.xy / in_resolution.xy;
                            p1_thisShaderSName_someGlobalVar = p1_thisShaderSName_anotherFunc(p1_thisShaderSName_someConstVar);
                            p1_thisShaderSName_gl_FragColor = vec4(uv.xy, in_bluenessSlider, 1.);
                        }


                        #line 10001
                        void main() {
                          // Invoke Pixel Location
                          in_pixelLocation = ds_pixelLocation_getPixelCoords(gl_FragCoord.xy);

                          // Invoke Cylindrical Projection
                          p0_cylindricalProjectioni_result = p0_cylindricalProjection_main(in_pixelLocation);

                          // Invoke This Shader's Name
                          p1_thisShaderSName_main();

                          sm_result = p1_thisShaderSName_gl_FragColor;
                        }
                    """.trimIndent()
                )
            }
        }

        describe("mixing from another channel") {
            val otherShaderActualChannel by value { "other" }

            override(shaderText) {
                """
                    // Cross-fade shader
                    uniform float fade;

                    // @return color
                    // @param inColor color
                    // @param inColor2 color
                    vec4 main(vec4 inColor, vec4 inColor2) {
                        return mix(inColor, inColor2, fade);
                    }
                """.trimIndent()
            }

            val mainPaintShader by value {
                testToolchain.import(
                    """
                        // Main Paint Shader
                        void main( void ) {
                            gl_FragColor = vec4(1., 0., 0., 1.);
                        }
                    """.trimIndent()
                )
            }

            val otherPaintShader by value {
                testToolchain.import(
                    """
                        // Other Paint Shader
                        void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
                            fragColor = vec4(0., 1., 0., 1.);
                        }
                    """.trimIndent()
                )
            }

            beforeEachTest {
                mutablePatch.addShaderInstance(mainPaintShader)
                mutablePatch.addShaderInstance(otherPaintShader) {
                    link("fragCoord", MutableDataSourcePort(CorePlugin.RasterCoordinateDataSource()))
                    shaderChannel = MutableShaderChannel.from(otherShaderActualChannel)
                }

                mutablePatch.addShaderInstance(mainShader) {
                    link("fade", CorePlugin.SliderDataSource("Fade", 0f, 0f, 1f, null))
                    link("inColor", MutableShaderChannel("main"))
                    link("inColor2", MutableShaderChannel("other"))
                }
            }

            it("generates GLSL") {
                kexpect(glsl).toBe(
                    /**language=glsl*/
                    """
                        #ifdef GL_ES
                        precision mediump float;
                        #endif

                        // SparkleMotion-generated GLSL

                        layout(location = 0) out vec4 sm_result;

                        // Data source: Fade Slider
                        uniform float in_fadeSlider;

                        // Shader: Main Paint Shader; namespace: p0
                        // Main Paint Shader

                        vec4 p0_mainPaintShader_gl_FragColor = vec4(0., 0., 0., 1.);

                        #line 2
                        void p0_mainPaintShader_main( void ) {
                            p0_mainPaintShader_gl_FragColor = vec4(1., 0., 0., 1.);
                        }

                        // Shader: Other Paint Shader; namespace: p1
                        // Other Paint Shader

                        vec4 p1_otherPaintShader_fragColor = vec4(0., 0., 0., 1.);

                        #line 2
                        void p1_otherPaintShader_mainImage( out vec4 fragColor, in vec2 fragCoord ) {
                            fragColor = vec4(0., 1., 0., 1.);
                        }

                        // Shader: Cross-fade shader; namespace: p2
                        // Cross-fade shader

                        vec4 p2_crossFadeShaderi_result = vec4(0., 0., 0., 1.);

                        #line 7
                        vec4 p2_crossFadeShader_main(vec4 inColor, vec4 inColor2) {
                            return mix(inColor, inColor2, in_fadeSlider);
                        }


                        #line 10001
                        void main() {
                          // Invoke Main Paint Shader
                          p0_mainPaintShader_main();

                          // Invoke Other Paint Shader
                          p1_otherPaintShader_mainImage(p1_otherPaintShader_fragColor, gl_FragCoord.xy);

                          // Invoke Cross-fade shader
                          p2_crossFadeShaderi_result = p2_crossFadeShader_main(p0_mainPaintShader_gl_FragColor, p1_otherPaintShader_fragColor);

                          sm_result = p2_crossFadeShaderi_result;
                        }
                    """.trimIndent()
                )
            }

            context("when there's no paint shader on the other channel") {
                override(otherShaderActualChannel) { "notOther" }

                it("should give a warning") {
                    expect(linkedPatch.warnings).containsExactly(
                        "No upstream shader found, using default for color.\n" +
                                "Stack:\n" +
                                "    Resolving Track[main/color] -> [Cross-fade shader].inColor2 (color)"
                    )
                }

                it("should use default for that content type") {
                    // TODO: WRONG WRONG WRONG we should tell the user that something's wrong?
                    kexpect(glsl).toBe(
                        /**language=glsl*/
                        """
                            #ifdef GL_ES
                            precision mediump float;
                            #endif

                            // SparkleMotion-generated GLSL

                            layout(location = 0) out vec4 sm_result;

                            // Data source: Fade Slider
                            uniform float in_fadeSlider;

                            // Shader: Main Paint Shader; namespace: p0
                            // Main Paint Shader

                            vec4 p0_mainPaintShader_gl_FragColor = vec4(0., 0., 0., 1.);

                            #line 2
                            void p0_mainPaintShader_main( void ) {
                                p0_mainPaintShader_gl_FragColor = vec4(1., 0., 0., 1.);
                            }

                            // Shader: Cross-fade shader; namespace: p1
                            // Cross-fade shader

                            vec4 p1_crossFadeShaderi_result = vec4(0., 0., 0., 1.);

                            #line 7
                            vec4 p1_crossFadeShader_main(vec4 inColor, vec4 inColor2) {
                                return mix(inColor, inColor2, in_fadeSlider);
                            }


                            #line 10001
                            void main() {
                              // Invoke Main Paint Shader
                              p0_mainPaintShader_main();

                              // Invoke Cross-fade shader
                              p1_crossFadeShaderi_result = p1_crossFadeShader_main(p0_mainPaintShader_gl_FragColor, vec4(0.));

                              sm_result = p1_crossFadeShaderi_result;
                            }
                        """.trimIndent()
                    )
                }
            }
        }

        context("with a shader using a struct uniform") {
            override(shaderText) {
                /**language=glsl*/
                """
                    struct FixtureInfo {
                        vec3 origin;            
                        vec3 heading; // in Euler angles
                    };
                    
                    uniform FixtureInfo fixtureInfo;
                    
                    struct MovingHeadParams {
                        float pan;
                        float tilt;
                        float colorWheel;
                        float dimmer;
                    };
 
                    // @param params moving-head-params
                    void main(out MovingHeadParams params) {
                        params.pan = fixtureInfo.origin.x;
                        params.tilt = fixtureInfo.origin.y,
                        params.colorWheel = fixtureInfo.heading.x,
                        params.dimmer = fixtureInfo.heading.y;
                    }
                """.trimIndent()
            }
            override(resultContentType) { MovingHeadParams.contentType }

            beforeEachTest {
                mutablePatch.addShaderInstance(mainShader) {
                    link("fixtureInfo", FixtureInfoDataSource())
                }
            }

            it("generates GLSL") {
                kexpect(glsl).toBe(
                    /**language=glsl*/
                    """
                        #ifdef GL_ES
                        precision mediump float;
                        #endif

                        // SparkleMotion-generated GLSL

                        layout(location = 0) out vec4 sm_result;

                        struct MovingHeadParams {
                            float pan;
                            float tilt;
                            float colorWheel;
                            float dimmer;
                        };

                        struct FixtureInfo {
                            vec3 origin;
                            vec3 heading;
                        };

                        // Data source: Fixture Info
                        uniform FixtureInfo in_fixtureInfo;

                        // Shader: Untitled Shader; namespace: p0
                        // Untitled Shader

                        MovingHeadParams p0_untitledShader_params = MovingHeadParams(0., 0., 0., 1.);

                        #line 16
                        void p0_untitledShader_main(out MovingHeadParams params) {
                            params.pan = in_fixtureInfo.origin.x;
                            params.tilt = in_fixtureInfo.origin.y,
                            params.colorWheel = in_fixtureInfo.heading.x,
                            params.dimmer = in_fixtureInfo.heading.y;
                        }


                        #line 10001
                        void main() {
                          // Invoke Untitled Shader
                          p0_untitledShader_main(p0_untitledShader_params);

                          sm_result = vec4(
                            p0_untitledShader_params.pan,
                            p0_untitledShader_params.tilt,
                            p0_untitledShader_params.colorWheel,
                            p0_untitledShader_params.dimmer
                          );
                        }
                    """.trimIndent()
                )
            }
        }

        context("with a shader using a struct internally") {
            override(shaderText) {
                /**language=glsl*/
                """
                    struct AnotherStruct {
                        float first;
                        float second;
                    };
                    AnotherStruct a;

                    struct MovingHeadParams {
                        float pan;
                        float tilt;
                        float colorWheel;
                        float dimmer;
                    };

                    // @return moving-head-params
                    MovingHeadParams main() {
                        AnotherStruct b;
                        return MovingHeadParams(a.first, a.second, 0., 0.);
                    }
                """.trimIndent()
            }
            override(resultContentType) { MovingHeadParams.contentType }

            beforeEachTest {
                mutablePatch.addShaderInstance(mainShader) {
                    link("fixtureInfo", FixtureInfoDataSource())
                }
            }

            it("generates GLSL") {
                kexpect(glsl).toBe(
                    /**language=glsl*/
                    """
                        #ifdef GL_ES
                        precision mediump float;
                        #endif

                        // SparkleMotion-generated GLSL

                        layout(location = 0) out vec4 sm_result;

                        struct MovingHeadParams {
                            float pan;
                            float tilt;
                            float colorWheel;
                            float dimmer;
                        };
                        
                        struct p0_untitledShader_AnotherStruct {
                            float first;
                            float second;
                        };

                        // Shader: Untitled Shader; namespace: p0
                        // Untitled Shader

                        MovingHeadParams p0_untitledShaderi_result = MovingHeadParams(0., 0., 0., 1.);

                        #line 5
                        p0_untitledShader_AnotherStruct p0_untitledShader_a;

                        #line 15
                        MovingHeadParams p0_untitledShader_main() {
                            p0_untitledShader_AnotherStruct b;
                            return MovingHeadParams(p0_untitledShader_a.first, p0_untitledShader_a.second, 0., 0.);
                        }


                        #line 10001
                        void main() {
                          // Invoke Untitled Shader
                          p0_untitledShaderi_result = p0_untitledShader_main();

                          sm_result = vec4(
                            p0_untitledShaderi_result.pan,
                            p0_untitledShaderi_result.tilt,
                            p0_untitledShaderi_result.colorWheel,
                            p0_untitledShaderi_result.dimmer
                          );
                        }
                    """.trimIndent()
                )
            }
        }

        context("with a shader using a struct internally, declaring a global") {
            override(shaderText) {
                /**language=glsl*/
                """
                    struct AnotherStruct {
                        float first;            
                        float second;
                    } a;
                    
                    // @return color
                    vec4 main() {
                        AnotherStruct b;
                        return vec4(a.first, a.second, 0., 0.);
                    }
                """.trimIndent()
            }
            override(resultContentType) { Color }

            beforeEachTest {
                mutablePatch.addShaderInstance(mainShader) {
                    link("fixtureInfo", FixtureInfoDataSource())
                }
            }

            it("generates GLSL") {
                kexpect(glsl).toBe(
                    /**language=glsl*/
                    """
                        #ifdef GL_ES
                        precision mediump float;
                        #endif

                        // SparkleMotion-generated GLSL

                        layout(location = 0) out vec4 sm_result;

                        struct p0_untitledShader_AnotherStruct {
                            float first;
                            float second;
                        };

                        // Shader: Untitled Shader; namespace: p0
                        // Untitled Shader

                        vec4 p0_untitledShaderi_result = vec4(0., 0., 0., 1.);

                        #line 1
                        p0_untitledShader_AnotherStruct p0_untitledShader_a;

                        #line 7
                        vec4 p0_untitledShader_main() {
                            p0_untitledShader_AnotherStruct b;
                            return vec4(p0_untitledShader_a.first, p0_untitledShader_a.second, 0., 0.);
                        }


                        #line 10001
                        void main() {
                          // Invoke Untitled Shader
                          p0_untitledShaderi_result = p0_untitledShader_main();

                          sm_result = p0_untitledShaderi_result;
                        }
                    """.trimIndent()
                )
            }
        }
    }
})

