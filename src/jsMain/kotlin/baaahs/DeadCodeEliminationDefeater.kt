package baaahs

object DeadCodeEliminationDefeater {
    // Protection from dead code elimination; this method is never actually called.
    fun noDCE() {
        // Entry points to Kotlin code that are only called by JS:
        baaahs.sim.ui.Console(nuffin())
        baaahs.sim.ui.GeneratedGlslPalette(nuffin())
        baaahs.visualizer.ui.VisualizerPanel(nuffin())
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> nuffin(): T = null as T
}
