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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.MESSAGE_GROUP_ERROR_SUFFIX
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import org.xml.sax.SAXParseException
import java.io.File
import java.util.function.Consumer
import java.util.regex.Pattern

class XmlErrorOutputParser : BuildOutputParser {
  override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
    if (line == null || reader == null || messageConsumer == null) {
      return false
    }
    val matchIndex = line.indexOf(SAXParseException::class.java.name)
    if (matchIndex == -1) {
      return false
    }

    val messageLine = line.substring(matchIndex + SAXParseException::class.java.name.length).trim()
    val matcher = pattern.matcher(messageLine)
    if (matcher.matches()) {
      val systemId = matcher.group(1)
      val lineNumber = parseNumber(matcher.group(2))
      val columnNumber = parseNumber(matcher.group(3))
      val message = matcher.group(4) ?: ""

      var file: File? = null
      if (systemId != null) {
        file = if (systemId.startsWith("file:")) File(systemId.substring(5)) else File(systemId)
      }

      if (file != null && file.isFile) {
        messageConsumer.accept(
          FileMessageEventImpl(
            reader.buildId,
            MessageEvent.Kind.ERROR,
            XML_PARSING_GROUP + MESSAGE_GROUP_ERROR_SUFFIX,
            message,
            message,
            FilePosition(file, lineNumber, columnNumber)
          )
        )
      }
      else {
        messageConsumer.accept(
          MessageEventImpl(
            reader.buildId,
            MessageEvent.Kind.ERROR,
            XML_PARSING_GROUP + MESSAGE_GROUP_ERROR_SUFFIX,
            message,
            message
          )
        )
      }

      return true
    }
    return false
  }


  private fun parseNumber(numberAsString: String?): Int {
    if (numberAsString == null) {
      return -1
    }
    return try {
      // FilePosition is 0-based while the exception output is 1-based
      Integer.valueOf(numberAsString) - 1
    }
    catch (e: Exception) {
      -1
    }
  }

  companion object {
    const val XML_PARSING_GROUP = "Xml parsing"

    /**
     * Matches the sax parsing exception format:
     * org.xml.sax.SAXParseException; systemId: <file>; lineNumber: <lineNumber>; columnNumber: <colNumber>; <message>
     *
     * See [SAXParseException.toString]
     */
    private val pattern = Pattern.compile(
      "^(?:publicId: .*?)?(?:; systemId: (.*?))?(?:; lineNumber: (.*?))?(?:; columnNumber: (.*?))?(?:; (.*))?$")
  }
}
