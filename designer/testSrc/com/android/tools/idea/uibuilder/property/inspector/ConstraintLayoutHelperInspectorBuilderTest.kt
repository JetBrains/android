/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ConstraintLayoutHelperInspectorBuilderTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @JvmField @Rule val edtRule = EdtRule()

  @Test
  fun testApplicableForConstraintHelper() {
    val util = InspectorTestUtil(projectRule, CLASS_CONSTRAINT_LAYOUT_BARRIER.newName())
    val builder = ConstraintLayoutHelperInspectorBuilder(util.editorProvider)
    util.addProperty(AUTO_URI, CONSTRAINT_REFERENCED_IDS, NlPropertyType.STRING)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).isNotEmpty()
    util.checkTitle(0, InspectorSection.REFERENCES.title)
  }

  @Test
  fun testNotApplicableForTextView() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = ConstraintLayoutHelperInspectorBuilder(util.editorProvider)
    util.addProperty(AUTO_URI, CONSTRAINT_REFERENCED_IDS, NlPropertyType.STRING)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).isEmpty()
  }
}
