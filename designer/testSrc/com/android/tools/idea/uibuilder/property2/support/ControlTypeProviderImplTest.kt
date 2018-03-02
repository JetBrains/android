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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants.*
import com.android.tools.idea.common.property2.api.ControlType
import com.android.tools.idea.common.property2.api.EnumSupport
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import org.mockito.Mockito.mock

class ControlTypeProviderImplTest: AndroidTestCase() {

  @Test
  fun testComboBoxForEnumSupport() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val provide = ControlTypeProviderImpl()
    val property = util.makeProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NelePropertyType.BOOLEAN)
    val enumSupport = mock(EnumSupport::class.java)
    assertThat(provide(property, enumSupport)).isEqualTo(ControlType.COMBO_BOX)
  }

  @Test
  fun testBooleanForBooleanTypes() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val provide = ControlTypeProviderImpl()
    val property = util.makeProperty(ANDROID_URI, ATTR_CLICKABLE, NelePropertyType.BOOLEAN)
    assertThat(provide(property, null)).isEqualTo(ControlType.THREE_STATE_BOOLEAN)
  }

  @Test
  fun testTextEditorForEverythingElse() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW)
    val provide = ControlTypeProviderImpl()
    val property = util.makeProperty(ANDROID_URI, ATTR_PADDING_BOTTOM, NelePropertyType.DIMENSION)
    assertThat(provide(property, null)).isEqualTo(ControlType.TEXT_EDITOR)
  }
}
