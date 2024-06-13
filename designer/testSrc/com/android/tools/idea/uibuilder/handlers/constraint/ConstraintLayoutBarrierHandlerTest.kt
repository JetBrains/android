package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.uibuilder.LayoutTestCase
import icons.StudioIcons

private val VERTICAL_BARRIER_DIRECTIONS =
  setOf(
    SdkConstants.CONSTRAINT_BARRIER_START,
    SdkConstants.CONSTRAINT_BARRIER_END,
    SdkConstants.CONSTRAINT_BARRIER_LEFT,
    SdkConstants.CONSTRAINT_BARRIER_RIGHT,
  )

private val HORIZONTAL_BARRIER_DIRECTION =
  setOf(SdkConstants.CONSTRAINT_BARRIER_TOP, SdkConstants.CONSTRAINT_BARRIER_BOTTOM)

class ConstraintLayoutBarrierHandlerTest : LayoutTestCase() {

  fun testIcon() {
    val barrierHandler = ConstraintLayoutBarrierHandler()

    for (direction in VERTICAL_BARRIER_DIRECTIONS) {
      val model = createNlModelWithBarrier(direction)
      val barrier = model.find("barrier")!!
      assertEquals(
        StudioIcons.LayoutEditor.Palette.BARRIER_VERTICAL,
        barrierHandler.getIcon(barrier),
      )
    }

    for (direction in HORIZONTAL_BARRIER_DIRECTION) {
      val model = createNlModelWithBarrier(direction)
      val barrier = model.find("barrier")!!
      assertEquals(
        StudioIcons.LayoutEditor.Palette.BARRIER_HORIZONTAL,
        barrierHandler.getIcon(barrier),
      )
    }
  }

  fun testTitle() {
    val barrierHandler = ConstraintLayoutBarrierHandler()

    run {
      val model = createNlModelWithBarrierWithoutDirection()
      val barrier = model.find("barrier")!!
      assertEquals("Barrier", barrierHandler.getTitle(barrier))
    }

    for (direction in VERTICAL_BARRIER_DIRECTIONS) {
      val model = createNlModelWithBarrier(direction)
      val barrier = model.find("barrier")!!
      assertEquals("Vertical Barrier", barrierHandler.getTitle(barrier))
    }

    for (direction in HORIZONTAL_BARRIER_DIRECTION) {
      val model = createNlModelWithBarrier(direction)
      val barrier = model.find("barrier")!!
      assertEquals("Horizontal Barrier", barrierHandler.getTitle(barrier))
    }
  }

  private fun createNlModelWithBarrier(direction: String): SyncNlModel {
    val builder =
      model(
        "constraint_barrier.xml",
        component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
          .withBounds(0, 0, 1000, 1500)
          .id("@id/constraint")
          .matchParentWidth()
          .matchParentHeight()
          .children(
            component(AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER.newName())
              .withBounds(100, 100, 100, 100)
              .id("@+id/barrier")
              .width("wrap_content")
              .height("wrap_content")
              .withAttribute(
                SdkConstants.SHERPA_URI,
                SdkConstants.ATTR_BARRIER_DIRECTION,
                direction,
              )
          ),
      )
    return builder.build()
  }

  private fun createNlModelWithBarrierWithoutDirection(): SyncNlModel {
    val builder =
      model(
        "constraint_barrier.xml",
        component(AndroidXConstants.CONSTRAINT_LAYOUT.newName())
          .withBounds(0, 0, 1000, 1500)
          .id("@id/constraint")
          .matchParentWidth()
          .matchParentHeight()
          .children(
            component(AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER.newName())
              .withBounds(100, 100, 100, 100)
              .id("@+id/barrier")
              .width("wrap_content")
              .height("wrap_content")
          ),
      )
    return builder.build()
  }
}
