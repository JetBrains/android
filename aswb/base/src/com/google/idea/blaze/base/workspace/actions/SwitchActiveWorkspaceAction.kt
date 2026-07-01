/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.workspace.actions

import com.google.idea.blaze.base.actions.BlazeProjectAction
import com.google.idea.blaze.base.projectview.ProjectViewEdit
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.projectview.section.ScalarSection
import com.google.idea.blaze.base.projectview.section.sections.EnableWorkspaceSwitcherSection
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceLocationSection
import com.google.idea.blaze.base.settings.BazelImportSettingsManager
import com.google.idea.blaze.base.workspace.WorkspaceDescriptor
import com.google.idea.blaze.base.workspace.WorkspaceDiscoverer
import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager
import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager.getWorkspaceTarget
import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager.setWorkspaceTarget
import com.google.idea.blaze.base.workspace.WorkspaceSwitcherStateService
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.awt.RelativePoint
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Presentation wrapper class representing a physical workspace candidate in chooser menus. */
data class WorkspaceCandidate(val descriptor: WorkspaceDescriptor) : Comparable<WorkspaceCandidate> {
  val absolutePath: Path
    get() = descriptor.path

  val name: String
    get() = descriptor.name

  override fun toString(): String = name

  override fun compareTo(other: WorkspaceCandidate): Int = this.name.compareTo(other.name, ignoreCase = true)
}

/** Action group and popup trigger allowing developers to switch their project workspace location dynamically. */
class SwitchActiveWorkspaceAction : BlazeProjectAction(), DumbAware {

  companion object {
    private val LOG = logger<SwitchActiveWorkspaceAction>()

    /** Discovers and returns all alternative physical workspace candidates for the project. */
    fun discoverCandidates(project: Project): List<WorkspaceCandidate> {
      val discoverer = WorkspaceDiscoverer.getWorkspaceDiscoverer(project)
      val discovered = discoverer.discoverWorkspaces(project).toMutableList()

      val activeTarget = project.getWorkspaceTarget()
      if (activeTarget != null && discovered.none { it.path == activeTarget }) {
        val activeDescriptor =
          discoverer.getWorkspaceDescriptor(activeTarget) ?: WorkspaceDescriptor(activeTarget, activeTarget.fileName?.toString() ?: "")
        discovered.add(activeDescriptor)
      }

      return discovered.distinct().map { WorkspaceCandidate(it) }.sorted()
    }

    /**
     * Redirects the project view location to point to the selected physical workspace.
     *
     * @param physicalPath the absolute Path of the physical target workspace directory
     */
    fun performSwitch(project: Project, physicalPath: Path) {
      val coroutineScope = project.service<WorkspaceSwitcherStateService>().coroutineScope
      coroutineScope.launch {
        withBackgroundProgress(project, "Switching Active Workspace", false) {
          try {
            val edit =
              ProjectViewEdit.editLocalProjectView(project) { builder ->
                val oldSection = builder.getLast(WorkspaceLocationSection.KEY)
                val newSection = ScalarSection.builder(WorkspaceLocationSection.KEY).set(physicalPath.toString()).build()
                builder.replace(oldSection, newSection)
                true
              } ?: error("Failed to initialize ProjectViewEdit framework editor for project ${project.name}")
            edit.apply()

            project
              .setWorkspaceTarget(physicalPath)
              .let { LocalFileSystem.getInstance().findFileByNioFile(it) as? NewVirtualFile }
              ?.refresh(true, true)
          } catch (ex: Exception) {
            if (ex is kotlinx.coroutines.CancellationException) {
              throw ex
            }
            LOG.error("Failed to switch workspace targets", ex)
            ApplicationManager.getApplication().invokeLater {
              Messages.showErrorDialog(project, "Failed to update workspace targets: ${ex.message}", "Switch Workspace Error")
            }
          }
        }
      }
    }

    /**
     * Creates a ListPopup to switch the active workspace.
     *
     * @param relativePoint the clicked menu screen location Point
     */
    fun createSwitchPopup(project: Project, relativePoint: RelativePoint): ListPopup {
      val popupFactory = JBPopupFactory.getInstance()
      val dataContext = DataManager.getInstance().getDataContext(relativePoint.component)

      val loadingGroup =
        DefaultActionGroup(
          object : AnAction("Searching for workspaces...", null, com.intellij.icons.AllIcons.Process.Step_2) {
            override fun actionPerformed(e: AnActionEvent) {}

            override fun update(e: AnActionEvent) {
              e.presentation.isEnabled = false
            }
          }
        )

      val loadingPopup =
        popupFactory.createActionGroupPopup(
          "Select Active Workspace",
          loadingGroup,
          dataContext,
          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
          true,
        )

      val coroutineScope = project.service<WorkspaceSwitcherStateService>().coroutineScope
      coroutineScope.launch {
        val candidates = withContext(Dispatchers.Default) { discoverCandidates(project) }
        val activeTarget = project.getWorkspaceTarget()

        withContext(Dispatchers.EDT) {
          if (loadingPopup.isDisposed) return@withContext
          loadingPopup.cancel()

          val realGroup =
            DefaultActionGroup().apply {
              if (candidates.isEmpty()) {
                add(
                  object : AnAction("No workspaces found") {
                    override fun actionPerformed(e: AnActionEvent) {}

                    override fun update(e: AnActionEvent) {
                      e.presentation.isEnabled = false
                    }
                  }
                )
              } else {
                candidates.forEach { candidate ->
                  add(
                    object : AnAction(candidate.name), DumbAware {
                      override fun actionPerformed(e: AnActionEvent) {
                        if (candidate.absolutePath != activeTarget) {
                          performSwitch(project, candidate.absolutePath)
                        }
                      }

                      override fun update(e: AnActionEvent) {
                        if (candidate.absolutePath == activeTarget) {
                          e.presentation.text = candidate.name + " (current)"
                        } else {
                          e.presentation.text = candidate.name
                        }
                      }
                    }
                  )
                }
              }
            }

          val finalPopup =
            popupFactory.createActionGroupPopup(
              "Select Active Workspace",
              realGroup,
              dataContext,
              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
              true,
            )
          finalPopup.show(relativePoint)
        }
      }

      return loadingPopup
    }
  }

