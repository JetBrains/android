/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.index

import com.android.SdkConstants
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.TAG_LAYOUT
import com.android.ide.common.resources.stripPrefixFromId
import com.android.resources.ResourceFolderType
import com.android.tools.idea.res.binding.BindingLayoutType
import com.android.tools.idea.res.binding.BindingLayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.res.binding.BindingLayoutType.VIEW_BINDING_LAYOUT
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.xml.NanoXmlBuilder
import com.intellij.util.xml.NanoXmlUtil
import java.io.DataInput
import java.io.DataOutput


/**
 * File based index for data binding layout xml files.
 */
class BindingXmlIndex : FileBasedIndexExtension<String, IndexedLayoutInfo>() {
  companion object {
    @JvmField
    val NAME = ID.create<String, IndexedLayoutInfo>("BindingXmlIndex")

    @JvmStatic
    fun getKeyForFile(file: VirtualFile) = FileBasedIndex.getFileId(file).toString()
  }

  override fun getKeyDescriptor(): KeyDescriptor<String> {
    return EnumeratorStringDescriptor.INSTANCE
  }

  override fun dependsOnFileContent() = true

  /**
   * Defines the data externalizer handling the serialization/de-serialization of indexed information.
   */
  override fun getValueExternalizer(): DataExternalizer<IndexedLayoutInfo> {
    return object : DataExternalizer<IndexedLayoutInfo> {
      override fun save(out: DataOutput, value: IndexedLayoutInfo?) {
        value ?: return
        writeINT(out, value.layoutType.ordinal)
        writeINT(out, value.importCount)
        writeINT(out, value.variableCount)
        writeINT(out, value.viewIds.size)
        for (idInfo in value.viewIds) {
          IOUtil.writeUTF(out, idInfo.id)
          IOUtil.writeUTF(out, idInfo.viewName ?: "")
          IOUtil.writeUTF(out, idInfo.layoutName ?: "")
        }
      }

      override fun read(`in`: DataInput): IndexedLayoutInfo {
        val layoutType = BindingLayoutType.values()[readINT(`in`)]
        val importCount = readINT(`in`)
        val variableCount = readINT(`in`)
        val idList = mutableListOf<ViewIdInfo>()
        for (i in 1..readINT(`in`)) {
          idList.add(ViewIdInfo(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`).ifEmpty { null },
                                IOUtil.readUTF(`in`).ifEmpty { null }))
        }
        return IndexedLayoutInfo(layoutType, importCount, variableCount, idList)
      }
    }
  }

  override fun getName(): ID<String, IndexedLayoutInfo> {
    return NAME
  }

  override fun getIndexer(): DataIndexer<String, IndexedLayoutInfo, FileContent> {
    return DataIndexer { inputData ->
      var importCount = 0
      var variableCount = 0
      var isDataBindingLayout = false
      val idList = mutableListOf<ViewIdInfo>()
      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.contentAsText), object : NanoXmlBuilder {
        var id: String? = null
        var viewName: String? = null
        var viewClass: String? = null
        var layoutName: String? = null

        override fun addAttribute(key: String, nsPrefix: String?, nsURI: String?, value: String, type: String) {
          if (nsURI == SdkConstants.ANDROID_URI) {
            when (key) {
              // Used to determine view type of <View>.
              SdkConstants.ATTR_CLASS -> viewClass = value
              // Used to determine view type of <Merge> and <Include>.
              SdkConstants.ATTR_LAYOUT -> layoutName = value
              SdkConstants.ATTR_ID -> id = stripPrefixFromId(value)
            }
          }
        }

        override fun elementAttributesProcessed(name: String, nsPrefix: String?, nsURI: String?) {
          id?.let {
            if (SdkConstants.VIEW_TAG == viewName) {
              viewName = viewClass
            }
            idList.add(ViewIdInfo(it, viewName, layoutName))
          }
          resetVariables()
        }

        override fun startElement(name: String, nsPrefix: String?, nsURI: String?, systemID: String, lineNr: Int) {
          if (name == TAG_LAYOUT) {
            isDataBindingLayout = true
          }
          when (name) {
            SdkConstants.TAG_VARIABLE -> variableCount++
            SdkConstants.TAG_IMPORT -> importCount++
            else -> viewName = name
          }
        }

        private fun resetVariables() {
          id = null
          viewName = null
          viewClass = null
          layoutName = null
        }
      })
      if (isDataBindingLayout) {
        mapOf(getKeyForFile(inputData.file) to
                IndexedLayoutInfo(DATA_BINDING_LAYOUT, importCount, variableCount, idList))
      }
      else {
        mapOf(getKeyForFile(inputData.file) to
                IndexedLayoutInfo(VIEW_BINDING_LAYOUT, 0, 0, idList))
      }
    }
  }

  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return object : DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      override fun acceptInput(file: VirtualFile): Boolean {
        return "xml" == file.extension
               && ResourceFolderType.getFolderType(file.parent?.name.orEmpty()) == ResourceFolderType.LAYOUT
               && file.parent?.parent?.name == FD_RES
               && XmlFileType.INSTANCE == file.fileType
      }
    }
  }

  override fun getVersion() = 2
}

/**
 * Data class for storing information related to view viewIds
 */
data class ViewIdInfo(
  /** Id of the view. */
  val id: String,

  /** Name of the view. Typically the tag name: <TextView>. */
  val viewName: String?,

  /** Optional layout attribute. Only applicable to <Merge> or <Include> tags. */
  val layoutName: String?
)

/**
 * Data class for storing the indexed content of a data binding <layout> tag.
 */
data class IndexedLayoutInfo(
  /** Type of layout. */
  val layoutType: BindingLayoutType,

  /** Number of data binding import elements. */
  val importCount: Int,

  /** Number of data binding variable elements. */
  val variableCount: Int,

  /** Ids of views defined in this layout. */
  val viewIds: List<ViewIdInfo>
)