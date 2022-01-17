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
package com.android.tools.idea.gradle.variant.conflict

import com.android.tools.idea.gradle.model.IdeModuleDependency
import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.gradle.project.sync.messages.GroupNames
import com.android.tools.idea.gradle.variant.view.BuildVariantView
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.project.messages.MessageType
import com.android.tools.idea.project.messages.SyncMessage
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Set of all variant-selection-related conflicts.There are two reasons for conflicts to happen:
 *
 * 1. **Selection conflicts.** These conflicts occur when module A depends on module B/variant X but module B has variant Y selected
 * instead and no other modules depend on B/variant Y.These conflicts can be easily fixed by selecting the right variant in the "Build
 * Variants" tool window.
 *
 * 2. **Structure conflicts.** These conflicts occur when there are multiple modules depending on different variants of a single module.
 * For example, module A depends on module E/variant X, module B depends on module E/variant Y and module C depends on module E/variant Z.
 * These conflicts cannot be resolved through the "Build Variants" tool window because regardless of the variant is selected on module E,
 * we will always have a selection conflict. These conflicts need to be resolved in build configuration, but the user can still adjust the
 * selection to their needs.
 */
class ConflictSet private constructor(
  val project: Project,
  val selectionConflicts: List<Conflict>
) {
  /**
   * Shows the "variant selection" conflicts in the "Build Variant" and "Messages" windows.
   */
  fun showSelectionConflicts() {
    val messages = GradleSyncMessages.getInstance(project)
    for (conflict in selectionConflicts) {
      // Creates the "Select in 'Build Variants' window" hyperlink.
      val source = conflict.source
      val hyperlinkText = String.format("Select '%1\$s' in \"Build Variants\" window", source.name)
      val selectInBuildVariantsWindowHyperlink: NotificationHyperlink =
        object : NotificationHyperlink("select.conflict.in.variants.window", hyperlinkText) {
          override fun execute(project: Project) = BuildVariantView.getInstance(project).findAndSelect(source)
        }

      // Creates the "Fix problem" hyperlink.
      val quickFixHyperlink: NotificationHyperlink = object : NotificationHyperlink("fix.conflict", "Fix problem") {
        override fun execute(project: Project) {
          val solved = ConflictResolution.solveSelectionConflict(conflict)
          if (solved) {
            val conflicts = findConflicts(project)
            conflicts.showSelectionConflicts()
          }
        }
      }
      val msg = SyncMessage(GroupNames.VARIANT_SELECTION_CONFLICTS, MessageType.WARNING, conflict.toString())
      msg.add(selectInBuildVariantsWindowHyperlink)
      msg.add(quickFixHyperlink)
      messages.report(msg)
    }
    ApplicationManager.getApplication().invokeLater {
      if (!project.isDisposed) {
        BuildVariantView.getInstance(project).updateContents(selectionConflicts)
      }
    }
  }

  companion object {
    private class RawConflict(val sourceModule: Module, val selectedVariant: String, val targetModule: Module, val expectedVariant: String)

    @JvmStatic
    fun findConflicts(project: Project): ConflictSet {
      val androidHolderModules = project.getAndroidFacets().map { it.holderModule }

      val modulesByPath = androidHolderModules
        .asSequence()
        .mapNotNull {
          val gradleProjectPath = it.getGradleProjectPath() ?: return@mapNotNull null
          gradleProjectPath to it
        }
        .toMap()

      val selectedVariants =
        androidHolderModules
          .asSequence()
          .mapNotNull { module ->
            val model = GradleAndroidModel.get(module) ?: return@mapNotNull null
            module to model.selectedVariantName
          }
          .toMap()

      val conflicts =
        androidHolderModules
          .asSequence()
          .flatMap { module ->
            GradleAndroidModel.get(module)
              ?.getModuleDependencies().orEmpty()
              .mapNotNull {
                val targetVariant = it.variant?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val targetModule =
                  modulesByPath[it.getGradleProjectPath().copy(sourceSet = IdeModuleSourceSet.MAIN)] ?: return@mapNotNull null
                val selectedVariant = selectedVariants[targetModule] ?: return@mapNotNull null
                if (selectedVariant == targetVariant) null
                else RawConflict(module, selectedVariant, targetModule, targetVariant)
              }
          }


      val selectionConflicts = mutableMapOf<String, Conflict>()
      conflicts.forEach { conflict ->
        selectionConflicts.addConflict(conflict.targetModule, conflict.selectedVariant, conflict.sourceModule, conflict.expectedVariant)
      }
      return ConflictSet(project, ImmutableList.copyOf(selectionConflicts.values))
    }

    private fun MutableMap<String, Conflict>.addConflict(
      source: Module,
      selectedVariant: String,
      affected: Module,
      expectedVariant: String
    ) {
      val causeName = source.name
      val conflict = computeIfAbsent(causeName) { k: String? -> Conflict(source, selectedVariant) }
      conflict.addAffectedModule(affected, expectedVariant)
    }

    private fun GradleAndroidModel.getModuleDependencies(): Sequence<IdeModuleDependency> {
      val variant = selectedVariant
      return variant.mainArtifact.level2Dependencies.moduleDependencies.asSequence() +
        variant.androidTestArtifact?.level2Dependencies?.moduleDependencies?.asSequence().orEmpty() +
        variant.testFixturesArtifact?.level2Dependencies?.moduleDependencies?.asSequence().orEmpty() +
        variant.unitTestArtifact?.level2Dependencies?.moduleDependencies?.asSequence().orEmpty()
    }
  }
}