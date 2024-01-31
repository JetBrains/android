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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.studiobot.StudioBot
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Question to precede every layout when querying Studio Bot. The exact same question is used to
 * train the model.
 */
private const val PROMPT_PREFIX =
  "What's the Jetpack Compose equivalent of the following Android XML layout?\n\n"

private const val ACTION_TITLE = "I am feeling Compose"

class ConvertToComposeAction : AnAction(ACTION_TITLE) {

  private val logger = Logger.getInstance(ConvertToComposeAction::class.java)

  override fun actionPerformed(e: AnActionEvent) {
    val xmlFile = e.getData(VIRTUAL_FILE)?.contentsToByteArray() ?: return
    val project = e.project ?: return

    val query = "${PROMPT_PREFIX}${String(xmlFile)}"
    val studioBot = StudioBot.getInstance()
    try {
      val validatedQueryRequest =
        studioBot.aiExcludeService().validateQuery(project, query, listOf()).getOrThrow()
      ComposeCodeDialog(project).run {
        show()
        AndroidCoroutineScope(disposable).launch(workerThread) {
          // TODO(b/322759144): play with n-shot prompting instead of sending only the prefix prompt
          // Note: you must complete the Studio Bot onboarding and enable context sharing, otherwise
          // the following call will fail.
          val response =
            StudioBot.getInstance()
              .model()
              .sendQuery(validatedQueryRequest, StudioBot.RequestSource.DESIGN_TOOLS)
              .toList()

          withContext(uiThread) { updateContent(response.joinToString("\n")) }
        }
      }
    } catch (t: Throwable) {
      logger.error("Error while trying to send query", t)
    }
  }

  private class ComposeCodeDialog(project: Project) : DialogWrapper(project) {

    private val textArea =
      JBTextArea("Sending query to Studio Bot...").apply { preferredSize = JBUI.size(600, 1000) }

    init {
      isModal = false
      super.init()
    }

    override fun createCenterPanel() =
      JPanel(BorderLayout()).apply { add(textArea, BorderLayout.CENTER) }

    fun updateContent(content: String) {
      textArea.text = content
    }
  }
}
