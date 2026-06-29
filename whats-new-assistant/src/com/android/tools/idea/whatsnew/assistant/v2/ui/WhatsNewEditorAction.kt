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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.whatsnew.assistant.WhatsNewMetricsTracker
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.customization.ExternalProductResourceUrls

class WhatsNewEditorAction : AnAction("What's New in Android Studio") {
  private var whatsNewVirtualFile: WhatsNewVirtualFileImpl? = null

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = StudioFlags.WHATS_NEW_V2.get()
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (e.project == null) {
      thisLogger().info("project is null, browsing to URL instead")
      browseToWhatsNewUrl()
      return
    }

    openWhatsNewEditor(e.project!!, false)
  }

  fun openWhatsNewEditor(project: Project, isAutoOpened: Boolean) {
    if (whatsNewVirtualFile == null) {
      whatsNewVirtualFile = WhatsNewVirtualFileImpl()
    }
    FileEditorManager.getInstance(project)?.let { fileEditorManager ->
      WhatsNewMetricsTracker.getInstance().open(project, isAutoOpened)
      fileEditorManager.openFile(whatsNewVirtualFile!!)
    }
  }

  private fun browseToWhatsNewUrl() {
    ExternalProductResourceUrls.getInstance().whatIsNewPageUrl?.let { BrowserUtil.browse(it.toString()) }
  }
}
