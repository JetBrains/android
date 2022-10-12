/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.uibuilder.handlers.relative.draw

import com.android.SdkConstants.*
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.SceneMouseInteraction
import com.android.tools.idea.uibuilder.scene.SceneTest

class RelativeLayoutDecoratorTest: SceneTest() {

  private var originalShowOnlySelection = false

  override fun setUp() {
    super.setUp()
    originalShowOnlySelection = SceneContext.get().showOnlySelection()
  }

  override fun tearDown() {
    SceneContext.get().setShowOnlySelection(originalShowOnlySelection)
    super.tearDown()
  }

  override fun createModel(): ModelBuilder {
    return model("relative.xml",
        component(RELATIVE_LAYOUT)
            .id("@+id/root")
            .withBounds(0, 0, 1000, 1000)
            .width("1000dp")
            .height("1000dp")
            .withAttribute("android:padding", "20dp")
            .children(
                component(PROGRESS_BAR)
                    .id("@+id/a")
                    .withBounds(450, 450, 100, 100)
                    .width("100dp")
                    .height("100dp")
            )
    )
  }

  fun testBasicScene() {
    val expectedXml =
"""<ProgressBar
    android:id="@+id/a"
    android:layout_width="100dp"
    android:layout_height="100dp"/>"""

    myScreen.get("@+id/a").expectXml(expectedXml)

    val expectedSerializedList =
"""DrawNlComponentFrame,0,0,500,500,1,1000,1000
Clip,0,0,500,500
DrawComponentBackground,225,225,50,50,1
DrawProgressBar,225,225,50,50
DrawNlComponentFrame,225,225,50,50,1,100,100
UNClip
"""
    SceneContext.get().setShowOnlySelection(false)
    checkModelDrawCommand(myModel, expectedSerializedList)
  }

  fun testNoChild() {
    val builder = model("relative.xml",
        component(RELATIVE_LAYOUT)
            .id("@+id/root")
            .withBounds(0, 0, 1000, 1000)
            .width("1000dp")
            .height("1000dp")
            .withAttribute("android:padding", "20dp")
    )

    val expectedDrawCommand =
"""DrawNlComponentFrame,0,0,500,500,1,1000,1000
Clip,0,0,500,500
UNClip
"""
    SceneContext.get().setShowOnlySelection(false)
    checkModelDrawCommand(builder.build(), expectedDrawCommand)
  }

  fun testCenterAttributes() {
    val builder = model("relative.xml",
      component(RELATIVE_LAYOUT)
          .id("@+id/root")
          .withBounds(0, 0, 1000, 1000)
          .width("1000dp")
          .height("1000dp")
          .withAttribute("android:padding", "20dp")
          .children(
              component(PROGRESS_BAR)
                  .id("@+id/a")
                  .withBounds(450, 450, 100, 100)
                  .width("100dp")
                  .height("100dp")
                  .withAttribute("android:layout_centerInParent", "true"),
              component(BUTTON)
                  .id("@+id/b")
                  .withBounds(450, 490, 100, 20)
                  .width("100dp")
                  .height("20dp")
                  .text("this is a test")
                  .withAttribute("android:layout_centerHorizontal", "true")
                  .withAttribute("android:layout_centerVertical", "true")
          )
    )

    val expectedDrawCommand =
"""DrawNlComponentFrame,0,0,500,500,1,1000,1000
Clip,0,0,500,500
DrawComponentBackground,225,225,50,50,1
DrawProgressBar,225,225,50,50
DrawNlComponentFrame,225,225,50,50,1,100,100
DrawComponentBackground,225,245,50,10,1
DrawButton,225,245,50,10,0,8,1.0,28,"Button"
DrawNlComponentFrame,225,245,50,10,1,20,20
VerticalZigZagLineCommand - (250, 0, 225)
VerticalZigZagLineCommand - (250, 275, 500)
HorizontalZigZagLineCommand - (0, 225, 250)
HorizontalZigZagLineCommand - (275, 500, 250)
VerticalZigZagLineCommand - (250, 0, 245)
VerticalZigZagLineCommand - (250, 255, 500)
HorizontalZigZagLineCommand - (0, 225, 250)
HorizontalZigZagLineCommand - (275, 500, 250)
UNClip
"""
    SceneContext.get().setShowOnlySelection(false)
    checkModelDrawCommand(builder.build(), expectedDrawCommand)
  }

