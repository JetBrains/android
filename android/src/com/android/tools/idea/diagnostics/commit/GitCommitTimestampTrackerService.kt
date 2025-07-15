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
package com.android.tools.idea.diagnostics.commit

import com.android.tools.idea.serverflags.ServerFlagService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.VcsRoot

@Service(Service.Level.PROJECT)
class GitCommitTimestampTrackerService(private val project: Project) : Disposable {
  companion object {
    val LOG: Logger = Logger.getInstance(GitCommitTimestampTrackerService::class.java)
  }

  private val gitCommitTrackers = mutableListOf<GitCommitTracker>()

  private fun subscribeToVcsRootChanges(project: Project) {
    val vcsConnection = project.messageBus.connect(this)
    vcsConnection.subscribe(
      ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
      object : VcsListener {
        override fun directoryMappingChanged() {
          LOG.info("VCS roots updated")
          stopAllTrackers()
          val vcsRoots = ProjectLevelVcsManager.getInstance(project).allVcsRoots
          startTrackers(vcsRoots)
        }
      }
    )
  }

  private fun checkAndTrackGitRepository(project: Project) {
    LOG.info("Project base path: ${project.basePath}")
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val allVcsRoots = vcsManager.allVcsRoots
    subscribeToVcsRootChanges(project)
    startTrackers(allVcsRoots)
  }

  private fun startTrackers(allVcsRoots: Array<out VcsRoot>) {
    allVcsRoots.filter { "Git" == it.vcs?.name }.forEach { root ->
      val gitCommitTracker = GitCommitTracker(root.path.path)
      gitCommitTracker.startTracking()
      gitCommitTrackers.add(gitCommitTracker)
    }
  }

  override fun dispose() {
    stopAllTrackers()
  }

  private fun stopAllTrackers() {
    gitCommitTrackers.forEach(GitCommitTracker::stopTracking)
    gitCommitTrackers.clear()
  }

  fun startTracking() {
    if (!ServerFlagService.instance.getBoolean("diagnostics/commit_timestamp", false))
      return
    checkAndTrackGitRepository(project)
  }
}