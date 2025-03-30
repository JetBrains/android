/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.logcat.LogcatBundle
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.ide.scratch.ScratchFileService.Option.create_new_always
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.json.JsonLanguage
import com.intellij.lang.Language
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.PathUtil
import org.jetbrains.annotations.VisibleForTesting
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

private val GSON = Gson()
private val XML = DocumentBuilderFactory.newInstance().newDocumentBuilder()

/**
 * An action that opens a popup dialog containing embedded JSON/XML from the log message
 *
 * Embedded is a complete snippet of XML/JSON embedded in the message. We detect a snippet by
 * extracting the text between the first `{` or `<` and the last `}` or `>` and running it through a
 * parser.
 *
 * TODO(b/235501148): Add the ability to include even partial XML/JSON, as in, a snippet that
 *   doesn't full parse.
 */
internal class CreateScratchFileAction : DumbAwareAction("Create a Scratch File from JSON/XML") {
  private val scratchRootType = ScratchRootType.getInstance()

  private val navigationSupport = PsiNavigationSupport.getInstance()

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = false
    val (_, language) = e.findEmbeddedData() ?: return
    e.presentation.text =
      LogcatBundle.message("logcat.open.embedded.data.text", language.displayName)
    e.presentation.icon = language.associatedFileType?.icon
    e.presentation.isVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val (text, language) = e.findEmbeddedData() ?: return

    val formatted = ScratchFileCreationHelper.reformat(project, language, text)
    val fileExtension = language.associatedFileType?.defaultExtension ?: ""
    val fileName = PathUtil.makeFileName("logcat", fileExtension)
    val file =
      scratchRootType.createScratchFile(project, fileName, language, formatted, create_new_always)
        ?: return
    navigationSupport.createNavigatable(project, file, 0).navigate(true)
  }

  private enum class EmbeddedLanguage(
    val language: Language,
    val startChar: Char,
    val endChar: Char,
    val isValid: (String) -> Boolean,
  ) {
    @Suppress("unused") // Not used explicitly but `EmbeddedLanguage.values() is used
    JSON(JsonLanguage.INSTANCE, '{', '}', { isJson(it) }),
    @Suppress("unused") // Not used explicitly but `EmbeddedLanguage.values() is used
    XML(XMLLanguage.INSTANCE, '<', '>', { isXml(it) });

    fun findInText(text: String): EmbeddedData? {
      val start = text.indexOf(startChar)
      if (start < 0) {
        return null
      }
      val end = text.lastIndexOf(endChar)
      if (end < 0) {
        return null
      }
      val data = text.substring(start, end + 1)
      return if (isValid(data)) EmbeddedData(data, language) else null
    }
  }

  @VisibleForTesting internal data class EmbeddedData(val text: String, val language: Language)

  companion object {
    // This is visible for testing because it's tricky to test actionPerformed() directly since it
    // has side effects of creating actual
    // files on disk and changing the state of the Scratch File subsystem.
    @VisibleForTesting
    internal fun AnActionEvent.findEmbeddedData(): EmbeddedData? {
      val message = getLogcatMessage()?.message ?: return null
      EmbeddedLanguage.entries.forEach {
        val data = it.findInText(message)
        if (data != null) {
          return data
        }
      }
      return null
    }
  }
}

// TODO(b/235501148): Detect partial JSON. See LintSyntaxHighlighter#tokenizeXml() for a starting
// point.
private fun isJson(text: String): Boolean {
  return try {
    GSON.fromJson(text, JsonObject::class.java)
    true
  } catch (e: Exception) {
    false
  }
}

// TODO(b/235501148): Detect partial XML. See LintSyntaxHighlighter#tokenizeXml() for a starting
// point.
private fun isXml(text: String): Boolean {
  return try {
    XML.parse(InputSource(StringReader(text)))
    true
  } catch (e: Exception) {
    false
  }
}
