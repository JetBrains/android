/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("PsiFileUtil")
package com.android.tools.idea.uibuilder.io

import com.android.resources.ResourceFolderType
import com.android.tools.idea.res.getFolderType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile

fun PsiFile.saveFileIfNecessary() {
  if (!needSave(getFolderType(this.virtualFile))) { // Avoid need for read lock in get parent
    return
  }
  val file = this.virtualFile ?: return
  val fileManager = FileDocumentManager.getInstance()
  if (!fileManager.isFileModified(file)) {
    return
  }
  val document = fileManager.getCachedDocument(file)
  if (document == null || !fileManager.isDocumentUnsaved(document)) {
    return
  }
  val application = ApplicationManager.getApplication()
  application.invokeAndWait { application.runWriteAction { fileManager.saveDocument(document) } }
}

private fun needSave(type: ResourceFolderType?): Boolean {
  // Only layouts are delegates to the LayoutlibCallback#getParser where we can supply a
  // parser directly from the live document; others read contents from disk via layoutlib.
  // TODO: Work on adding layoutlib support for this.
  return type != ResourceFolderType.LAYOUT
}