/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.project.TargetsToBuild
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.jvm.optionals.getOrNull

/**
 * Helper class for actions that build dependencies for source files, to allow the core logic to be
 * shared.
 */
class BuildDependenciesHelper(val project: Project) {
  private val syncManager = QuerySyncManager.getInstance(project)

  fun canEnableAnalysisNow(): Boolean = !syncManager.operationInProgress()

  fun getTargetsToEnableAnalysisFor(virtualFile: VirtualFile): TargetsToBuild {
    if (!syncManager.isProjectLoaded || syncManager.operationInProgress()) {
      return TargetsToBuild.None
    }
    return syncManager.getTargetsToBuild(virtualFile)
  }

  fun getTargetsToEnableAnalysisFor(workspaceRelativeFile: Path): TargetsToBuild {
    if (!syncManager.isProjectLoaded || syncManager.operationInProgress()) {
      return TargetsToBuild.None
    }
    return syncManager.getTargetsToBuild(workspaceRelativeFile)
  }

  fun getSourceFileMissingDepsCount(toBuild: TargetsToBuild.SourceFile): Int {
    val snapshot = syncManager.currentSnapshot.getOrNull() ?: return 0
    return snapshot.getPendingExternalDeps(toBuild.targets).size
  }

  fun canEnableAnalysisFor(virtualFile: VirtualFile): Boolean {
    if (!virtualFile.isInLocalFileSystem) {
      return false
    }
    val workspaceRoot = WorkspaceRoot.fromProject(project).path()
    val filePath = virtualFile.fileSystem.getNioPath(virtualFile) ?: return false
    if (!filePath.startsWith(workspaceRoot)) {
      return false
    }

    val relative = workspaceRoot.relativize(filePath)
    return syncManager.canEnableAnalysisFor(relative)
  }

  @get:Throws(BuildException::class)
  val workingSet: Set<Path>
    get() =// TODO: Any output from the context here is not shown in the console.
      syncManager.getLoadedProject().orElseThrow().getWorkingSet(BlazeContext.create())

  fun getAffectedTargetsForPaths(paths: Set<Path>): Set<Label> {
    val disambiguator = createDisambiguatorForPaths(paths)
    val ambiguousTargets = disambiguator.calculateUnresolvableTargets()
    if (ambiguousTargets.isNotEmpty()) {
      QuerySyncManager.getInstance(project)
        .notifyWarning(
          "Ambiguous target sets found",
          "Ambiguous target sets found; not building them: "
            + ambiguousTargets
            .map {it.displayLabel }
            .joinToString { ", " }
        )
    }

    return disambiguator.unambiguousTargets
  }

  /**
   * Additional targets to consider when disambiguating targets to build for a file.
   */
  sealed interface TargetDisambiguationAnchors {
    val anchorTargets: Set<Label>

    /**
     * A set of specific targets to consider when disambiguating targets to build for a file.
     */
    data class Targets(override val anchorTargets: Set<Label>) : TargetDisambiguationAnchors

    /**
     * An anchor requesting that the working set be considered when disambiguating targets to build for a file.
     */
    class WorkingSet(private val helper: BuildDependenciesHelper) : TargetDisambiguationAnchors {
      override val anchorTargets: Set<Label> get() = helper.workingSetTargetsIfEnabled
    }

    companion object {
      @JvmField val NONE: TargetDisambiguationAnchors = Targets(emptySet())
    }
  }

  fun determineTargetsAndRun(
    vf: VirtualFile,
    positioner: PopupPositioner,
    consumer: Consumer<ImmutableSet<Label>>,
    targetDisambiguationAnchors: TargetDisambiguationAnchors,
  ) {
      determineTargetsAndRun(vf, positioner, targetDisambiguationAnchors) { consumer.accept(ImmutableSet.copyOf(it)) }
  }

  fun determineTargetsAndRun(
    vf: VirtualFile,
    positioner: PopupPositioner,
    targetDisambiguationAnchors: TargetDisambiguationAnchors,
    consumer: (Set<Label>) -> Unit
  ) {
    val toBuild = getTargetsToEnableAnalysisFor(vf)
    val additionalTargets = targetDisambiguationAnchors.anchorTargets

    if (toBuild.overlapsWith(additionalTargets)
      || (toBuild.isEmpty() && additionalTargets.isNotEmpty())
    ) {
      consumer(additionalTargets)
      return
    }

    if (toBuild.isEmpty()) {
      consumer(emptySet())
      return
    }

    if (!toBuild.isAmbiguous()) {
      consumer(toBuild.targets)
      return
    }

    BuildDependenciesHelperSelectTargetPopup.chooseTargetToBuildFor(
      vf.name,
      toBuild,
      positioner
    ) { consumer(setOf(it)) }
  }

  val workingSetTargetsIfEnabled: Set<Label>
    /**
     * Returns the set of targets affected by files in the current working set if automatic building of the dependencies
     * in the working set is enabled.
     */
    get() = if (QuerySyncSettings.getInstance().buildWorkingSet()) getWorkingSetTargets() else setOf()

  private fun getWorkingSetTargets(): Set<Label> {
    return try {
      getAffectedTargetsForPaths(this.workingSet)
    }
    catch (be: BuildException) {
      syncManager.notifyWarning(
        "Could not obtain working set",
        "Error trying to obtain working set. Not including it in build: $be",
      )
      setOf()
    }
  }
}