  fun testAlignParentAttributes() {
    val builder = model("relative.xml",
      component(RELATIVE_LAYOUT)
          .id("@+id/root")
          .withBounds(0, 0, 1000, 1000)
          .width("1000dp")
          .height("1000dp")
          .withAttribute("android:padding", "20dp")
          .children(
              component(IMAGE_VIEW)
                  .id("@+id/c")
                  .withBounds(450, 530, 60, 20)
                  .width("100dp")
                  .height("20dp")
                  .withAttribute("android:layout_alignParentStart", "true")
                  .withAttribute("android:layout_alignParentTop", "true")
                  .withAttribute("android:layout_marginStart", "10dp")
                  .withAttribute("android:layout_marginTop", "10dp"),
              component(TEXT_VIEW)
                  .id("@+id/d")
                  .withBounds(920, 960, 60, 20)
                  .width("100dp")
                  .height("20dp")
                  .withAttribute("android:layout_alignParentEnd", "true")
                  .withAttribute("android:layout_alignParentBottom", "true")
                  .withAttribute("android:layout_marginEnd", "10dp")
                  .withAttribute("android:layout_marginTop", "10dp")
          )
    )

    val expectedDrawCommand =
"""DrawNlComponentFrame,0,0,500,500,1,1000,1000
Clip,0,0,500,500
DrawComponentBackground,225,265,30,10,1
DrawImageView,225,265,30,10
DrawNlComponentFrame,225,265,30,10,1,20,20
DrawComponentBackground,460,480,30,10,1
DrawTextRegion,460,480,30,10,0,8,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,460,480,30,10,1,20,20
DrawVerticalArrowCommand - (240, 265, 0)
DrawHorizontalArrowCommand - (225, 270, 270)
DrawVerticalArrowCommand - (475, 490, 500)
DrawHorizontalArrowCommand - (490, 485, 485)
UNClip
"""
    SceneContext.get().setShowOnlySelection(false)
    checkModelDrawCommand(builder.build(), expectedDrawCommand)
  }


  fun testAlignWidgetAttribute() {
    val builder = model("relative.xml",
      component(RELATIVE_LAYOUT)
          .id("@+id/root")
          .withBounds(0, 0, 1000, 1000)
          .width("1000dp")
          .height("1000dp")
          .withAttribute("android:padding", "20dp")
          .children(
              component(PROGRESS_BAR)
                  .id("@+id/a")
                  .withBounds(450, 450, 100, 100)
                  .width("100dp")
                  .height("100dp")
                  .withAttribute("android:layout_centerInParent", "true"),
              component(CHECK_BOX)
                  .id("@+id/e")
                  .withBounds(450, 530,60, 20)
                  .width("100dp")
                  .height("20dp")
                  .withAttribute("android:layout_alignStart", "@+id/a")
                  .withAttribute("android:layout_alignBottom", "@+id/a")
                  .withAttribute("android:layout_marginStart", "-10dp")
                  .withAttribute("android:layout_marginBottom", "-10dp"),
              component(SEEK_BAR)
                  .id("@+id/f")
                  .withBounds(490, 450, 60, 20)
                  .width("100dp")
                  .height("20dp")
                  .withAttribute("android:layout_alignEnd", "@+id/a")
                  .withAttribute("android:layout_alignTop", "@+id/a")
                  .withAttribute("android:layout_marginEnd", "-10dp")
                  .withAttribute("android:layout_marginTop", "-10dp"),
              component(SWITCH)
                  .id("@+id/g")
                  .withBounds(550, 430, 60, 20)
                  .width("100dp")
                  .height("20dp")
                  .text("switch")
                  .withAttribute("android:layout_toEndOf", "@+id/a")
                  .withAttribute("android:layout_above", "@+id/a"),
              component(IMAGE_BUTTON)
                  .id("@+id/h")
                  .withBounds(390, 550, 60, 20)
                  .width("100dp")
                  .height("20dp")
                  .withAttribute("android:layout_toStartOf", "@+id/a")
                  .withAttribute("android:layout_below", "@+id/a")
          )
    )

    val expectedDrawCommand =
"""DrawNlComponentFrame,0,0,500,500,1,1000,1000
Clip,0,0,500,500
DrawComponentBackground,225,225,50,50,1
DrawProgressBar,225,225,50,50
DrawNlComponentFrame,225,225,50,50,1,100,100
DrawComponentBackground,225,265,30,10,1
DrawCheckbox,225,265,30,10,0,8,0.0,""
DrawNlComponentFrame,225,265,30,10,1,20,20
DrawComponentBackground,245,225,30,10,1
DrawSeekBar,245,225,30,10
DrawNlComponentFrame,245,225,30,10,1,20,20
DrawComponentBackground,275,215,30,10,1
DrawSwitch,275,215,30,10,0,8,false,false,2,2,14,1.0,"switch"
DrawNlComponentFrame,275,215,30,10,1,20,20
DrawComponentBackground,195,275,30,10,1
DrawUnknownDecorator,195,275,30,10,28,1.0,"h"
DrawNlComponentFrame,195,275,30,10,1,20,20
VerticalZigZagLineCommand - (250, 0, 225)
VerticalZigZagLineCommand - (250, 275, 500)
HorizontalZigZagLineCommand - (0, 225, 250)
HorizontalZigZagLineCommand - (275, 500, 250)
DrawVerticalArrowCommand - (240, 275, 275)
DrawHorizontalDashedLineCommand: (225, 275) - (275, 275)
DrawHorizontalArrowCommand - (225, 270, 270)
DrawVerticalDashedLineCommand: (225, 225) - (225, 275)
DrawVerticalArrowCommand - (260, 225, 225)
DrawHorizontalDashedLineCommand: (225, 225) - (275, 225)
DrawHorizontalArrowCommand - (275, 230, 230)
DrawVerticalDashedLineCommand: (275, 225) - (275, 275)
DrawVerticalArrowCommand - (290, 225, 225)
DrawHorizontalDashedLineCommand: (225, 225) - (305, 225)
DrawHorizontalArrowCommand - (275, 220, 220)
DrawVerticalDashedLineCommand: (275, 215) - (275, 275)
DrawVerticalArrowCommand - (210, 275, 275)
DrawHorizontalDashedLineCommand: (195, 275) - (275, 275)
DrawHorizontalArrowCommand - (225, 280, 280)
DrawVerticalDashedLineCommand: (225, 225) - (225, 285)
UNClip
"""
    SceneContext.get().setShowOnlySelection(false)
    checkModelDrawCommand(builder.build(), expectedDrawCommand)
  }

