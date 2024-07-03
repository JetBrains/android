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
