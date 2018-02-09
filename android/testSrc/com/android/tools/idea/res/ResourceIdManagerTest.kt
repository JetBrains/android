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

import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace.ANDROID
import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import gnu.trove.TIntObjectHashMap
import gnu.trove.TObjectIntHashMap
import org.jetbrains.android.AndroidFacetProjectDescriptor
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.assertNotEquals

class ResourceIdManagerTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = AndroidFacetProjectDescriptor

  private val module get() = myModule
  private lateinit var facet: AndroidFacet
  private lateinit var idManager: ResourceIdManager

  override fun setUp() {
    super.setUp()
    facet = AndroidFacet.getInstance(module)!!
    idManager = ResourceIdManager.get(module)
  }

  fun testGetDeclaredArrayValues() {
    val appResources = createTestAppResourceRepository(facet)

    val attrList = mutableListOf(AttrResourceValue(ResourceReference(RES_AUTO, ResourceType.ATTR, "some-attr"), null))
    assertOrderedEquals(idManager.getDeclaredArrayValues(appResources.libraries, attrList, "Styleable1")!!, 0x7f010000)

    // Declared styleables mismatch
    attrList += AttrResourceValue(ResourceReference(RES_AUTO, ResourceType.ATTR, "some-attr"), null)
    attrList += AttrResourceValue(ResourceReference(RES_AUTO, ResourceType.ATTR, "other-attr"), null)

    assertNull(idManager.getDeclaredArrayValues(appResources.libraries, attrList, "Styleable1"))

    assertOrderedEquals(
      idManager.getDeclaredArrayValues(
        appResources.libraries,
        listOf(
          AttrResourceValue(ResourceReference(RES_AUTO, ResourceType.ATTR, "app_attr1"), null),
          AttrResourceValue(ResourceReference(RES_AUTO, ResourceType.ATTR, "app_attr2"), null),
          AttrResourceValue(ResourceReference(ANDROID, ResourceType.ATTR, "framework-attr1"), null),
          AttrResourceValue(ResourceReference(RES_AUTO, ResourceType.ATTR, "app_attr3"), null),
          AttrResourceValue(ResourceReference(ANDROID, ResourceType.ATTR, "framework_attr2"), null)
        ),
        "Styleable_with_underscore"
      )!!,
      0x7f010000, 0x7f010068, 0x01010125, 0x7f010069, 0x01010142
    )
  }

  fun testDynamicIds() {
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
  }

  fun testResetDynamicIds() {
    val id1 = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string1"))
    val id2 = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string2"))
    val id3 = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string3"))
    idManager.resetDynamicIds()

    // They should be all gone now.
    assertNull(idManager.findById(id1))
    assertNull(idManager.findById(id2))
    assertNull(idManager.findById(id3))

    // Check in different order. These should be new IDs.
    assertNotEquals(id3, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string3")))
    assertNotEquals(id1, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string1")))
    assertNotEquals(id2, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string2")))
  }

  fun testSetCompiledResources() {
    val stringId = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string"))
    val styleId = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STYLE, "style"))
    val layoutId = idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout"))

    val id2res = TIntObjectHashMap<ResourceReference>()
    id2res.put(0x7F000000, ResourceReference(RES_AUTO, ResourceType.STRING, "string"))
    id2res.put(0x7F010000, ResourceReference(RES_AUTO, ResourceType.STYLE, "style"))
    id2res.put(0x7F020000, ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout"))

    val res2id = TObjectIntHashMap<ResourceReference>()
    res2id.put(ResourceReference(RES_AUTO, ResourceType.STRING, "string"), 0x7F000000)
    res2id.put(ResourceReference(RES_AUTO, ResourceType.STYLE, "style"), 0x7F010000)
    res2id.put(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout"), 0x7F020000)

    idManager.setCompiledIds(res2id, id2res)

    // Compiled resources should replace the dynamic IDs.
    assertNotEquals(stringId, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string")))
    assertEquals(Integer.valueOf(0x7F000000), idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STRING, "string")))
    assertNotEquals(styleId, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STYLE, "style")))
    assertEquals(Integer.valueOf(0x7F010000), idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.STYLE, "style")))
    assertNotEquals(layoutId, idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout")))
    assertEquals(Integer.valueOf(0x7F020000), idManager.getOrGenerateId(ResourceReference(RES_AUTO, ResourceType.LAYOUT, "layout")))

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
}
