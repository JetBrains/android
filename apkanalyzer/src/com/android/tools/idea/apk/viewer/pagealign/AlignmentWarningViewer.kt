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

import com.android.ide.common.pagealign.AlignmentProblem
import com.android.ide.common.pagealign.AlignmentProblem.LoadSectionNotAligned
import com.android.ide.common.pagealign.AlignmentProblem.RelroEndNotAligned
import com.android.ide.common.pagealign.AlignmentProblem.RelroStartNotAligned
import com.android.ide.common.pagealign.AlignmentProblem.ZipEntryNotAligned
import com.android.ide.common.pagealign.ProgramHeader
import com.android.tools.idea.apk.viewer.ApkFileEditorComponent
import com.android.tools.idea.apk.viewer.pagealign.AlignmentWarningViewer.HighlightField.ALIGN
import com.android.tools.idea.apk.viewer.pagealign.AlignmentWarningViewer.HighlightField.END
import com.android.tools.idea.apk.viewer.pagealign.AlignmentWarningViewer.HighlightField.START
import com.android.tools.idea.ndk.PageAlignConfig.createShortSoUnalignedLoadSegmentsMessage
import com.android.tools.idea.ndk.PageAlignConfig.isPageAlignMessageEnabled
import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons.General.BalloonWarning
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent

/**
 * Show a warning message with HTML link about alignment problems of an ELF file.
 * FUTURE: This panel could be promoted to an more general ElfViewer if we
 *         ever want to show more details about the file the user clicked.
 */
class AlignmentWarningViewer(val alignment: AlignmentFinding) : ApkFileEditorComponent {

  /**
   * True if there are LOAD alignment problems.
   * We only show the Play Store message if there are any LOAD alignment problems.
   */
  private val hasLoadAlignmentProblem =
    alignment.problems?.any { it is LoadSectionNotAligned } ?: false

  /**
   * Generates the warning content.
   */
  @VisibleForTesting
  fun warningContent() : String {
    val sb = StringBuilder()

    if (isPageAlignMessageEnabled() && hasLoadAlignmentProblem) {
      sb.append(createShortSoUnalignedLoadSegmentsMessage())
      sb.append("<br/><br/>")
    }

    if (alignment.problems != null) {
      sb.append("<h3>All ELF Alignment Problems</h3>")
      for(problem in alignment.problems) {
        sb.append("<li>${problem.toDetailedDisplayString()}</li>")
      }
    }
    return "$sb"
  }

  override fun getComponent() : JComponent {
    val editorPane = JTextPane()
    editorPane.isEditable = false
    editorPane.setContentType("text/html")

    // Use IntelliJ's kit for better theme integration
    editorPane.editorKit = HTMLEditorKitBuilder.simple()
    // Insert a warning icon if we're warning about Play Store deadline.
    if (hasLoadAlignmentProblem && isPageAlignMessageEnabled()) {
      editorPane.setCaretPosition(1)
      val icon = JLabel(BalloonWarning)
      icon.setAlignmentY(0.72f)
      icon.border = JBUI.Borders.empty(JBUI.scale(4))
      editorPane.insertComponent(icon)
    }
    // Add the warning content.
    editorPane.text = warningContent()
    // Hyperlink listener that opens a clicked URL.
    editorPane.addHyperlinkListener { e ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        // Use the URL from the event
        if (e.url != null) BrowserUtil.browse(e.url)
      }
    }
    return JBScrollPane(editorPane)
  }

  override fun dispose() { }

  /**
   * Generate HTML with the relevant part of the program header highlighted.
   */
  private fun AlignmentProblem.toDetailedDisplayString() : String {
    return when (this) {
      is ZipEntryNotAligned -> toString()
      is LoadSectionNotAligned -> "${ph.toPresentationHtml(ALIGN)} $this"
      is RelroEndNotAligned -> "${ph.toPresentationHtml(END)} $this"
      is RelroStartNotAligned -> "${ph.toPresentationHtml(START)} $this"
    }
  }

  /**
   * The field within the program header to highlight.
   */
  enum class HighlightField {
    START,
    END,
    ALIGN
  }

  /**
   * Generate highlighted HTML for a program header.
   */
  fun ProgramHeader.toPresentationHtml(vararg highlights: HighlightField) : String {
    val sb = StringBuilder()
    sb.append(programHeaderType)
    if (highlights.contains(START)) {
      sb.append(" <strong>start: ${vaddr.toHex()}</strong>")
    } else {
      sb.append(" start: ${vaddr.toHex()}")
    }
    if (highlights.contains(END)) {
      sb.append(" <strong>end: ${endVaddr.toHex()}</strong>")
    } else {
      sb.append(" end: ${endVaddr.toHex()}")
    }
    if (highlights.contains(ALIGN)) {
      sb.append(" <strong>align: ${align.toHex()}</strong>")
    } else {
      sb.append(" align: ${align.toHex()}")
    }
    return sb.toString()
  }

  private fun Long.toHex(): String = "0x" + toString(16).padStart(8, '0')
}