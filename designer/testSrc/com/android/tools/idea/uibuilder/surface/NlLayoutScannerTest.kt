/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.mockito.Mockito

class NlLayoutScannerTest : LayoutTestCase() {

  @Volatile
  private var disposable: Disposable? = null

  override fun setUp() {
    super.setUp()
    disposable = object : Disposable {
      // If using a lambda, it can be reused by the JVM and causing an exception
      // because the Disposable is already disposed.
      //noinspection Convert2Lambda
      override fun dispose() { }
    }
  }

  override fun tearDown() {
    try {
      Disposer.dispose(disposable!!)
      disposable = null
    } finally {
      super.tearDown()
    }
  }

  private fun createScanner(): NlLayoutScanner {
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val metricTracker = NlLayoutScannerMetricTrackerTest.createMetricTracker()
    return NlLayoutScanner(issueModel, disposable!!, metricTracker)
  }

  fun testAddListener() {
    val scanner = createScanner()
    val listener = object : NlLayoutScanner.Listener {
      override fun lintUpdated(result: ValidatorResult?) { }
    }

    scanner.addListener(listener)

    assertTrue(scanner.listeners.contains(listener))
  }

  fun testRemoveListener() {
    val scanner = createScanner()
    val listener = object : NlLayoutScanner.Listener {
      override fun lintUpdated(result: ValidatorResult?) { }
    }
    scanner.addListener(listener)
    assertTrue(scanner.listeners.contains(listener))

    scanner.removeListener(listener)
    assertFalse(scanner.listeners.contains(listener))
  }

  fun testRemoveListenerInCallback() {
    val scanner = createScanner()
    val model = ScannerTestHelper().buildModel(0)
    val renderResult = Mockito.mock(RenderResult::class.java)
    val listener = object : NlLayoutScanner.Listener {
      override fun lintUpdated(result: ValidatorResult?) {
        scanner.removeListener(this)
      }
    }
    scanner.addListener(listener)

    scanner.validateAndUpdateLint(renderResult, model)

    assertFalse(scanner.listeners.contains(listener))
  }

  fun testBuildViewToComponentMap() {
    val scanner = createScanner()
    val helper = ScannerTestHelper()
    val component = helper.buildNlComponent()

    // Test building component to view mapping.
    scanner.buildViewToComponentMap(component)

    assertNotNull(component.viewInfo)
    assertNotNull(component.viewInfo?.viewObject)
    assertTrue(component.viewInfo?.viewObject is View)

    assertEquals(1, scanner.viewToComponent.size)
    assertEquals(1, scanner.idToComponent.size)
    assertTrue(scanner.viewToComponent.values.contains(component))
    assertTrue(scanner.idToComponent.keys.contains(helper.lastUsedViewId))
  }

  fun testBuildViewToComponentMapMultipleComponents() {
    val scanner = createScanner()
    val helper = ScannerTestHelper()
    val componentSize = 4
    val component = helper.buildModel(componentSize).components[0]

    scanner.buildViewToComponentMap(component)

    assertEquals(componentSize, scanner.viewToComponent.size)
    assertEquals(componentSize, scanner.idToComponent.size)
    assertTrue(scanner.viewToComponent.values.contains(component))
    component.children.forEach {
      assertTrue(scanner.viewToComponent.values.contains(it))
    }
    assertTrue(scanner.idToComponent.keys.contains(helper.lastUsedViewId))
  }

  fun testFindComponent() {
    val scanner = createScanner()
    val helper = ScannerTestHelper()
    val component = helper.buildNlComponent()
    scanner.buildViewToComponentMap(component)

    val result = helper.generateResult(component).build()
    val issue = ScannerTestHelper.createTestIssueBuilder()
      .setSrcId(helper.lastUsedIssueId).build()

    val found = scanner.findComponent(issue, result.srcMap)

    assertNotNull(found)
    assertEquals(component, found)
  }

  fun testFindComponentThruId() {
    val scanner = createScanner()
    val helper = ScannerTestHelper()
    val component = helper.buildNlComponent()
    scanner.buildViewToComponentMap(component)

    val result = helper.generateResult(component).build()
    val issue = ScannerTestHelper.createTestIssueBuilder()
      .setSrcId(helper.lastUsedIssueId).build()

    // Simulate render out of sync. View to component map no longer useful.
    // When render is out of sync with error update, new view instance is created
    // per component. Force components to find its View thru id match.
    scanner.viewToComponent.clear()

    val found = scanner.findComponent(issue, result.srcMap)

    assertNotNull(found)
    assertEquals(component, found)
  }

