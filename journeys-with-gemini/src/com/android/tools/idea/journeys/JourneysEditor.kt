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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.journeys.view.JourneysEditorViewImpl
import com.android.tools.idea.journeys.view.JourneysEditorViewListener
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import icons.StudioIcons.Common.ADD
import kotlinx.coroutines.launch
import java.beans.PropertyChangeListener
import javax.swing.JComponent

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
class JourneysEditor(
  project: Project,
  private val virtualFile: VirtualFile
) : UserDataHolderBase(), FileEditor, JourneysEditorViewListener {
  private val model = JourneysEditorViewModel(project, virtualFile)
  private val view = JourneysEditorViewImpl(this, model, this)
  private val updater = XmlTagUpdater(project, virtualFile)
  private val uiThreadScope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)

  init {
    VirtualFileManager.getInstance().addAsyncFileListener({ events ->
      object : ChangeApplier {
        override fun afterVfsChange() {
          for (event in events) {
            if (virtualFile.path == event.path) {
              uiThreadScope.launch {
                model.refreshData()
              }
            }
          }
        }
      }
    }, this)

    //view.trackModelChanges(uiThreadScope)
    uiThreadScope.launch {
      model.refreshData()
    }
  }

  override suspend fun nameTextUpdated(text: String) {
    updater.updateJourneyName(text)
  }
  override suspend fun descriptionTextUpdated(text: String) {
    updater.updateDescription(text)
  }

  override suspend fun addNewActionWithText(text: String) {
    updater.addNewActionToFile(text)
  }
  override fun actionTypeUpdated(rowIndex: Int, action: JourneysEditorViewImpl.Action) {
    updater.updateActionTag(rowIndex, action)
  }
  override suspend fun actionValueUpdated(rowIndex: Int, text: String) {
    updater.updateActionTagValue(rowIndex, text)
  }

  override fun removeAction(rowIndex: Int) {
    model.removeActionData(rowIndex)

    uiThreadScope.launch {
      updater.removeAction(rowIndex)
    }
  }

  override fun moveAction(currentIndex: Int, newIndex: Int) {
    model.moveActionData(currentIndex, newIndex)

    uiThreadScope.launch {
      updater.moveAction(currentIndex, newIndex)
    }
  }

  override fun dispose() {}

  override fun getComponent(): JComponent = view.getComponent()

  override fun getPreferredFocusedComponent(): JComponent = view.getComponent()

  override fun getName(): String = "Journeys File Editor"

  override fun setState(state: FileEditorState) {}

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = virtualFile.isValid

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getFile() = virtualFile

  override fun selectNotify() {
    uiThreadScope.launch {
      model.refreshData()
    }

    super.selectNotify()
  }

  //override fun getTabActions(): ActionGroup {
  //  return DefaultActionGroup().apply {
  //    add(object : AnAction(ADD) {
  //      override fun actionPerformed(e: AnActionEvent) {
  //        updater.addNewActionToFile()
  //        model.tableModel.appendNewRow()
  //      }
  //
  //    })
  //  }
  //}
}