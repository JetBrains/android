/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.index

import com.android.resources.ResourceFolderType
import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataOutput
import java.io.StringReader
import javax.xml.bind.JAXBContext

/**
 * File based index for the parts of navigation xml files relevant to generating Safe Args classes.
 */
class NavXmlIndex : FileBasedIndexExtension<String, NavXmlData>() {
  companion object {
    private fun getLog() = Logger.getInstance(NavXmlIndex::class.java)

    @JvmField
    val NAME = ID.create<String, NavXmlData>("NavXmlIndex")

    private fun getKeyForFile(file: VirtualFile) = FileBasedIndex.getFileId(file).toString()

    private fun getDataForFile(file: VirtualFile, scope: GlobalSearchScope): NavXmlData? {
      val project = scope.project ?: return null
      if (DumbService.getInstance(project).isDumb) {
        getLog().info("${NavXmlIndex::class.simpleName} queried outside of smart mode.")
        return null
      }

      val index = FileBasedIndex.getInstance()
      return index.getValues(NAME, getKeyForFile(file), scope).firstOrNull()
    }

    fun getDataForFile(project: Project, file: VirtualFile) = getDataForFile(file, GlobalSearchScope.fileScope(project, file))
  }

  private val jaxbContext = JAXBContext.newInstance(MutableNavNavigationData::class.java)
  private val jaxbSerializer = jaxbContext.createMarshaller()
  private val jaxbDeserializer = jaxbContext.createUnmarshaller()

  override fun getVersion() = 1
  override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
  override fun dependsOnFileContent() = true
  override fun getName(): ID<String, NavXmlData> = NAME

  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return object : DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      override fun acceptInput(file: VirtualFile): Boolean {
        if (!StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) {
          return false
        }
        return "xml" == file.extension
               && ResourceFolderType.getFolderType(file.parent?.name.orEmpty()) == ResourceFolderType.NAVIGATION
               && XmlFileType.INSTANCE == file.fileType
      }
    }
  }

  /**
   * Defines the data externalizer handling the serialization/de-serialization of indexed information.
   */
  override fun getValueExternalizer(): DataExternalizer<NavXmlData> {
    return object : DataExternalizer<NavXmlData> {
      override fun save(out: DataOutput, value: NavXmlData) {
        val outBytes = ByteArrayOutputStream().use { writer ->
          jaxbSerializer.marshal(value.root, writer)
          writer.toByteArray()
        }
        out.writeInt(outBytes.size)
        out.write(outBytes)
      }

      override fun read(`in`: DataInput): NavXmlData {
        val inBytes = ByteArray(`in`.readInt())
        `in`.readFully(inBytes)
        val rootNav = ByteArrayInputStream(inBytes).use { bytes -> jaxbDeserializer.unmarshal(bytes) as NavNavigationData }
        return NavXmlData(rootNav)
      }
    }
  }

  override fun getIndexer(): DataIndexer<String, NavXmlData, FileContent> {
    return DataIndexer { inputData ->
      val rootNav = jaxbDeserializer.unmarshal(StringReader(inputData.contentAsText.toString())) as NavNavigationData
      mapOf(getKeyForFile(inputData.file) to NavXmlData(rootNav))
    }
  }
}