  fun testValidateNoResult() {
    val scanner = createScanner()
    val helper = ScannerTestHelper()
    val componentSize = 0
    val model = helper.buildModel(componentSize)
    val renderResult = Mockito.mock(RenderResult::class.java)

    var listenerTriggered = false
    val listener = object : NlLayoutScanner.Listener {
      override fun lintUpdated(result: ValidatorResult?) {
        listenerTriggered = true
      }
    }
    scanner.addListener(listener)

    scanner.validateAndUpdateLint(renderResult, model)

    assertTrue(listenerTriggered)
    assertEquals(0, scanner.issues.size)
    assertTrue(scanner.viewToComponent.isEmpty())
    assertTrue(scanner.idToComponent.isEmpty())
  }

  fun testValidateEmpty() {
    val scanner = createScanner()
    val helper = ScannerTestHelper()
    val componentSize = 0
    val model = helper.buildModel(componentSize)
    val renderResult = helper.mockRenderResult(model)

    var listenerTriggered = false
    val listener = object : NlLayoutScanner.Listener {
      override fun lintUpdated(result: ValidatorResult?) {
        listenerTriggered = true
      }
    }
    scanner.addListener(listener)

    scanner.validateAndUpdateLint(renderResult, model)

    assertTrue(listenerTriggered)
    assertEquals(0, scanner.issues.size)
    assertTrue(scanner.viewToComponent.isEmpty())
    assertTrue(scanner.idToComponent.isEmpty())
  }

  fun testValidateMultipleIssues() {
    val scanner = createScanner()
    val helper = ScannerTestHelper()
    val componentSize = 5
    val model = helper.buildModel(componentSize)
    val renderResult = helper.mockRenderResult(model)

    var validatorResult: ValidatorResult? = null
    val listener = object : NlLayoutScanner.Listener {
      override fun lintUpdated(result: ValidatorResult?) {
        validatorResult = result
      }
    }
    scanner.addListener(listener)

    scanner.validateAndUpdateLint(renderResult, model)

    assertNotNull(validatorResult)
    assertEquals(componentSize, validatorResult!!.issues.size)
    assertEquals(componentSize, scanner.issues.size)
    assertTrue(scanner.viewToComponent.isEmpty())
    assertTrue(scanner.idToComponent.isEmpty())
  }

  fun testValidateFiltersInternalIssues() {
    val scanner = createScanner()
    val helper = ScannerTestHelper()
    val model = helper.buildModel(1)
    val resultToInject = ValidatorResult.Builder()

    // Add 3 types of issues that will be filtered: Internal, verbose or info.
    resultToInject.mIssues.add(
      ScannerTestHelper.createTestIssueBuilder()
        .setLevel(ValidatorData.Level.ERROR)
        .setType(ValidatorData.Type.INTERNAL_ERROR)
        .build())
    resultToInject.mIssues.add(
      ScannerTestHelper.createTestIssueBuilder()
        .setLevel(ValidatorData.Level.VERBOSE)
        .setType(ValidatorData.Type.ACCESSIBILITY)
        .build())
    resultToInject.mIssues.add(
      ScannerTestHelper.createTestIssueBuilder()
        .setLevel(ValidatorData.Level.INFO)
        .setType(ValidatorData.Type.ACCESSIBILITY)
        .build())

    // Run the scanner core code.
    val renderResult = helper.mockRenderResult(model, resultToInject.build())
    var validatorResult: ValidatorResult? = null
    val listener = object : NlLayoutScanner.Listener {
      override fun lintUpdated(result: ValidatorResult?) {
        validatorResult = result
      }
    }
    scanner.addListener(listener)
    scanner.validateAndUpdateLint(renderResult, model)

    // Expect the results to be filtered.
    assertNotNull(validatorResult)
    assertEquals( 3, validatorResult!!.issues.size)
    assertTrue("Issue from Validator Result must be filtered.", scanner.issues.isEmpty())
    assertTrue("Maps must be cleaned after the scan.", scanner.viewToComponent.isEmpty())
    assertTrue("Maps must be cleaned after the scan.", scanner.idToComponent.isEmpty())
  }

  fun testFindRootWithViewLikeDataBinding() {

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
    Mockito.`when`(root.children).thenReturn(children)

    val scanner = createScanner()
    val result = scanner.tryFindingRootWithViewInfo(root)

    assertEquals(child2, result)
  }

  fun testFindRootWithViewInfoInRoot() {

    // Build component tree with root, c1, c2.
    val helper = ScannerTestHelper()
    val root = helper.buildNlComponent()
    val child1 = helper.buildNlComponent()
    val child2 = helper.buildNlComponent()
    val children = ArrayList<NlComponent>(2)
    children.add(child1)
    children.add(child2)
    Mockito.`when`(root.children).thenReturn(children)

    val scanner = createScanner()
    val result = scanner.tryFindingRootWithViewInfo(root)

    assertEquals(root, result)
  }
}
