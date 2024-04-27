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
package com.android.tools.res.ids.apk

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.test.testutils.TestUtils
import com.android.tools.res.apk.TEST_DATA_DIR
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkResourceIdManagerTest {
  @Test
  fun testFindResourceById() {
    val path = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "apk-for-local-test.ap_")
    val idManager = ApkResourceIdManager().apply { this.loadApkResources(path.toString()) }

    run {
      val resId = idManager.getCompiledId(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ANIM, "fragment_fast_out_extra_slow_in"))
      assertEquals("fragment_fast_out_extra_slow_in", idManager.findById(resId!!)?.name)
    }

    run {
      val resId = idManager.getCompiledId(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DIMEN, "compat_control_corner_material"))
      assertEquals("compat_control_corner_material", idManager.findById(resId!!)?.name)
    }

    run {
      val resId = idManager.getCompiledId(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "news_notification_channel_description"))
      assertEquals("news_notification_channel_description", idManager.findById(resId!!)?.name)
    }
  }
}