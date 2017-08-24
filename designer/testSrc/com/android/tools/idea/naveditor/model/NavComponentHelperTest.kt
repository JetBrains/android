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
package com.android.tools.idea.naveditor.model

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class NavComponentHelperTest {

  @Test
  fun testUiName() {
    val component = mock(NlComponent::class.java)
    `when`(component.tagName).thenReturn("myTag")
    assertEquals("myTag", component.getUiName(null))
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)).thenReturn("@+id/myId")
    assertEquals("myId", component.getUiName(null))
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)).thenReturn("myName")
    assertEquals("myName", component.getUiName(null))
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL)).thenReturn("myLabel")
    assertEquals("myLabel", component.getUiName(null))
  }

  @Test
  fun testUiNameWithResources() {
    val resolver = mock(ResourceResolver::class.java)
    val value = mock(ResourceValue::class.java)
    `when`(value.getValue()).thenReturn("resolvedValue")
    `when`(resolver.findResValue("myLabel", false)).thenReturn(value)
    `when`(resolver.resolveResValue(value)).thenReturn(value)

    val component = mock(NlComponent::class.java)
    `when`(component.resolveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LABEL)).thenReturn("myLabel")
    assertEquals("resolvedValue", component.getUiName(resolver))
  }

}
