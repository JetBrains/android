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
package com.android.tools.idea.preview.actions

import com.android.tools.adtui.status.IdeStatus
import com.android.tools.adtui.status.InformationPopup
import com.android.tools.adtui.swing.findAllDescendants
import com.android.tools.idea.editors.fast.DisableReason
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.fast.ManualDisabledReason
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.components.ActionLink
import com.intellij.xml.util.XmlStringUtil
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private data class TestPreviewViewModelStatus(
  override var isRefreshing: Boolean = false,
  override var hasErrorsAndNeedsBuild: Boolean = false,
  override var hasSyntaxErrors: Boolean = false,
  override var isOutOfDate: Boolean = false,
  override val areResourcesOutOfDate: Boolean = false,
  override var previewedFile: PsiFile? = null,
) : PreviewViewModelStatus

/** Use this method when [CommonIssueNotificationAction] should not create a popup. */
@Suppress("UNUSED_PARAMETER")
private fun noPopupFactor(project: Project, dataContext: DataContext): InformationPopup =
  throw IllegalStateException("Unexpected popup created")

class CommonIssueNotificationActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private var viewModelStatus = TestPreviewViewModelStatus()
  private val dataContext = DataContext {
    when (it) {
      PREVIEW_VIEW_MODEL_STATUS.name -> viewModelStatus
      CommonDataKeys.PROJECT.name -> projectRule.project
      else -> null
    }
  }

  @Test
  fun `check simple states`() {
    val action = CommonIssueNotificationAction()
    val event = TestActionEvent.createTestEvent(dataContext)

    action.update(event)
    assertEquals("Up-to-date", event.presentation.text)
    assertEquals("The preview is up to date", event.presentation.description)

    viewModelStatus = TestPreviewViewModelStatus(hasErrorsAndNeedsBuild = true)
    action.update(event)
    assertEquals("Render Issues", event.presentation.text)
    assertEquals(
      "Some problems were found while rendering the preview",
      event.presentation.description,
    )

    viewModelStatus = TestPreviewViewModelStatus(isOutOfDate = true)
    action.update(event)
    assertEquals("Out of date", event.presentation.text)
    assertEquals("The preview is out of date", event.presentation.description)
    try {
      FastPreviewManager.getInstance(projectRule.project).disable(ManualDisabledReason)
      action.update(event)
      assertEquals("Out of date", event.presentation.text)
      assertEquals("The preview is out of date", event.presentation.description)
    } finally {
      FastPreviewManager.getInstance(projectRule.project).enable()
    }

    viewModelStatus = TestPreviewViewModelStatus(hasSyntaxErrors = true)
    action.update(event)
    assertEquals("Paused", event.presentation.text)
    assertEquals(
      "The preview will not update while your project contains syntax errors.",
      event.presentation.description,
    )

    viewModelStatus = TestPreviewViewModelStatus(isRefreshing = true)
    action.update(event)
    assertEquals("Loading...", event.presentation.text)
    assertEquals("The preview is updating...", event.presentation.description)

    viewModelStatus = TestPreviewViewModelStatus(hasErrorsAndNeedsBuild = true)
    action.update(event)
    val statusInfo = getStatusInfo(projectRule.project, dataContext)!!
    assertTrue(statusInfo.hasRefreshIcon)
    assertEquals(IdeStatus.Presentation.Warning, statusInfo.presentation)
    assertEquals("Render Issues", event.presentation.text)
    assertEquals(
      "Some problems were found while rendering the preview",
      event.presentation.description,
    )
  }

  @Test
  fun `check state priorities`() {
    val action = CommonIssueNotificationAction(::noPopupFactor)
    val event = TestActionEvent.createTestEvent(dataContext)

    viewModelStatus =
      TestPreviewViewModelStatus(
        hasSyntaxErrors = true,
        hasErrorsAndNeedsBuild = true,
        isOutOfDate = true,
      )
    action.update(event)
    // Syntax errors take precedence over out of date when Fast Preview is Enabled
    assertEquals("Paused", event.presentation.text)
    assertEquals(
      "The preview will not update while your project contains syntax errors.",
      event.presentation.description,
    )

    try {
      FastPreviewManager.getInstance(projectRule.project).disable(ManualDisabledReason)

      action.update(event)
      // Syntax errors does NOT take precedence over out of date when Fast Preview is Disabled
      assertEquals("Out of date", event.presentation.text)
      assertEquals("The preview is out of date", event.presentation.description)
    } finally {
      FastPreviewManager.getInstance(projectRule.project).enable()
    }

    viewModelStatus =
      TestPreviewViewModelStatus(
        hasSyntaxErrors = true,
        hasErrorsAndNeedsBuild = true,
        isOutOfDate = true,
        isRefreshing = true,
      )
    action.update(event)
    assertEquals("Loading...", event.presentation.text)
    assertEquals("The preview is updating...", event.presentation.description)

    // Most other statuses take precedence over runtime errors
    viewModelStatus =
      TestPreviewViewModelStatus(
        hasSyntaxErrors = true,
        hasErrorsAndNeedsBuild = true,
        isOutOfDate = true,
        isRefreshing = true,
      )
    action.update(event)
    assertEquals("Loading...", event.presentation.text)
    assertEquals("The preview is updating...", event.presentation.description)

    viewModelStatus = TestPreviewViewModelStatus(hasErrorsAndNeedsBuild = true, isOutOfDate = true)
    try {
      FastPreviewManager.getInstance(projectRule.project).disable(ManualDisabledReason)

      action.update(event)
      // Syntax errors does NOT take precedence over out of date when Fast Preview is Disabled
      assertEquals("Out of date", event.presentation.text)
      assertEquals("The preview is out of date", event.presentation.description)
    } finally {
      FastPreviewManager.getInstance(projectRule.project).enable()
    }

    viewModelStatus =
      TestPreviewViewModelStatus(hasErrorsAndNeedsBuild = true, hasSyntaxErrors = true)
    action.update(event)
    assertEquals(
      "The preview will not update while your project contains syntax errors.",
      event.presentation.description,
    )
    assertEquals(
      "The preview will not update while your project contains syntax errors.",
      event.presentation.description,
    )
  }

  private fun InformationPopup.labelsDescription(): String =
    popupComponent
      .findAllDescendants(JLabel::class.java)
      .map { XmlStringUtil.stripHtml(it.text) }
      .joinToString("\n")

  private fun InformationPopup.linksDescription(): String =
    popupComponent
      .findAllDescendants(ActionLink::class.java)
      .map { it.text.replace("\\(.*\\)".toRegex(), "(SHORTCUT)") }
      .joinToString("\n")

  @Test
  fun `check InformationPopup states`() {
    val fastPreviewManager = projectRule.project.fastPreviewManager
    // Default state check
    run {
      val popup = defaultCreateInformationPopup(projectRule.project, dataContext)!!
      assertEquals("The preview is up to date", popup.labelsDescription())
      assertEquals("Build & Refresh (SHORTCUT)", popup.linksDescription())
    }

    run {
      viewModelStatus = TestPreviewViewModelStatus(isOutOfDate = true)
      val popup = defaultCreateInformationPopup(projectRule.project, dataContext)!!
      assertEquals("The preview is out of date", popup.labelsDescription())
      assertEquals("Build & Refresh (SHORTCUT)", popup.linksDescription())
    }

    // Verify popup for an error that auto disabled the Fast Preview
    run {
      fastPreviewManager.disable(DisableReason("error"))
      try {
        viewModelStatus = TestPreviewViewModelStatus(isOutOfDate = true)
        val popup = defaultCreateInformationPopup(projectRule.project, dataContext)!!
        assertEquals(
          "The code might contain errors or might not work with Preview Live Edit.",
          popup.labelsDescription(),
        )
        assertEquals(
          """
          Build & Refresh (SHORTCUT)
          Re-enable
          Do not disable automatically
          View Details
        """
            .trimIndent(),
          popup.linksDescription(),
        )
      } finally {
        fastPreviewManager.enable()
      }
    }

    // Verify popup when the preview is out of date and the USER has disabled Fast Preview
    run {
      fastPreviewManager.disable(ManualDisabledReason)
      try {
        viewModelStatus = TestPreviewViewModelStatus(isOutOfDate = true)
        val popup = defaultCreateInformationPopup(projectRule.project, dataContext)!!
        assertEquals("The preview is out of date", popup.labelsDescription())
        assertEquals("Build & Refresh (SHORTCUT)", popup.linksDescription())
      } finally {
        fastPreviewManager.enable()
      }
    }

    // Verify refresh status
    run {
      viewModelStatus =
        TestPreviewViewModelStatus(
          isRefreshing = true,
          isOutOfDate =
            true, // Leaving out of date to true to verify it does not take precedence over refresh
        )
      val popup = defaultCreateInformationPopup(projectRule.project, dataContext)!!
      assertEquals("The preview is updating...", popup.labelsDescription())
      assertEquals("Build & Refresh (SHORTCUT)", popup.linksDescription())
    }

    // Verify syntax error status
    run {
      viewModelStatus =
        TestPreviewViewModelStatus(
          hasSyntaxErrors = true,
          isOutOfDate =
            true, // Leaving out of date to true to verify it does not take precedence over refresh
        )
      val popup = defaultCreateInformationPopup(projectRule.project, dataContext)!!
      assertEquals(
        "The preview will not update while your project contains syntax errors.",
        popup.labelsDescription(),
      )
      assertEquals(
        """
        Build & Refresh (SHORTCUT)
        View Problems"""
          .trimIndent(),
        popup.linksDescription(),
      )
    }

    // Verify render issues status
    run {
      viewModelStatus = TestPreviewViewModelStatus(hasErrorsAndNeedsBuild = true)
      val popup = defaultCreateInformationPopup(projectRule.project, dataContext)!!
      assertEquals(
        "Some problems were found while rendering the preview",
        popup.labelsDescription(),
      )
      assertEquals(
        """
        Build & Refresh (SHORTCUT)
        View Problems"""
          .trimIndent(),
        popup.linksDescription(),
      )
    }
  }

  @Test
  fun `test popup is triggered`() {
    val fakePopup =
      object : InformationPopup {
        override val popupComponent: JComponent = object : JComponent() {}
        override var onMouseEnteredCallback: () -> Unit = {}

        override fun hidePopup() {}

        override fun showPopup(disposableParent: Disposable, owner: JComponent) {}

        override fun isVisible(): Boolean = false

        override fun dispose() {}
      }

    var popupRequested = 0
    val action = CommonIssueNotificationAction { _, _ ->
      popupRequested++
      fakePopup
    }
    val event =
      TestActionEvent.createTestEvent(
        action,
        dataContext,
        MouseEvent(JPanel(), 0, 0, 0, 0, 0, 1, true, MouseEvent.BUTTON1),
      )
    action.update(event)
    assertEquals(0, popupRequested)
    action.actionPerformed(event)
    assertEquals(1, popupRequested)
  }
}
