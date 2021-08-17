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
package com.android.tools.idea.editors.literals.actions

import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.android.tools.idea.editors.literals.internal.LiveLiteralsDeploymentReportService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseTreePopupStep
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.ui.treeStructure.SimpleTreeStructure
import com.intellij.util.ui.JBUI
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.Dimension

/**
 * A [SimpleNode] representing the given [LiveLiteralsMonitorHandler.Problem].
 */
private class ProblemNode(project: Project, problem: LiveLiteralsMonitorHandler.Problem) : SimpleNode(project) {
  init {
    templatePresentation.setIcon(when (problem.severity) {
                                   LiveLiteralsMonitorHandler.Problem.Severity.ERROR -> AllIcons.General.Error
                                   LiveLiteralsMonitorHandler.Problem.Severity.WARNING -> AllIcons.General.Warning
                                   LiveLiteralsMonitorHandler.Problem.Severity.INFO -> AllIcons.General.Information
                                 })
    templatePresentation.addText(problem.content, SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }

  override fun getChildren(): Array<ProblemNode> = arrayOf()
}

/**
 * The root node containing all the [ProblemNode]s.
 */
private class ProblemRootNode(project: Project, problems: Collection<LiveLiteralsMonitorHandler.Problem>) : SimpleNode(project) {
  private val children = problems.map { ProblemNode(project, it) }.toTypedArray()

  override fun getChildren(): Array<ProblemNode> = children

}

/**
 * A [SimpleTreeStructure] for representing [LiveLiteralsMonitorHandler.Problem]
 */
private class ProblemsTreeStructure(val project: Project) : SimpleTreeStructure() {
  override fun getRootElement(): Any =
    ProblemRootNode(project, LiveLiteralsDeploymentReportService.getInstance(project).problems.map { it.second })
}

/**
 * Action that shows the problems dialog.
 */
internal class ShowLiveLiteralsProblemAction : AnAction(message("live.literals.action.show.problems.title")) {
  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    e.presentation.isVisible = LiveLiteralsDeploymentReportService.getInstance(project).hasProblems
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return


    JBPopupFactory.getInstance().createTree(BaseTreePopupStep<String>(project, message("live.literals.problems.dialog.title"),
                                                                               ProblemsTreeStructure(project))).apply {
      content.preferredSize = Dimension(content.preferredSize.width, JBUI.scale(200))

      showCenteredInCurrentWindow(project)
    }
  }
}