  override fun querySyncSupport() = QuerySyncStatus.SUPPORTED

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent) {
    if (!WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED.getValue()) {
      return
    }
    val projectViewSet =
      ProjectViewManager.getInstance(project).projectViewSet
        ?: error("Active project view set is uninitialized for project ${project.name}")
    val isEnabled = projectViewSet.getScalarValue(EnableWorkspaceSwitcherSection.KEY).getOrNull() ?: false

    val relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(this, e)
    if (!isEnabled) {
      val result =
        Messages.showYesNoDialog(
          project,
          "Active workspace switching is not enabled for this project.\n" +
            "Would you like to enable it in your .bazelproject file and configure it?",
          "Enable Workspace Switching",
          "Enable",
          "Cancel",
          Messages.getQuestionIcon(),
        )
      if (result == Messages.YES) {
        enableAndShowPopup(project, relativePoint)
      }
      return
    }
    val popup = createSwitchPopup(project, relativePoint)
    popup.show(relativePoint)
  }

  /** Configures and enables active workspace switching in the `.bazelproject` file, then presents the candidate chooser popup. */
  private fun enableAndShowPopup(project: Project, relativePoint: RelativePoint) {
    val coroutineScope = project.service<WorkspaceSwitcherStateService>().coroutineScope
    coroutineScope.launch {
      withBackgroundProgress(project, "Enabling Workspace Switching", false) {
        fun writeEnableWorkspaceSwitcherToProjectView() {
          val edit =
            ProjectViewEdit.editLocalProjectView(project) { builder ->
              val oldSection = builder.getLast(EnableWorkspaceSwitcherSection.KEY)
              val newSection = ScalarSection.builder(EnableWorkspaceSwitcherSection.KEY).set(true).build()
              builder.replace(oldSection, newSection)
              true
            } ?: error("Failed to initialize ProjectViewEdit framework editor for project ${project.name}")
          edit.apply()
        }

        fun reloadProjectViewConfiguration() {
          BazelImportSettingsManager.getInstance(project).reloadProjectView()
        }

        suspend fun showWorkspaceCandidatesPopup() {
          withContext(Dispatchers.EDT) {
            val popup = createSwitchPopup(project, relativePoint)
            popup.show(relativePoint)
          }
        }

        try {
          writeEnableWorkspaceSwitcherToProjectView()
          reloadProjectViewConfiguration()
          showWorkspaceCandidatesPopup()
        } catch (ex: Exception) {
          if (ex is kotlinx.coroutines.CancellationException) {
            throw ex
          }
          LOG.error("Failed to enable workspace switching", ex)
          withContext(Dispatchers.EDT) {
            Messages.showErrorDialog(project, "Failed to enable workspace switching: ${ex.message}", "Configuration Error")
          }
        }
      }
    }
  }

  override fun updateForBlazeProject(project: Project, e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED.getValue()
  }
}
