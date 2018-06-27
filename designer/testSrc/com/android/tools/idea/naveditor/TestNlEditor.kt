/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor

import com.android.tools.idea.common.editor.NlEditor
import com.google.common.collect.ImmutableList
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.DocumentReferenceProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class TestNlEditor(private val myFile: VirtualFile, project: Project) : NlEditor(myFile, project), DocumentReferenceProvider {

  override fun getDocumentReferences(): Collection<DocumentReference> {
    return ImmutableList.of(DocumentReferenceManager.getInstance().create(myFile))
  }
}