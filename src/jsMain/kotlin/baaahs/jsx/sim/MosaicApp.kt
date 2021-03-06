@file:JsModule("js/simulator/MosaicApp.jsx")
@file:JsNonModule

package baaahs.jsx.sim

import baaahs.SheepSimulator
import baaahs.app.ui.AppIndexProps
import react.FunctionalComponent
import react.RProps

@JsName("default")
external class MosaicApp(props: Props) {
    interface Props : RProps {
        var simulator: SheepSimulator
        var webClientWindow: FunctionalComponent<AppIndexProps>
    }
}
