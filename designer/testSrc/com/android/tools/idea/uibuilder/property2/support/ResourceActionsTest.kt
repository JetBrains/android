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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants
import com.android.tools.idea.common.property2.api.HelpSupport
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

class ResourceActionsTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @RunsInEdt
  @Test
  fun testOpenResourceActionWithInvalidXmlTag() {
    val action = OpenResourceManagerAction
    val util = SupportTestUtil(projectRule, SdkConstants.TEXT_VIEW)
    val property = util.makeProperty(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT, NelePropertyType.STRING)
    val context = SimpleDataContext.getSimpleContext(HelpSupport.PROPERTY_ITEM.name, property)
    val event = AnActionEvent.createFromDataContext("", null, context)
    deleteXmlTag(property)

    // Expect the dialog not to be displayed, because the tag is now gone.
    // Displaying the resource picker would cause an exception since no UI is available in headless mode.
    action.actionPerformed(event)
  }

  private fun deleteXmlTag(property: NelePropertyItem) {
    val tag = property.components.first().backend.getTagPointer().element!!
    WriteCommandAction.writeCommandAction(projectRule.project).run<Throwable> {
      tag.delete()
    }
  }
}
