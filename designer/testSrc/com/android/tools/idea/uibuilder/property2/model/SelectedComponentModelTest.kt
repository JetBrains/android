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
package com.android.tools.idea.uibuilder.property2.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import icons.StudioIcons
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SelectedComponentModelTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Test
  fun testModel() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    NlWriteCommandAction.run(util.components[0], "") {
      util.components.first().setAttribute(ANDROID_URI, ATTR_ID, null)
    }
    val model = SelectedComponentModel(util.components, "What?")
    assertThat(model.componentIcon).isEqualTo(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
    assertThat(model.componentName).isEqualTo("<unnamed>")
    assertThat(model.elementDescription).isEqualTo("What?")
  }

  @Test
  fun testModelWithTextViewId() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW)
    val model = SelectedComponentModel(util.components, "What?")
    assertThat(model.componentIcon).isEqualTo(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
    assertThat(model.componentName).isEqualTo("textview")
    assertThat(model.elementDescription).isEqualTo("What?")
  }

  @Test
  fun testModelWithMultipleComponents() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW, BUTTON, parentTag = LINEAR_LAYOUT)
    val model = SelectedComponentModel(util.components, "What?")
    assertThat(model.componentIcon).isEqualTo(StudioIcons.LayoutEditor.Palette.VIEW_SWITCHER)
    assertThat(model.componentName).isEqualTo("<multiple>")
    assertThat(model.elementDescription).isEqualTo("What?")
  }
}
