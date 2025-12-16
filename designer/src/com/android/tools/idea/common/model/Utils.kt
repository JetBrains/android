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

import com.android.tools.idea.concurrency.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.FileContentUtil

suspend fun NlModel.updateFileContentBlocking(content: String): NlModel {
  runWriteActionAndWait {
    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
    if (document != null) {
      document.setText(content)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    } else {
      // Fallback for files without a document. This is not ideal but better than the previous
      // version.
      val file = virtualFile as LightVirtualFile
      file.setContent(null, content, true)
      FileContentUtil.reparseFiles(project, listOf(file), true)
    }
  }
  return this
}
