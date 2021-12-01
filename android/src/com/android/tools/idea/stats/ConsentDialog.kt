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
import com.android.tools.adtui.ImageUtils.iconToImage
import com.android.tools.analytics.AnalyticsSettings
import com.google.common.base.Predicates
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.AppUIUtil
import icons.StudioIllustrations
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.NORTHWEST
import java.awt.GridBagLayout
import java.awt.Insets
import java.lang.reflect.InvocationTargetException
import javax.swing.Action
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants.TOP
import javax.swing.event.HyperlinkEvent

private const val LABEL_TEXT =
  "<html>Help make Android Studio better by automatically sending usage statistics and crash reports to Google</html>"

class ConsentDialog(private val consent: Consent) : DialogWrapper(null) {
  private val checkBox = JCheckBox(null, null, true)
  override fun createActions(): Array<Action> {
    return arrayOf(okAction)
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
      gridwidth = 2
      gridheight = 1
      insets = Insets(10, 0, 15, 0)
    }

    add(title, constraints)

    constraints.apply {
      gridy = 1
      gridwidth = 1
      insets = Insets(0, 0, 0, 5)
    }

    add(checkBox, constraints)

    val label = JLabel(LABEL_TEXT).apply {
      verticalAlignment = TOP
      preferredSize = Dimension(420, 40)
    }

    constraints.apply {
      constraints.gridx = 2
      constraints.insets = Insets(0, 0, 0, 0)
    }

    add(label, constraints)

    val message = JEditorPane("text/html", consent.text).apply {
      isEditable = false
      background = Color(0, 0, 0, 0)
      foreground = Color.GRAY
      font = Font(font.name, Font.PLAIN, checkBox.font.size - 2)
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
      preferredSize = Dimension(420, 100)

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
  }

  init {
    isResizable = false
    isModal = true
    setUndecorated(true)
    init()
  }

  override fun createCenterPanel(): JComponent = content

  override fun doOKAction() {
    val result = listOf<Consent>(consent.derive(checkBox.isSelected))
    AppUIUtil.saveConsents(result)
    super.doOKAction()
  }

  companion object {
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
      if (application.isUnitTestMode || application.isHeadlessEnvironment) {
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
        val dialog = ConsentDialog(list[0])
        dialog.isModal = true
        dialog.show()
      }
    }
  }
}
