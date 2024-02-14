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
import com.android.tools.idea.ml.xmltocompose.ComposeConverterDataType
import com.android.tools.idea.ml.xmltocompose.NShotXmlToComposeConverter
import com.android.tools.idea.studiobot.StudioBot
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ACTION_TITLE = "I am feeling Compose"

/**
 * List of possible types to use for storing data in a view model.
 *
 * TODO(b/322759144): Use ComposeConverterDataType
 */
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

    private val textArea = JBTextArea("Sending query to Gemini...")

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
      val dataType =
        if (useViewModel.isSelected) {
          // TODO(b/322759144): Use ComposeConverterDataType directly in the radio button
          ComposeConverterDataType.values().firstOrNull {
            it.classFqn == dataTypeGroup.selection.actionCommand
          } ?: ComposeConverterDataType.UNKNOWN
        } else {
          ComposeConverterDataType.UNKNOWN
        }
      convertXmlToCompose(dataType)
    }

    private fun convertXmlToCompose(dataType: ComposeConverterDataType) {
      NShotXmlToComposeConverter.Builder(project)
        .useViewModel(useViewModel.isSelected)
        .displayDependencies(displayDependencies.isSelected)
        .withDataType(dataType)
        .build()
        .let { nShotXmlToComposeConverter ->
          ComposeCodeDialog(project).run {
            Disposer.register(disposable, nShotXmlToComposeConverter)
            show()
            AndroidCoroutineScope(disposable).launch(workerThread) {
              val response = nShotXmlToComposeConverter.convertToCompose(xmlFileContent)
              withContext(uiThread) { updateContent(response) }
            }
          }
        }
    }
  }
}
