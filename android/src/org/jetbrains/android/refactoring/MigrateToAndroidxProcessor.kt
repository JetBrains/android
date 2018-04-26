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

import com.android.SdkConstants.FN_GRADLE_PROPERTIES
import com.android.builder.model.TestOptions.Execution
import com.android.ide.common.repository.GradleCoordinate
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.google.common.collect.Range
import com.google.common.collect.RangeMap
import com.google.common.collect.TreeRangeMap
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.RefactoringHelper
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.*
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ClassMigrationUsageInfo
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.PackageMigrationUsageInfo
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import java.io.File

private const val CLASS_MIGRATION_BASE_PRIORITY = 1_000_000
private const val PACKAGE_MIGRATION_BASE_PRIORITY = 1_000
private const val DEFAULT_MIGRATION_BASE_PRIORITY = 0

/**
 * Returns a [PropertiesFile] instance for the `gradle.properties` file in the given project or null if it does not exist.
 */
private fun getProjectProperties(project: Project): PropertiesFile? {
  val gradlePropertiesFile = findFileByIoFile(File(FileUtil.toCanonicalPath(project.basePath), FN_GRADLE_PROPERTIES), true)
  val psiPropertiesFile = PsiManager.getInstance(project).findFile(gradlePropertiesFile ?: return null)

  return if (psiPropertiesFile is PropertiesFile) psiPropertiesFile else null
}

private fun isImportElement(element: PsiElement?): Boolean =
  element != null && (element.node?.elementType.toString() == "IMPORT_LIST" || isImportElement(element.parent))

private const val USE_ANDROIDX_PROPERTY = "android.useAndroidX"
private const val ENABLE_JETIFIER_PROPERTY = "android.enableJetifier"

open class MigrateToAndroidxProcessor(val project: Project,
                                 private val migrationMap: List<AppCompatMigrationEntry>) : BaseRefactoringProcessor(project) {

  private val elements: MutableList<PsiElement> = ArrayList()
  private var psiMigration: PsiMigration? = startMigration(project)
  private val refsToShorten: MutableList<SmartPsiElementPointer<PsiElement>> = ArrayList()

  final override fun createUsageViewDescriptor(usages: Array<out UsageInfo>) = MigrateToAndroidxUsageViewDescriptor(elements.toTypedArray())

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
              gradleDependencyEntries[migrationEntry.compactKey()] = migrationEntry
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

      // Add gradle properties to enable the androidx handling
      getProjectProperties(project)?.let {
        it.findPropertyByKey(USE_ANDROIDX_PROPERTY) ?: it.addProperty(USE_ANDROIDX_PROPERTY, "true")
        it.findPropertyByKey(ENABLE_JETIFIER_PROPERTY) ?: it.addProperty(ENABLE_JETIFIER_PROPERTY, "true")
      }

      val smartPointerManager = SmartPointerManager.getInstance(myProject)

      usages
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
    finally {
      migration.finish()
    }
  }

  /**
   * We do not want to apply [com.intellij.refactoring.OptimizeImportsRefactoringHelper] since the project might be broken after changing
   * the imports.
   *
   * This is a workaround for http://b/79220682.
   */
  override fun shouldApplyRefactoringHelper(key: RefactoringHelper<*>): Boolean = false

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
    val projectBuildModel = ProjectBuildModel.get(project)
    for (module in ModuleManager.getInstance(project).modules) {
      val gradleBuildModel = projectBuildModel.getModuleBuildModel(module) ?: continue

      val dependencies = gradleBuildModel.dependencies()
      for (dep in dependencies.all()) {
        if (dep is ArtifactDependencyModel) {
          val compactDependencyNotation = dep.compactNotation()
          val psiElement = dep.psiElement ?: continue
          val gc = GradleCoordinate.parseCoordinateString(compactDependencyNotation) ?: continue
          val key: Pair<String, String> = Pair.create(gc.groupId, gc.artifactId)
          val entry = gradleDependencyEntries[key] ?: continue
          gradleUsages.add(MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo(psiElement, entry))
        }
      }

      fun addStringUsage(model: GradlePropertyModel, oldString: String, newString: String) {
        val psiElement = model.psiElement ?: return
        for (literal in PsiTreeUtil.findChildrenOfType(psiElement, GrLiteral::class.java).filter { it.value == oldString }) {
          gradleUsages.add(MigrateToAppCompatUsageInfo.GradleStringUsageInfo(literal, newString))
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
      if (executionValue.equals(Execution.ANDROID_TEST_ORCHESTRATOR.name, ignoreCase = true)) {
        addStringUsage(executionProperty, executionValue!!, Execution.ANDROIDX_TEST_ORCHESTRATOR.name)
      }
    }

    return gradleUsages
  }

}
