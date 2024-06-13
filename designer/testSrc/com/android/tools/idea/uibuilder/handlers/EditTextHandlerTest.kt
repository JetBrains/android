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
package com.android.tools.idea.uibuilder.handlers

import com.android.SdkConstants
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.intellij.openapi.util.text.StringUtil
import java.util.HashSet

class EditTextHandlerTest : LayoutTestCase() {
  fun testIncrementIdTextViewId() {
    val model =
      model(
          "linear.xml",
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .id("@id/linear")
            .matchParentWidth()
            .matchParentHeight()
            .children(
              component(SdkConstants.EDIT_TEXT)
                .withBounds(0, 0, 200, 200)
                .wrapContentHeight()
                .wrapContentWidth()
            ),
        )
        .build()
    val editText = model.components.get(0).getChild(0)!!
    val existIds = HashSet<String>()

    val baseId = StringUtil.decapitalize(editText.tagName)
    var expected: String

    // Test no input type cases.
    expected = baseId
    NlWriteCommandActionUtil.run(editText, "") { editText.incrementId(existIds) }
    assertEquals(expected, editText.id)

    expected += "2"
    NlWriteCommandActionUtil.run(editText, "") { editText.incrementId(existIds) }
    assertEquals(expected, editText.id)

    existIds.add(baseId + "3")
    existIds.add(baseId + "5")
    expected = baseId + "4"
    NlWriteCommandActionUtil.run(editText, "") { editText.incrementId(existIds) }
    assertEquals(expected, editText.id)
    assertTrue(existIds.contains(expected))

    // Test input type attribute cases.
    NlWriteCommandActionUtil.run(editText, "") {
      editText.removeAndroidAttribute(SdkConstants.ATTR_ID)
    }

    val inputTypeValue = "testInputType"
    expected = baseId + StringUtil.capitalize(inputTypeValue)
    NlWriteCommandActionUtil.run(editText, "") {
      editText.setAndroidAttribute(SdkConstants.ATTR_INPUT_TYPE, inputTypeValue)
    }
    NlWriteCommandActionUtil.run(editText, "") { editText.incrementId(existIds) }
    assertEquals(expected, editText.id)
    assertTrue(existIds.contains(baseId + StringUtil.capitalize(inputTypeValue)))

    expected += "2"
    NlWriteCommandActionUtil.run(editText, "") { editText.incrementId(existIds) }
    assertEquals(expected, editText.id)
    assertTrue(existIds.contains(expected))
  }
}
