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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeEditor.JavaEditorFileSwapper
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.application.EDT
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
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.function.Function
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.sdk.getInstance

/**
 * Notifies users that the Android SDK file they opened doesn't have a source file associated with
 * it, and provides a link to download the source via the SDK Manager.
 */
open class AttachAndroidSdkSourcesNotificationProvider : EditorNotificationProvider {

  @RequiresReadLock
  override fun collectNotificationData(
    project: Project,
    file: VirtualFile
  ): Function<FileEditor, EditorNotificationPanel?>? {
    return createNotificationPanelForDebugSession(file, project)
      ?: createNotificationPanelForClassFiles(file, project)
  }

  private fun createNotificationPanelForDebugSession(
    file: VirtualFile,
    project: Project
  ): Function<FileEditor, EditorNotificationPanel?>? {
    // AndroidPositionManager is responsible for detecting that a specific SDK is needed during a
    // debugging session, and will set the REQUIRED_SOURCES_KEY when necessary.
    val missingApiLevel = file.getUserData(REQUIRED_SOURCES_KEY) ?: return null
    return createPanel(project, AndroidVersion(missingApiLevel))
  }

  private fun createNotificationPanelForClassFiles(
    file: VirtualFile,
    myProject: Project
  ): Function<FileEditor, EditorNotificationPanel?>? {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) return null

    // If the java source can be found, no need to download sources.
    if (JavaEditorFileSwapper.findSourceFile(myProject, file) != null) return null

    // Since the java source was not found, it might come from an Android SDK.
    val jdkOrderEntry = findAndroidSdkEntryForFile(myProject, file) ?: return null
    val sdk = jdkOrderEntry.jdk ?: return null

    // If we have sources, no need to display the panel.
    if (sdk.rootProvider.getFiles(OrderRootType.SOURCES).isNotEmpty()) return null

    val apiVersion = getInstance(sdk)?.apiVersion ?: return null

    return createPanel(myProject, apiVersion) { AndroidSdkUtils.updateSdkSourceRoot(sdk) }
  }

  private fun createPanel(
    project: Project,
    requestedSourceVersion: AndroidVersion,
    refreshAfterDownload: Runnable? = null
  ): Function<FileEditor, EditorNotificationPanel?> = Function { fileEditor ->
    doCreatePanel(project, requestedSourceVersion, refreshAfterDownload, fileEditor)
  }

  @RequiresEdt
  private fun doCreatePanel(
    project: Project,
    requestedSourceVersion: AndroidVersion,
    refreshAfterDownload: Runnable? = null,
    fileEditor: FileEditor
  ): EditorNotificationPanel {
    val panel =
      MyEditorNotificationPanel(fileEditor).apply {
        text = "Android SDK sources for API ${requestedSourceVersion.apiString} are not available."
      }

    createCoroutineScopeForEditor(fileEditor).launch {
      // SdkQuickfixUtils.checkPathIsAvailableForDownload must not be called on the EDT.
      val sourcesPath = DetailsTypes.getSourcesPath(requestedSourceVersion)
      if (SdkQuickfixUtils.checkPathIsAvailableForDownload(sourcesPath)) {
        withContext(Dispatchers.EDT) {
          panel.createAndAddLink("Download") {
            if (createSdkDownloadDialog(project, listOf(sourcesPath))?.showAndGet() == true) {
              refreshAfterDownload?.run()
            }
          }
        }
      }
    }

    return panel
  }

  @VisibleForTesting
  protected open fun createSdkDownloadDialog(
    project: Project,
    requestedPaths: List<String>?
  ): ModelWizardDialog? {
    // TODO(b/230852993) calls a heavy method AndroidSdkHandler.getSdkManager
    return SdkQuickfixUtils.createDialogForPaths(project, requestedPaths!!)
  }

  @VisibleForTesting
  protected open fun createCoroutineScopeForEditor(fileEditor: FileEditor): CoroutineScope =
    AndroidCoroutineScope(fileEditor)

  private fun findAndroidSdkEntryForFile(project: Project, file: VirtualFile): JdkOrderEntry? {
    return ProjectFileIndex.getInstance(project)
      .getOrderEntriesForFile(file)
      .filterIsInstance<JdkOrderEntry>()
      .firstOrNull { entry ->
        entry.jdk?.let { AndroidSdks.getInstance().isAndroidSdk(it) } == true
      }
  }

  @VisibleForTesting
  internal class MyEditorNotificationPanel(fileEditor: FileEditor?) :
    EditorNotificationPanel(fileEditor) {
    private val _links: MutableMap<String, Runnable> = mutableMapOf()

    fun createAndAddLink(text: @NlsContexts.LinkLabel String, action: Runnable) {
      // Despite the name, `createActionLabel` both creates the label and adds it to the panel.
      val label = createActionLabel(text, action)

      // This collection is just for tracking for test purposes.
      _links[label.text] = action
    }

    @get:VisibleForTesting
    val links: Map<String, Runnable>
      get() = _links
  }

  companion object {
    @JvmField val REQUIRED_SOURCES_KEY = Key.create<Int>("sources to download")
  }
}
