/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors

import com.android.sdklib.AndroidVersion
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeEditor.JavaEditorFileSwapper
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.sdk.getInstance
import java.util.function.Function
import javax.swing.JComponent

/**
 * Notifies users that the Android SDK file they opened doesn't have a source file associated with it, and provides a link to download the
 * source via the SDK Manager.
 */
open class AttachAndroidSdkSourcesNotificationProvider() : EditorNotificationProvider {

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.get()) {
      // AndroidPositionManager is responsible for detecting that a specific SDK is needed during a debugging session, and will set the
      // REQUIRED_SOURCES_KEY when necessary.
      val missingApiLevel = file.getUserData(REQUIRED_SOURCES_KEY) ?: return null
      return Function { fileEditor ->
        createPanel(project, fileEditor, AndroidVersion(missingApiLevel), null)
      }
    }
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) return null

    // If the java source can be found, no need to download sources.
    if (JavaEditorFileSwapper.findSourceFile(project, file) != null) return null

    // Since the java source was not found, it might come from an Android SDK.
    val jdkOrderEntry = findAndroidSdkEntryForFile(project, file) ?: return null
    val sdk = jdkOrderEntry.jdk ?: return null

    // If we have sources, no need to display the panel.
    if (sdk.rootProvider.getFiles(OrderRootType.SOURCES).isNotEmpty()) return null

    val apiVersion = getInstance(sdk)?.apiVersion ?: return null
    val refresh = Runnable { AndroidSdkUtils.updateSdkSourceRoot(sdk) }

    return Function { fileEditor ->
      if (StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.get()) {
        createPanel(project, fileEditor, apiVersion, refresh)
      }
      else {
        val title = "Sources for '${jdkOrderEntry.jdkName}' not found."
        createLegacyPanel(project, fileEditor, title, apiVersion, refresh)
      }
    }
  }


  private fun createPanel(
    project: Project,
    fileEditor: FileEditor,
    requestedSourceVersion: AndroidVersion,
    refreshAfterDownload: Runnable?
  ): MyEditorNotificationPanel {
    val panel = MyEditorNotificationPanel(fileEditor)
    panel.text = "Android SDK sources for API ${requestedSourceVersion.apiString} not found."
    panel.createAndAddLink("Download") {
      val sourcesPath = DetailsTypes.getSourcesPath(requestedSourceVersion)
      if (createSdkDownloadDialog(project, listOf(sourcesPath))?.showAndGet() == true) {
        refreshAfterDownload?.run()
      }
    }
    return panel
  }

  private fun createLegacyPanel(
    project: Project,
    fileEditor: FileEditor,
    title: String,
    requestedSourceVersion: AndroidVersion,
    refresh: Runnable
  ): MyEditorNotificationPanel {
    val panel = MyEditorNotificationPanel(fileEditor)
    panel.text = title
    panel.createAndAddLink("Download") {
      val sourcesPaths = listOf(DetailsTypes.getSourcesPath(requestedSourceVersion))
      if (createSdkDownloadDialog(project, sourcesPaths)?.showAndGet() == true) {
        refresh.run()
      }
    }
    panel.createAndAddLink("Refresh (if already downloaded)", refresh)
    return panel
  }

  @VisibleForTesting
  protected open fun createSdkDownloadDialog(project: Project, requestedPaths: List<String>?): ModelWizardDialog? {
    return SdkQuickfixUtils.createDialogForPaths(project, requestedPaths!!)
  }

  private fun findAndroidSdkEntryForFile(project: Project, file: VirtualFile): JdkOrderEntry? {
    return ProjectFileIndex.getInstance(project)
      .getOrderEntriesForFile(file)
      .filterIsInstance<JdkOrderEntry>()
      .firstOrNull { entry -> entry.jdk?.let { AndroidSdks.getInstance().isAndroidSdk(it) } == true }
  }

  @VisibleForTesting
  internal class MyEditorNotificationPanel(fileEditor: FileEditor?) : EditorNotificationPanel(fileEditor) {
    private val myLinks: MutableMap<String, Runnable> = HashMap()
    fun createAndAddLink(text: @NlsContexts.LinkLabel String, action: Runnable) {
      // Despite the name, `createActionLabel` both creates the label and adds it to the panel.
      val label = createActionLabel(text, action)

      // This collection is just for tracking for test purposes.
      myLinks[label.text] = action
    }

    @get:VisibleForTesting
    val links: Map<String, Runnable>
      get() = myLinks
  }

  companion object {

    @JvmField
    val REQUIRED_SOURCES_KEY = Key.create<Int>("sources to download")
  }
}