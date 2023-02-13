/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.ide.common.rendering.api.Bridge
import com.android.tools.adtui.stdui.ActionData
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.preview.mvvm.PreviewView
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private class TestPreviewView : PreviewView {
  val errorMessages = mutableListOf<String>()
  val loadingMessages = mutableListOf<String>()
  var showContentCalls = 0
  var updateToolbarCalls = 0

  override fun showErrorMessage(
    message: String,
    recoveryUrl: UrlData?,
    actionToRecover: ActionData?
  ) {
    errorMessages.add(message)
  }

  override fun showLoadingMessage(message: String) {
    loadingMessages.add(message)
  }

  override fun showContent() {
    showContentCalls++
  }

  override fun updateToolbar() {
    updateToolbarCalls++
  }
}

class GlancePreviewViewModelTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project: Project
    get() = projectRule.project
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val statusManager =
    object : ProjectBuildStatusManager {
      override var isBuilding: Boolean = false
      override var status: ProjectStatus = ProjectStatus.NotReady
    }

  private val testView = TestPreviewView()

  private val hasRenderErrors =
    object : () -> Boolean {
      var has = false

      override fun invoke() = has
    }

  private lateinit var file: PsiFile

  private lateinit var viewModel: GlancePreviewViewModel

  @Before
  fun setUp() {
    file = fixture.configureByText("foo.txt", "")
    val filePtr = runReadAction { SmartPointerManager.createPointer(file) }

    viewModel = GlancePreviewViewModel(testView, statusManager, project, filePtr, hasRenderErrors)
  }

  @After
  fun tearDown() {
    Bridge.setNativeCrash(false)
  }

  @Test
  fun testRefreshWhenNeedsBuild() =
    runBlocking(uiThread) {
      statusManager.status = ProjectStatus.NeedsBuild

      viewModel.activate()

      Assert.assertEquals(
        "A successful build is needed before the preview can be displayed",
        testView.errorMessages.last()
      )
      Assert.assertTrue(testView.loadingMessages.isEmpty())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(1, testView.updateToolbarCalls)

      viewModel.onEnterSmartMode()

      Assert.assertEquals(
        "A successful build is needed before the preview can be displayed",
        testView.errorMessages.last()
      )
      Assert.assertTrue(testView.loadingMessages.isEmpty())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(2, testView.updateToolbarCalls)

      viewModel.refreshCompleted(false, 10)

      Assert.assertEquals(
        "A successful build is needed before the preview can be displayed",
        testView.errorMessages.last()
      )
      Assert.assertTrue(testView.loadingMessages.isEmpty())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(3, testView.updateToolbarCalls)
    }

  @Test
  fun testRefreshWithBuildNotReady() =
    runBlocking(uiThread) {
      viewModel.activate()

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals("Initializing...", testView.loadingMessages.last())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(1, testView.updateToolbarCalls)

      viewModel.onEnterSmartMode()

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals("Initializing...", testView.loadingMessages.last())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(2, testView.updateToolbarCalls)

      statusManager.status = ProjectStatus.NeedsBuild

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals("Initializing...", testView.loadingMessages.last())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(2, testView.updateToolbarCalls)

      statusManager.isBuilding = true
      viewModel.buildStarted()

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals("Waiting for build to finish...", testView.loadingMessages.last())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(3, testView.updateToolbarCalls)

      statusManager.isBuilding = false
      statusManager.status = ProjectStatus.Ready
      viewModel.buildSucceeded()

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals("Initializing...", testView.loadingMessages.last())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(4, testView.updateToolbarCalls)

      val loadingMessagesCount = testView.loadingMessages.count()

      viewModel.refreshStarted()

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals(loadingMessagesCount, testView.loadingMessages.count())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(5, testView.updateToolbarCalls)

      viewModel.setHasPreviews(false)

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals(loadingMessagesCount, testView.loadingMessages.count())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(5, testView.updateToolbarCalls)

      viewModel.beforePreviewsRefreshed()

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals(loadingMessagesCount + 1, testView.loadingMessages.count())
      Assert.assertEquals(0, testView.showContentCalls)
      Assert.assertEquals(6, testView.updateToolbarCalls)

      viewModel.afterPreviewsRefreshed()

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals(loadingMessagesCount + 1, testView.loadingMessages.count())
      Assert.assertEquals(1, testView.showContentCalls)
      Assert.assertEquals(7, testView.updateToolbarCalls)

      viewModel.refreshFinished()

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals(loadingMessagesCount + 1, testView.loadingMessages.count())
      Assert.assertEquals(1, testView.showContentCalls)
      Assert.assertEquals(8, testView.updateToolbarCalls)

      viewModel.refreshCompleted(false, 10_000_000)

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertEquals(loadingMessagesCount + 1, testView.loadingMessages.count())
      Assert.assertEquals(2, testView.showContentCalls)
      Assert.assertEquals(9, testView.updateToolbarCalls)
    }

  @Test
  fun testNativeCrash() =
    runBlocking(uiThread) {
      Bridge.setNativeCrash(false)

      viewModel.checkForNativeCrash {}

      Assert.assertTrue(testView.errorMessages.isEmpty())
      Assert.assertTrue(testView.loadingMessages.isEmpty())
      Assert.assertEquals(0, testView.showContentCalls)

      Bridge.setNativeCrash(true)

      viewModel.checkForNativeCrash {}

      Assert.assertFalse(testView.errorMessages.isEmpty())
      Assert.assertTrue(testView.loadingMessages.isEmpty())
      Assert.assertEquals(
        "The preview has been disabled following a crash in the rendering engine. If the problem persists, please report the issue.",
        testView.errorMessages.last()
      )
    }

  @Test
  fun testIsRefreshing() {
    Assert.assertFalse(viewModel.isRefreshing)

    viewModel.refreshStarted()

    Assert.assertTrue(viewModel.isRefreshing)

    viewModel.refreshStarted()

    Assert.assertTrue(viewModel.isRefreshing)

    viewModel.refreshFinished()

    Assert.assertTrue(viewModel.isRefreshing)

    viewModel.refreshFinished()

    Assert.assertFalse(viewModel.isRefreshing)
  }

  @Test
  fun testHasErrorsAndNeedsBuild() {
    Assert.assertFalse(viewModel.hasErrorsAndNeedsBuild)

    viewModel.setHasPreviews(true)

    Assert.assertTrue(viewModel.hasErrorsAndNeedsBuild)

    viewModel.afterPreviewsRefreshed()

    Assert.assertFalse(viewModel.hasErrorsAndNeedsBuild)

    hasRenderErrors.has = true

    Assert.assertTrue(viewModel.hasErrorsAndNeedsBuild)

    viewModel.setHasPreviews(false)

    Assert.assertFalse(viewModel.hasErrorsAndNeedsBuild)
  }

  @Test
  fun testIsOutOfDate() {
    Assert.assertFalse(viewModel.isOutOfDate)

    statusManager.status = ProjectStatus.OutOfDate.Code

    Assert.assertTrue(viewModel.isOutOfDate)
  }

  @Test
  fun testPreviewedFile() {
    Assert.assertEquals(file, viewModel.previewedFile)
  }
}
