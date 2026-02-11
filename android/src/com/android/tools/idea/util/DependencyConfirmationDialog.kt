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
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent

class DependencyConfirmationDialog(
  project: Project,
  private val module: Module,
  private val artifact: GoogleMavenArtifactId,
  private val configuration: DependencyType,
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

    // The dark rounded box holding the code snippet details
    val detailsContainer =
      JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border =
          BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(15), // Slightly increased overall padding
          )
        background = UIUtil.getPanelBackground()
        alignmentX = Component.LEFT_ALIGNMENT
      }

    // Define a monospace font to replicate the HTML <pre> tag behavior
    val codeFont = JBFont.create(Font(Font.MONOSPACED, Font.PLAIN, JBFont.label().size))

    // Line 1: build.gradle (bold, monospace)
    val fileLabel =
      JBLabel("${module.name}/build.gradle").apply {
        font = codeFont.asBold()
        alignmentX = Component.LEFT_ALIGNMENT
      }
    detailsContainer.add(fileLabel)

    val actionLabel =
      JBLabel("Add the library dependency:").apply {
        font = codeFont
        border = JBUI.Borders.empty(10, 40, 5, 0)
        alignmentX = Component.LEFT_ALIGNMENT
      }
    detailsContainer.add(actionLabel)

    val dependencyText =
      SimpleColoredComponent().apply {
        font = codeFont
        append("${configuration.configurationName} ", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, null))
        append("'$artifact:+'", SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.GREEN))
        // Indent further by 40px on the left
        border = JBUI.Borders.emptyLeft(60)
        alignmentX = Component.LEFT_ALIGNMENT
      }
    detailsContainer.add(dependencyText)

    content.add(detailsContainer)
    panel.add(content, BorderLayout.CENTER)

    return panel
  }
}
