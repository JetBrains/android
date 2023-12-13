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
package com.android.tools.idea.appinspection.inspectors.network.view.connectionsview

import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import com.android.tools.idea.appinspection.inspectors.network.model.connections.HttpData
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.util.Base64

/** Creates a cURL command representing an [HttpData] and puts it in the clipboard */
@Suppress("DialogTitleCapitalization")
internal class CopyAsCurlAction(
  data: ConnectionData,
  private val getClipboard: () -> Clipboard = { Toolkit.getDefaultToolkit().systemClipboard },
) : AnAction("Copy as cURL") {
  private val data: HttpData

  init {
    assert(data is HttpData)
    this.data = data as HttpData
  }

  override fun getActionUpdateThread() = BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = data.url.isNotBlank() && data.method.isNotBlank()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val payload = data.requestPayload
    val isBinaryRequestData = !payload.isValidUtf8
    val curlCommand = buildString {
      if (isBinaryRequestData) {
        val encoder = Base64.getEncoder()
        append("echo -n ${encoder.encodeToString(payload.toByteArray())} | base64 -d | ")
      }
      append("curl '${data.url}'")
      if (data.method != "GET") {
        appendLine("-X '${data.method}'")
      }
      data.requestHeaders.forEach { header ->
        appendLine("-H '${header.key}: ${header.value.joinToString { it }}'")
      }
      if (!payload.isEmpty) {
        if (payload.isValidUtf8) {
          appendLine("--data-raw '${payload.toStringUtf8()}'")
        } else {
          appendLine("--data-binary @-")
        }
      }
      appendLine("--compressed")
    }
    getClipboard().setContents(StringSelection(curlCommand), null)
  }
}

private fun StringBuilder.appendLine(line: String) = append(" \\\r  $line")
