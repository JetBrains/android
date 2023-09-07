/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider
import com.android.tools.idea.lint.common.LintBatchResult
import com.android.tools.idea.lint.common.LintIdeRequest
import com.android.tools.idea.lint.common.LintIdeSupport.Companion.get
import com.android.tools.idea.lint.common.LintProblemData
import com.android.tools.idea.res.getFolderType
import com.android.tools.lint.checks.UnusedResourceDetector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.google.common.collect.Lists
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.IncorrectOperationException
import java.io.File
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

class UnusedResourcesProcessor(
  project: Project,
  private val modules: Array<Module>,
  private val filter: String?
) : BaseRefactoringProcessor(project, null) {

  private var elements = PsiElement.EMPTY_ARRAY
  private var includeIds = false
  private var cachedCommandName: String? = null
  private val buildModelMap: MutableMap<PsiElement?, GradleBuildModel?> = mutableMapOf()

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor =
    UnusedResourcesUsageViewDescriptor(elements)

  public override fun findUsages(): Array<UsageInfo> {
    val map = computeUnusedMap()
    elements = computeUnusedDeclarationElements(map).toTypedArray()
    val result = elements.map { UsageInfo(it) }.toTypedArray()
    return UsageViewUtil.removeDuplicatedUsages(result)
  }

  private fun computeUnusedDeclarationElements(
    unusedMap: MutableMap<Issue, Map<File, List<LintProblemData>>>
  ): List<PsiElement> {
    val elements: MutableList<PsiElement> = mutableListOf()

    // Make sure lint didn't put extra issues into the map
    for (issue in Lists.newArrayList(unusedMap.keys)) {
      if (issue !== UnusedResourceDetector.ISSUE && issue !== UnusedResourceDetector.ISSUE_IDS) {
        unusedMap.remove(issue)
      }
    }

    ApplicationManager.getApplication().assertReadAccessAllowed()
    val psiManager = PsiManager.getInstance(myProject)
    val files: MutableMap<File, PsiFile> = mutableMapOf()
    val excludedFiles: MutableSet<PsiFile?> = mutableSetOf()
    for (file in unusedMap.values.flatMap { it.keys }) {
      if (files.containsKey(file)) continue

      val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file) ?: continue

      // Gradle model errors currently don't have source positions
      if (virtualFile.isDirectory) continue

      val psiFile = psiManager.findFile(virtualFile) ?: continue
      files[file] = psiFile

      // See whether the file with the warnings is in module that is not included
      // in this scope. If so, record it into the list of excluded files such that
      // we can skip removing these references later on.
      val module = ModuleUtilCore.findModuleForFile(psiFile) ?: continue
      if (!modules.contains(module)) excludedFiles.add(psiFile)
    }

    // We cannot just skip removing references in modules outside of the scope.
    // If an unused resource is referenced from outside the included scope,
    // then deleting it partially would result in a broken project. Therefore,
    // track which references appear in excluded files, which we'll then later
    // use to also skip removing references in included scopes that are referenced
    // from excluded files.
    val excludedResources: MutableSet<String> = mutableSetOf()
    if (excludedFiles.isNotEmpty()) {
      for ((file, problems) in unusedMap.values.flatMap { fileMap -> fileMap.entries }) {
        val psiFile = files[file]
        if (!excludedFiles.contains(psiFile)) continue

        for (problem in problems) {
          getResource(problem)?.let { excludedResources.add(it) }
        }
      }
    }

    val allowedIssues = setOf(UnusedResourceDetector.ISSUE, UnusedResourceDetector.ISSUE_IDS)
    for ((issue, fileListMap) in unusedMap) {
      if (issue !in allowedIssues || fileListMap.isEmpty() || files.isEmpty()) continue

      for ((file, psiFile) in files) {
        if (
          excludedFiles.contains(psiFile) ||
            !CommonRefactoringUtil.checkReadOnlyStatus(myProject, psiFile)
        ) {
          continue
        }

        val problems = fileListMap[file] ?: continue

        if (psiFile.fileType.isBinary) {
          // Delete the whole file
          if (matchesFilter(problems)) {
            elements.add(psiFile)
          }
        } else {
          when (getFolderType(psiFile)) {
            null -> {
              // Not found in a resource folder. This happens for example for
              // matches in build.gradle.
              //
              // Attempt to find the resource in the build file. If we can't,
              // we'll ignore this resource (it would be dangerous to just delete the
              // file; see for example http://b.android.com/220069.)
              if (
                (psiFile is GroovyFile || psiFile is KtFile) &&
                  (psiFile.name.endsWith(SdkConstants.EXT_GRADLE) ||
                    psiFile.name.endsWith(SdkConstants.EXT_GRADLE_KTS))
              ) {
                val gradleBuildModel =
                  GradleModelProvider.getInstance().parseBuildFile(psiFile.virtualFile, myProject)

                // Get all the resValue declared within the android block.
                val androidElement = gradleBuildModel.android()
                val resValues = androidElement.defaultConfig().resValues()
                resValues.addAll(androidElement.productFlavors().flatMap { it.resValues() })
                resValues.addAll(androidElement.buildTypes().flatMap { it.resValues() })

                for (resValue in resValues) {
                  val typeString = resValue.type()
                  val nameString = resValue.name()
                  // See if this is one of the unused resources
                  for (problem in fileListMap[VfsUtilCore.virtualToIoFile(psiFile.virtualFile)]!!) {
                    val unusedResource = getResource(problem)
                    if (
                      unusedResource != null &&
                        unusedResource == "${SdkConstants.R_PREFIX}$typeString.$nameString"
                    ) {
                      resValue.getModel().getPsiElement()?.let { psiElement ->
                        elements.add(psiElement)
                        // Keep track of the current buildModel to apply refactoring later on.
                        buildModelMap[psiElement] = gradleBuildModel
                        resValue.remove()
                      }
                    }
                  }
                }
              }
            }
            ResourceFolderType.VALUES -> {
              addElementsInFile(elements, psiFile, problems, excludedResources)
            }
            else -> {
              // Make sure it's not an unused id declaration in a layout/menu/etc file that's
              // also being deleted as unused
              if (issue === UnusedResourceDetector.ISSUE_IDS) {
                // The current `fileListMap` contains those identified as having unused ids. Get the
                // other list containing resources, which could contain the layout/menu/etc file
                // containing this id.
                val issueMap = unusedMap[UnusedResourceDetector.ISSUE]
                if (issueMap?.containsKey(file) == true) {
                  // Skip the current id since it's containing file will be deleted.
                  continue
                }

                // Delete ranges within the file
                addElementsInFile(elements, psiFile, problems, excludedResources)
              } else {
                // Unused non-value resource file: Delete the whole file
                if (matchesFilter(problems)) elements.add(psiFile)
              }
            }
          }
        }
      }
    }

    return elements
  }

  private fun addElementsInFile(
    elements: MutableList<PsiElement>,
    psiFile: PsiFile,
    problems: List<LintProblemData>,
    excludedResources: Set<String>
  ) {
    // Delete all the resources in the given file
    if (psiFile !is XmlFile || !psiFile.isValid()) return

    val starts =
      problems
        .filter { p -> !excludedResources.contains(getResource(p)) && matchesFilter(p) }
        .map { p -> p.textRange.startOffset }
        .sortedDescending()

    for (offset in starts) {
      val attribute =
        PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, XmlAttribute::class.java, false)

      val remove =
        when {
          attribute == null ->
            PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, XmlTag::class.java, false)
          SdkConstants.ATTR_ID != attribute.localName ->
            // If deleting a resource, delete the whole resource element, except for attribute
            // android:id="" declarations
            // where we remove the attribute, not the tag
            PsiTreeUtil.getParentOfType(attribute, XmlTag::class.java)
          else -> attribute
        }

      remove?.let { elements.add(it) }
    }
  }

  private fun computeUnusedMap(): MutableMap<Issue, Map<File, List<LintProblemData>>> {
    val map: MutableMap<Issue, Map<File, List<LintProblemData>>> = mutableMapOf()
    val issues =
      if (includeIds) setOf(UnusedResourceDetector.ISSUE, UnusedResourceDetector.ISSUE_IDS)
      else setOf(UnusedResourceDetector.ISSUE)

    val scope = AnalysisScope(myProject)

    val unusedWasEnabled = UnusedResourceDetector.ISSUE.isEnabledByDefault()
    val unusedIdsWasEnabled = UnusedResourceDetector.ISSUE_IDS.isEnabledByDefault()
    UnusedResourceDetector.ISSUE.setEnabledByDefault(true)
    UnusedResourceDetector.ISSUE_IDS.setEnabledByDefault(includeIds)

    try {
      val lintResult = LintBatchResult(myProject, map, scope, issues)
      val client = get().createBatchClient(lintResult)
      // Note: We pass in *all* modules in the project here, not just those in the scope of the
      // resource refactoring. If you for example are running the unused resource refactoring on a
      // library module, we want to only remove unused resources from the specific library
      // module, but we still have to have lint analyze all modules such that it doesn't consider
      // resources in the library as unused when they could be referenced from other modules.
      // So, we'll analyze all modules with lint, and then in the UnusedResourceProcessor
      // we'll filter the matches down to only those in the target modules when we're done.
      val modules = ModuleManager.getInstance(myProject).modules.toList()
      val request =
        LintIdeRequest(client, myProject, null, modules, false).apply { setScope(Scope.ALL) }

      // Make sure we don't remove resources that are still referenced from
      // tests (though these should probably be in a test resource source
      // set instead.)
      val lint =
        client.createDriver(request, get().getIssueRegistry()).apply { checkTestSources = true }
      lint.analyze()
    } finally {
      UnusedResourceDetector.ISSUE.setEnabledByDefault(unusedWasEnabled)
      UnusedResourceDetector.ISSUE_IDS.setEnabledByDefault(unusedIdsWasEnabled)
    }

    return map
  }

  private fun matchesFilter(problems: List<LintProblemData>): Boolean {
    if (filter == null) return true
    return problems.any { filter == getResource(it) }
  }

  private fun matchesFilter(problem: LintProblemData) =
    filter == null || filter == getResource(problem)

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>) = true

  override fun refreshElements(elements: Array<PsiElement>) {
    System.arraycopy(elements, 0, this.elements, 0, elements.size)
  }

  override fun getBeforeData() = RefactoringEventData().apply { addElements(elements) }

  override fun getRefactoringId() = "refactoring.unused.resources"

  override fun performRefactoring(usages: Array<UsageInfo>) {
    try {
      for (element in usages.mapNotNull(UsageInfo::getElement).filter { it.isValid }) {
        val buildModel = buildModelMap[element]
        if (buildModel?.isModified() == true) {
          WriteCommandAction.runWriteCommandAction(myProject) { buildModel.applyChanges() }
        } else {
          element.delete()
        }
      }
    } catch (e: IncorrectOperationException) {
      RefactoringUIUtil.processIncorrectOperation(myProject, e)
    }
  }

  private fun calcCommandName() =
    "Deleting " + RefactoringUIUtil.calculatePsiElementDescriptionList(elements)

  override fun getCommandName(): String {
    if (cachedCommandName == null) {
      cachedCommandName = calcCommandName()
    }
    return requireNotNull(cachedCommandName)
  }

  override fun skipNonCodeUsages() = true

  fun setIncludeIds(includeIds: Boolean) {
    this.includeIds = includeIds
  }

  override fun isToBeChanged(usageInfo: UsageInfo): Boolean {
    if (
      ApplicationManager.getApplication().isUnitTestMode &&
        usageInfo.element?.text?.contains("AUTO-EXCLUDE") == true
    ) {
      // Automatically exclude/deselect elements that contain the string "AUTO-EXCLUDE".
      // This is our simple way to unit test the UI operation of users deselecting certain
      // elements in the refactoring UI.
      return false
    }

    return super.isToBeChanged(usageInfo)
  }

  companion object {
    private fun getResource(problem: LintProblemData) =
      LintFix.getString(problem.quickfixData, UnusedResourceDetector.KEY_RESOURCE_FIELD, null)
  }
}