  fun testShowOnlySelectedComponent() {
    val builder = model("relative.xml",
                        component(RELATIVE_LAYOUT)
                          .id("@+id/root")
                          .withBounds(0, 0, 1000, 1000)
                          .width("1000dp")
                          .height("1000dp")
                          .withAttribute("android:padding", "20dp")
                          .children(
                            component(PROGRESS_BAR)
                              .id("@+id/a")
                              .withBounds(450, 450, 100, 100)
                              .width("100dp")
                              .height("100dp")
                              .withAttribute("android:layout_centerInParent", "true"),
                            component(CHECK_BOX)
                              .id("@+id/e")
                              .withBounds(450, 530,60, 20)
                              .width("100dp")
                              .height("20dp")
                              .withAttribute("android:layout_alignStart", "@+id/a")
                              .withAttribute("android:layout_alignBottom", "@+id/a")
                              .withAttribute("android:layout_marginStart", "-10dp")
                              .withAttribute("android:layout_marginBottom", "-10dp"),
                            component(SEEK_BAR)
                              .id("@+id/f")
                              .withBounds(490, 450, 60, 20)
                              .width("100dp")
                              .height("20dp")
                              .withAttribute("android:layout_alignEnd", "@+id/a")
                              .withAttribute("android:layout_alignTop", "@+id/a")
                              .withAttribute("android:layout_marginEnd", "-10dp")
                              .withAttribute("android:layout_marginTop", "-10dp"),
                            component(SWITCH)
                              .id("@+id/g")
                              .withBounds(550, 430, 60, 20)
                              .width("100dp")
                              .height("20dp")
                              .text("switch")
                              .withAttribute("android:layout_toEndOf", "@+id/a")
                              .withAttribute("android:layout_above", "@+id/a"),
                            component(IMAGE_BUTTON)
                              .id("@+id/h")
                              .withBounds(390, 550, 60, 20)
                              .width("100dp")
                              .height("20dp")
                              .withAttribute("android:layout_toStartOf", "@+id/a")
                              .withAttribute("android:layout_below", "@+id/a")
                          )
    )

    val expectedDrawCommand =
      """Clip,0,0,500,500
DrawNlComponentFrame,195,275,30,10,3,20,20
DrawResize,191,271,8,8,0
DrawResize,191,281,8,8,0
DrawResize,221,271,8,8,0
DrawResize,221,281,8,8,0
DrawAnchor,189,274,12,12,0
DrawAnchor,204,262,12,12,0
DrawAnchor,219,274,12,12,0
DrawAnchor,204,286,12,12,0
DrawVerticalArrowCommand - (210, 275, 275)
DrawHorizontalDashedLineCommand: (195, 275) - (275, 275)
DrawHorizontalArrowCommand - (225, 280, 280)
DrawVerticalDashedLineCommand: (225, 225) - (225, 285)
UNClip
"""
    val model = builder.build()
    SceneContext.get().setShowOnlySelection(true)
    model.surface.selectionModel.setSelection(listOf(model.find("h")!!))
    checkModelDrawCommand(model, expectedDrawCommand)
  }

  private fun checkModelDrawCommand(model: SyncNlModel, expectedSerializedCommand: String) {
    val sceneManager = model.surface.sceneManager!!
    val scene = model.surface.scene!!
    scene.isAnimated = false
    sceneManager.update()
    val interaction = SceneMouseInteraction(scene)
    val list = interaction.displayList.serialize()

    assertEquals(expectedSerializedCommand, list)
  }
}
