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
package com.google.idea.blaze.qsync

import com.google.idea.blaze.common.Context
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.vcs.WorkspaceFileChange
import com.google.idea.blaze.qsync.query.PackageSet
import com.google.idea.blaze.qsync.query.QuerySummary
import java.nio.file.Path

/**
 * Calculates the set of affected packages based on the project imports & excludes, output from a previous query and a set of modified files
 * in the workspace.
 */
class AffectedPackagesCalculator
private constructor(
  private val context: Context<*>,
  private val projectIncludes: Set<Path>,
  private val projectExcludes: Set<Path>,
  private val lastQuery: QuerySummary,
  private val changedFiles: Set<WorkspaceFileChange>,
) {

  class Builder {
    private var context: Context<*>? = null
    private var projectIncludes: Set<Path> = emptySet()
    private var projectExcludes: Set<Path> = emptySet()
    private var lastQuery: QuerySummary? = null
    private var changedFiles: Set<WorkspaceFileChange> = emptySet()

    fun context(value: Context<*>): Builder = apply { this.context = value }

    fun projectIncludes(value: Set<Path>): Builder = apply { this.projectIncludes = value }

    fun projectExcludes(value: Set<Path>): Builder = apply { this.projectExcludes = value }

    fun lastQuery(value: QuerySummary): Builder = apply { this.lastQuery = value }

    fun changedFiles(value: Set<WorkspaceFileChange>): Builder = apply { this.changedFiles = value }

    fun build(): AffectedPackagesCalculator {
      return AffectedPackagesCalculator(
        context = context ?: error("context is required"),
        projectIncludes = projectIncludes,
        projectExcludes = projectExcludes,
        lastQuery = lastQuery ?: error("lastQuery is required"),
        changedFiles = changedFiles,
      )
    }
  }

  companion object {
    @JvmStatic fun builder(): Builder = Builder()
  }

  fun getAffectedPackages(): AffectedPackages {
    val projectChanges = mutableListOf<WorkspaceFileChange>()
    val nonProjectChanges = mutableListOf<WorkspaceFileChange>()

    for (change in changedFiles) {
      if (isIncludedInProject(change.workspaceRelativePath)) {
        projectChanges.add(change)
      } else {
        nonProjectChanges.add(change)
      }
    }

    if (nonProjectChanges.isNotEmpty()) {
      // TODO should we have some better user messaging here, with the option to perform a full
      //  re-sync?
      context.output(
        PrintOutput.output(
          "Edited %d files outside of your project view, this may cause your project to be" + " out of sync. Files:\n  %s",
          nonProjectChanges.size,
          nonProjectChanges.joinToString("\n  ") { it.workspaceRelativePath.toString() },
        )
      )
    }

    // Find BUILD files that have been directly affected by edits.
    val buildFileChanges = projectChanges.filter { it.workspaceRelativePath.fileName.toString() == "BUILD" }
    val addedPackages = PackageSet.Builder()
    val deletedPackages = PackageSet.Builder()
    val addedOrDeletedPackages = mutableSetOf<Path>()

    val modifiedPackagesResult = mutableSetOf<Path>()
    val deletedPackagesResult = mutableSetOf<Path>()

    if (buildFileChanges.isNotEmpty()) {
      context.output(PrintOutput.log("Edited %d BUILD files", buildFileChanges.size))
      for (c in buildFileChanges) {
        val buildPackage = c.workspaceRelativePath.parent
        if (c.operation != WorkspaceFileChange.Operation.ADD) {
          // modifying/deleting an existing package
          if (!lastQuery.packages.contains(buildPackage)) {
            context.output(
              PrintOutput.log("Modified BUILD file %s not in a known package; your project may be out of sync", c.workspaceRelativePath)
            )
          }
        }
        when (c.operation) {
          WorkspaceFileChange.Operation.ADD -> {
            // Adding a new BUILD files also affects the parent package (if any).
            modifiedPackagesResult.add(buildPackage)
            if (!lastQuery.packages.contains(buildPackage)) {
              addedPackages.add(buildPackage)
            }
            addedOrDeletedPackages.add(buildPackage)
          }
          WorkspaceFileChange.Operation.DELETE -> {
            // Deleting a build package only affects the parent (if any).
            deletedPackagesResult.add(buildPackage)
            deletedPackages.add(buildPackage)
            addedOrDeletedPackages.add(buildPackage)
          }
          WorkspaceFileChange.Operation.MODIFY -> {
            modifiedPackagesResult.add(buildPackage)
          }
          else -> {}
        }
      }
    }

    // Find BUILD files that have been affected by edits to a subinclude (.bzl file)
    val affectedBySubinclude =
      changedFiles
        .asSequence()
        .map { it.workspaceRelativePath }
        .flatMap { path -> lastQuery.reverseSubincludeMap[path].orEmpty().asSequence() }
        .filter { it.endsWith("BUILD") }
        .toList()

    val nonProjectBuildAffectedCount = affectedBySubinclude.count { !isIncludedInProject(it) }
    if (nonProjectBuildAffectedCount > 0) {
      context.output(
        PrintOutput.log(
          "%d BUILD files outside of your project view are affected by changes to their includes; your project may be out of sync",
          nonProjectBuildAffectedCount,
        )
      )
    }

    val projectBuildAffected = affectedBySubinclude.filter { isIncludedInProject(it) }
    if (projectBuildAffected.isNotEmpty()) {
      context.output(PrintOutput.log("%d BUILD files affected by changes to .bzl files they load", projectBuildAffected.size))
      for (buildFile in projectBuildAffected) {
        val buildPackage = buildFile.parent
        if (!lastQuery.packages.contains(buildPackage)) {
          context.output(PrintOutput.log("Affected BUILD file %s not in a known package; your project may be out of sync", buildFile))
        }
        modifiedPackagesResult.add(buildPackage)
      }
    }

    val nonBuildEdits = projectChanges.filter { it.workspaceRelativePath.fileName.toString() != "BUILD" }

    // Calculate the set of effective packages, taking into account added/deleted BUILD files.
    // When processing added/deleted source files, we need to know what package they're in now,
    // rather than at the time of the last query.
    val effectivePackages = lastQuery.packages.addPackages(addedPackages.build()).deletePackages(deletedPackages.build())

    // For packages that were added or deleted, the parent package is also affected (due to blaze
    // globbing rules). Add them to affected packages too:
    addedOrDeletedPackages
      .asSequence()
      .map { effectivePackages.getParentPackage(it) }
      .filter { it.isPresent }
      .map { it.get() }
      .forEach { modifiedPackagesResult.add(it) }

    // Process adds/deletes to non-BUILD files. We don't need to worry about modifications, since
    // they shouldn't effect the build graph structure, and the IDE will pick them up as usual.
    nonBuildEdits
      .asSequence()
      .filter { it.operation != WorkspaceFileChange.Operation.MODIFY }
      .map { it.workspaceRelativePath }
      .map { effectivePackages.findIncludingPackage(it) }
      .filter { it.isPresent }
      .map { it.get() }
      .forEach { modifiedPackagesResult.add(it) }

    // Packages that had errors when we ran the last query may not strictly be affected, but we
    // should re-query them anyway to ensure the errors are visible and handled correctly (unless
    // they have been deleted).
    lastQuery.packagesWithErrors.asSequence().filter { effectivePackages.contains(it) }.forEach { modifiedPackagesResult.add(it) }

    // warn about adds/modifications to files outside of any build package
    val unownedSources =
      nonBuildEdits
        .asSequence()
        .filter { it.operation != WorkspaceFileChange.Operation.DELETE }
        .map { it.workspaceRelativePath }
        .filter { effectivePackages.findIncludingPackage(it).isEmpty }
        .toList()

    if (unownedSources.isNotEmpty()) {
      context.output(
        PrintOutput.output(
          "%d files is not in any known build package. Please check your build rules.\nFiles:\n  %s",
          unownedSources.size,
          unownedSources.joinToString("\n  ") { it.toString() },
        )
      )
    }

    return AffectedPackages(modifiedPackagesResult, deletedPackagesResult)
  }

  private fun isIncludedInProject(file: Path): Boolean {
    for (includePath in projectIncludes) {
      if (file.startsWith(includePath) || includePath.toString().isEmpty()) {
        for (excludePath in projectExcludes) {
          if (file.startsWith(excludePath)) {
            return false
          }
        }
        return true
      }
    }
    return false
  }
}
