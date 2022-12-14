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
import com.android.SdkConstants.TAG_LAYOUT
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
import com.android.tools.idea.databinding.index.BindingLayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.databinding.index.BindingLayoutType.PLAIN_LAYOUT
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension
import com.intellij.util.indexing.SingleEntryIndexer
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil
import com.intellij.util.xml.NanoXmlBuilder
import com.intellij.util.xml.NanoXmlUtil
import org.jetbrains.kotlin.idea.base.util.projectScope
import java.io.DataInput
import java.io.DataOutput
import java.io.Reader

/**
 * File based index for data binding layout xml files.
 */
class BindingXmlIndex : SingleEntryFileBasedIndexExtension<BindingXmlData>() {
  /**
   * An entry into this index, containing information associated with a target layout file.
   */
  data class Entry(val file: VirtualFile, val data: BindingXmlData)

  companion object {
    @JvmField
    val NAME = ID.create<Int, BindingXmlData>("BindingXmlIndex")

    fun acceptsFile(file: VirtualFile): Boolean =
      "xml" == file.extension &&
      ResourceFolderType.getFolderType(file.parent?.name.orEmpty()) == ResourceFolderType.LAYOUT

    private fun getDataForFile(file: VirtualFile, project: Project): BindingXmlData? {
      return FileBasedIndex.getInstance().getSingleEntryIndexData(NAME, file, project)
    }

    fun getDataForFile(project: Project, file: VirtualFile) = getDataForFile(file, project)
    fun getDataForFile(psiFile: PsiFile) = getDataForFile(psiFile.virtualFile, psiFile.project)

    /**
     * Returns all entries that match a given [layoutName].
     *
     * This may return multiple entries as a layout may have multiple configurations.
     */
    private fun getEntriesForLayout(project: Project, layoutName: String, scope: GlobalSearchScope): Collection<Entry> {
      val entries = mutableListOf<Entry>()
      FilenameIndex.getVirtualFilesByName(project, "$layoutName.xml", scope).forEach { file ->
        getDataForFile(file, project)?.let { data -> entries.add(Entry(file, data)) }
      }
      return entries
    }

    @JvmStatic
    fun getEntriesForLayout(project: Project, layoutName: String) = getEntriesForLayout(project, layoutName, project.projectScope())
    fun getEntriesForLayout(module: Module, layoutName: String) = getEntriesForLayout(module.project, layoutName,
                                                                                      module.moduleContentWithDependenciesScope)
  }

