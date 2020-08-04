package baaahs.glshaders

import baaahs.Logger
import baaahs.Surface
import baaahs.getBang
import baaahs.glsl.GlslContext
import baaahs.show.ShaderChannel
import baaahs.show.Surfaces
import baaahs.show.live.LiveShaderInstance
import baaahs.show.live.LiveShaderInstance.DataSourceLink
import baaahs.show.live.LiveShaderInstance.ShaderOutLink
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class LinkedPatch(
    val shaderInstance: LiveShaderInstance,
    val surfaces: Surfaces
) {
    private val dataSourceLinks: Set<DataSourceLink>
    private val componentLookup: Map<LiveShaderInstance, Component>
    private val components = arrayListOf<Component>()

    init {
        val instanceNodes = hashMapOf<LiveShaderInstance, InstanceNode>()

        fun traverseLinks(liveShaderInstance: LiveShaderInstance, depth: Int = 0): Set<DataSourceLink> {
            instanceNodes.getOrPut(liveShaderInstance) { InstanceNode(liveShaderInstance) }
                .atDepth(depth)

            val dataSourceLinks = hashSetOf<DataSourceLink>()
            liveShaderInstance.incomingLinks.forEach { (_, link) ->
                when (link) {
                    is DataSourceLink -> dataSourceLinks.add(link)
                    is ShaderOutLink -> traverseLinks(link.shaderInstance, depth + 1)
                        .also { dataSourceLinks.addAll(it) }
                }
            }
            return dataSourceLinks
        }

        dataSourceLinks = traverseLinks(shaderInstance)
//        println("instanceNodes:\n  " +
//                instanceNodes
//                    .entries
//                    .sortedByDescending { (_, v) -> v.maxDepth }
//                    .map { (k, v) -> "${k}(${v.maxDepth})" }
//                    .joinToString("\n  ")
//        )

        val componentsByChannel = hashMapOf<ShaderChannel, Component>()
        componentLookup = instanceNodes.values.sortedByDescending { it.maxDepth }.mapIndexed { index, instanceNode ->
            val component = Component(index, instanceNode)
            components.add(component)
//            shaderInstance.shaderChannel?.let { componentsByChannel[it] = component }
            instanceNode.liveShaderInstance to component
        }.associate { it }
        componentsByChannel[ShaderChannel.Main]?.redirectOutputTo("sm_pixelColor")
    }


    class InstanceNode(val liveShaderInstance: LiveShaderInstance, var maxDepth: Int = 0) {
        fun atDepth(depth: Int) {
            if (depth > maxDepth) maxDepth = depth
        }
    }

    inner class Component(
        val index: Int,
        val instanceNode: InstanceNode
    ) {
        private val shaderInstance = instanceNode.liveShaderInstance
        val title: String get() = shaderInstance.shader.title
        private val prefix = "p$index"
        private val namespace = GlslCode.Namespace(prefix)
        private val portMap: Map<String, Lazy<String>>
        private val resultInReturnValue: Boolean
        private var resultVar: String

        init {
            val tmpPortMap = hashMapOf<String, Lazy<String>>()

            shaderInstance.incomingLinks.forEach { (toPortId, fromLink) ->
                when (fromLink) {
                    is ShaderOutLink -> {
                        tmpPortMap[toPortId] = lazy {
                            val fromComponent = componentLookup.getBang(fromLink.shaderInstance, "shader")
                            val outputPort = fromLink.shaderInstance.shader.outputPort
                            if (outputPort.isReturnValue()) {
                                fromComponent.resultVar
                            } else {
                                fromComponent.namespace.qualify(outputPort.id)
                            }
                        }
                    }
                    is DataSourceLink -> {
                        tmpPortMap[toPortId] = lazy {
                            fromLink.dataSource.getVarName(fromLink.varName)
                        }
                    }
                    else -> logger.warn { "Unexpected incoming link $fromLink for $toPortId" }
                }
            }

            var usesReturnValue = false
            val outputPort = shaderInstance.shader.outputPort
            if (outputPort.isReturnValue()) {
                usesReturnValue = true
                resultVar = namespace.internalQualify("result")
            } else {
                resultVar = namespace.qualify(outputPort.id)
                tmpPortMap[outputPort.id] = lazy { resultVar }
            }

            portMap = tmpPortMap
            resultInReturnValue = usesReturnValue
        }

        var outputVar: String = resultVar
        private var resultRedirected = false

        private val resolvedPortMap by lazy {
            portMap.mapValues { (_, v) -> v.value } +
                    mapOf(shaderInstance.shader.outputPort.id to outputVar)
        }

        fun redirectOutputTo(varName: String) {
            outputVar = varName
            resultRedirected = true
        }

        fun appendStructs(buf: StringBuilder) {
            shaderInstance.shader.glslCode.structs.forEach { struct ->
                // TODO: we really ought to namespace structs, but that's not straightforward because
                // multiple shaders might share a uniform input (e.g. ModelInfo).

//                val qualifiedName = namespace.qualify(struct.name)
//                val structText = struct.fullText.replace(struct.name, qualifiedName)
                val structText = struct.fullText
                buf.append(structText, "\n")
            }
        }

        fun appendDeclaratoryLines(buf: StringBuilder) {
            val openShader = shaderInstance.shader

            buf.append("// Shader: ", openShader.title, "; namespace: ", prefix, "\n")
            buf.append("// ", openShader.title, "\n")

            if (!resultRedirected) {
                buf.append("\n")
                buf.append("${openShader.outputPort.dataType} $resultVar;\n")
            }

            buf.append(openShader.toGlsl(namespace, resolvedPortMap), "\n")
        }

        fun invokeAndSetResultGlsl(): String {
            return shaderInstance.shader.invocationGlsl(namespace, resultVar, resolvedPortMap)
        }
    }

    fun toGlsl(): String {
        val buf = StringBuilder()
        buf.append("#ifdef GL_ES\n")
        buf.append("precision mediump float;\n")
        buf.append("#endif\n")
        buf.append("\n")
        buf.append("// SparkleMotion-generated GLSL\n")
        buf.append("\n")
        buf.append("layout(location = 0) out vec4 sm_pixelColor;\n")
        buf.append("\n")

        components.forEach { component ->
            component.appendStructs(buf)
        }

        dataSourceLinks.sortedBy { it.varName }.forEach { (dataSource, varName) ->
            if (!dataSource.isImplicit())
                buf.append("uniform ${dataSource.getType()} ${dataSource.getVarName(varName)};\n")
        }
        buf.append("\n")

        components.forEach { component ->
            component.appendDeclaratoryLines(buf)
        }

        buf.append("\n#line -1\n")
        buf.append("void main() {\n")
        components.forEach { component ->
            buf.append("  ", component.invokeAndSetResultGlsl(), "; // ${component.title}\n")
        }
        buf.append("  sm_pixelColor = ", components.last().outputVar, ";\n")
        buf.append("}\n")
        return buf.toString()
    }

    fun compile(glslContext: GlslContext, resolver: Resolver): GlslProgram =
        GlslProgram(glslContext, this, resolver)

    fun createProgram(
        glslContext: GlslContext,
        dataFeeds: Map<String, GlslProgram.DataFeed>
    ): GlslProgram {
        return compile(glslContext) { id, dataSource -> dataFeeds.getBang(id, "data feed") }
    }

    fun matches(surface: Surface): Boolean = this.surfaces.matches(surface)

    fun bind(glslProgram: GlslProgram, resolver: Resolver): List<GlslProgram.Binding> {
        return dataSourceLinks.mapNotNull { (dataSource, id) ->
            if (dataSource.isImplicit()) return@mapNotNull null
            val dataFeed = resolver.invoke(id, dataSource)

            if (dataFeed != null) {
                val binding = dataFeed.bind(glslProgram)
                if (binding.isValid) binding else {
                    logger.debug { "unused uniform for $dataSource?" }
                    binding.release()
                    null
                }
            } else {
                logger.warn { "no UniformProvider bound for $dataSource" }
                null
            }
        }
    }

    companion object {
        private val logger = Logger("OpenPatch")
    }
}