package baaahs

import org.w3c.dom.*

@Suppress("ObsoleteKotlinJsPackages")
val document get() = kotlin.browser.document

@Suppress("ObsoleteKotlinJsPackages")
val window get() = kotlin.browser.window

var Element.disabled: Boolean
    get() = getAttribute("disabled") == "disabled"
    set(value) {
        if (value) {
            setAttribute("disabled", "disabled")
        } else {
            removeAttribute("disabled")
        }
    }

fun <T> ItemArrayLike<T>.forEach(action: (T) -> Unit) {
    for (i in 0 until length) {
        action(item(i)!!)
    }
}

fun DOMTokenList.clear() {
    while (length > 0) {
        remove(item(0)!!)
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : HTMLElement> HTMLElement.first(className: String) : T = (getElementsByClassName(className)[0] as T?)!!

fun HTMLCanvasElement.context2d() = this.getContext("2d")!! as CanvasRenderingContext2D