  /**
   * Defines the data externalizer handling the serialization/de-serialization of indexed information.
   */
  override fun getValueExternalizer(): DataExternalizer<BindingXmlData> {
    return object : DataExternalizer<BindingXmlData> {
      override fun save(out: DataOutput, value: BindingXmlData?) {
        value ?: return
        writeINT(out, value.layoutType.ordinal)
        IOUtil.writeUTF(out, value.rootTag)
        writeINT(out, if (value.viewBindingIgnore) 1 else 0)
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
        for (viewId in value.viewIds) {
          IOUtil.writeUTF(out, viewId.id)
          IOUtil.writeUTF(out, viewId.viewName)
          IOUtil.writeUTF(out, viewId.layoutName ?: "")
          IOUtil.writeUTF(out, viewId.typeOverride ?: "")
        }
      }

      override fun read(`in`: DataInput): BindingXmlData {
        val layoutType = BindingLayoutType.values()[readINT(`in`)]
        val rootTag = IOUtil.readUTF(`in`)
        val viewBindingIgnore = readINT(`in`) == 1
        val customBindingName = IOUtil.readUTF(`in`).ifEmpty { null }

        val imports = mutableListOf<ImportData>()
        for (i in 0 until readINT(`in`)) {
          imports.add(ImportData(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`).ifEmpty { null }))
        }
        val variables = mutableListOf<VariableData>()
        for (i in 0 until readINT(`in`)) {
          variables.add(VariableData(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`)))
        }

        val viewIds = mutableListOf<ViewIdData>()
        for (i in 1..readINT(`in`)) {
          viewIds.add(ViewIdData(IOUtil.readUTF(`in`), IOUtil.readUTF(`in`),
                                 IOUtil.readUTF(`in`).ifEmpty { null }, IOUtil.readUTF(`in`).ifEmpty { null }))
        }
        return BindingXmlData(layoutType, rootTag, viewBindingIgnore, customBindingName, imports, variables, viewIds)
      }
    }
  }

  override fun getName(): ID<Int, BindingXmlData> {
    return NAME
  }


  override fun getIndexer(): SingleEntryIndexer<BindingXmlData> {
    return object : SingleEntryIndexer<BindingXmlData>(false) {
      override fun computeValue(inputData: FileContent): BindingXmlData {
        var bindingLayoutType = PLAIN_LAYOUT
        var customBindingName: String? = null
        var viewBindingIgnore = false
        var rootTag: String? = null
        val variables = mutableListOf<VariableData>()
        val imports = mutableListOf<ImportData>()
        val viewIds = mutableListOf<ViewIdData>()

        class TagData(val name: String) {
          var importType: String? = null
          var importAlias: String? = null

          var variableName: String? = null
          var variableType: String? = null

          var viewClass: String? = null
          var viewId: String? = null
          var viewLayout: String? = null
          var viewTypeOverride: String? = null
        }

        NanoXmlUtil.parse(EscapingXmlReader(inputData.contentAsText), object : NanoXmlBuilder {
          val tags = mutableListOf<TagData>()

          override fun startElement(name: String, nsPrefix: String?, nsURI: String?, systemID: String, lineNr: Int) {
            tags.add(TagData(name))
            if (name == TAG_LAYOUT) {
              bindingLayoutType = DATA_BINDING_LAYOUT
            }

            if (tags.size == 1) {
              rootTag = name
            }
          }

          override fun addAttribute(key: String, nsPrefix: String?, nsURI: String?, value: String, type: String) {
            val currTag = tags.last() // We are processing a tag so we know there's at least one
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

              else -> {
                // If here, we are a View tag, e.g. <Button>, <EditText>, <include>, <merge>, etc.

                if (nsURI == SdkConstants.ANDROID_URI) {
                  when (key) {
                    SdkConstants.ATTR_ID -> currTag.viewId = ResourceUrl.parse(value)?.name
                  }
                }
                else if (nsURI == SdkConstants.TOOLS_URI) {
                  if (bindingLayoutType == PLAIN_LAYOUT
                      && currTag.name != SdkConstants.VIEW_INCLUDE && currTag.name != SdkConstants.VIEW_MERGE) {
                    when (key) {
                      SdkConstants.ATTR_VIEW_BINDING_TYPE -> currTag.viewTypeOverride = value
                    }
                  }
                }
                else if (nsURI == null) {
                  if (currTag.name == SdkConstants.VIEW_INCLUDE || currTag.name == SdkConstants.VIEW_MERGE) {
                    when (key) {
                      SdkConstants.ATTR_LAYOUT -> currTag.viewLayout = value
                    }
                  }
                  else if (currTag.name == SdkConstants.VIEW_TAG) {
                    when (key) {
                      SdkConstants.ATTR_CLASS -> currTag.viewClass = value
                    }
                  }
                }
              }
            }

            // If here, it means we're on the root tag
            if (tags.size == 1) {
              if (bindingLayoutType == PLAIN_LAYOUT && nsURI == SdkConstants.TOOLS_URI && key == SdkConstants.ATTR_VIEW_BINDING_IGNORE) {
                viewBindingIgnore = value.toBoolean()
              }
            }
          }

          override fun elementAttributesProcessed(name: String, nsPrefix: String?, nsURI: String?) {
            val currTag = tags.last() // We are processing a tag so we know there's at least one
            when (currTag.name) {
              SdkConstants.TAG_DATA -> {
                // Nothing to do here, but case needed to avoid ending up in default branch
              }

              SdkConstants.TAG_IMPORT ->
                if (currTag.importType != null) {
                  imports.add(ImportData(currTag.importType!!, currTag.importAlias))
                }

              SdkConstants.TAG_VARIABLE ->
                if (currTag.variableName != null && currTag.variableType != null) {
                  variables.add(VariableData(currTag.variableName!!, currTag.variableType!!))
                }

              else ->
                if (currTag.viewId != null) {
                  // Tag should either be something like <TextView>, <Button>, etc.
                  // OR the special-case <view class="path.to.CustomView"/>
                  val viewName = if (currTag.name != SdkConstants.VIEW_TAG) currTag.name else currTag.viewClass
                  if (viewName != null) {
                    viewIds.add(ViewIdData(currTag.viewId!!, viewName, currTag.viewLayout, currTag.viewTypeOverride))
                  }
                }
            }
          }

          override fun endElement(s: String?, s1: String?, s2: String?) {
            tags.removeAt(tags.lastIndex)
          }
        })

        return BindingXmlData(bindingLayoutType, rootTag.orEmpty(), viewBindingIgnore, customBindingName, imports, variables, viewIds)
      }
    }
  }

  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return object : DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      override fun acceptInput(file: VirtualFile): Boolean = acceptsFile(file)
    }
  }

  override fun getVersion() = 10
}

