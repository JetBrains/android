/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.ide

import com.android.tools.adtui.stdui.ContentType
import com.android.tools.idea.appinspection.inspectors.network.view.UiComponentsProvider
import com.android.tools.inspectors.common.api.ide.stacktrace.IntelliJStackTraceGroup
import com.android.tools.inspectors.common.ui.dataviewer.DataViewer
import com.android.tools.inspectors.common.ui.dataviewer.IntellijDataViewer
import com.android.tools.inspectors.common.ui.dataviewer.IntellijImageDataViewer
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceGroup
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DefaultUiComponentsProvider(
  private val project: Project,
  private val parentDisposable: Disposable,
) : UiComponentsProvider {
  private val gson = Gson()
  private val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()

  override fun createDataViewer(
    bytes: ByteArray,
    contentType: ContentType,
    styleHint: DataViewer.Style,
    formatted: Boolean,
  ): DataViewer {
    return when {
      contentType.isSupportedImageType -> IntellijImageDataViewer(bytes, parentDisposable)
      contentType.isMultipart -> IntellijDataViewer.createRawTextViewer(bytes)
      contentType.isSupportedTextType -> createViewer(bytes, contentType, styleHint, formatted)
      else -> handleUnsupportedContentType(bytes, styleHint, formatted)
    }
  }

  private fun createViewer(
    bytes: ByteArray,
    contentType: ContentType,
    styleHint: DataViewer.Style,
    formatted: Boolean,
  ): IntellijDataViewer {
    return when (styleHint) {
      DataViewer.Style.RAW -> IntellijDataViewer.createRawTextViewer(bytes)
      DataViewer.Style.PRETTY ->
        IntellijDataViewer.createPrettyViewerIfPossible(
          project,
          bytes,
          contentType.fileType,
          formatted,
          parentDisposable,
        )
      DataViewer.Style.INVALID -> throw RuntimeException("DataViewer style is invalid.")
    }
  }

  /*
   * Some web services may not specify content correctly so we try detecting it.
   */
  private fun handleUnsupportedContentType(
    bytes: ByteArray,
    styleHint: DataViewer.Style,
    formatted: Boolean,
  ): DataViewer {
    return when {
      bytes.isJson() -> createViewer(bytes, ContentType.JSON, styleHint, formatted)
      bytes.isXml() -> createViewer(bytes, ContentType.XML, styleHint, formatted)
      else -> IntellijDataViewer.createInvalidViewer()
    }
  }

  override fun createStackGroup(): StackTraceGroup {
    return IntelliJStackTraceGroup(project, parentDisposable)
  }

  // TODO(b/235501148): Detect partial JSON. See LintSyntaxHighlighter#tokenizeXml() for a starting
  // point.
  private fun ByteArray.isJson(): Boolean {
    return try {
      ByteArrayInputStream(this).reader().use { gson.fromJson(it, JsonObject::class.java) != null }
    } catch (_: Exception) {
      false
    }
  }

  // TODO(b/235501148): Detect partial XML. See LintSyntaxHighlighter#tokenizeXml() for a starting
  // point.
  private fun ByteArray.isXml(): Boolean {
    return try {
      ByteArrayInputStream(this).use { xml.parse(it) }
      true
    } catch (_: Exception) {
      false
    }
  }
}
