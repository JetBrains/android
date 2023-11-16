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
package org.jetbrains.android

import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.SdkConstants.NDK_DIR_PROPERTY
import com.android.SdkConstants.SDK_DIR_PROPERTY
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.android.util.AndroidBundle

/**
 * Implementation of [RelatedItemLineMarkerProvider] for Android local.properties.
 *
 * This class provides related items for supported properties allowing users to easily open the settings UI to modify them.
 * These items are displayed as line markers in the gutter of the local.properties file for sdk.dir and ndk.dir.
 */
class AndroidPropertiesLineMarkerProvider : RelatedItemLineMarkerProvider() {

  override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
    if (element.containingFile.name == FN_LOCAL_PROPERTIES) {
      createMarkerInfoForLocalProperties(element)?.let {
        result.add(it)
      }
    }
  }

  private fun createMarkerInfoForLocalProperties(element: PsiElement) =
    (element as? PropertyKeyImpl)?.let { property ->
      when (property.text) {
        SDK_DIR_PROPERTY, NDK_DIR_PROPERTY -> createRelatedItemLineMarkerInfo(property) { openSdkSettings() }
        else -> null
      }
    }

  private fun createRelatedItemLineMarkerInfo(
    property: PropertyKeyImpl,
    onMarkerClicked: AndroidProjectSettingsService.() -> Unit
  ) = RelatedItemLineMarkerInfo(
    property,
    property.textRange,
    AllIcons.General.Settings,
    { AndroidBundle.message("android.local.properties.file.settings.tooltip") },
    { _, _ ->
      savePropertiesFile(property.containingFile.virtualFile)
      val service = ProjectSettingsService.getInstance(property.project)
      (service as? AndroidProjectSettingsService)?.let { androidService ->
        onMarkerClicked(androidService)
      }
    },
    GutterIconRenderer.Alignment.RIGHT,
    { emptyList<GotoRelatedItem>() }
  )

  private fun savePropertiesFile(virtualFile: VirtualFile) {
    WriteAction.computeAndWait<Unit, Throwable> {
      FileDocumentManager.getInstance().getDocument(virtualFile)?.let {
        FileDocumentManager.getInstance().saveDocument(it)
      }
    }
  }
}