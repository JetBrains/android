/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.res

import com.android.ide.common.rendering.api.ArrayResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.test.testutils.TestUtils
import com.android.tools.res.apk.TEST_DATA_DIR
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.io.path.absolutePathString

class FolderResourceRepositoryTest {
  @Test
  fun testResourcesLoaded() {
    val path = TestUtils.resolveWorkspacePath(TEST_DATA_DIR + "Allresources/lib/src/main/res")
    val folderRepository = FolderResourceRepository(path.toFile())

    run {
      val items = folderRepository.getResources(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "ic_launcher_background")
      assertEquals(1, items.size)
      val item = items.first()
      assertEquals("${path.absolutePathString().replace('\\', '/')}/drawable/ic_launcher_background.xml", item.resourceValue!!.value)
    }

    run {
      val items = folderRepository.getResources(ResourceNamespace.RES_AUTO, ResourceType.FONT, "aclonica")
      assertEquals(1, items.size)
      val item = items.first()
      assertEquals("${path.absolutePathString().replace('\\', '/')}/font/aclonica.xml", item.resourceValue!!.value)
    }

    run {
      val items = folderRepository.getResources(ResourceNamespace.RES_AUTO, ResourceType.STRING, "my_qualifier")
      assertEquals(2, items.size)
      val item = items.first { it.configuration.qualifierString == "b+es+419" }
      assertEquals("b+es+419", item.resourceValue!!.value)
    }

    run {
      val items = folderRepository.getResources(ResourceNamespace.RES_AUTO, ResourceType.ARRAY, "icons")
      assertEquals(1, items.size)
      val item = items.first() as ArrayResourceValue
      assertEquals(2, item.elementCount)
      assertEquals("@drawable/ic_launcher_background", item.getElement(0))
      assertEquals("@drawable/ic_launcher_foreground", item.getElement(1))
    }
  }
}