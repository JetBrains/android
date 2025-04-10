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
package com.android.tools.idea.apk.viewer.pagealign

import com.android.tools.idea.apk.viewer.ApkFileEditorComponent
import com.android.tools.idea.ndk.PageAlignConfig.createShortSoUnalignedLoadSegmentsMessage
import com.intellij.icons.AllIcons.General.BalloonWarning
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

/**
 * Show a warning message with HTML link about alignment problems of an ELF file.
 * FUTURE: This panel could be promoted to an more general ElfViewer if we
 *         ever want to show more details about the file the user clicked.
 */
class AlignmentWarningViewer : ApkFileEditorComponent {

  override fun getComponent() : JComponent {
    val editorPane = JTextPane()
    editorPane.isEditable = false
    editorPane.setContentType("text/html")

    // Use IntelliJ's kit for better theme integration
    editorPane.editorKit = HTMLEditorKitBuilder.simple()

    editorPane.text = createShortSoUnalignedLoadSegmentsMessage()
    editorPane.setCaretPosition(1)
    val icon = JLabel(BalloonWarning)
    icon.setAlignmentY(0.72f)
    icon.border = JBUI.Borders.empty(JBUI.scale(4))
    editorPane.insertComponent(icon)

    editorPane.addHyperlinkListener(object : HyperlinkListener {
      override fun hyperlinkUpdate(e: HyperlinkEvent) {
        if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          // Use the URL from the event
          if (e.url != null) BrowserUtil.browse(e.url)
        }
      }
    })
    return JBScrollPane(editorPane)
  }

  override fun dispose() { }
}