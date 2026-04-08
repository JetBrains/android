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

import com.android.repository.Revision
import com.android.tools.idea.assistant.AssistantGetBundleTask
import com.android.tools.idea.assistant.datamodel.TutorialBundleData
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.whatsnew.assistant.WhatsNewBundle
import com.android.tools.idea.whatsnew.assistant.WhatsNewBundleCreator
import com.android.tools.idea.whatsnew.assistant.WhatsNewURLProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import java.io.InputStream
import org.apache.http.concurrent.FutureCallback

class WhatsNewEditorAction : AnAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = StudioFlags.WHATS_NEW_V2.get()
  }

  override fun actionPerformed(e: AnActionEvent) {
    // TODO: enable bundle creator instead of prototype
    // val bundleCreator = AssistantBundleCreator.EP_NAME.findExtension<WhatsNewBundleCreator?>(WhatsNewBundleCreator::class.java)
    val bundleCreator =
      WhatsNewBundleCreator(
        object : WhatsNewURLProvider() {
          override fun getResourceFileAsStream(bundleCreator: WhatsNewBundleCreator?, version: String): InputStream? {
            return bundleCreator?.javaClass?.getResourceAsStream("/whats-new-assistant-v2-prototype.xml")
          }
        },
        Revision.safeParseRevision(ApplicationInfo.getInstance().strictVersion),
        false,
      )

    if (e.project == null) {
      thisLogger().info("project is null, browsing to URL instead")
      browseToWhatsNewUrl()
      return
    } else if (bundleCreator == null) {
      thisLogger().info("bundleCreator is null, browsing to URL instead")
      browseToWhatsNewUrl()
      return
    } else if (bundleCreator.shouldNotShowWhatsNew()) {
      thisLogger().info("should not show panel, browsing to URL instead")
      browseToWhatsNewUrl()
      return
    }

    AssistantGetBundleTask(e.project!!, bundleCreator, WhatsNewLoadingCallback(e.project!!)).queue()
  }

  private inner class WhatsNewLoadingCallback(private val project: Project) : FutureCallback<TutorialBundleData> {
    override fun completed(bundleData: TutorialBundleData?) {
      FileEditorManager.getInstance(project)?.openFile(WhatsNewVirtualFile(bundleData as WhatsNewBundle))
    }

    override fun failed(p0: Exception?) {
      thisLogger().info("failed to load bundle, browsing to URL instead")
      browseToWhatsNewUrl()
    }

    override fun cancelled() {
      thisLogger().info("bundle loading cancelled, browsing to URL instead")
      browseToWhatsNewUrl()
    }
  }

  private fun browseToWhatsNewUrl() {
    ExternalProductResourceUrls.getInstance().whatIsNewPageUrl?.let { BrowserUtil.browse(it.toString()) }
  }
}
