/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.lint

import com.android.tools.idea.common.model.NlComponent
import com.intellij.designer.model.EmptyXmlTag
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.xml.XmlChildRole
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/** Create a simple default hyper links to open the given [url]. */
fun createDefaultHyperLinkListener(): HyperlinkListener {
  return HyperlinkListener {
    val url = it.description
    if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      try {
        BrowserUtil.browse(url)
      } catch (exception: Exception) {
        val project: Project? = null
        val builder = StringBuilder()
        builder.append("Unable to open a default browser. \n")
        builder.append("Please open a browser manually and goto the url.\n")
        try {
          Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
          builder.append("\"$url\" has been copied to the clipboard.\n")
        } catch (exception: Exception) {
          builder.append("\"$url\"")
        }

        Messages.showErrorDialog(project, builder.toString(), "Error")
      }
    }
  }
}

/** Gets the text range within the file based on [component] */
fun NlComponent.getTextRange(): TextRange? {
  if (tag == null || tag == EmptyXmlTag.INSTANCE) {
    return (navigatable as? OpenFileDescriptor)?.rangeMarker?.textRange
  }
  val nameElement = tag?.let { XmlChildRole.START_TAG_NAME_FINDER.findChild(it.node) }
  return nameElement?.textRange
}
