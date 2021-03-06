package baaahs.app.ui.editor.layout

import baaahs.app.ui.CommonIcons
import baaahs.app.ui.appContext
import baaahs.show.mutable.MutableShow
import baaahs.show.mutable.MutableTab
import baaahs.ui.Prompt
import baaahs.ui.unaryPlus
import baaahs.ui.value
import baaahs.ui.xComponent
import kotlinx.css.Display
import kotlinx.css.GridTemplateColumns
import kotlinx.css.display
import kotlinx.css.gridTemplateColumns
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import materialui.components.tab.tab
import materialui.components.tabs.tabs
import materialui.icon
import org.w3c.dom.events.Event
import react.*
import react.dom.div
import styled.inlineStyles

val LayoutEditor = xComponent<LayoutEditorProps>("LayoutEditor") { props ->
    val appContext = useContext(appContext)
    val styles = appContext.allStyles.layoutEditor

    var currentTabIndex by state { 0 }
    val mutableLayouts = props.mutableShow.layouts
    val mutableLayout = mutableLayouts.formats[props.format]!!
    val currentTab = mutableLayout.tabs[currentTabIndex]

    val handleTabChange = handler("handleTabChange") { e: Event ->
        currentTabIndex = e.target.value.toInt()
    }

    val handleNewTabClick = handler("handleNewTabClick") { _: Event ->
        appContext.prompt(
            Prompt(
                "Create New Tab",
                "Enter a name for your new tab.",
                "",
                fieldLabel = "Tab Name",
                cancelButtonLabel = "Cancel",
                submitButtonLabel = "Create",
                isValid = { name ->
                    if (name.isBlank()) return@Prompt "No name given."

                    if (mutableLayout.tabs.any { it.title == name }) {
                        "Looks like there's already a tab named named \"$name\"."
                    } else null
                },
                onSubmit = { name ->
                    val newTab = MutableTab(name)
                    mutableLayout.tabs.add(newTab)
                    props.onGridChange()
                }
            )
        )
    }

    div {
        tabs {
            attrs.value = currentTabIndex.toString()
            attrs.onChangeFunction = handleTabChange

            mutableLayout.tabs.forEachIndexed { index, tab ->
                tab {
                    attrs.value = index.toString()
                    attrs.label { +currentTab.title }
                }
            }

            tab {
                attrs.value = currentTabIndex.toString()
                attrs.icon { icon(CommonIcons.Add) }
                attrs.disabled = true
                attrs.onClickFunction = handleNewTabClick
            }
        }

        div(+styles.editorGrid) {
            // grid holder
            inlineStyles {
                display = Display.grid
                gridTemplateColumns = GridTemplateColumns(
                    MutableList(currentTab.columns.size + 1) { "1fr" }.joinToString(" ")
                )
//                        gridTemplateAreas = GridTemplateAreas(areas.joinToString(" ") { "\"$it\"" })
//                        gridTemplateColumns = GridTemplateColumns(currentTab.columns.joinToString(" "))
//                        gridTemplateRows = GridTemplateRows(currentTab.rows.joinToString(" "))
            }

            (-1 until currentTab.rows.size).forEach { rowIndex ->
                (-1 until currentTab.columns.size).forEach { columnIndex ->
                    if (rowIndex == -1 && columnIndex == -1) {
                        // Empty cell at top-left.
                        div {}
                    } else if (rowIndex == -1) {
                        // Cell along the top.
                        layoutSizeCell {
                            attrs.key = "col$columnIndex"
                            attrs.dimen = currentTab.columns[columnIndex]
                            attrs.onChange = props.onGridChange
                        }
                    } else if (columnIndex == -1) {
                        // Cell along the left.
                        layoutSizeCell {
                            attrs.key = "row$rowIndex"
                            attrs.dimen = currentTab.rows[rowIndex]
                            attrs.onChange = props.onGridChange
                        }
                    } else {
                        layoutAreaCell {
                            attrs.key = "area$rowIndex,$columnIndex"
                            attrs.layouts = mutableLayouts
                            attrs.tab = currentTab
                            attrs.columnIndex = columnIndex
                            attrs.rowIndex = rowIndex
                            attrs.onChange = props.onGridChange
                        }
                    }
                }
            }
        }
    }
}

external interface LayoutEditorProps : RProps {
    var mutableShow: MutableShow
    var format: String
    var onGridChange: () -> Unit
}

fun RBuilder.layoutEditor(handler: RHandler<LayoutEditorProps>) =
    child(LayoutEditor, handler = handler)