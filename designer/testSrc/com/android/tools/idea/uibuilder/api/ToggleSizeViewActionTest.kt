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
package com.android.tools.idea.uibuilder.api

import com.android.SdkConstants.ATTR_WIDTH
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl
import com.android.tools.idea.uibuilder.model.layoutHandler
import com.android.tools.idea.uibuilder.scene.SceneTest
import com.intellij.util.ui.EmptyIcon
import junit.framework.TestCase

class ToggleSizeViewActionTest: SceneTest() {

  /**
   * Regression test for b/143964703
   */
  fun testNoExceptionWhenPerform() {
    val action = ToggleSizeViewAction("Toggle", ATTR_WIDTH, EmptyIcon.ICON_0, EmptyIcon.ICON_0)

    val root = myModel.find("root")!!
    val text = myModel.find("myText")!!
    val editor = ViewEditorImpl(myScreen.screen)

    try {
      action.setSelected(editor, root.layoutHandler!!, root, listOf(text), true)
      action.setSelected(editor, root.layoutHandler!!, root, listOf(text), false)
    }
    catch (error: AssertionError) {
      TestCase.fail("AssertionError \"$error\" should not happen")
    }
  }

  override fun createModel(): ModelBuilder {
    return model("layout.xml",
                 component(LINEAR_LAYOUT)
                   .id("@id/root")
                   .withBounds(0, 0, 2000, 2000)
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(TEXT_VIEW)
                       .withBounds(200, 200, 200, 200)
                       .id("@id/myText")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute("android:layout_x", "100dp")
                       .withAttribute("android:layout_y", "100dp")
                   ))
  }
}
