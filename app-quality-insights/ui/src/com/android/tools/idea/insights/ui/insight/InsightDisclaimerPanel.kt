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
package com.android.tools.idea.insights.ui.insight

import com.android.tools.adtui.stdui.CommonHyperLinkLabel
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.mapReadyOrDefault
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import java.awt.CardLayout
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val WITHOUT_CODE = "without_code"
private const val WITH_CODE = "with_code"

class InsightDisclaimerPanel(
  scope: CoroutineScope,
  currentInsightFlow: Flow<LoadingState<AiInsight?>>,
  private val onEnhanceInsight: (Boolean) -> Unit,
) : JPanel() {
  private val cardLayout =
    object : CardLayout() {
      override fun preferredLayoutSize(parent: Container): Dimension? {
        val current = findCurrentComponent(parent) ?: return super.preferredLayoutSize(parent)
        val insets = parent.insets
        val pref = current.preferredSize
        pref.width += insets.left + insets.right
        pref.height += insets.top + insets.bottom
        return pref
      }

      private fun findCurrentComponent(parent: Container) = parent.components.first { it.isVisible }
    }

  private val withoutCodeDisclaimer =
    DisclaimerLabel(
      "This insight was generated without code context. For better results, review and share limited code context with Gemini."
    )

  private val linkLabel =
    CommonHyperLinkLabel().apply {
      text = "Regenerate with context"
      font = JBFont.label()
      hyperLinkListeners.add {
        val dialogBuilder =
          MessageDialogBuilder.okCancel(
            "Confirm Context Sharing",
            "<html>Android Studio needs to send code and context from " +
              "your project to enhance the insight for this issue.<br>" +
              "Would you like to continue?</html>",
          )
        if (dialogBuilder.ask(this)) {
          onEnhanceInsight(true)
        }
      }
    }
  private val withoutCodePanel =
    JPanel(VerticalLayout(JBUI.scale(3))).apply {
      name = "without_code_disclaimer_panel"
      add(withoutCodeDisclaimer)
      add(linkLabel)
    }

  private val withCodeDisclaimer = DisclaimerLabel("This insight was generated with code context.")

  init {
    layout = cardLayout

    add(withoutCodePanel, WITHOUT_CODE)
    add(withCodeDisclaimer, WITH_CODE)
    cardLayout.show(this, WITHOUT_CODE)

    scope.launch {
      currentInsightFlow
        .mapReadyOrDefault(false) { it?.isEnhancedWithCodeContext() == true }
        .distinctUntilChanged()
        .collect { isEnhancedWithCodeContext ->
          if (isEnhancedWithCodeContext) {
            cardLayout.show(this@InsightDisclaimerPanel, WITH_CODE)
          } else {
            cardLayout.show(this@InsightDisclaimerPanel, WITHOUT_CODE)
          }
        }
    }
  }

  private inner class DisclaimerLabel(text: String) : JLabel("<html>$text</html>") {
    init {
      font = JBFont.label().asItalic()
      foreground = NamedColorUtil.getInactiveTextColor()
    }
  }
}
