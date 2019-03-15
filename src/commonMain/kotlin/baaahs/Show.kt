package baaahs

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

enum class ShaderType {
    SOLID,
    PIXEL
}

abstract class ShaderBuffer(val type: ShaderType) {
    abstract fun serialize(writer: ByteArrayWriter)
}

class SolidShaderBuffer : ShaderBuffer(ShaderType.SOLID) {
    var color: Color = Color.WHITE

    override fun serialize(writer: ByteArrayWriter) {
        color.serialize(writer)
    }

    companion object {
        fun parse(reader: ByteArrayReader): SolidShaderBuffer {
            val buf = SolidShaderBuffer()
            buf.color = Color.parse(reader)
            return buf
        }
    }
}

class PixelShaderBuffer : ShaderBuffer(ShaderType.PIXEL) {
    var fakeyTerribleHardCodedNumberOfPixels: Int = 1337
    var colors: MutableList<Color> = ((0..fakeyTerribleHardCodedNumberOfPixels).map { Color.WHITE }).toMutableList()

    override fun serialize(writer: ByteArrayWriter) {
        writer.writeInt(colors.size)
        colors.forEach { color -> color.serialize(writer) }
    }

    companion object {
        fun parse(reader: ByteArrayReader): PixelShaderBuffer {
            val buf = PixelShaderBuffer()
            (0 until reader.readInt()).forEach { index -> buf.colors[index] = Color.parse(reader) }
            return buf
        }
    }

}

interface Show {

    /*{
        63: { shader: solid, data: { color: blue } }
    }*/

    fun nextFrame()
}

class SomeDumbShow(sheepModel: SheepModel, showRunner: ShowRunner) : Show {
    val colorPicker = showRunner.getColorPicker()
//    val panelShaderBuffers = sheepModel.allPanels.map { showRunner.getSolidShaderBuffer(it) }
    val pixelShaderBuffers = sheepModel.allPanels.map { showRunner.getPixelShaderBuffer(it) }
    val movingHeadBuffers = sheepModel.eyes.map { showRunner.getMovingHeadBuffer(it) }

    init {
        println("Creating new SomeDumbShow, we have ${pixelShaderBuffers.size} buffers")
    }

    override fun nextFrame() {
//        panelShaderBuffers.forEach { shaderBuffer -> shaderBuffer.color = Color.random() }
        val seed = Random(0)

        pixelShaderBuffers.forEach { shaderBuffer ->
            val baseSaturation = seed.nextFloat()
            val panelColor = if (seed.nextFloat() < 0.1) Color.random() else colorPicker.color

            shaderBuffer.colors.forEachIndexed { i, pixel ->
                shaderBuffer.colors[i] = desaturateRandomishly(baseSaturation, seed, panelColor)
            }

            movingHeadBuffers.forEach { buf ->
                buf.colorIllicitDontUse = colorPicker.color
                buf.rotAIllicitDontUse += (nextRandomFloat(seed) - .5).toFloat() / 1000
                buf.rotBIllicitDontUse += (nextRandomFloat(seed) - .5).toFloat() / 1000
            }
        }
    }

    private fun desaturateRandomishly(
        baseSaturation: Float,
        seed: Random,
        panelColor: Color
    ): Color {
        val saturation = baseSaturation * abs(nextRandomFloat(seed)).toFloat()
        val desaturatedColor = panelColor.withSaturation(saturation)
        return desaturatedColor
    }

    private fun nextRandomFloat(seed: Random) = sin(seed.nextDouble() + getTimeMillis() / 1000.toDouble())
}