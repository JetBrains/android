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

import com.android.tools.idea.common.analytics.CommonNopTracker
import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssuePanel
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.validator.LayoutValidator
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorHierarchy
import com.android.tools.idea.validator.ValidatorResult
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(JUnit4::class)
class NlLayoutScannerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  @Mock
  lateinit var mockSurface: NlDesignSurface
  @Mock
  lateinit var mockModel: NlModel

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this)
  }

  private fun createScanner(): NlLayoutScanner {
    val issueModel: IssueModel = Mockito.mock(IssueModel::class.java)
    val issuePanel: IssuePanel = Mockito.mock(IssuePanel::class.java)
    Mockito.`when`(mockSurface.issueModel).thenReturn(issueModel)
    Mockito.`when`(mockSurface.issuePanel).thenReturn(issuePanel)
    return NlLayoutScanner(mockSurface, projectRule.fixture.testRootDisposable!!)
  }

  @Test
  fun testEnsureObtainCharacterLocationsOn() {
    createScanner()

    assertTrue(LayoutValidator.obtainCharacterLocations())
  }

  @Test
  fun issuePanelExpanded() {
    val scanner = createScanner()
    val usageTracker = CommonUsageTracker.getInstance(mockSurface) as CommonNopTracker
    usageTracker.resetLastTrackedEvent()

    val issue = NlAtfIssue(ScannerTestHelper.createTestIssueBuilder().build(),
                           IssueSource.NONE,
                           mockModel)
    scanner.issuePanelListener.onIssueExpanded(issue, true)

    LayoutTestCase.assertEquals(LayoutEditorEvent.LayoutEditorEventType.ATF_AUDIT_RESULT, usageTracker.lastTrackedEvent)
  }

  @Test
  fun issuePanelCollapsed() {
    val scanner = createScanner()
    val usageTracker = CommonUsageTracker.getInstance(mockSurface) as CommonNopTracker
    usageTracker.resetLastTrackedEvent()

    val issue = NlAtfIssue(ScannerTestHelper.createTestIssueBuilder().build(),
                           IssueSource.NONE,
                           mockModel)
    scanner.issuePanelListener.onIssueExpanded(issue, false)

    LayoutTestCase.assertNull(usageTracker.lastTrackedEvent)
  }

  @Test
  fun issueIssueExpanded() {
    val scanner = createScanner()
    val usageTracker = CommonUsageTracker.getInstance(mockSurface) as CommonNopTracker
    usageTracker.resetLastTrackedEvent()

    val issue = NlAtfIssue(ScannerTestHelper.createTestIssueBuilder().build(),
                           IssueSource.NONE,
                           mockModel)
    scanner.issuePanelListener.onIssueExpanded(issue, true)

    LayoutTestCase.assertEquals(LayoutEditorEvent.LayoutEditorEventType.ATF_AUDIT_RESULT, usageTracker.lastTrackedEvent)
  }

  @Test
  fun issueIssueCollapsed() {
    val scanner = createScanner()
    val usageTracker = CommonUsageTracker.getInstance(mockSurface) as CommonNopTracker
    usageTracker.resetLastTrackedEvent()

    val issue = NlAtfIssue(ScannerTestHelper.createTestIssueBuilder().build(),
                           IssueSource.NONE,
                           mockModel)
    scanner.issuePanelListener.onIssueExpanded(issue, false)

    LayoutTestCase.assertNull(usageTracker.lastTrackedEvent)
  }

  @Test
  fun pauseAndresume() {
    val scanner = createScanner()
    try {
      scanner.pause()
      assertTrue(LayoutValidator.isPaused())

      scanner.resume()
      assertFalse(LayoutValidator.isPaused())
    } finally {
      scanner.resume()
    }
  }

  @Test
  fun addListener() {
    val scanner = createScanner()
    val listener = object : NlLayoutScanner.Listener {
      override fun lintUpdated(result: ValidatorResult?) { }
    }

    scanner.addListener(listener)

    assertTrue(scanner.listeners.contains(listener))
  }

  @Test
  fun removeListener() {
    val scanner = createScanner()
    val listener = object : NlLayoutScanner.Listener {
      override fun lintUpdated(result: ValidatorResult?) { }
    }
    scanner.addListener(listener)
    assertTrue(scanner.listeners.contains(listener))

    scanner.removeListener(listener)
    assertFalse(scanner.listeners.contains(listener))
  }

  @Test
  fun removeListenerInCallback() {
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

  @Test
  fun validateNoResult() {
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
    assertTrue(scanner.isParserCleaned())
  }

  @Test
  fun validateEmpty() {
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
    assertTrue(scanner.isParserCleaned())
  }

  @Test
  fun validateMultipleIssues() {
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
    assertTrue(scanner.isParserCleaned())
  }

  @Test
  fun validateFiltersInternalIssues() {
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
    assertTrue("Maps must be cleaned after the scan.", scanner.isParserCleaned())
  }
}
