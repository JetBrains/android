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
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Question to precede every layout when querying Studio Bot. The exact same question is used to
 * train the model.
 */
private const val PROMPT_PREFIX =
  "What's the Jetpack Compose equivalent of the following Android XML layout?"

private const val ACTION_TITLE = "I am feeling Compose"

/**
 * Prompts to be appended after [PROMPT_PREFIX] to improve the quality of the Studio Bot responses.
 */
private val nShotPrompts =
  listOf(
    "Include imports in your answer.",
    "Add a @Preview function.",
    "Don't use ConstraintLayout.",
    "Use material3, not material.",
  )

/** List of possible types to use for storing data in a view model. */
private val optionalStateType =
  listOf(
    "androidx.compose.runtime.MutableState",
    "kotlinx.coroutines.flow.StateFlow",
    "androidx.lifecycle.LiveData",
  )

class ConvertToComposeAction : AnAction(ACTION_TITLE) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    // Only enable the action if user has opted-in to share context.
    e.presentation.isEnabled = StudioBot.getInstance().isContextAllowed()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val xmlFile = e.getData(VIRTUAL_FILE)?.contentsToByteArray() ?: return
    val project = e.project ?: return

    ConvertToComposeDialog(project, String(xmlFile)).showAndGet()
  }

  private class ComposeCodeDialog(project: Project) : DialogWrapper(project) {

    private val textArea = JBTextArea("Sending query to Studio Bot...")

    init {
      isModal = false
      super.init()
    }

    override fun createCenterPanel(): JComponent {
      val scrollPane = JBScrollPane(textArea).apply { preferredSize = JBUI.size(600, 1000) }
      return JPanel(BorderLayout()).apply { add(scrollPane, BorderLayout.CENTER) }
    }

    fun updateContent(content: String) {
      textArea.text = content
    }
  }

  private class ConvertToComposeDialog(
    private val project: Project,
    private val xmlFileContent: String,
  ) : DialogWrapper(project) {
    private val logger = Logger.getInstance(ConvertToComposeAction::class.java)
    private val displayDependencies = JBCheckBox("Display dependencies", false)
    private val useViewModel = JBCheckBox("Use ViewModel", false)
    private val dataTypeGroup = ButtonGroup()
    private val dataTypeButtons: List<JBRadioButton>

    init {
      title = ACTION_TITLE
      dataTypeButtons =
        optionalStateType.map {
          JBRadioButton("Use $it", false).apply {
            isEnabled = false
            actionCommand = it
            dataTypeGroup.add(this)
          }
        }
      dataTypeButtons[0].isSelected = true
      useViewModel.addItemListener {
        dataTypeButtons.forEach { it.isEnabled = useViewModel.isSelected }
      }
      init()
    }

    override fun createCenterPanel(): JComponent {
      val dataTypePanel = Box.createVerticalBox().apply { dataTypeButtons.forEach { add(it) } }
      return Box.createVerticalBox().apply {
        add(displayDependencies)
        add(useViewModel)
        add(dataTypePanel)
        preferredSize = JBUI.size(300, 300)
      }
    }

    override fun doOKAction() {
      super.doOKAction()
      val nShots = nShotPrompts.toMutableList()
      if (useViewModel.isSelected) {
        nShots.add("Create a subclass of androidx.lifecycle.ViewModel to store the states.")
        val dataTypes = dataTypeGroup.elements
        while (dataTypes.hasMoreElements()) {
          val button = dataTypes.nextElement()
          if (button.isSelected) {
            nShots.add(
              "The ViewModel must store data using objects of type ${button.actionCommand}. The Composable methods will use states derived from the data stored in the ViewModel."
            )
          } else {
            nShots.add("Do not use ${button.actionCommand} in the ViewModel.")
          }
        }
      }
      if (displayDependencies.isSelected) {
        nShots.add(
          "After the Kotlin code, display all the dependencies that are required to be added to build.gradle.kts for this code to compile."
        )
      }
      val nShot = nShots.joinToString(" ")
      val query = "$PROMPT_PREFIX ${nShot}\n\n$xmlFileContent"
      val studioBot = StudioBot.getInstance()
      try {
        val validatedQueryRequest =
          studioBot.aiExcludeService().validateQuery(project, query, listOf()).getOrThrow()
        ComposeCodeDialog(project).run {
          show()
          AndroidCoroutineScope(disposable).launch(workerThread) {
            // Note: you must complete the Studio Bot onboarding and enable context sharing,
            // otherwise
            // the following call will fail.
            val response =
              StudioBot.getInstance()
                .model()
                .sendQuery(validatedQueryRequest, StudioBot.RequestSource.DESIGN_TOOLS)
                .toList()

            withContext(uiThread) { updateContent(response.parseCode()) }
          }
        }
      } catch (t: Throwable) {
        logger.error("Error while trying to send query", t)
      }
    }
  }
}

/**
 * Takes a list of strings returned by Studio Bot and returns the content without metadata or
 * formatting.
 *
 * See `LlmService#sendQuery` documentation for details about StudioBot's response formatting.
 */
private fun List<String>.parseCode(): String {
  val kotlinPattern = "```kotlin\n"
  val textPattern = "```\n"
  return joinToString("\n") {
    if (it.startsWith(kotlinPattern) || it.startsWith(textPattern)) {
      it.substringAfter("\n").trim('`')
    } else {
      ""
    }
  }
}
