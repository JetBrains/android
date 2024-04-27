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
package com.android.tools.idea.res

import com.android.tools.idea.namespacing
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import org.jetbrains.android.facet.AndroidFacet

class ShowFileResourcesAction : AnAction("Show resources defined in file") {
  override fun actionPerformed(e: AnActionEvent) {
    val psiFile = e.dataContext.getData(PSI_FILE) ?: return
    val project = psiFile.project
    val virtualFile = psiFile.virtualFile
    val resFolder = virtualFile?.parent?.parent ?: return
    val facet = AndroidFacet.getInstance(psiFile) ?: return
    val repository =
      ResourceFolderRegistry.getInstance(project).getCached(resFolder, facet.namespacing) ?: return
    val definedInFile =
      repository.allResources.filter { it.getSourceAsVirtualFile() == virtualFile }
    val textOutput =
      definedInFile.joinToString(
        prefix = "Resources defined in ${virtualFile.path}\n\n",
        separator = "\n",
        transform = { "${it.referenceToSelf.resourceUrl} $it" }
      )

    val scratchFile =
      ScratchRootType.getInstance()
        .createScratchFile(
          project,
          "resources from ${psiFile.name}",
          PlainTextLanguage.INSTANCE,
          textOutput,
          ScratchFileService.Option.create_new_always
        ) ?: return
    FileEditorManager.getInstance(project).openFile(scratchFile, true)
  }
}
