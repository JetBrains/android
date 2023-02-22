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
package com.android.tools.idea.glance.preview.actions

import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.preview.actions.PreviewStatus
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.projectsystem.TestProjectSystemBuildManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiFile
import javax.swing.JLabel
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GlanceIssueNotificationActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var buildManager: TestProjectSystemBuildManager
  private val viewModelStatus =
    object : PreviewViewModelStatus {
      override var isRefreshing: Boolean = true
      override var hasErrorsAndNeedsBuild: Boolean = true
      override var hasSyntaxErrors: Boolean = true
      override var isOutOfDate: Boolean = true
      override var previewedFile: PsiFile? = null
    }
  private val dataContext = DataContext {
    when (it) {
      PREVIEW_VIEW_MODEL_STATUS.name -> viewModelStatus
      else -> null
    }
  }

  @Before
  fun setUp() {
    val projectSystem = TestProjectSystem(projectRule.project, listOf())
    projectSystem.useInTests()
    buildManager = projectSystem.getBuildManager() as TestProjectSystemBuildManager
    buildManager.buildCompleted(ProjectSystemBuildManager.BuildStatus.FAILED)
  }

  @Test
  fun testGetStatusInfo() {
    Assert.assertNull(getStatusInfo(projectRule.project) { null })

    Assert.assertTrue(getStatusInfo(projectRule.project, dataContext) is PreviewStatus.Refreshing)

    viewModelStatus.isRefreshing = false

    Assert.assertEquals(PreviewStatus.OutOfDate, getStatusInfo(projectRule.project, dataContext))

    viewModelStatus.isOutOfDate = false

    Assert.assertEquals(PreviewStatus.NeedsBuild, getStatusInfo(projectRule.project, dataContext))

    buildManager.buildCompleted(ProjectSystemBuildManager.BuildStatus.SUCCESS)

    Assert.assertEquals(PreviewStatus.SyntaxError, getStatusInfo(projectRule.project, dataContext))

    viewModelStatus.hasSyntaxErrors = false

    Assert.assertEquals(PreviewStatus.RenderIssues, getStatusInfo(projectRule.project, dataContext))

    viewModelStatus.hasErrorsAndNeedsBuild = false

    Assert.assertEquals(PreviewStatus.UpToDate, getStatusInfo(projectRule.project, dataContext))
  }

  @Test
  fun testCreateInformationPopup() {
    Assert.assertNull(createInformationPopup(projectRule.project) { null })

    run {
      val popup = createInformationPopup(projectRule.project, dataContext)
      Assert.assertTrue(
        popup!!
          .popupComponent
          .getDescendant(JLabel::class.java) { it.text.contains("The preview is updating...") }
          .isVisible
      )
    }

    viewModelStatus.isRefreshing = false

    run {
      val popup = createInformationPopup(projectRule.project, dataContext)
      Assert.assertTrue(
        popup!!
          .popupComponent
          .getDescendant(JLabel::class.java) { it.text.contains("The preview is out of date") }
          .isVisible
      )
    }

    viewModelStatus.isOutOfDate = false

    run {
      val popup = createInformationPopup(projectRule.project, dataContext)
      Assert.assertTrue(
        popup!!
          .popupComponent
          .getDescendant(JLabel::class.java) {
            it.text.contains("The project needs to be compiled")
          }
          .isVisible
      )
    }

    buildManager.buildCompleted(ProjectSystemBuildManager.BuildStatus.SUCCESS)

    run {
      val popup = createInformationPopup(projectRule.project, dataContext)
      Assert.assertTrue(
        popup!!
          .popupComponent
          .getDescendant(JLabel::class.java) {
            it.text.contains(
              "The preview will not update while your project contains syntax errors"
            )
          }
          .isVisible
      )
    }

    viewModelStatus.hasSyntaxErrors = false

    run {
      val popup = createInformationPopup(projectRule.project, dataContext)
      Assert.assertTrue(
        popup!!
          .popupComponent
          .getDescendant(JLabel::class.java) {
            it.text.contains("Some problems were found while rendering the preview")
          }
          .isVisible
      )
    }

    viewModelStatus.hasErrorsAndNeedsBuild = false

    run {
      val popup = createInformationPopup(projectRule.project, dataContext)
      Assert.assertTrue(
        popup!!
          .popupComponent
          .getDescendant(JLabel::class.java) { it.text.contains("The preview is up to date") }
          .isVisible
      )
    }
  }
}
