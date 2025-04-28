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

import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.codecontext.CodeContextResolverImpl
import com.android.tools.idea.insights.ai.transform.CodeTransformation
import com.android.tools.idea.insights.ai.transform.CodeTransformationDeterminerImpl
import com.android.tools.idea.insights.ai.transform.CodeTransformationImpl
import com.android.tools.idea.insights.ai.transform.TransformDiffViewerEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SUGGEST_A_FIX = "Suggest a fix"
private const val SUGGEST_A_FIX_DISABLED = "No fix available"

class InsightBottomPanel(
  controller: AppInsightsProjectLevelController,
  currentInsightFlow: StateFlow<LoadingState<AiInsight?>>,
  private val parentDisposable: Disposable,
  private val insightFixTransformationDeterminer: CodeTransformationDeterminerImpl =
    CodeTransformationDeterminerImpl(
      controller.project,
      CodeContextResolverImpl(controller.project),
    ),
) : JPanel(BorderLayout()) {

  private val scope = parentDisposable.createCoroutineScope()

  private val fixInsightButton =
    JButton(SUGGEST_A_FIX).apply {
      name = "suggest_a_fix_button"
      isEnabled = false
      isVisible = StudioFlags.SUGGEST_A_FIX.get()
    }

  private var currentTransformation: CodeTransformation? = null

  init {
    if (StudioFlags.SUGGEST_A_FIX.get()) {
      fixInsightButton.addActionListener {
        fixInsightButton.isEnabled = false
        scope.launch {
          currentTransformation?.apply()?.collect {
            if (it == TransformDiffViewerEvent.CLOSED) {
              withContext(Dispatchers.EDT) { fixInsightButton.isEnabled = true }
            }
          }
        }
      }
      currentInsightFlow
        .onEach { insightState ->
          when (insightState) {
            is LoadingState.Ready -> {
              val insightText = insightState.value?.rawInsight
              proposeFix(insightText)
            }
            else -> {
              proposeFix(null)
            }
          }
        }
        .flowOn(Dispatchers.EDT)
        .launchIn(scope)
    }

    val leftPanel = JPanel(HorizontalLayout(JBUI.scale(5)))
    leftPanel.add(fixInsightButton)
    add(leftPanel, BorderLayout.CENTER)

    add(
      InsightToolbarPanel(currentInsightFlow, parentDisposable, controller::submitInsightFeedback),
      BorderLayout.EAST,
    )

    border = SideBorder(JBColor.border(), SideBorder.TOP)
  }

  private suspend fun proposeFix(text: String?) {
    abandonCurrentTransformation()
    if (text != null) {
      currentTransformation =
        insightFixTransformationDeterminer.getApplicableTransformation(text).also {
          Disposer.register(parentDisposable, it)
        }
    }
    setFixButtonState(currentTransformation)
  }

  private fun setFixButtonState(transformation: CodeTransformation?) {
    if (transformation is CodeTransformationImpl) {
      fixInsightButton.isEnabled = true
      fixInsightButton.text = SUGGEST_A_FIX
    } else {
      fixInsightButton.isEnabled = false
      fixInsightButton.text = SUGGEST_A_FIX_DISABLED
    }
  }

  private fun abandonCurrentTransformation() {
    currentTransformation?.let { Disposer.dispose(it) }
    currentTransformation = null
  }
}
