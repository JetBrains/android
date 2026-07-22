/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.ui

import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewAssets
import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewDocumentLoaderImpl
import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewMarkdownDocument
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

class WhatsNewEditorProvider : AsyncFileEditorProvider {
  private var cachedMarkdownDocuments: List<WhatsNewMarkdownDocument>? = null
  private var cachedAssets: WhatsNewAssets? = null

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is WhatsNewVirtualFileImpl
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    // This is a fallback for when the async provider is not used.
    // It returns an editor with empty documents, which is not ideal, but `createEditor`
    // shouldn't be called if the platform prefers async providers.
    return WhatsNewEditor(file as WhatsNewVirtualFile, emptyList(), WhatsNewAssets(), project)
  }

  @Suppress("UnstableApiUsage")
  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    val loader = WhatsNewDocumentLoaderImpl()
    val markdownDocs = cachedMarkdownDocuments ?: loader.loadDocuments().also { cachedMarkdownDocuments = it }
    val assets = cachedAssets ?: loader.loadAssets().also { cachedAssets = it }
    return withContext(Dispatchers.EDT) { WhatsNewEditor(file as WhatsNewVirtualFile, markdownDocs, assets, project) }
  }

  override fun getEditorTypeId(): @NonNls String {
    return "whats-new-editor"
  }

  override fun getPolicy(): FileEditorPolicy {
    return FileEditorPolicy.HIDE_OTHER_EDITORS
  }
}

abstract class WhatsNewVirtualFile : LightVirtualFile()

class WhatsNewVirtualFileImpl : WhatsNewVirtualFile() {
  override fun getPresentableName(): @NlsSafe String {
    return "What's New"
  }
}
