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

import com.android.ide.common.gradle.model.IdeTestOptions
import com.android.ide.common.repository.GradleCoordinate
import com.android.repository.io.FileOpUtils
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.google.common.collect.Range
import com.google.common.collect.RangeMap
import com.google.common.collect.TreeRangeMap
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_REFACTOR_MIGRATE_TO_ANDROIDX
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMigration
import com.intellij.psi.PsiPackage
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.CHANGE_CLASS
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.CHANGE_GRADLE_DEPENDENCY
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.CHANGE_PACKAGE
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.ClassMigrationEntry
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.GradleDependencyMigrationEntry
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.GradleMigrationEntry
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.PackageMigrationEntry
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.UPGRADE_GRADLE_DEPENDENCY_VERSION
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.UpdateGradleDependencyVersionMigrationEntry
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ClassMigrationUsageInfo
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.PackageMigrationUsageInfo
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.idea.codeInsight.KotlinOptimizeImportsRefactoringHelper
import org.jetbrains.kotlin.psi.KtFile

private const val CLASS_MIGRATION_BASE_PRIORITY = 1_000_000
private const val PACKAGE_MIGRATION_BASE_PRIORITY = 1_000
private const val DEFAULT_MIGRATION_BASE_PRIORITY = 0

private val log = logger<MigrateToAndroidxProcessor>()

private fun isImportElement(element: PsiElement?): Boolean =
  element != null && (element.node?.elementType.toString() == "IMPORT_LIST" || isImportElement(element.parent))

/**
 * Returns the latest available version for the given `AppCompatMigrationEntry.GradleMigrationEntry`
 */
private fun getLibraryRevision(newGroupName: String, newArtifactName: String, defaultVersion: String): String {
  val revision = RepositoryUrlManager.get().getLibraryRevision(newGroupName,
                                                               newArtifactName, null,
                                                               true,
                                                               FileOpUtils.create())
  if (revision != null) {
    log.debug { "$newGroupName:$newArtifactName will use $revision" }
    return revision
  }
  log.debug { "Unable to find library revision for $newGroupName:$newArtifactName. Using $defaultVersion" }
  return defaultVersion
}

