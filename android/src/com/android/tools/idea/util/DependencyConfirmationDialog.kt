/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.util

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.DependencyType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JComponent

class DependencyConfirmationDialog(
  project: Project,
  private val module: Module,
  private val artifact: GoogleMavenArtifactId,
  private val configuration: DependencyType,
  private val resolvedCoordinate: String? = null,
) : DialogWrapper(project, false) {

  init {
    init()
    title = "Add ${getArtifactName()} Dependency"
    setOKButtonText("Accept Changes")
  }

  private fun getArtifactName(): String {
    val artifactName = artifact.toString()
    val lastDot = artifactName.lastIndexOf('.')
    return if (lastDot > 0 && lastDot < artifactName.length - 1) {
      val simpleName = artifactName.substring(lastDot + 1)
      simpleName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    } else {
      artifactName
    }
  }

  private val DependencyType.configurationName
    get() =
      when (this) {
        DependencyType.ANNOTATION_PROCESSOR -> "annotationProcessor"
        DependencyType.DEBUG_IMPLEMENTATION -> "debugImplementation"
        DependencyType.IMPLEMENTATION -> "implementation"
      }

  override fun createCenterPanel(): JComponent {
    val panel = JBPanel<JBPanel<*>>(BorderLayout())

    val content = JBPanel<JBPanel<*>>().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    val summary =
      JBLabel("Performing this action will make the following changes to your project:").apply {
        border = JBUI.Borders.emptyBottom(10)
        alignmentX = Component.LEFT_ALIGNMENT
      }
    content.add(summary)

    val displayCoordinate = resolvedCoordinate ?: "$artifact:+"

    // The dark rounded box holding the code snippet details
    val details =
      javax.swing.JTextPane().apply {
        editorKit = com.intellij.util.ui.HTMLEditorKitBuilder.simple()
        isEditable = false
        margin = JBUI.insets(10)
        putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        background = JBUI.CurrentTheme.ToolWindow.background()
        border = JBUI.Borders.customLine(JBColor.border(), 1)
        alignmentX = Component.LEFT_ALIGNMENT

        val css = ".dependency { color: green; font-weight: bold; font-family: monospace; }"
        text =
          "<html><head><style>body { font-family: ${JBFont.label().family}; margin: 0px; } " +
            "$css</style></head><body><div style=\"padding: 10px;\">" +
            "<pre><b>${module.name}/build.gradle</b>\n\n" +
            "    Add the library dependency:\n" +
            "        ${configuration.configurationName} <font class=\"dependency\">'$displayCoordinate'</font>\n" +
            "</pre></div></body></html>"
      }

    content.add(details)
    panel.add(content, BorderLayout.CENTER)

    return panel
  }
}
