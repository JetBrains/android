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
import com.android.tools.idea.gradle.structure.AndroidProjectSettingsServiceImpl
import com.android.tools.idea.gradle.util.LocalProperties
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendants
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.plugins.gradle.properties.GRADLE_PROPERTIES_FILE_NAME
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import java.awt.event.MouseEvent
import javax.swing.JLabel

class AndroidPropertiesLineMarkerProviderTest : AndroidTestCase() {

  fun testEmptyLocalProperties() {
    createPropertiesFileMarkersInfo(
      fileName = FN_LOCAL_PROPERTIES,
      properties = ""
    ) {
      assertEmpty(this)
    }
  }

  fun testUnexpectedLocalProperties() {
    createPropertiesFileMarkersInfo(
      fileName = FN_LOCAL_PROPERTIES,
      properties = """
        unknown.dir=/test/unknown/path
        unknown.dir2=/test/unknown2/path
        unknown.dir3=/test/unknown3/path
       """
    ) {
      assertEmpty(this)
    }
  }

  fun testDifferentDeclarationWaysLocalProperties() {
    createPropertiesFileMarkersInfo(
      fileName = FN_LOCAL_PROPERTIES,
      headerComment = LocalProperties.getHeaderComment(),
      properties = """
        sdk.dir=/test/sdk/path
        sdk.dir=
        sdk.dir
      """
    ) {
      assertEquals(3, size)
      assertMarkersInfo(this)
    }
  }

  fun testNavigationHandlerSdkDirLocalProperties() {
    val mockService = mock<AndroidProjectSettingsServiceImpl>()
    replaceProjectService(ProjectSettingsService::class.java, mockService)

    createPropertiesFileMarkersInfo(
      fileName = FN_LOCAL_PROPERTIES,
      properties = "sdk.dir=/test/sdk/path"
    ) {
      assertEquals(1, size)
      first().navigationHandler.navigate(simpleClickEvent, null)
      verify(mockService).openSdkSettings()
    }
  }

  fun testNavigationHandlerNdkDirLocalProperties() {
    val mockService = mock<AndroidProjectSettingsServiceImpl>()
    replaceProjectService(ProjectSettingsService::class.java, mockService)

    createPropertiesFileMarkersInfo(
      fileName = FN_LOCAL_PROPERTIES,
      properties = "ndk.dir=/test/ndk/path"
    ) {
      assertEquals(1, size)
      first().navigationHandler.navigate(simpleClickEvent, null)
      verify(mockService).openSdkSettings()
    }
  }

  fun testExpectedLocalProperties() {
    createPropertiesFileMarkersInfo(
      fileName = FN_LOCAL_PROPERTIES,
      headerComment = LocalProperties.getHeaderComment(),
      properties = """
        sdk.dir=/test/sdk/path
        ndk.dir=/test/ndk/path
      """
    ) {
      assertEquals(2, size)
      assertMarkersInfo(this)
    }
  }

  fun testExpectedPropertiesInNonLocalProperties() {
    createPropertiesFileMarkersInfo(
      fileName = GRADLE_PROPERTIES_FILE_NAME,
      properties = """
        sdk.dir=/test/sdk/path
        ndk.dir=/test/ndk/path
        test.dir=/test/ndk/path
      """
    ) {
      assertEmpty(this)
    }
  }

  private fun createPropertiesFileMarkersInfo(
    fileName: String,
    properties: String,
    headerComment: String? = null,
    onFileCreated: (PsiFile.() -> Unit)? = null,
    onCollectMarkersInfo: List<RelatedItemLineMarkerInfo<*>>.() -> Unit
  ) {
    val propertiesFile = createPropertiesFile(fileName, properties, headerComment)
    onFileCreated?.invoke(propertiesFile)

    val lineMarkerProvider = AndroidPropertiesLineMarkerProvider()
    val lineMarkersInfo = mutableListOf<RelatedItemLineMarkerInfo<*>>()
    val descendantsElements = propertiesFile.descendants().toList()
    lineMarkerProvider.collectNavigationMarkers(descendantsElements, lineMarkersInfo, false)
    onCollectMarkersInfo(lineMarkersInfo)
  }

  private fun createPropertiesFile(name: String, properties: String, headerComment: String? = null): PsiFile {
    val fileContent = StringBuilder()
    headerComment?.let { fileContent.appendLine(it.trimIndent()) }
    fileContent.appendLine(properties)
    val propertiesFile = myFixture.addFileToProject(name, fileContent.toString()).virtualFile
    myFixture.configureFromExistingVirtualFile(propertiesFile)
    return myFixture.file
  }

  private fun assertMarkersInfo(markersInfo: List<RelatedItemLineMarkerInfo<*>>) {
    markersInfo
      .map { it.createGutterRenderer() }
      .forEach {
        assertEquals(GutterIconRenderer.Alignment.RIGHT, it.alignment)
        assertEquals(AndroidBundle.message("android.local.properties.file.settings.tooltip"), it.tooltipText)
        assertEquals(AllIcons.General.Settings, it.icon)
      }
  }

  private val simpleClickEvent =
    MouseEvent(JLabel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false, 0)
}
