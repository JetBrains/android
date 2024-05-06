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
import com.android.ide.common.rendering.api.PluralsResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import com.android.test.testutils.TestUtils
import com.android.tools.res.ids.apk.ApkResourceIdManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkResourceRepositoryTest {

  @Test
  fun testResourceValues() {
    val path = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "apk-for-local-test.ap_")
    val idManager = ApkResourceIdManager().apply { this.loadApkResources(path.toString()) }
    val apkRes = ApkResourceRepository(path.toString()) { idManager.findById(it) }

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
    val idManager = ApkResourceIdManager().apply { this.loadApkResources(path.toString()) }
    val apkRes = ApkResourceRepository(path.toString()) { idManager.findById(it) }

    val attrRes = apkRes.getResources(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "buttonSize")
    )[0]

    val attrVals = (attrRes.resourceValue as AttrResourceValue).attributeValues
    assertEquals(3, attrVals.size)
    assertEquals(
      """
        icon_only
        standard
        wide
      """.trimIndent(),
      attrVals.keys.sorted().joinToString("\n")
      )
    assertEquals(0, attrVals["standard"])
    assertEquals(1, attrVals["wide"])
    assertEquals(2, attrVals["icon_only"])
  }

  @Test
  fun testStyleValues() {
    val path = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "apk-for-local-test.ap_")
    val idManager = ApkResourceIdManager().apply { this.loadApkResources(path.toString()) }
    val apkRes = ApkResourceRepository(path.toString()) { idManager.findById(it) }

    val styleRes = apkRes.getResources(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "DialogWindowTheme")
    )[0]


    val styleItems = (styleRes.resourceValue as StyleResourceValue).definedItems.toList()
    assertEquals(1, styleItems.size)
    assertEquals("android:windowClipToOutline", styleItems[0].attrName)
    assertEquals("false", styleItems[0].value)
  }

  @Test
  fun testPluralsValues() {
    val path = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "apk-with-plurals.ap_")
    val idManager = ApkResourceIdManager().apply { this.loadApkResources(path.toString()) }
    val apkRes = ApkResourceRepository(path.toString()) { idManager.findById(it) }

    val pluralsRes = apkRes.getResources(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.PLURALS, "mtrl_badge_content_description")
    )[0]


    val pluralsItem = pluralsRes.resourceValue as PluralsResourceValue
    assertEquals(2, pluralsItem.pluralsCount)
    assertEquals("%d new notification", pluralsItem.getValue(0))
    assertEquals("one", pluralsItem.getQuantity(0))
    assertEquals("%d new notifications", pluralsItem.getValue(1))
    assertEquals("other", pluralsItem.getQuantity(1))
  }
}