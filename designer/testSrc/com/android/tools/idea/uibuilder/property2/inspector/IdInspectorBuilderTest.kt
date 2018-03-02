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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.model.PreferenceUtils
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase

class IdInspectorBuilderTest: AndroidTestCase() {

  fun testAvailableWhenIdIsPresent() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val builder = IdInspectorBuilder(util.editorProvider)
    util.addProperty(ANDROID_URI, ATTR_ID, NelePropertyType.ID)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(1)
    assertThat(util.inspector.lines[0].editorModel?.property?.name).isEqualTo(ATTR_ID)
  }

  fun testNotAvailableWhenIdIsAbsent() {
    val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val builder = IdInspectorBuilder(util.editorProvider)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).isEmpty()
  }

  fun testNotAvailableForPreferenceTags() {
    for (tagName in PreferenceUtils.VALUES) {
      val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, tagName)
      val builder = IdInspectorBuilder(util.editorProvider)
      util.addProperty(ANDROID_URI, ATTR_ID, NelePropertyType.ID)
      builder.attachToInspector(util.inspector, util.properties)
      assertThat(util.inspector.lines).isEmpty()
    }
  }

  fun testNotAvailableForMenuTags() {
    for (tagName in arrayOf(TAG_MENU, TAG_ITEM, TAG_GROUP)) {
      val util = InspectorTestUtil(testRootDisposable, myFacet, myFixture, tagName)
      val builder = IdInspectorBuilder(util.editorProvider)
      util.addProperty(ANDROID_URI, ATTR_ID, NelePropertyType.ID)
      builder.attachToInspector(util.inspector, util.properties)
      assertThat(util.inspector.lines).isEmpty()
    }
  }
}