private const val COMMENT_START = "<!--"
private const val COMMENT_END = "-->"
/**
 * Reader that attempts to escape known codes (e.g. "&lt;") on the fly as it reads.
 *
 * It seems that NanoXml does not itself translate escape characters, instead skipping over them.
 * So, we have to intercept them ourselves. For the attributes we parse, we only care about a
 * subset of all potentially escaped characters -- specifically '<' and '>', which can be used
 * in generic types. The rest, we can skip over, which NanoXml would have done anyway.
 *
 * We also skip over comments ourselves, as NanoXml seems to trip up if it hits a comment that
 * has an unescaped & inside it.
 */
private class EscapingXmlReader(text: CharSequence): Reader() {
  /**
   * State associated with the input text.
   *
   * This state will live across multiple calls to [EscapingXmlReader.read].
   */
  private class InputState(val text: CharSequence) {
    var srcIndex = 0
    fun read(): Char = text[srcIndex++]
    fun skip(numChars: Int = 1) { srcIndex += numChars }
    fun beforeEnd() = srcIndex < text.length
    fun atEnd() = srcIndex >= text.length
  }

  /**
   * Helper class for simplifying writing to the output buffer.
   *
   * This class is created each time [EscapingXmlReader.read] is called.
   */
  private class OutputBuffer(val cbuf: CharArray, var dstIndex: Int, val maxWriteCount: Int) {
    var numCharsWritten = 0
    fun isFull() = numCharsWritten >= maxWriteCount
    fun write(c: Char) {
      assert(!isFull())
      cbuf[dstIndex++] = c
      numCharsWritten++
    }
  }

  private val buffer = StringBuilder()
  private val state = InputState(text)

  override fun read(cbuf: CharArray, off: Int, len: Int): Int {
    val output = OutputBuffer(cbuf, off, len)
    while (!output.isFull() && prepareNextChar()) {
      readNextCharInto(output)
    }
    return output.numCharsWritten.takeIf { it > 0 } ?: -1 // Indicate EOF so parser terminates
  }

  /**
   * Verify the input state is pointing at a valid character, potentially updating it if
   * necessary (e.g. to skip over comments).
   *
   * If true is returned, you can safely call [readNextCharInto]; otherwise, you should abort as we
   * are out of input text.
   */
  private fun prepareNextChar(): Boolean {
    if (state.atEnd()) return false

    if (skipIfMatch(COMMENT_START)) {
      // We don't really need to save this into the buffer, as we don't use it, but this is an easy
      // way to consume a bunch of text we don't care about.
      readIntoBufferUntil(COMMENT_END)
    }
    return (state.beforeEnd())
  }

  /**
   * Read the next character out of the input text and write it into the output buffer.
   *
   * When done, the input state will be pointing at the next character to parse. This may be
   * more than one character later, if the character that was just read in was escaped
   * (e.g. '&lt;')
   *
   * It's expected that the state is valid before calling this method. In other words, callers
   * should call [prepareNextChar] first.
   */
  private fun readNextCharInto(output: OutputBuffer) {
    var nextChar: Char? = state.read()
    if (nextChar == '&') {
      readIntoBufferUntil(";")
      when (buffer.toString()) {
        "lt" -> nextChar = '<'
        "gt" -> nextChar = '>'
        else -> nextChar = null
      }
    }

    if (nextChar != null) {
      output.write(nextChar)
    }
  }

  /**
   * Keep reading characters out of the input text until you hit the [terminal] string or the
   * end of the text, whichever comes first. Once finished, [buffer] will be populated with all
   * text up to but not including [terminal]. However, [terminal] will still be consumed.
   */
  private fun readIntoBufferUntil(terminal: String) {
    buffer.clear()
    do {
      buffer.append(state.read())
    } while (state.beforeEnd() && !isMatch(terminal))
    if (state.beforeEnd()) {
      state.skip(terminal.length) // Consume `terminal`
    }
  }

  /**
   * Check if the [target] text matches the current input position.
   */
  private fun isMatch(target: String): Boolean {
    return (state.srcIndex + target.length <= state.text.length
        && target.indices.all { i -> target[i] == state.text[state.srcIndex + i] })
  }

  private fun skipIfMatch(target: String): Boolean {
    if (isMatch(target)) {
      state.skip(target.length)
      return true
    }
    return false
  }

  override fun close() {} // We just point at a String in memory, no need to close anything
}
