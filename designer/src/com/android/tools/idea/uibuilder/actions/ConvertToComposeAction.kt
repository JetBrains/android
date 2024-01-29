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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.project.AndroidNotification
import com.android.tools.idea.studiobot.StudioBot
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Question to precede every layout when querying Studio Bot. The exact same question is used to
 * train the model.
 */
private const val PROMPT_PREFIX =
  "What's the Jetpack Compose equivalent of the following Android XML layout?\n\n"

class ConvertToComposeAction(private val root: NlComponent) : AnAction("Convert to Compose") {

  private val logger = Logger.getInstance(ConvertToComposeAction::class.java)

  override fun actionPerformed(e: AnActionEvent) {
    val xmlFile = root.backend.tag?.containingFile?.virtualFile?.contentsToByteArray() ?: return
    val project = e.project ?: return

    val query = "${PROMPT_PREFIX}${String(xmlFile)}"
    val studioBot = StudioBot.getInstance()
    try {
      val validatedQueryRequest =
        studioBot.aiExcludeService().validateQuery(project, query, listOf()).getOrThrow()
      // TODO(b/322759144): Don't use the project as the scope disposable
      // Note: you must complete the Studio Bot onboarding and enable context sharing, otherwise
      // the following call will fail.
      AndroidCoroutineScope(project).launch(workerThread) {
        val response =
          StudioBot.getInstance()
            .model()
            .sendQuery(validatedQueryRequest, StudioBot.RequestSource.DESIGN_TOOLS)
            .toList()

        copyResponseToClipboard(project, response.joinToString("\n"))
      }
    } catch (t: Throwable) {
      logger.error("Error while trying to send query", t)
    }
  }

  private suspend fun copyResponseToClipboard(project: Project, response: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(response), null)
    withContext(uiThread) {
      AndroidNotification.getInstance(project)
        .showBalloon(
          "Response copied to the clipboard",
          "The Compose code corresponding to the layout was copied to the clipboard.",
          NotificationType.INFORMATION,
        )
    }
  }
}
