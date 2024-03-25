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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import io.ktor.utils.io.core.toByteArray
import java.io.StringReader
import javax.swing.JComponent
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.text.RegexOption.MULTILINE
import org.xml.sax.InputSource

private val GSON = Gson()
private val XML = DocumentBuilderFactory.newInstance().newDocumentBuilder()

internal class GrpcDataComponentFactory(
  private val project: Project,
  private val parentDisposable: Disposable,
  data: GrpcData,
  private val protoFileFinder: ProtoFileFinder = ProtoFileFinderImpl(project),
) : DataComponentFactory(data) {
  private val grpcData: GrpcData
    get() = data as GrpcData

  override fun createDataViewer(type: ConnectionType, formatted: Boolean) = null

  override fun createBodyComponent(type: ConnectionType): JComponent? {
    val bytes =
      when (type) {
        REQUEST -> data.requestPayload.toByteArray()
        RESPONSE -> data.responsePayload.toByteArray()
      }

    val text =
      when (type) {
        REQUEST -> data.requestPayloadText
        RESPONSE -> data.responsePayloadText
      }
    val fileType = bytes.getFileType()

    return when {
      bytes.isEmpty() -> null
      fileType != null ->
        createPrettyComponent("Payload (${fileType.displayName})", bytes, fileType)
      text.isTextProto() -> createTextProtoComponent(text, bytes)
      else -> createRawComponent(text, bytes)
    }
  }

  /**
   * Creates a component that displays a prototext payload.
   *
   * The prototext snippet is created by the agent using the `toString()` method with a
   * `proto-message` annotation comment prepended. We make an attempt to locate the `proto` source
   * file that contains the definition for the message. If found, it is added as a `proto-file`
   * annotation.
   */
  private fun createTextProtoComponent(text: String, bytes: ByteArray): JComponent {
    val protoFiles = protoFileFinder.findProtoFiles()
    val type = text.substringAfter(": ").substringBefore("\n")
    val regex = "^message $type \\{$".toRegex(MULTILINE)
    val protoFile = protoFiles.find { it.readText().contains(regex) }?.name ?: "???"
    val protoBytes = "# proto-file: $protoFile\n$text".toByteArray()
    val fileType = getFileTypeManager().getFileTypeByExtension("textproto")
    val protoTextComponent = createPrettyComponent(protoBytes, fileType)
    val rawComponent = BinaryDataViewer(bytes)
    val switchingPanel =
      SwitchingPanel(protoTextComponent, "View Proto Text", rawComponent, "View Raw")
    return createTitledPanel("Payload (Proto)", switchingPanel, switchingPanel.switcher)
  }

  /** Creates a component that displays a raw payload. */
  private fun createRawComponent(text: String, bytes: ByteArray): JComponent {
    val rawComponent = BinaryDataViewer(bytes)
    val textComponent = createPrettyComponent(text.toByteArray(), PlainTextFileType.INSTANCE)
    val switchingPanel = SwitchingPanel(rawComponent, "View Raw", textComponent, "View Text")
    return createTitledPanel("Payload", switchingPanel, switchingPanel.switcher)
  }

  /**
   * Gets the registered [FileTypeManager]
   *
   * The standard [FileTypeManager.getInstance] doesn't work in tests.
   */
  private fun getFileTypeManager(): FileTypeManager =
    ApplicationManager.getApplication().getService(FileTypeManager::class.java)

  override fun createTrailersComponent(): JComponent? {
    return when {
      grpcData.responseTrailers.isEmpty() -> null
      else -> createHeaderComponent(grpcData.responseTrailers)
    }
  }

  private fun createPrettyComponent(title: String, bytes: ByteArray, fileType: FileType) =
    createTitledPanel(title, createPrettyComponent(bytes, fileType), null)

  private fun createPrettyComponent(bytes: ByteArray, fileType: FileType): JComponent {
    return IntellijDataViewer.createPrettyViewerIfPossible(
        project,
        bytes,
        fileType,
        true,
        parentDisposable,
      )
      .component
  }

  fun interface ProtoFileFinder {
    fun findProtoFiles(): List<VirtualFile>
  }

  private class ProtoFileFinderImpl(private val project: Project) : ProtoFileFinder {
    override fun findProtoFiles(): List<VirtualFile> {
      val index = ProjectFileIndex.getInstance(project)
      return FilenameIndex.getAllFilesByExt(project, "proto", ProjectScope.getContentScope(project))
        .filter { index.isInSource(it) && !index.isInGeneratedSources(it) }
    }
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

private fun String.isTextProto() = startsWith("# proto-message:")
