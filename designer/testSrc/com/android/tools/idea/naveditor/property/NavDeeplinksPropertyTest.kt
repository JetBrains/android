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
package com.android.tools.idea.naveditor.property

import com.android.SdkConstants.ATTR_URI
import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavigationTestCase

class NavDeeplinksPropertyTest : NavigationTestCase() {
  private lateinit var model: SyncNlModel
  private val uri1 = "http://www.example.com"
  private val uri2 = "http://www.example2.com/and/then/some/long/stuff/after"

  override fun setUp() {
    super.setUp()
    model = model("nav.xml",
        NavModelBuilderUtil.rootComponent("root").unboundedChildren(
            NavModelBuilderUtil.fragmentComponent("f1")
                .unboundedChildren(NavModelBuilderUtil.actionComponent("a1").withDestinationAttribute("f2")),
            NavModelBuilderUtil.fragmentComponent("f2")
                .unboundedChildren(NavModelBuilderUtil.deepLinkComponent(uri1),
                    NavModelBuilderUtil.deepLinkComponent(uri2)),
            NavModelBuilderUtil.fragmentComponent("f3")))
        .build()
  }

  fun testMultipleLinks() {
    val property = NavDeeplinkProperty(listOf(model.find("f2")!!))
    assertSameElements(property.properties.keys, listOf(uri1, uri2))
    assertSameElements(property.properties.values.map { it.name }, listOf(uri1, uri2))
  }

  fun testNoActions() {
    val property = NavDeeplinkProperty(listOf(model.find("f1")!!))
    assertTrue(property.properties.isEmpty())
  }

  fun testModify() {
    val fragment = model.find("f1")!!
    val property = NavDeeplinkProperty(listOf(fragment))
    val deeplink = model.find { it.getAttribute(AUTO_URI, ATTR_URI) == uri1 }!!
    fragment.addChild(deeplink)
    property.refreshList()
    assertEquals(deeplink, property.getChildProperty(uri1).components[0])
    fragment.removeChild(deeplink)
    property.refreshList()
    assertTrue(property.properties.isEmpty())
  }
}