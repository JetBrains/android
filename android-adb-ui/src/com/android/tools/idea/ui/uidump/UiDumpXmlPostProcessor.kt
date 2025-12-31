/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.uidump

import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.measureTimeMillis
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource

val implicitAttributeDefaultsInstruction =
  "For all the attributes listed below, if you don't see it in the input, assume it is the value provided below.\n"
val implicitAttributeDefaults = mapOf(
  "checkable" to "false",
  "checked" to "false",
  "clickable" to "false",
  "content-desc" to "",
  "enabled" to "true",
  "focusable" to "false",
  "focused" to "false",
  "long-clickable" to "false",
  "password" to "false",
  "resource-id" to "",
  "scrollable" to "false",
  "selected" to "false",
  "text" to "",
  "hint" to "",
  "index" to "0"
)

val omitAttributes = setOf("package")

fun createLlmInstruction(): String {
  val instruction = StringBuilder()
  instruction.append(implicitAttributeDefaultsInstruction)
  for ((key, value) in implicitAttributeDefaults) {
    instruction.append("  $key=\"$value\"\n")
  }
  return instruction.toString()
}

fun postProcess(xmlString: String): String {
  lateinit var result: String
  val time = measureTimeMillis {
    val doc = parse(xmlString)
    result = documentToString(doc, true)
  }
  println("XMLPostProcess took $time ms")
  return result
}

private fun parse(xmlString: String): Document {
  val factory = DocumentBuilderFactory.newInstance()
  val builder = factory.newDocumentBuilder()
  val inputSource = InputSource(StringReader(xmlString))
  return builder.parse(inputSource)
}

private fun documentToString(doc: Document, indent: Boolean = false): String {
  val writer = StringWriter()
  if (doc.xmlEncoding != null && doc.xmlVersion != null) {
    writer.write("<?xml version='${doc.xmlVersion}'" +
                 " encoding='${doc.xmlEncoding}'" +
                 " standalone='${if (doc.xmlStandalone) "yes" else "no"}' ?>\n")
  }
  val rootElement = doc.documentElement

  if (rootElement != null) {
    printNode(rootElement, writer, "", indent)
  }
  return writer.toString()
}

private fun printNode(node: Node, writer: StringWriter, currentIndent: String, indent: Boolean) {
  if (node.nodeType == Node.TEXT_NODE) {
    val text = node.nodeValue.trim()
    if (text.isNotEmpty()) {
      writer.write(text)
    }
    return
  }

  if (node.nodeType == Node.ELEMENT_NODE) {
    if (indent) writer.write(currentIndent)
    writer.write("<${node.nodeName}")
    val attributes = node.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i)
      val attrName = attr.nodeName
      if (implicitAttributeDefaults[attrName] != attr.nodeValue &&
        attrName !in omitAttributes) {
        writer.write(" ${attr.nodeName}=\"${attr.nodeValue}\"")
      }
    }

    if (node.hasChildNodes()) {
      writer.write(">")
      val children = node.childNodes
      var hasElementChildren = false
      for (i in 0 until children.length) {
        if (children.item(i).nodeType == Node.ELEMENT_NODE) {
          hasElementChildren = true
          break
        }
      }

      if (indent && hasElementChildren) writer.write("\n")

      for (i in 0 until children.length) {
        val child = children.item(i)
        if (child.nodeType == Node.ELEMENT_NODE || (child.nodeType == Node.TEXT_NODE && child.nodeValue.trim().isNotEmpty())) {
          printNode(child, writer, if (indent) "$currentIndent  " else "", indent)
          if (indent && child.nodeType == Node.ELEMENT_NODE) writer.write("\n")
        }
      }

      if (indent && hasElementChildren) writer.write(currentIndent)
      writer.write("</${node.nodeName}>")
    }
    else {
      writer.write("/>")
    }
  }
}
