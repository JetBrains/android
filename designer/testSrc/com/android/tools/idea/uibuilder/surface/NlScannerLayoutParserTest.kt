/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import android.view.View
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.model.viewInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class NlScannerLayoutParserTest {

  @Test
  fun buildViewToComponentMap() {
    val layoutParser = NlScannerLayoutParser()
    val helper = ScannerTestHelper()
    val component = helper.buildNlComponent()

    // Test building component to view mapping.
    layoutParser.buildViewToComponentMap(component)

    assertNotNull(component.viewInfo)
    assertNotNull(component.viewInfo?.viewObject)
    assertTrue(component.viewInfo?.viewObject is View)

    assertEquals(1, layoutParser.viewToComponent.size)
    assertEquals(1, layoutParser.idToComponent.size)
    assertTrue(layoutParser.viewToComponent.values.contains(component))
    assertTrue(layoutParser.idToComponent.keys.contains(helper.lastUsedViewId))
  }

  @Test
  fun buildViewToComponentMapMultipleComponents() {
    val layoutParser = NlScannerLayoutParser()
    val helper = ScannerTestHelper()
    val componentSize = 4
    val component = helper.buildModel(componentSize).components[0]

    layoutParser.buildViewToComponentMap(component)

    assertEquals(componentSize, layoutParser.viewToComponent.size)
    assertEquals(componentSize, layoutParser.idToComponent.size)
    assertTrue(layoutParser.viewToComponent.values.contains(component))
    component.children.forEach {
      assertTrue(layoutParser.viewToComponent.values.contains(it))
    }
    assertTrue(layoutParser.idToComponent.keys.contains(helper.lastUsedViewId))
  }

  @Test
  fun findComponent() {
    val layoutParser = NlScannerLayoutParser()
    val helper = ScannerTestHelper()
    val component = helper.buildNlComponent()
    layoutParser.buildViewToComponentMap(component)

    val result = helper.generateResult(component).build()
    val issue = ScannerTestHelper.createTestIssueBuilder()
      .setSrcId(helper.lastUsedIssueId).build()

    val found = layoutParser.findComponent(issue, result.srcMap, result.nodeInfoMap)

    assertNotNull(found)
    assertEquals(component, found)
  }

  @Test
  fun findComponentThruId() {
    val layoutParser = NlScannerLayoutParser()
    val helper = ScannerTestHelper()
    val component = helper.buildNlComponent()
    layoutParser.buildViewToComponentMap(component)

    val result = helper.generateResult(component).build()
    val issue = ScannerTestHelper.createTestIssueBuilder()
      .setSrcId(helper.lastUsedIssueId).build()

    // Simulate render out of sync. View to component map no longer useful.
    // When render is out of sync with error update, new view instance is created
    // per component. Force components to find its View thru id match.
    layoutParser.viewToComponent.clear()

    val found = layoutParser.findComponent(issue, result.srcMap, result.nodeInfoMap)

    assertNotNull(found)
    assertEquals(component, found)
  }

  @Test
  fun findRootWithViewLikeDataBinding() {

    // Build component tree with root, c1, c2 with only c2 with view info
    val helper = ScannerTestHelper()
    val root = helper.buildNlComponent()
    val child1 = helper.buildNlComponent()
    val child2 = helper.buildNlComponent()
    root.viewInfo = null
    child1.viewInfo = null
    val children = ArrayList<NlComponent>(2)
    children.add(child1)
    children.add(child2)
    whenever(root.children).thenReturn(children)

    val layoutParser = NlScannerLayoutParser()
    val result = layoutParser.tryFindingRootWithViewInfo(root)

    assertEquals(child2, result)
  }

  @Test
  fun findRootWithViewInfoInRoot() {

    // Build component tree with root, c1, c2.
    val helper = ScannerTestHelper()
    val root = helper.buildNlComponent()
    val child1 = helper.buildNlComponent()
    val child2 = helper.buildNlComponent()
    val children = ArrayList<NlComponent>(2)
    children.add(child1)
    children.add(child2)
    whenever(root.children).thenReturn(children)

    val layoutParser = NlScannerLayoutParser()
    val result = layoutParser.tryFindingRootWithViewInfo(root)

    assertEquals(root, result)
  }

  companion object {
    /** Returns parser and the root component */
    fun createComponentWithInclude(): NlComponent {
      val helper = ScannerTestHelper()
      val root = helper.buildNlComponent()
      val include = helper.buildNlComponent(tagName = "include")
      whenever(root.children).thenReturn(listOf(include))

      return root
    }
  }

  @Test
  fun buildViewWithInclude() {
    val parser = NlScannerLayoutParser()
    val root = createComponentWithInclude()

    assertTrue(parser.includeComponents.isEmpty())
    parser.buildViewToComponentMap(root)
    assertEquals(1, parser.includeComponents.size)
  }
}