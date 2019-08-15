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
import com.android.tools.idea.res.BindingLayoutType
import com.android.tools.idea.res.BindingLayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.res.BindingLayoutType.VIEW_BINDING_LAYOUT
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
import java.io.Reader

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
        IOUtil.writeUTF(out, value.customBindingName ?: "")

        writeINT(out, value.imports.size)
        for (import in value.imports) {
          IOUtil.writeUTF(out, import.type)
          IOUtil.writeUTF(out, import.alias ?: "")
        }

        writeINT(out, value.variables.size)
        for (variable in value.variables) {
          IOUtil.writeUTF(out, variable.name)
          IOUtil.writeUTF(out, variable.type)
        }

        writeINT(out, value.viewIds.size)
        for (idInfo in value.viewIds) {
          IOUtil.writeUTF(out, idInfo.id)
          IOUtil.writeUTF(out, idInfo.viewName)
          IOUtil.writeUTF(out, idInfo.layoutName ?: "")
        }
      }

      override fun read(`in`: DataInput): IndexedLayoutInfo {
        val layoutType = BindingLayoutType.values()[readINT(`in`)]
        val customBindingName = IOUtil.readUTF(`in`).ifEmpty { null }

        val imports = mutableListOf<ImportInfo>()
        for (i in 0 until readINT(`in`)) {
          imports.add(ImportInfo(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`).ifEmpty { null }))
        }
        val variables = mutableListOf<VariableInfo>()
        for (i in 0 until readINT(`in`)) {
          variables.add(VariableInfo(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`)))
        }

        val viewIds = mutableListOf<ViewIdInfo>()
        for (i in 1..readINT(`in`)) {
          viewIds.add(ViewIdInfo(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`),
                                 IOUtil.readUTF(`in`).ifEmpty { null }))
        }
        return IndexedLayoutInfo(layoutType, customBindingName, imports, variables, viewIds)
      }
    }
  }

  override fun getName(): ID<String, IndexedLayoutInfo> {
    return NAME
  }

  override fun getIndexer(): DataIndexer<String, IndexedLayoutInfo, FileContent> {
    return DataIndexer { inputData ->
      var isDataBindingLayout = false
      var customBindingName: String? = null
      val variables = mutableListOf<VariableInfo>()
      val imports = mutableListOf<ImportInfo>()
      val viewIds = mutableListOf<ViewIdInfo>()

      class TagInfo(val name: String) {
        var importType: String? = null
        var importAlias: String? = null

        var variableName: String? = null
        var variableType: String? = null

        var viewClass: String? = null
        var viewId: String? = null
        var viewLayout: String? = null
      }

      NanoXmlUtil.parse(EscapingXmlReader(inputData.contentAsText), object : NanoXmlBuilder {
        var currTag: TagInfo? = null

        override fun startElement(name: String, nsPrefix: String?, nsURI: String?, systemID: String, lineNr: Int) {
          currTag = TagInfo(name)
          if (name == TAG_LAYOUT) {
            isDataBindingLayout = true
          }
        }

        override fun addAttribute(key: String, nsPrefix: String?, nsURI: String?, value: String, type: String) {
          val currTag = currTag!! // Always valid inside tags
          when (currTag.name) {
            SdkConstants.TAG_DATA ->
              when (key) {
                SdkConstants.ATTR_CLASS -> customBindingName = value
              }

            SdkConstants.TAG_IMPORT ->
              when (key) {
                SdkConstants.ATTR_TYPE -> currTag.importType = value
                SdkConstants.ATTR_ALIAS -> currTag.importAlias = value
              }

            SdkConstants.TAG_VARIABLE ->
              when (key) {
                SdkConstants.ATTR_NAME -> currTag.variableName = value
                SdkConstants.ATTR_TYPE -> currTag.variableType = value
              }

            else ->
              if (nsURI == SdkConstants.ANDROID_URI) {
                when (key) {
                  // Used to determine view type of <View>.
                  SdkConstants.ATTR_CLASS -> currTag.viewClass = value
                  // Used to determine view type of <Merge> and <Include>.
                  SdkConstants.ATTR_LAYOUT -> currTag.viewLayout = value
                  SdkConstants.ATTR_ID -> currTag.viewId = stripPrefixFromId(value)
                }
              }
          }
        }

        override fun elementAttributesProcessed(name: String, nsPrefix: String?, nsURI: String?) {
          val currTag = currTag!! // Always valid inside tags
          when (currTag.name) {
            SdkConstants.TAG_DATA -> {
              // Nothing to do here, but case needed to avoid ending up in default branch
            }

            SdkConstants.TAG_IMPORT ->
              if (currTag.importType != null) {
                imports.add(ImportInfo(currTag.importType!!, currTag.importAlias))
              }

            SdkConstants.TAG_VARIABLE ->
              if (currTag.variableName != null && currTag.variableType != null) {
                variables.add(VariableInfo(currTag.variableName!!, currTag.variableType!!))
              }

            else ->
              if (currTag.viewId != null) {
                // Tag should either be something like <TextView>, <Button>, etc.
                // OR the special-case <view class="path.to.CustomView"/>
                val viewName = if (currTag.name != SdkConstants.VIEW_TAG) currTag.name else currTag.viewClass
                if (viewName != null) {
                  viewIds.add(ViewIdInfo(currTag.viewId!!, viewName, currTag.viewLayout))
                }
              }
            }

          this.currTag = null
        }
      })

      val layoutType = if (isDataBindingLayout) DATA_BINDING_LAYOUT else VIEW_BINDING_LAYOUT
      mapOf(getKeyForFile(inputData.file) to IndexedLayoutInfo(layoutType, customBindingName, imports, variables, viewIds))
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

  override fun getVersion() = 3
}

/**
 * Reader that attempts to escape known codes (e.g. "&lt;") on the fly as it reads.
 *
 * It seems that NanoXml does not itself translate escape characters, instead skipping over them.
 * So, we have to intercept them ourselves. For the attributes we parse, we only care about a
 * subset of all potentially escaped characters -- specifically '<' and '>', which can be used
 * in generic types. The rest, we can skip over, which NanoXml would have done anyway.
 */
private class EscapingXmlReader(text: CharSequence): Reader() {
  private val delegate = CharArrayUtil.readerFromCharSequence(text)
  private val buffer = StringBuilder()

  override fun read(cbuf: CharArray, off: Int, len: Int): Int {
    var numRead = 0
    while (numRead < len) {
      var nextChar: Char = delegate.read().takeIf { it >= 0 }?.toChar() ?: break
      var skipChar = false
      if (nextChar == '&') {
        assert(buffer.isEmpty())

        buffer.append(nextChar)
        while (true) {
          nextChar = delegate.read().takeIf { it >= 0 }?.toChar() ?: break
          buffer.append(nextChar)
          if (nextChar == ';')
            break
        }

        when (buffer.toString()) {
          "&lt;" -> nextChar = '<'
          "&gt;" -> nextChar = '>'
          else -> skipChar = true
        }
        buffer.clear()
      }

      if (!skipChar) {
        cbuf[off + numRead] = nextChar
        ++numRead
      }
    }

    return if (numRead > 0) numRead else -1
  }

  override fun close() {
    delegate.close()
  }
}

/**
 * Data class for storing information related to <variable> tags.
 */
data class VariableInfo(
  val name: String,
  val type: String
)

/**
 * Data class for storing information related to <import> tags.
 */
data class ImportInfo(
  val type: String,
  val alias: String?
)

/**
 * Data class for storing information related to views with IDs.
 */
data class ViewIdInfo(
  /** Id of the view. */
  val id: String,

  /** Name of the view. Typically the tag name: <TextView>. */
  val viewName: String,

  /** Optional layout attribute. Only applicable to <Merge> or <Include> tags. */
  val layoutName: String?
)

/**
 * Data class for storing the indexed content of layouts we want to generate bindings for,
 * e.g. data binding or view binding candidates.
 *
 * For view binding data, many of these fields will be left empty.
 */
data class IndexedLayoutInfo(
  /** Type of layout. */
  val layoutType: BindingLayoutType,

  /** Name used to affect the final Binding class path, if present. */
  val customBindingName: String?,

  /** Data binding import elements. */
  val imports: Collection<ImportInfo>,

  /** Data binding variable elements. */
  val variables: Collection<VariableInfo>,

  /** Ids of views defined in this layout. */
  val viewIds: Collection<ViewIdInfo>
)