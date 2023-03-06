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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.android.AndroidFacetProjectDescriptor
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.assertNotEquals
import java.util.concurrent.CountDownLatch

class ResourceIdManagerTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = AndroidFacetProjectDescriptor

  private lateinit var facet: AndroidFacet
  private lateinit var idManager: ResourceIdManager

  override fun setUp() {
    super.setUp()
    facet = AndroidFacet.getInstance(module)!!
    idManager = ResourceIdManager.get(module)
  }

  fun testDynamicIds() {
    val initialGeneration = idManager.generation
    val stringId = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string"))
    assertNotNull(stringId)
    val styleId = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STYLE, "style"))
    assertNotNull(styleId)
    val layoutId = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout"))
    assertNotNull(layoutId)
    assertEquals(stringId, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string")))
    assertEquals(ResourceReference(RES_AUTO, ResourceType.STRING, "string"), idManager.findById(stringId))
    assertEquals(styleId, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STYLE, "style")))
    assertEquals(ResourceReference(RES_AUTO, ResourceType.STYLE, "style"), idManager.findById(styleId))
    assertEquals(layoutId, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout")))
    assertEquals(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout"), idManager.findById(layoutId))
    assertEquals("Generation must be constant if no calls to resetDynamicIds happened", initialGeneration, idManager.generation)
  }

  fun testResetDynamicIds() {
    var lastGeneration = idManager.generation
    idManager.resetDynamicIds()
    assertNotEquals(lastGeneration, idManager.generation)
    lastGeneration = idManager.generation

    val id1 = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string1"))
    val id2 = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string2"))
    val id3 = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string3"))
    assertNotEquals(id1, id2)
    assertNotEquals(id1, id3)
    assertNotEquals(id2, id3)

    idManager.resetDynamicIds()
    assertNotEquals(lastGeneration, idManager.generation)

    // They should be all gone now.
    assertNull(idManager.findById(id1))
    assertNull(idManager.findById(id2))
    assertNull(idManager.findById(id3))

    // Check in different order. These should be new IDs.
    assertNotEquals(id3, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string3")))
    assertNotEquals(id1, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string1")))
    assertNotEquals(id2, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string2")))
  }

  fun testLoadCompiledResources() {
    val stringId = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string"))
    val styleId = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STYLE, "style"))
    val layoutId = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout"))

    idManager.resetCompiledIds { it.parse(R::class.java) }

    // Compiled resources should replace the dynamic IDs.
    assertNotEquals(stringId, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string")))
    assertEquals(Integer.valueOf(0x7F000001), idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string")))
    assertNotEquals(styleId, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STYLE, "style")))
    assertEquals(Integer.valueOf(0x7F010001), idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STYLE, "style")))
    assertNotEquals(layoutId, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout")))
    assertEquals(Integer.valueOf(0x7F020001), idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout")))

    // Dynamic IDs should still resolve though.
    assertEquals(ResourceReference(RES_AUTO, ResourceType.STRING, "string"), idManager.findById(stringId))
    assertEquals(ResourceReference(RES_AUTO, ResourceType.STYLE, "style"), idManager.findById(styleId))
    assertEquals(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout"), idManager.findById(layoutId))

    // But not after reset.
    idManager.resetDynamicIds()
    assertNull(idManager.findById(stringId))
    assertNull(idManager.findById(styleId))
    assertNull(idManager.findById(layoutId))
  }

  fun testResetIdsDoesNotPreventAccess() {
    assertNull(idManager.findById(0x7f000001))

    idManager.resetCompiledIds { it.parse(R::class.java) }

    assertNotNull(idManager.findById(0x7f000001))

    val idsReset = CountDownLatch(1)
    val canParse = CountDownLatch(1)

    val thread = Thread {
      idManager.resetCompiledIds {
        idsReset.countDown()
        canParse.await()
        it.parse(R::class.java)
      }
    }
    thread.start()

    idsReset.await()

    assertNotNull(idManager.findById(0x7f000001))

    canParse.countDown()
    thread.join()
  }

  class R {
    class string {
      companion object {
        const val string: Int = 0x7f000001
      }
    }
    class style {
      companion object {
        const val style: Int = 0x7f010001
      }
    }

    class layout {
      companion object {
        const val layout: Int = 0x7f020001
      }
    }
  }
}
