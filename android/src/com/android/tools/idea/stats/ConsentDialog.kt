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
package com.android.tools.idea.stats

import com.android.tools.adtui.ImageComponent
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.idea.ui.GuiTestingService
import com.google.common.base.Predicates
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.AppUIUtil
import icons.StudioIllustrations
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.NORTHWEST
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.lang.Boolean
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import kotlin.Array
import kotlin.apply
import kotlin.arrayOf

class ConsentDialog(private val consent: Consent) : DialogWrapper(null) {
  override fun createActions(): Array<Action> {
    val decline = object : DialogWrapperAction("Don't send") {
      override fun doAction(e: ActionEvent) {
        close(NEXT_USER_EXIT_CODE)
      }
    }

    val consent = object : DialogWrapperAction(consent.name) {
      override fun doAction(e: ActionEvent) {
        close(OK_EXIT_CODE)
      }
    }

    return arrayOf(decline, consent)
  }

  val content: JComponent = JPanel(GridBagLayout()).apply {
    val icon = StudioIllustrations.Common.PRODUCT_ICON
    val imageComponent = ImageComponent(icon)
    imageComponent.preferredSize = Dimension(icon.iconWidth, icon.iconHeight)

    val constraints = GridBagConstraints().apply {
      gridx = 0
      gridy = 0
      gridheight = 3
      anchor = NORTHWEST
      insets = Insets(10, 5, 5, 12)
    }

    add(imageComponent, constraints)

    val title = JLabel().apply {
      text = "Help improve Android Studio"
      font = Font(font.name, Font.BOLD, font.size + 2)
    }

    constraints.apply {
      gridx = 1
      gridheight = 1
      insets = Insets(10, 0, 10, 0)
    }

    add(title, constraints)

    val message = JEditorPane("text/html", consent.text).apply {
      isEditable = false
      background = Color(0, 0, 0, 0)
      font = Font(font.name, Font.PLAIN, title.font.size - 2)
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
      preferredSize = Dimension(420, 120)

      addHyperlinkListener { e ->
        if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(e.url.toURI());
          }
        }
      }
    }

    constraints.apply {
      gridy = 2
    }

    add(message, constraints)

    val menuName = ShowSettingsUtil.getSettingsMenuName()
    val text = "You can always change this behavior in $menuName | Appearance & Behavior | System Settings | Data Sharing."

    val hint = JEditorPane("text/html", text).apply {
      isEditable = false
      background = Color(0, 0, 0, 0)
      foreground = Color.GRAY
      font = Font(font.name, Font.PLAIN, title.font.size - 4)
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
      preferredSize = Dimension(420, 35)
    }

    constraints.apply {
      gridy = 3
    }

    add(hint, constraints)
  }

  init {
    isResizable = false
    isModal = true
    setUndecorated(true)
    init()
  }

  override fun createCenterPanel(): JComponent = content

  companion object {
    private const val ENABLE_DIALOG_PROPERTY = "enable.android.analytics.consent.dialog.for.test"

    private val isConsentDialogEnabledInTests
      get() = Boolean.getBoolean(ENABLE_DIALOG_PROPERTY)

    // If the user hasn't opted in, we will ask IJ to check if the user has
    // provided a decision on the statistics consent. If the user hasn't made a
    // choice, a modal dialog will be shown asking for a decision
    // before the regular IDE ui components are shown.
    @JvmStatic
    fun showConsentDialogIfNeeded() {
      if (AnalyticsSettings.optedIn) {
        return
      }

      // If we're running in a test or headless mode, do not show the dialog
      // as it would block the test & IDE from proceeding.
      // NOTE: in this case the metrics logic will be left in the opted-out state
      // and no metrics are ever sent.
      val application = ApplicationManager.getApplication()
      if ((GuiTestingService.isInTestingMode() || application.isHeadlessEnvironment)
          && !isConsentDialogEnabledInTests) {
        return
      }

      val options = ConsentOptions.getInstance()
      val consentsToShow = options.getConsents(Predicates.alwaysTrue())
      val list = consentsToShow.first

      if (list.size != 1) {
        return
      }

      // if second is false, it indicates that the user has responded
      // to the consent dialog
      val majorVersion = ApplicationInfo.getInstance().majorVersion
      val minorVersion = ApplicationInfo.getInstance().minorVersion
      if (!consentsToShow.second) {
        if (AnalyticsSettings.hasUserBeenPromptedForOptin(majorVersion, minorVersion)) {
          // If the user has already declined during this major release,
          // do not prompt
          return
        }
      }

      AnalyticsSettings.lastOptinPromptVersion = "${majorVersion}.${minorVersion}"
      AnalyticsSettings.saveSettings()

      application.invokeLater {
        val consent = list[0]

        val dialog = ConsentDialog(consent)
        dialog.isModal = true
        dialog.show()

        val result = listOf<Consent>(consent.derive(dialog.exitCode == OK_EXIT_CODE))
        AppUIUtil.saveConsents(result)
      }
    }
  }
}
