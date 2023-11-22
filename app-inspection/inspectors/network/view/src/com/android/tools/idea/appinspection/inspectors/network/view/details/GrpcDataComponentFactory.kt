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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.idea.appinspection.inspectors.network.model.connections.GrpcData
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType.REQUEST
import com.android.tools.idea.appinspection.inspectors.network.view.details.DataComponentFactory.ConnectionType.RESPONSE
import com.android.tools.inspectors.common.ui.dataviewer.IntellijDataViewer
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.json.JsonFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import java.io.StringReader
import javax.swing.JComponent
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

private val GSON = Gson()
private val XML = DocumentBuilderFactory.newInstance().newDocumentBuilder()

internal class GrpcDataComponentFactory(
  private val project: Project,
  private val parentDisposable: Disposable,
  data: GrpcData,
) : DataComponentFactory(data) {
  private val grpcData: GrpcData
    get() = data as GrpcData

  override fun createDataViewer(type: ConnectionType, formatted: Boolean) = null

  override fun createBodyComponent(type: ConnectionType): JComponent {
    val bytes =
      when (type) {
        REQUEST -> data.requestPayload.toByteArray()
        RESPONSE -> data.responsePayload.toByteArray()
      }

    return when (val fileType = bytes.getFileType()) {
      null -> createHideablePanel("Payload (Raw)", BinaryDataViewer(bytes), null)
      else -> createPrettyComponent("Payload (${fileType.displayName})", bytes, fileType)
    }
  }

  override fun createTrailersComponent(): JComponent? {
    return when {
      grpcData.responseTrailers.isEmpty() -> null
      else -> createStyledMapComponent(grpcData.responseTrailers)
    }
  }

  private fun createPrettyComponent(
    title: String,
    bytes: ByteArray,
    fileType: FileType
  ): JComponent {
    val viewer =
      IntellijDataViewer.createPrettyViewerIfPossible(
        project,
        bytes,
        fileType,
        true,
        parentDisposable
      )
    return createHideablePanel(title, viewer.component, null)
  }
}

private fun ByteArray.getFileType(): FileType? {
  val string = decodeToString()
  return when {
    string.isJson() -> JsonFileType.INSTANCE
    string.isXml() -> XmlFileType.INSTANCE
    else -> null
  }
}

private fun String.isJson(): Boolean {
  return try {
    GSON.fromJson(this, JsonObject::class.java)
    true
  } catch (e: Exception) {
    false
  }
}

private fun String.isXml(): Boolean {
  return try {
    XML.parse(InputSource(StringReader(this)))
    true
  } catch (e: Exception) {
    false
  }
}
