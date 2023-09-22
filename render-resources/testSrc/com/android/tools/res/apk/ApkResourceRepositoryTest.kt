/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.res.apk

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkResourceRepositoryTest {
  @Test
  fun testResourceValues() {
    val path = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "apk-for-local-test.ap_")
    val apkRes = ApkResourceRepository(path.toString()) { null }

    val animRes = apkRes.getResources(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ANIM, "fragment_fast_out_extra_slow_in")
    )[0]

    assertTrue(
      animRes.resourceValue?.value?.endsWith("res/anim-v21/fragment_fast_out_extra_slow_in.xml") == true
    )

    val dimenRes = apkRes.getResources(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DIMEN, "compat_control_corner_material")
    )[0]

    assertEquals("2dp", dimenRes.resourceValue?.value)

    val strRes = apkRes.getResources(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "news_notification_channel_description")
    )[0]

    assertEquals("The latest updates on what's new in Android", strRes.resourceValue?.value)
  }

  @Test
  fun testAttrValues() {
    val path = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "apk-for-local-test.ap_")
    val apkRes = ApkResourceRepository(path.toString()) { null }

    val attrRes = apkRes.getResources(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "buttonSize")
    )[0]

    val attrVals = (attrRes.resourceValue as AttrResourceValue).attributeValues
    assertEquals(3, attrVals.size)
  }

  @Test
  fun testStyleValues() {
    val path = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "apk-for-local-test.ap_")
    val apkRes = ApkResourceRepository(path.toString()) { null }

    val styleRes = apkRes.getResources(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "DialogWindowTheme")
    )[0]

    val styleItems = (styleRes.resourceValue as StyleResourceValue).definedItems
    assertEquals(1, styleItems.size)
  }
}