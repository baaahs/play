package baaahs.gl.shader

import baaahs.RefCounted
import baaahs.RefCounter
import baaahs.gl.glsl.GlslCode
import baaahs.gl.glsl.GlslCode.GlslFunction
import baaahs.gl.glsl.GlslCode.Namespace
import baaahs.gl.glsl.GlslError
import baaahs.gl.glsl.GlslType
import baaahs.gl.glsl.ShaderAnalysis
import baaahs.gl.shader.dialect.ShaderDialect
import baaahs.gl.shader.type.ShaderType
import baaahs.only
import baaahs.show.Shader
import baaahs.unknown
import kotlin.collections.set

interface OpenShader : RefCounted {
    val shader: Shader
    val src: String get() = glslCode.src
    val glslCode: GlslCode
    val title: String
    val entryPoint: GlslFunction

    val inputPorts: List<InputPort>
    val outputPort: OutputPort

    val shaderType: ShaderType
    val shaderDialect: ShaderDialect

    val errors: List<GlslError>

    fun findInputPortOrNull(portId: String): InputPort? =
        inputPorts.find { it.id == portId }

    fun findInputPort(portId: String): InputPort =
        findInputPortOrNull(portId)
            ?: error(unknown("input port", portId, inputPorts))

    val portStructs: List<GlslType.Struct> get() =
        (inputPorts.map { it.contentType.glslType } + outputPort.contentType.glslType)
            .filterIsInstance<GlslType.Struct>()

    fun toGlsl(namespace: Namespace, portMap: Map<String, String> = emptyMap()): String

    fun invocationGlsl(
        namespace: Namespace,
        resultVar: String,
        portMap: Map<String, String> = emptyMap()
    ): String

    class Base(
        override val shader: Shader,
        override val glslCode: GlslCode,
        override val entryPoint: GlslFunction,
        override val inputPorts: List<InputPort>,
        override val outputPort: OutputPort,
        override val shaderType: ShaderType,
        override val shaderDialect: ShaderDialect,
        override val errors: List<GlslError> = emptyList()
    ) : OpenShader, RefCounted by RefCounter() {

        constructor(shaderAnalysis: ShaderAnalysis, shaderType: ShaderType) : this(
            shaderAnalysis.shader, shaderAnalysis.glslCode, shaderAnalysis.entryPoint!!,
            shaderAnalysis.inputPorts, shaderAnalysis.outputPorts.only(), shaderType,
            shaderAnalysis.shaderDialect
        )

        override val title: String get() = shader.title

        override fun toGlsl(namespace: Namespace, portMap: Map<String, String>): String {
            val buf = StringBuilder()

            val nonUniformGlobalsMap = hashMapOf<String, String>()
            glslCode.globalVars.forEach { glslVar ->
                if (!glslVar.isUniform && !glslVar.isVarying) {
                    nonUniformGlobalsMap[glslVar.name] = namespace.qualify(glslVar.name)
                    buf.append(glslVar.toGlsl(namespace, glslCode.symbolNames, emptyMap()))
                    buf.append("\n")
                }
            }

            val uniformGlobalsMap = portMap.filter { (id, _) ->
                val inputPort = findInputPortOrNull(id)

                inputPort?.isGlobal == true ||
                        (outputPort.id == id && !outputPort.isParam)
            }

            val symbolsToNamespace = glslCode.symbolNames.toSet() - portStructs.map { it.name}
            val symbolMap = uniformGlobalsMap + nonUniformGlobalsMap
            glslCode.functions.forEach { glslFunction ->
                buf.append(glslFunction.toGlsl(namespace, symbolsToNamespace, symbolMap))
                buf.append("\n")
            }

            return buf.toString()
        }

        override fun invocationGlsl(namespace: Namespace, resultVar: String, portMap: Map<String, String>): String {
            return entryPoint.invocationGlsl(namespace, resultVar, portMap)
        }

        override fun equals(other: Any?): Boolean =
            other != null
                    && other is Base
                    && this::class == other::class
                    && this.src == other.src

        override fun hashCode(): Int =
            src.hashCode()
    }
}