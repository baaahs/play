package baaahs.glshaders

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.expect

object ColorShaderSpec : Spek({
    describe("ColorShader") {
        val shaderText by value<String> { TODO() }
        val shader by value { GlslAnalyzer().asShader(shaderText) as ColorShader }
        val namespace by value { GlslCode.Namespace("p0") }

        context("generic shaders") {
            override(shaderText) {
                /**language=glsl*/
                """
                        // This Shader's Name
                        // Other stuff.
                        
                        uniform float time;
                        uniform vec2  resolution;
                        uniform vec2  mouse;
                        uniform float blueness;
                        int someGlobalVar;
                        const int someConstVar = 123;
                        
                        float identity(float value) { return value; }
    
                        void main( void ) {
                            vec2 uv = gl_FragCoord.xy / resolution.xy;
                            gl_FragColor = vec4(uv.xy, identity(blueness), 1.);
                        }
                        """.trimIndent()
            }

            it("finds magic uniforms") {
                expect(listOf(
                    InputPort("time", "float", "Time", ContentType.Time),
                    InputPort("resolution", "vec2", "Resolution", ContentType.Resolution),
                    InputPort("mouse", "vec2", "Mouse", ContentType.Mouse),
                    InputPort("blueness", "float", "Blueness"),
                    InputPort("gl_FragCoord", "vec4", "Coordinates", ContentType.UvCoordinate)
                )) { shader.inputPorts.map { it.copy(glslVar = null) } }
            }

            it("generates function declarations") {
                expect(
                    """
                        #line 8
                        int p0_someGlobalVar;
                        
                        #line 9
                        const int p0_someConstVar = 123;
                        
                        #line 11
                        float p0_identity(float value) { return value; }
                        
                        #line 13
                        void p0_main( void ) {
                            vec2 uv = gl_FragCoord.xy / in_resolution.xy;
                            sm_pixelColor = vec4(uv.xy, p0_identity(aquamarinity), 1.);
                        }
                        """.trimIndent()
                ) {
                    shader.toGlsl(
                        namespace, mapOf(
                            "resolution" to "in_resolution",
                            "blueness" to "aquamarinity",
                            "identity" to "p0_identity",
                            "gl_FragColor" to "sm_pixelColor"
                        )).trim()
                }
            }

            it("generates invocation GLSL") {
                expect("p0_main()") { shader.invocationGlsl(namespace) }
            }
        }

        context("ShaderToy shaders") {
            override(shaderText) {
                /**language=glsl*/
                """
                        // This Shader's Name
                        // Other stuff.
                        
                        uniform float blueness;
                        int someGlobalVar;
                        const int someConstVar = 123;

                        float identity(float value) { return value; }
    
                        void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
                            vec2 uv = fragCoord.xy / iResolution.xy * iTime;
                            fragColor = vec4(uv.xy / iMouse, identity(blueness), 1.);
                        }
                        """.trimIndent()
            }

            describe("#inputPorts") {
                it("finds magic uniforms") {
                    expect(listOf(
                        InputPort("blueness", "float", "Blueness"),
                        InputPort("iResolution", "vec3", "Resolution", ContentType.Resolution),
                        InputPort("iTime", "float", "Time", ContentType.Time),
                        InputPort("iMouse", "vec2", "Mouse", ContentType.Mouse),
                        InputPort("sm_FragCoord", "vec2", "Coordinates", ContentType.UvCoordinate)
                    )) { shader.inputPorts.map { it.copy(glslVar = null) } }
                }
            }

            it("generates function declarations") {
                expect(
                    """
                        #line 5
                        int p0_someGlobalVar;
                        
                        #line 6
                        const int p0_someConstVar = 123;
                        
                        #line 8
                        float p0_identity(float value) { return value; }
                        
                        #line 10
                        void p0_mainImage( out vec4 fragColor, in vec2 fragCoord ) {
                            vec2 uv = fragCoord.xy / in_resolution.xy * in_time;
                            fragColor = vec4(uv.xy / in_mouse, p0_identity(aquamarinity), 1.);
                        }
                        """.trimIndent()
                ) { shader.toGlsl(
                    namespace, mapOf(
                        "iResolution" to "in_resolution",
                        "iMouse" to "in_mouse",
                        "iTime" to "in_time",
                        "blueness" to "aquamarinity",
                        "identity" to "p0_identity"
                    )).trim() }
            }

            it("generates invocation GLSL") {
                expect("p0_mainImage(sm_pixelColor, sm_FragCoord.xy)") {
                    shader.invocationGlsl(namespace)
                }
            }
        }
    }
})