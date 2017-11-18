/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.refactoring

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.google.common.collect.Range
import com.google.common.collect.RangeMap
import com.google.common.collect.TreeRangeMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.*
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ClassMigrationUsageInfo
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.PackageMigrationUsageInfo
import org.jetbrains.android.util.AndroidBundle

class MigrateToAndroidxProcessor(val project: Project,
                                 private val migrationMap: List<AppCompatMigrationEntry>) : BaseRefactoringProcessor(project) {

  private val elements: MutableList<PsiElement> = ArrayList()
  private var psiMigration: PsiMigration? = startMigration(project)
  private val refsToShorten: MutableList<SmartPsiElementPointer<PsiElement>> = ArrayList()

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>) = MigrateToAndroidxUsageViewDescriptor(elements.toTypedArray())

  private fun startMigration(project: Project): PsiMigration {
    val migration = PsiMigrationManager.getInstance(project).startMigration()
    for (entry in migrationMap) {
      when (entry) {
        is PackageMigrationEntry -> MigrateToAppCompatUtil.findOrCreatePackage(project, migration, entry.myOldName)
        is ClassMigrationEntry -> MigrateToAppCompatUtil.findOrCreateClass(project, migration, entry.myOldName)
      }
    }
    return migration
  }

  override fun findUsages(): Array<UsageInfo> {

    val usageAccumulator = UsageAccumulator()

    // Filter out all generated code usages. We don't want generated code to come up in findUsages.
    val generatedCodeUsages: (UsageInfo) -> Boolean = { usage ->
      usage.virtualFile?.let {
        !GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(it, project)
      } != false
    }

    try {
      psiMigration?.let { migration ->
        val gradleDependencyEntries = mutableMapOf<com.intellij.openapi.util.Pair<String, String>, GradleDependencyMigrationEntry>()
        for (entry in migrationMap) {
          when (entry.type) {
            CHANGE_CLASS -> {
              val clsEntry = entry as ClassMigrationEntry
              val classUsages = MigrateToAppCompatUtil.findClassUsages(project, clsEntry.myOldName)
              val infos = mutableListOf<UsageInfo>()
              classUsages
                  .filter(generatedCodeUsages)
                  .map { ClassMigrationUsageInfo(it, clsEntry) }
                  .toCollection(infos)

              usageAccumulator.addAll(infos)
            }
            CHANGE_PACKAGE -> {
              val pkgEntry = entry as PackageMigrationEntry
              val packageUsages = MigrateToAppCompatUtil.findPackageUsages(project, migration, pkgEntry.myOldName)
              val infos = mutableListOf<UsageInfo>()
              packageUsages
                  .filter(generatedCodeUsages)
                  .map { PackageMigrationUsageInfo(it, pkgEntry) }
                  .toCollection(infos)

              usageAccumulator.addAll(infos)
            }
            CHANGE_GRADLE_DEPENDENCY -> {
              val migrationEntry = entry as GradleDependencyMigrationEntry
              gradleDependencyEntries.put(migrationEntry.compactKey(), migrationEntry)
            }
          }
        }

        usageAccumulator.addAll(findUsagesInBuildFiles(project, gradleDependencyEntries))
      }
    }
    finally {
      ApplicationManager.getApplication().invokeLater(Runnable {
        WriteAction.run<RuntimeException> { run { this.finishFindMigration() } }
      }, myProject.disposed)
    }

    return usageAccumulator.usageInfos.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    finishFindMigration()
    val migration = startMigration(project)
    refsToShorten.clear()
    try {
      CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
      val smartPointerManager = SmartPointerManager.getInstance(myProject)
      for (usage in usages) {
        val psiElement = (usage as? MigrateToAppCompatUsageInfo)?.applyChange(migration)
        if (psiElement != null) {
          refsToShorten.add(smartPointerManager.createSmartPsiElementPointer(psiElement))
        }
      }
    }
    catch (e: IncorrectOperationException) {
      RefactoringUIUtil.processIncorrectOperation(project, e)
    }
    finally {
      migration.finish()
    }
  }

  override fun performPsiSpoilingRefactoring() {
    val styleManager = JavaCodeStyleManager.getInstance(myProject)
    for (pointer in refsToShorten) {
      val element = pointer.element ?: continue
      styleManager.shortenClassReferences(element)
    }
    refsToShorten.clear()
  }

  override fun getCommandName(): String = AndroidBundle.message("android.refactoring.migrateto.androidx")

  override fun getBeforeData() = RefactoringEventData().apply {
    addElements(elements)
  }

  override fun getAfterData(usages: Array<UsageInfo>) = RefactoringEventData().apply {
    addElements(elements)
  }

  override fun skipNonCodeUsages() = true

  override fun refreshElements(elements: Array<PsiElement>) {
    psiMigration = startMigration(myProject)
  }

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    if (refUsages.get().isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        Messages.showInfoMessage(myProject, RefactoringBundle.message("migration.no.usages.found.in.the.project"),
            AndroidBundle.message("android.refactoring.migrateto.androidx"))
      }
      return false
    }
    isPreviewUsages = true
    return true
  }

  private fun finishFindMigration() {
    if (psiMigration != null) {
      psiMigration?.finish()
      psiMigration = null
    }
  }

  /**
   * Accumulate usages ensuring that usages with overlapping ranges are removed.
   * A mapping between the containing file and a tree of ranges is maintained.
   * The [RangeMap] lets us quickly check if a startOffset already exists/overlaps
   * an existing [UsageInfo]
   */
  class UsageAccumulator {
    val usageInfos = mutableListOf<UsageInfo>()
    private val fileToRangeMap = mutableMapOf<PsiFile, RangeMap<Int, UsageInfo>>()

    /**
     * @param rangeMap A [TreeRangeMap] containing all the accumulated [UsageInfo] arranged
     * by their ranges.
     * @param info to check for overlapping ranges within the [rangeMap]
     * @return whether the given [info] overlaps and existing [UsageInfo]
     */
    private fun hasOverlappingUsage(rangeMap: RangeMap<Int, UsageInfo>, info: UsageInfo): Boolean {
      val segment = info.smartPointer.psiRange ?: return false
      val startOffset: Int = segment.startOffset
      return rangeMap.get(startOffset) != null
    }

    /**
     * Adds [infos] to this instance after ensuring that each [UsageInfo] does not
     * overlap an existing usageInfo.
     * @param infos List of infos to add
     * @param checkOverlappingUsages whether to check for overlapping ranges
     */
    fun addAll(infos: List<UsageInfo>, checkOverlappingUsages: Boolean = true) {
      for (info in infos) {
        val containingFile = info.smartPointer.containingFile ?: continue
        val rangeMap = fileToRangeMap.getOrDefault(containingFile, TreeRangeMap.create())
        if (!(checkOverlappingUsages && hasOverlappingUsage(rangeMap, info))) {
          val segment = info.smartPointer.psiRange
          segment?.let {
            rangeMap.put(Range.closed(it.startOffset, it.endOffset), info)
          }
          fileToRangeMap[containingFile] = rangeMap
          usageInfos.add(info)
        }
      }
    }
  }

  private fun findUsagesInBuildFiles(project: Project,
                                     gradleDependencyEntries: Map<Pair<String, String>, GradleDependencyMigrationEntry>)
      : List<UsageInfo> {
    val gradleUsages = mutableListOf<UsageInfo>()
    if (gradleDependencyEntries.isEmpty()) {
      return emptyList()
    }
    for (module in ModuleManager.getInstance(project).modules) {
      val dependencies = GradleBuildModel.get(module)?.dependencies() ?: continue
      for (dep in dependencies.all()) {
        if (dep is ArtifactDependencyModel) {
          val compactDependencyNotation = dep.compactNotation().value()
          val psiElement = dep.compactNotation().psiElement ?: continue
          val gc = GradleCoordinate.parseCoordinateString(compactDependencyNotation) ?: continue
          val key: Pair<String, String> = Pair.create(gc.groupId, gc.artifactId)
          val entry = gradleDependencyEntries[key] ?: continue
          gradleUsages.add(MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo(psiElement, entry))
        }
      }
    }
    return gradleUsages
  }
}
