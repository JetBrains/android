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
package com.android.tools.idea.journeys

import androidx.compose.runtime.mutableStateOf
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.journeys.view.JourneysEditorViewImpl.Action
import com.android.tools.idea.journeys.view.JourneysEditorViewImpl.ActionData
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.withContext

class JourneysEditorViewModel(
  private val project: Project,
  private val file: VirtualFile,
) {
  var name = mutableStateOf("")
  var description = mutableStateOf("")
  val actionValueList = mutableStateOf<List<ActionData>>(emptyList())

  suspend fun refreshData() {
    var descriptionText = ""
    var nameText = ""
    val list = mutableListOf<ActionData>()

    withContext(workerThread) {
      readAction {
        val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
        xmlFile?.let { currentXmlFile ->
          val rootTag = currentXmlFile.rootTag
          rootTag?.let {
            if (it.name == "journey") {
              nameText = rootTag.getAttribute("name")?.value?.trim() ?: ""
              descriptionText = it.subTags.firstOrNull {tag -> tag.name == "description" }?.value?.text?.trim() ?: ""
              val actionsTag = it.subTags.firstOrNull { tag -> tag.name == "actions" }
              // TODO: We could do better here
              actionsTag?.subTags?.filter { prompt -> Action.enumValueOfOrNull(prompt.name) != null }?.forEach { tag ->
                list.add(ActionData(Action.enumValueOfOrNull(tag.name)!!, tag.value.text.trim()))
              }
            }
          }
        }
      }
    }

      name.value = nameText
      description.value = descriptionText
      actionValueList.value = list
  }

  fun moveActionData(prevIndex: Int, newIndex: Int) {
    val newList = actionValueList.value.toMutableList()
    val value = newList.removeAt(prevIndex)
    newList.add(newIndex, value)

    actionValueList.value = newList
  }

  fun removeActionData(index: Int) {
    actionValueList.value = actionValueList.value.toMutableList().apply { removeAt(index) }
  }
}