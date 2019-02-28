/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF
import com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF
import com.android.SdkConstants.ATTR_PARENT
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.SdkConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.SHERPA_URI
import com.android.SdkConstants.TEXT_VIEW
import junit.framework.TestCase

class SceneDisplayListWithHiddenComponentTest : SceneTest() {

  override fun createModel(): ModelBuilder = model("constraint.xml",
                                                   component(CONSTRAINT_LAYOUT.defaultName())
                                                     .id("@id/root")
                                                     .withBounds(0, 0, 2000, 2000)
                                                     .width("1000dp")
                                                     .height("1000dp")
                                                     .withAttribute("android:padding", "20dp")
                                                     .children(
                                                       component(TEXT_VIEW)
                                                         .id("@id/textView")
                                                         .withBounds(200, 400, 200, 40)
                                                         .width("100dp")
                                                         .height("20dp")
                                                         .withAttribute(SHERPA_URI, ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, ATTR_PARENT)
                                                         .withAttribute(SHERPA_URI, ATTR_LAYOUT_TOP_TO_TOP_OF, ATTR_PARENT)
                                                         .withAttribute("tools:layout_editor_absoluteX", "-100dp"),
                                                       component(TEXT_VIEW)
                                                         .id("@id/textView2")
                                                         .withBounds(200, 1000, 10, 10)
                                                         .width("5dp")
                                                         .height("5dp")
                                                         .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                                         .withAttribute("tools:layout_editor_absoluteY", "500dp")
                                                     ))

  fun testNoSelectionCase() {
    // Should render SceneComponent and decoration only
    myScene.select(listOf())
    myInteraction.repaint()

    val simpleList = """DrawNlComponentFrame,0,0,1000,1000,1,1000,1000
Clip,0,0,1000,1000
DrawComponentBackground,100,200,100,20,1
DrawTextRegion,100,200,100,20,0,0,false,false,5,5,28,1.0,""
DrawNlComponentFrame,100,200,100,20,1,20,20
DrawConnection,2,100x200x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,false,0,0,0
DrawConnection,2,100x200x100x20,3,0x0x1000x1000,3,1,false,0,0,false,0.5,false,0,0,0
DrawComponentBackground,100,500,5,5,1
DrawTextRegion,100,500,5,5,0,0,false,false,5,5,28,1.0,""
DrawNlComponentFrame,100,500,5,5,1,5,5
UNClip
"""

    TestCase.assertEquals(simpleList, myInteraction.displayList.serialize())
  }

  fun testSingleSelectionCase() {
    // Should render Anchor and Resize Target in this case
    val textView = myScene.getSceneComponent(myScreen.findById("@id/textView")!!.component)!!
    myScene.select(listOf(textView))
    myInteraction.repaint()

    val simpleList = """DrawNlComponentFrame,0,0,1000,1000,1,1000,1000
Clip,0,0,1000,1000
DrawComponentBackground,100,200,100,20,3
DrawTextRegion,100,200,100,20,2,0,false,false,5,5,28,1.0,""
DrawNlComponentFrame,100,200,100,20,3,20,20
DrawResize,96,196,8,8,0
DrawResize,96,216,8,8,0
DrawResize,196,196,8,8,0
DrawResize,196,216,8,8,0
DrawAnchor,94,204,12,12,0
DrawAnchor,144,192,12,12,0
DrawAnchor,194,204,12,12,0
DrawAnchor,144,216,12,12,0
DrawConnection,2,100x200x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,true,0,2,0
DrawConnection,2,100x200x100x20,3,0x0x1000x1000,3,1,false,0,0,false,0.5,true,0,2,0
DrawComponentBackground,100,500,5,5,1
DrawTextRegion,100,500,5,5,0,0,false,false,5,5,28,1.0,""
DrawNlComponentFrame,100,500,5,5,1,5,5
UNClip
"""

    TestCase.assertEquals(simpleList, myInteraction.displayList.serialize())
  }

  fun testMultipleSelectionCase() {
    // Should not render Anchor and Resize Target in this case
    val textView = myScene.getSceneComponent(myScreen.findById("@id/textView")!!.component)!!
    val textView2 = myScene.getSceneComponent(myScreen.findById("@id/textView2")!!.component)!!
    myScene.select(listOf(textView, textView2))
    myInteraction.repaint()

    val simpleList = """DrawNlComponentFrame,0,0,1000,1000,1,1000,1000
Clip,0,0,1000,1000
DrawComponentBackground,100,200,100,20,3
DrawTextRegion,100,200,100,20,2,0,false,false,5,5,28,1.0,""
DrawNlComponentFrame,100,200,100,20,3,20,20
DrawConnection,2,100x200x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,true,0,2,0
DrawConnection,2,100x200x100x20,3,0x0x1000x1000,3,1,false,0,0,false,0.5,true,0,2,0
DrawComponentBackground,100,500,5,5,3
DrawTextRegion,100,500,5,5,2,0,false,false,5,5,28,1.0,""
DrawNlComponentFrame,100,500,5,5,3,5,5
UNClip
"""

    TestCase.assertEquals(simpleList, myInteraction.displayList.serialize())
  }
}
