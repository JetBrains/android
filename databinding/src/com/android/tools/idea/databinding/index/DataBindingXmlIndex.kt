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

import com.android.SdkConstants.FD_RES
import com.android.resources.ResourceFolderType
import com.android.tools.idea.databinding.index.DataBindingXmlIndex.Layout
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.xml.NanoXmlBuilder
import com.intellij.util.xml.NanoXmlBuilder.stop
import com.intellij.util.xml.NanoXmlUtil
import java.io.DataInput
import java.io.DataOutput

/**
 * File based index for data binding layout xml files. Currently it collects metrics related information only.
 */
class DataBindingXmlIndex : FileBasedIndexExtension<String, Layout>() {
  companion object {
    @JvmStatic
    val NAME = ID.create<String, Layout>("DataBindingXmlIndex")
  }

  /**
   * Class for building the indexed content of a data binding <layout> tag.
   */
  data class Layout(val importCount: Int, val variableCount: Int)

  override fun getKeyDescriptor(): KeyDescriptor<String> {
    return EnumeratorStringDescriptor.INSTANCE
  }

  override fun dependsOnFileContent() = true

  override fun getValueExternalizer(): DataExternalizer<Layout> {
    return object : DataExternalizer<Layout> {
      override fun save(out: DataOutput, value: Layout?) {
        value ?: return
        `out`.writeInt(value.importCount)
        `out`.writeInt(value.variableCount)
      }

      override fun read(`in`: DataInput): Layout {
        return Layout( `in`.readInt(), `in`.readInt())
      }
    }
  }

  override fun getName(): ID<String, Layout> {
    return NAME
  }

  private enum class XmlParserState {NOT_STARTED, INVALID_LAYOUT, STARTED}

  override fun getIndexer(): DataIndexer<String, Layout, FileContent> {
    return DataIndexer { inputData ->
      var parserState = XmlParserState.NOT_STARTED
      var importCount = 0
      var variableCount = 0
      NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.contentAsText), object : NanoXmlBuilder {
        override fun startElement(s: String?, s1: String?, s2: String?, s3: String?, i: Int) {
          if (parserState == XmlParserState.NOT_STARTED) {
            if (s == "layout") {
              parserState = XmlParserState.STARTED
            }
            else {
              parserState = XmlParserState.INVALID_LAYOUT
              stop()
            }
          }
          when (s) {
            "variable" -> variableCount++
            "import" -> importCount++
          }
        }
      })

      if (parserState == XmlParserState.INVALID_LAYOUT) {
        emptyMap<String, Layout>()
      } else {
        mapOf("${FileBasedIndex.getFileId(inputData.file)}" to Layout(importCount, variableCount))
      }
    }
  }

  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return object : DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      override fun acceptInput(file: VirtualFile): Boolean {
        return XmlFileType.INSTANCE == file.fileType && "xml" == file.extension
               && ResourceFolderType.getFolderType(file.parent?.name.orEmpty()) == ResourceFolderType.LAYOUT
               && file.parent?.parent?.name == FD_RES
      }
    }
  }

  override fun getVersion() = 1
}