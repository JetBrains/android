/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.idea.flags.StudioFlags
import junit.framework.TestCase

class SceneDisplayListWithSelectionTest : SceneTest() {

  override fun createModel(): ModelBuilder = model("constraint.xml",
                                                   component(CONSTRAINT_LAYOUT.defaultName())
                                                     .id("@id/root")
                                                     .withBounds(0, 0, 2000, 2000)
                                                     .width("1000dp")
                                                     .height("1000dp")
                                                     .withAttribute("android:padding", "20dp")
                                                     .children(
                                                       component(TEXT_VIEW)
                                                         .id("@id/button")
                                                         .withBounds(200, 400, 200, 40)
                                                         .width("100dp")
                                                         .height("20dp")
                                                         .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                                         .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                                       component(TEXT_VIEW)
                                                         .id("@id/button2")
                                                         .withBounds(200, 1000, 10, 10)
                                                         .width("5dp")
                                                         .height("5dp")
                                                         .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                                         .withAttribute("tools:layout_editor_absoluteY", "500dp"),
                                                       component(LINEAR_LAYOUT)
                                                         .id("@id/linear")
                                                         .withBounds(1200, 1200, 500, 500)
                                                         .width("250dp")
                                                         .height("250dp")
                                                         .withAttribute("tools:layout_editor_absoluteX", "600dp")
                                                         .withAttribute("tools:layout_editor_absoluteY", "600dp")
                                                         .children(
                                                           component(TEXT_VIEW)
                                                             .id("@id/textView3")
                                                             .withBounds(1200, 1200, 200, 200)
                                                             .width("100dp")
                                                             .height("100dp")
                                                         )
                                                     ))

  fun testNoSelectionCase() {
    // Should render SceneComponent and decoration only
    myScene.select(listOf())
    myInteraction.repaint()

    val simpleList = """DrawNlComponentFrame,0,0,1000,1000,1,1000,1000
Clip,0,0,1000,1000
DrawComponentBackground,100,200,100,20,1
DrawTextRegion,100,200,100,20,0,16,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,100,200,100,20,1,20,20
DrawComponentBackground,100,500,5,5,1
DrawTextRegion,100,500,5,5,0,4,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,100,500,5,5,1,5,5
DrawLinearLayout,600,600,250,250,1
DrawNlComponentFrame,600,600,250,250,1,250,250
Clip,600,600,250,250
DrawComponentBackground,600,600,100,100,1
DrawTextRegion,600,600,100,100,0,80,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,600,600,100,100,1,100,100
UNClip
UNClip
"""

    TestCase.assertEquals(simpleList, myInteraction.displayList.serialize())
  }

  fun testSingleSelectionCase() {
    // Should render Anchor and Resize Target in this case
    val button = myScene.getSceneComponent(myScreen.findById("@id/button")!!.component)!!
    myScene.select(listOf(button))
    myInteraction.repaint()

    val simpleList = if (StudioFlags.NELE_DP_SIZED_PREVIEW.get()) {
      """DrawNlComponentFrame,0,0,1000,1000,1,1000,1000
Clip,0,0,1000,1000
DrawComponentBackground,100,200,100,20,3
DrawTextRegion,100,200,100,20,2,16,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,100,200,100,20,3,20,20
DrawResize,96,196,8,8,0
DrawResize,96,216,8,8,0
DrawResize,196,196,8,8,0
DrawResize,196,216,8,8,0
DrawAnchor,94,204,12,12,0
DrawAnchor,144,180,12,12,0
DrawAnchor,194,204,12,12,0
DrawAnchor,144,228,12,12,0
DrawComponentBackground,100,500,5,5,1
DrawTextRegion,100,500,5,5,0,4,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,100,500,5,5,1,5,5
DrawLinearLayout,600,600,250,250,1
DrawNlComponentFrame,600,600,250,250,1,250,250
Clip,600,600,250,250
DrawComponentBackground,600,600,100,100,1
DrawTextRegion,600,600,100,100,0,80,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,600,600,100,100,1,100,100
UNClip
UNClip
"""
    } else {
      """DrawNlComponentFrame,0,0,1000,1000,1,1000,1000
Clip,0,0,1000,1000
DrawComponentBackground,100,200,100,20,3
DrawTextRegion,100,200,100,20,2,16,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,100,200,100,20,3,20,20
DrawResize,96,196,8,8,0
DrawResize,96,216,8,8,0
DrawResize,196,196,8,8,0
DrawResize,196,216,8,8,0
DrawAnchor,94,204,12,12,0
DrawAnchor,144,192,12,12,0
DrawAnchor,194,204,12,12,0
DrawAnchor,144,216,12,12,0
DrawComponentBackground,100,500,5,5,1
DrawTextRegion,100,500,5,5,0,4,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,100,500,5,5,1,5,5
DrawLinearLayout,600,600,250,250,1
DrawNlComponentFrame,600,600,250,250,1,250,250
Clip,600,600,250,250
DrawComponentBackground,600,600,100,100,1
DrawTextRegion,600,600,100,100,0,80,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,600,600,100,100,1,100,100
UNClip
UNClip
"""
    }

    TestCase.assertEquals(simpleList, myInteraction.displayList.serialize())
  }

  fun testMultipleSelectionCase() {
    // Should not render Anchor and Resize Target in this case
    val button = myScene.getSceneComponent(myScreen.findById("@id/button")!!.component)!!
    val button2 = myScene.getSceneComponent(myScreen.findById("@id/button2")!!.component)!!
    myScene.select(listOf(button, button2))
    myInteraction.repaint()

    val simpleList = """DrawNlComponentFrame,0,0,1000,1000,1,1000,1000
Clip,0,0,1000,1000
DrawComponentBackground,100,200,100,20,3
DrawTextRegion,100,200,100,20,2,16,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,100,200,100,20,3,20,20
DrawComponentBackground,100,500,5,5,3
DrawTextRegion,100,500,5,5,2,4,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,100,500,5,5,3,5,5
DrawLinearLayout,600,600,250,250,1
DrawNlComponentFrame,600,600,250,250,1,250,250
Clip,600,600,250,250
DrawComponentBackground,600,600,100,100,1
DrawTextRegion,600,600,100,100,0,80,false,false,4,5,28,1.0,"TextView"
DrawNlComponentFrame,600,600,100,100,1,100,100
UNClip
UNClip
"""

    TestCase.assertEquals(simpleList, myInteraction.displayList.serialize())
  }
}
