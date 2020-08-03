package baaahs

import baaahs.glshaders.GlslAnalyzer
import baaahs.glshaders.GlslProgram
import baaahs.glshaders.OpenShader
import baaahs.glshaders.Plugins
import baaahs.glsl.GlslContext
import baaahs.model.ModelInfo
import baaahs.show.DataSource
import baaahs.show.Shader
import baaahs.show.Show

interface ShowPlayer {
    val plugins: Plugins
    val glslContext: GlslContext
    val modelInfo: ModelInfo
    val dataSources: List<DataSource>

    fun <T : Gadget> createdGadget(id: String, gadget: T)
    fun <T : Gadget> useGadget(id: String): T

    fun openShader(shader: Shader, addToCache: Boolean = false): OpenShader
    fun openDataFeed(id: String, dataSource: DataSource): GlslProgram.DataFeed
    fun useDataFeed(dataSource: DataSource): GlslProgram.DataFeed
    fun openShow(show: Show): OpenShow = OpenShow(show, this)

    fun releaseUnused()
}

abstract class BaseShowPlayer(
    final override val plugins: Plugins,
    final override val modelInfo: ModelInfo
) : ShowPlayer {
    private val glslAnalyzer = GlslAnalyzer()

    private val dataFeeds = mutableMapOf<DataSource, GlslProgram.DataFeed>()
    private val shaders = mutableMapOf<Shader, OpenShader>()

    override val dataSources: List<DataSource> get() = dataFeeds.keys.toList()

    override fun openDataFeed(id: String, dataSource: DataSource): GlslProgram.DataFeed {
        return dataFeeds.getOrPut(dataSource) { dataSource.createFeed(this, id) }
    }

    override fun useDataFeed(dataSource: DataSource): GlslProgram.DataFeed {
        return dataFeeds[dataSource]!!
    }

    override fun openShader(shader: Shader, addToCache: Boolean): OpenShader {
        return if (addToCache) {
            shaders.getOrPut(shader) { glslAnalyzer.asShader(shader) }
        } else {
            shaders[shader] ?: glslAnalyzer.asShader(shader)
        }
    }

    override fun releaseUnused() {
        ArrayList(dataFeeds.entries).forEach { (dataSource, dataFeed) ->
            if (!dataFeed.inUse()) dataFeeds.remove(dataSource)
        }

        ArrayList(shaders.entries).forEach { (shader, openShader) ->
            if (!openShader.inUse()) shaders.remove(shader)
        }
    }
}