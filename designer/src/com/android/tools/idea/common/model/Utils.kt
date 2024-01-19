/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.model

import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.concurrency.runWriteActionAndWait
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile

suspend fun NlModel.updateFileContentBlocking(content: String): NlModel {
  val psiFileManager = PsiManager.getInstance(project)
  val file = virtualFile as LightVirtualFile
  runWriteActionAndWait {
    // Update the contents of the VirtualFile associated to the NlModel. fireEvent value is
    // currently ignored, just set to true in case
    // that changes in the future.
    file.setContent(null, content, true)
    psiFileManager.reloadFromDisk(this@updateFileContentBlocking.file)
  }
  return this
}

/**
 * Helper function to wrapped [NlModel.addComponents] to select the added [NlComponent]s when
 * [insertType] is [InsertType.CREATE]. This happens when adding a new created [NlComponent]s into
 * [NlModel] but not moving the existing [NlComponent]s.
 *
 * We use [NlModel.addComponents] to create and moving [NlComponent]s, so we need to check the
 * [insertType].
 *
 * Note: Do not inline this function into [NlModel]. [NlModel] shouldn't depend on [SelectionModel].
 */
@JvmOverloads
fun NlModel.addComponentsAndSelectedIfCreated(
  toAdd: List<NlComponent>,
  receiver: NlComponent,
  before: NlComponent?,
  insertType: InsertType,
  selectionModel: SelectionModel,
  attributeUpdatingTask: Runnable? = null,
) {
  addComponents(
    toAdd,
    receiver,
    before,
    insertType,
    {
      if (insertType == InsertType.CREATE) {
        selectionModel.setSelection(toAdd)
      }
    },
    attributeUpdatingTask,
  )
}

/**
 * Helper function to wrapped [NlModel.addComponents] to add and selected the added [NlComponent]s.
 * This is used to add a new created [NlComponent]s into [NlModel] but not moving the existing
 * [NlComponent]s.
 *
 * Note: Do not inline this function into [NlModel]. [NlModel] shouldn't depend on [SelectionModel].
 */
@JvmOverloads
fun NlModel.createAndSelectComponents(
  toAdd: List<NlComponent>,
  receiver: NlComponent,
  before: NlComponent?,
  selectionModel: SelectionModel,
  attributeUpdatingTask: Runnable? = null,
) {
  addComponents(
    toAdd,
    receiver,
    before,
    InsertType.CREATE,
    { selectionModel.setSelection(toAdd) },
    attributeUpdatingTask,
  )
}
