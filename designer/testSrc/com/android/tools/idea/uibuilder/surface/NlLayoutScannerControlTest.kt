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

import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssuePanel
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.mockito.Mockito

class NlLayoutScannerControlTest : LayoutTestCase() {

  @Volatile
  private var disposable: Disposable? = null
  private var surface: NlDesignSurface? = null
  private var sceneManager: LayoutlibSceneManager? = null
  private val check = object : LayoutScannerConfiguration {
    override var isLayoutScannerEnabled: Boolean = true
    override var isScannerAlwaysOn: Boolean = true
  }

  override fun setUp() {
    super.setUp()
    disposable = object : Disposable {
      // If using a lambda, it can be reused by the JVM and causing an exception
      // because the Disposable is already disposed.
      //noinspection Convert2Lambda
      override fun dispose() { }
    }
    surface = mockSurfaceNoSceneManager()
    sceneManager = Mockito.mock(LayoutlibSceneManager::class.java)
    Mockito.`when`(surface!!.sceneManager).thenReturn(sceneManager)
    Mockito.`when`(sceneManager!!.layoutScannerConfig).thenReturn(check)

    val analyticsManager = NlAnalyticsManager(surface!!)
    Mockito.`when`(surface!!.analyticsManager).thenReturn(analyticsManager)
  }

  override fun tearDown() {
    try {
      Disposer.dispose(disposable!!)
      disposable = null
      surface = null
      sceneManager = null
    } finally {
      super.tearDown()
    }
  }

  fun testIssuePanelExpandedWithIssues() {
    val surface = surface!!
    val control = NlLayoutScannerControl(surface, disposable!!)

    // Precondition: Scanner has some issues already
    val issuesSize = 3
    simulateSurfaceRefreshedWithScanner(control, issuesSize)
    assertEquals(issuesSize, control.issues.size)
    assertTrue(check.isLayoutScannerEnabled)

    // Test: ensure it doesn't re-render.
    control.issuePanelListener.onMinimizeChanged(false)

    Mockito.verify(surface, Mockito.never()).requestRender()
  }

  /**
   * Simulate surface render with scanner option turned on, and generate
   * issues thru [NlLayoutScanner.validateAndUpdateLint].
   */
  private fun simulateSurfaceRefreshedWithScanner(control: NlLayoutScannerControl, issuesSize: Int) {
    val helper = ScannerTestHelper()
    val model = helper.buildModel(issuesSize)
    check.isLayoutScannerEnabled = true
    control.validateAndUpdateLint(helper.mockRenderResult(model), model)
  }

  private fun mockSurfaceNoSceneManager(): NlDesignSurface {
    val surface = Mockito.mock(NlDesignSurface::class.java)
    val issueModel = IssueModel()
    val issuePanel = IssuePanel(surface, issueModel)
    Mockito.`when`(surface.issueModel).thenReturn(issueModel)
    Mockito.`when`(surface.issuePanel).thenReturn(issuePanel)
    return surface
  }
}