open class MigrateToAndroidxProcessor(val project: Project,
                                      private val migrationMap: List<AppCompatMigrationEntry>,
                                      versionProvider: ((String, String, String) -> String)? = null,
                                      private val callSyncAfterMigration: Boolean = true
) : BaseRefactoringProcessor(project) {
  private val elements: MutableList<PsiElement> = ArrayList()
  private val refsToShorten: MutableList<SmartPsiElementPointer<PsiElement>> = ArrayList()
  private val versionProvider = versionProvider ?: ::getLibraryRevision

  final override fun createUsageViewDescriptor(usages: Array<out UsageInfo>) = MigrateToAndroidxUsageViewDescriptor(elements.toTypedArray())

  private fun startMigration(project: Project): PsiMigration {
    finishFindMigration()
    val migration = PsiMigrationManager.getInstance(project).startMigration()
    for (entry in migrationMap) {
      when (entry) {
        is PackageMigrationEntry -> {
          findOrCreatePackage(project, migration, entry.myNewName)
          findOrCreatePackage(project, migration, entry.myOldName)
        }
        is ClassMigrationEntry -> {
          findOrCreateClass(project, migration, entry.myNewName)
          findOrCreateClass(project, migration, entry.myOldName)
        }
      }
    }
    return migration
  }

  /**
   * Workaround for b/113514500
   * This avoids the [KotlinOptimizeImportsRefactoringHelper] kicking in for androidx refactorings.
   */
  private class KotlinFileWrapper(val delegate: UsageInfo) : UsageInfo(delegate.smartPointer,
                                                                       delegate.psiFileRange,
                                                                       delegate.isDynamicUsage,
                                                                       delegate.isNonCodeUsage) {
    /**
     * Verifies if one of the calls on the stack comes from the [KotlinOptimizeImportsRefactoringHelper].
     * We check the last 5 elements to allow for some future flow changes.
     */
    private fun isKotlinOptimizerCall(): Boolean = Thread.currentThread().stackTrace
      .take(5)
      .map { it.className }
      .any { KotlinOptimizeImportsRefactoringHelper::class.qualifiedName == it }

    override fun getFile(): PsiFile? = if (isKotlinOptimizerCall()) {
      null
    }
    else {
      super.getFile()
    }
  }

  override fun execute(usages: Array<out UsageInfo>) {
    // As part of the workaround for b/113514500 we wrap any element from KtFile into a KotlinFileWrapper. This allows
    // for disabling the KotlinOptimizeImportsRefactoringHelper.
    // Once the code that decides the helpers to apply has run, we unwrap all the wrapped instances on the doRefactor method.
    val wrapped = usages.map {
      if (it.file is KtFile) KotlinFileWrapper(it) else it
    }.toTypedArray()
    super.execute(wrapped)
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
      val gradleDependencyEntries = mutableMapOf<com.intellij.openapi.util.Pair<String, String>, GradleMigrationEntry>()
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
            val packageUsages = MigrateToAppCompatUtil.findPackageUsages(project, pkgEntry.myOldName)
            val infos = mutableListOf<UsageInfo>()
            packageUsages
              .filter(generatedCodeUsages)
              .map { PackageMigrationUsageInfo(it, pkgEntry) }
              .toCollection(infos)

            usageAccumulator.addAll(infos)
          }
          CHANGE_GRADLE_DEPENDENCY -> {
            val migrationEntry = entry as GradleDependencyMigrationEntry
            gradleDependencyEntries[migrationEntry.compactKey()] = migrationEntry
          }
          UPGRADE_GRADLE_DEPENDENCY_VERSION -> {
            val migrationEntry = entry as UpdateGradleDependencyVersionMigrationEntry
            gradleDependencyEntries[migrationEntry.compactKey()] = migrationEntry
          }
        }
      }

      usageAccumulator.addAll(findUsagesInBuildFiles(project, gradleDependencyEntries))
    }
    finally {
      ApplicationManager.getApplication().invokeLater(Runnable {
        WriteAction.run<RuntimeException> { run { this.finishFindMigration() } }
      }, myProject.disposed)
    }

    log.debug {
      usageAccumulator.usageInfos.joinToString(separator = "\n")
    }

    return usageAccumulator.usageInfos.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>) {
    val migration = startMigration(project)
    refsToShorten.clear()
    try {
      CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)

      // Add gradle properties to enable the androidx handling
      project.setAndroidxProperties()

      val smartPointerManager = SmartPointerManager.getInstance(myProject)

      usages
        // First, unwrap any KotlinFileWrapper
        .map { (it as? KotlinFileWrapper)?.delegate ?: it }
        .filterIsInstance<MigrateToAppCompatUsageInfo>()
        .sortedByDescending {
          // The refactoring operations need to be done in a specific order to work correctly.
          // We need to refactor the imports first in order to allow shortenReferences to work (since it needs
          // to check that the imports are there).
          // We need to process first the class migrations since they have higher priority. If we don't,
          // the package refactoring would be applied first and then the class would incorrectly be refactored.
          // Then, we need to first process the longest package names so, if there are conflicting refactorings,
          // the most specific one applies.

          var value = when (it) {
            is ClassMigrationUsageInfo -> CLASS_MIGRATION_BASE_PRIORITY
            is PackageMigrationUsageInfo -> PACKAGE_MIGRATION_BASE_PRIORITY + it.mapEntry.myOldName.length
            else -> DEFAULT_MIGRATION_BASE_PRIORITY
          }

          if (isImportElement(it.element)) {
            // This is an import, promote
            value += 1000
          }

          value
        }
        .mapNotNull { it.applyChange(migration) }
        .filter { it.isValid }
        .map { smartPointerManager.createSmartPsiElementPointer(it) }
        .forEach { refsToShorten.add(it) }
    }
    catch (e: IncorrectOperationException) {
      RefactoringUIUtil.processIncorrectOperation(project, e)
    }

    if (callSyncAfterMigration && usages.any { it is MigrateToAppCompatUsageInfo.GradleUsageInfo }) {
      // If we modified gradle entries, request sync.
      syncBeforeFinishingRefactoring(myProject, TRIGGER_REFACTOR_MIGRATE_TO_ANDROIDX, null)
    }
  }

  override fun performPsiSpoilingRefactoring() {
    val styleManager = JavaCodeStyleManager.getInstance(myProject)
    for (pointer in refsToShorten) {
      val element = pointer.element ?: continue
      styleManager.shortenClassReferences(element)
    }
    refsToShorten.clear()

    finishFindMigration()
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
  }

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    val filtered = refUsages.get().filter {
      when (it) {
        is ClassMigrationUsageInfo -> {
          return@filter it.mapEntry.myOldName != it.mapEntry.myNewName
        }
        is PackageMigrationUsageInfo -> {
          return@filter it.mapEntry.myOldName != it.mapEntry.myNewName
        }
        else -> true
      }
    }.toTypedArray()

    if (filtered.isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        Messages.showInfoMessage(myProject, JavaRefactoringBundle.message("migration.no.usages.found.in.the.project"),
                                 AndroidBundle.message("android.refactoring.migrateto.androidx"))
      }
      return false
    }
    refUsages.set(filtered)
    isPreviewUsages = true
    return true
  }

  private fun finishFindMigration() {
    PsiMigrationManager.getInstance(project)?.currentMigration?.finish()
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
     * Returns the effective range for the given [UsageInfo]. This is usually the [Segment] of
     * the usage info but in cases like Kotlin package usages, it might a superset of it.
     */
    private fun getInfoUsageRange(info: UsageInfo): Segment? {
      val element = info.element

      if (info is PackageMigrationUsageInfo &&
          element !is PsiPackage &&
          element?.text != info.mapEntry.myOldName &&
          element?.parent?.text == info.mapEntry.myOldName) {
        // This looks like the case of a Kotlin package. In this case, we want to use
        // the range of the full package import, not only the last segment.
        return element.parent.textRange
      }

      return info.smartPointer.psiRange
    }

    /**
     * @param rangeMap A [TreeRangeMap] containing all the accumulated [UsageInfo] arranged
     * by their ranges.
     * @param segment the segment to check for overlaps within the [rangeMap]
     * @return whether the given [segment] overlaps and existing [UsageInfo]
     */
    private fun hasOverlappingUsage(rangeMap: RangeMap<Int, UsageInfo>, segment: Segment?): Boolean {
      val startOffset: Int = segment?.startOffset ?: return false
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
        val segment = getInfoUsageRange(info)
        if (!(checkOverlappingUsages && hasOverlappingUsage(rangeMap, segment))) {
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
                                     gradleDependencyEntries: Map<Pair<String, String>, GradleMigrationEntry>)
    : List<MigrateToAppCompatUsageInfo> {
    val gradleUsages = mutableListOf<MigrateToAppCompatUsageInfo>()
    if (gradleDependencyEntries.isEmpty()) {
      return emptyList()
    }
    val projectBuildModel = ProjectBuildModel.get(project)
    for (module in ModuleManager.getInstance(project).modules) {
      val gradleBuildModel = projectBuildModel.getModuleBuildModel(module) ?: continue

      val dependencies = gradleBuildModel.dependencies()
      for (dep in dependencies.all()) {
        if (dep is ArtifactDependencyModel) {
          val compactDependencyNotation = dep.compactNotation()
          val psiElement = dep.completeModel().resultModel.expressionPsiElement ?: continue
          val gc = GradleCoordinate.parseCoordinateString(compactDependencyNotation) ?: continue
          val key: Pair<String, String> = Pair.create(gc.groupId, gc.artifactId)
          val entry = gradleDependencyEntries[key] ?: continue
          val migrationEntryCoordinates = GradleCoordinate.parseCoordinateString(entry.toCompactNotation(entry.newBaseVersion)) ?: continue
          // Prevent showing the migration entry if there is already a newer version in the file
          if (gc.isSameArtifact(migrationEntryCoordinates) &&
              GradleCoordinate.COMPARE_PLUS_HIGHER.compare(migrationEntryCoordinates, gc) <= 0) {
            continue
          }
          gradleUsages.add(
            MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo(psiElement, projectBuildModel, dep, entry, versionProvider))
        }
      }

      fun addStringUsage(model: GradlePropertyModel, oldString: String, newString: String) {
        val psiElement = model.psiElement ?: return
        if (model.getValue(GradlePropertyModel.STRING_TYPE) == oldString) {
          model.setValue(newString)
          gradleUsages.add(MigrateToAppCompatUsageInfo.GradleStringUsageInfo(psiElement, newString, gradleBuildModel))
        }
        else {
          // Here the lookup will go through all the children psiElements
          for (literal in PsiTreeUtil.findChildrenOfType(psiElement, PsiElement::class.java).filter { it.text == oldString }) {
            gradleUsages.add(MigrateToAppCompatUsageInfo.GradleStringUsageInfo(literal, newString, gradleBuildModel))
          }
        }
      }

      val androidBlock = gradleBuildModel.android() ?: continue
      for (flavorBlock in androidBlock.productFlavors() + androidBlock.defaultConfig()) {
        val runnerModel = flavorBlock.testInstrumentationRunner().resultModel
        val runnerName = runnerModel.getValue(GradlePropertyModel.STRING_TYPE) ?: continue
        val newRunnerName = AndroidxNameUtils.getNewName(runnerName)
        if (newRunnerName != runnerName) {
          addStringUsage(runnerModel, runnerName, newRunnerName)
        }
      }

      val executionProperty = androidBlock.testOptions().execution()
      val executionValue = executionProperty.getValue(GradlePropertyModel.STRING_TYPE)
      if (executionValue.equals(IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR.name, ignoreCase = true)) {
        addStringUsage(executionProperty, executionValue!!, IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR.name)
      }
    }

    // Add google() repositories
    if (gradleUsages.isNotEmpty()) {
      fun addGoogleRepoUsage(repositoriesModel: RepositoriesModel) {
        if (!repositoriesModel.hasGoogleMavenRepository()) {
          val repositoriesModelPsiElement = repositoriesModel.psiElement
          if (repositoriesModelPsiElement != null) {
            gradleUsages.add(
              MigrateToAppCompatUsageInfo.AddGoogleRepositoryUsageInfo(projectBuildModel, repositoriesModel, repositoriesModelPsiElement))
          }
        }
      }

      for (file: VirtualFile in AddGoogleMavenRepositoryHyperlink.getBuildFileForPlugin(project)) {
        val gradleBuildModel = projectBuildModel.getModuleBuildModel(file)

        addGoogleRepoUsage(gradleBuildModel.buildscript().repositories())
        addGoogleRepoUsage(gradleBuildModel.repositories())
      }
    }

    return gradleUsages
  }
}
