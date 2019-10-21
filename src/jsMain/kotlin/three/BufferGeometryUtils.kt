@file:JsQualifier("THREE")
package three

import info.laht.threekt.core.BufferGeometry

open external class BufferGeometryUtils() {
    companion object {
        fun mergeBufferGeometries(
            geometries: Array<BufferGeometry>,
            useGroups: Boolean = definedExternally
        ): BufferGeometry
    }
}