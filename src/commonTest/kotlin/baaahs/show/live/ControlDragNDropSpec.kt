package baaahs.show.live

import baaahs.app.ui.Draggable
import baaahs.getBang
import baaahs.gl.override
import baaahs.show.live.ControlDisplay.PanelBuckets.PanelBucket
import baaahs.show.mutable.MutableShow
import baaahs.show.mutable.ShowBuilder
import baaahs.shows.FakeGlContext
import baaahs.shows.FakeKgl
import baaahs.shows.FakeShowPlayer
import baaahs.toBeSpecified
import baaahs.unknown
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.expect

object ControlDragNDropSpec : Spek({
    describe("Control Drag & Drop") {
        val mutableShow by value {
            MutableShow("Show")
                .editLayouts { copyFrom(createLayouts("Panel 1", "Panel 2", "Panel 3")) }
        }
        val show by value { mutableShow.build(ShowBuilder()) }

        val showPlayer by value { FakeShowPlayer(FakeGlContext(FakeKgl())) }
        val openShow by value { show.open(showPlayer) }
        val editMode by value { true }
        val editHandler by value { FakeEditHandler() }
        val dragNDrop by value { FakeDragNDrop() }
        val controlDisplay by value { ControlDisplay(openShow, editMode, editHandler, dragNDrop) }

        fun renderEditedShow(): String {
            val editedOpenShow = editHandler.updatedShow.open(showPlayer)
            val newControlDisplay = ControlDisplay(editedOpenShow, editMode, editHandler, dragNDrop)
            return editedOpenShow.fakeRender(newControlDisplay)
        }

        beforeEachTest {
            mutableShow.addFixtureControls()
            mutableShow.addButton("Panel 1", "Button A") {}
            mutableShow.addButton("Panel 1", "Button B") {}
        }

        val panelBuckets by value {
            openShow.layouts.panelNames.associateWith { panelName ->
                controlDisplay.renderBuckets(panelName)
            }
        }

        fun findBucket(panelName: String, sectionTitle: String): PanelBucket {
            val panelBucket = panelBuckets.getBang(panelName, "Panel")
            return panelBucket.find { it.section.title == sectionTitle }
                ?: error(unknown("section", sectionTitle, panelBucket.map { it.section.title }))
        }

        val fromBucket by value { toBeSpecified<PanelBucket>() }
        val toBucket by value { toBeSpecified<PanelBucket>() }
        val draggedControl by value { toBeSpecified<Draggable>() }

        it("has the expected initial appearance") {
            expect(
                """
                    Panel 1:
                      |Show| scenesButtonGroup[*scene1Button*, scene2Button], buttonAButton, buttonBButton
                      |Scene 1|
                      |Backdrop 1.1|
                    Panel 2:
                      |Show|
                      |Scene 1| backdropsButtonGroup[*backdrop11Button*, backdrop12Button]
                      |Backdrop 1.1|
                    Panel 3:
                      |Show|
                      |Scene 1| slider1SliderControl
                      |Backdrop 1.1|
                """.trimIndent()
            ) { openShow.fakeRender(controlDisplay) }
        }

        context("when a button is dragged") {
            override(fromBucket) { findBucket("Panel 1", "Show") }
            override(draggedControl) { fromBucket.getDraggable(1) }

            it("can be dropped back in the same bucket") {
                expect(true) { draggedControl.willMoveTo(fromBucket) }
            }

            context("and dropped within the same DropTarget") {
                override(toBucket) { fromBucket }

                beforeEachTest {
                    dragNDrop.doMove(fromBucket, 1, fromBucket, 2)
                }

                it("reorders controls in the parent") {
                    expect(
                        listOf("scenesButtonGroup", "buttonBButton", "buttonAButton")
                    ) { editHandler.updatedShow.controlLayout["Panel 1"] }
                }
            }

            it("can be dropped back into another bucket") {
                expect(true) { draggedControl.willMoveTo(findBucket("Panel 1", "Scene 1")) }
            }

            context("and dropped to another DropTarget") {
                override(toBucket) { findBucket("Panel 1", "Scene 1") }

                beforeEachTest {
                    dragNDrop.doMove(fromBucket, 1, toBucket, 0)
                }

                it("removes the control from prior parent and adds it to the new parent") {
                    expect(
                        """
                            Panel 1:
                              |Show| scenesButtonGroup[*scene1Button*, scene2Button], buttonBButton
                              |Scene 1| buttonAButton
                              |Backdrop 1.1|
                            Panel 2:
                              |Show|
                              |Scene 1| backdropsButtonGroup[*backdrop11Button*, backdrop12Button]
                              |Backdrop 1.1|
                            Panel 3:
                              |Show|
                              |Scene 1| slider1SliderControl
                              |Backdrop 1.1|
                        """.trimIndent()
                    ) { renderEditedShow() }
                }
            }
        }
    }
})