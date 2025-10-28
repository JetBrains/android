/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.action

import com.google.idea.blaze.base.actions.BlazeProjectAction
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.qsync.QuerySyncManager.Companion.getInstance
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * An internal action to purge the build artifact cache, i.e. remove all entries from it. This does
 * not directly affect the project structure.
 */
class ReapplyProjectStructureAction : BlazeProjectAction(), DumbAware {
  override fun querySyncSupport(): QuerySyncStatus = QuerySyncStatus.REQUIRED

  override fun actionPerformedInBlazeProject(project: Project, e: AnActionEvent) {
    getInstance(project)
      .reapplyProjectStructure(QuerySyncActionStatsScope.create(project, javaClass, e))
  }
}
