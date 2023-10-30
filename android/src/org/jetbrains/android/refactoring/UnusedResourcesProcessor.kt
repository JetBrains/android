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
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
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
import org.jetbrains.kotlin.ir.types.impl.IrErrorClassImpl.startOffset
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import java.io.File

class UnusedResourcesProcessor(project: Project, filter: Filter? = null) :
  BaseRefactoringProcessor(project, null) {

  interface Filter {
    fun shouldProcessFile(psiFile: PsiFile): Boolean
    fun shouldProcessResource(resource: String?): Boolean
  }

  private object AllFilter : Filter {
    override fun shouldProcessFile(psiFile: PsiFile) = true
    override fun shouldProcessResource(resource: String?) = true
  }

  class FileFilter private constructor(
    private val files: Set<PsiFile>,
    private val directories: Set<PsiDirectory>) : Filter {

    override fun shouldProcessFile(psiFile: PsiFile): Boolean {
      if (psiFile in files) return true

      if (directories.isEmpty()) return false

      var dir = psiFile.containingDirectory
      while (dir != null) {
        if (dir in directories) return true
        dir = dir.parentDirectory
      }

      return false
    }

    override fun shouldProcessResource(resource: String?) = true

    companion object {
      @JvmStatic
      fun from(elements: Collection<PsiElement>) : FileFilter {
        val files = elements.mapNotNull { it.containingFile }.toSet()
        val dirs = elements.mapNotNull { it as? PsiDirectory }.toSet()

        return FileFilter(files, dirs)
      }
    }
  }

  private var elements = PsiElement.EMPTY_ARRAY
  var includeIds = false
  private val buildModelMap: MutableMap<PsiElement, GradleBuildModel> = mutableMapOf()
  private val filter = filter ?: AllFilter

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor =
    UnusedResourcesUsageViewDescriptor(elements)

  public override fun findUsages(): Array<UsageInfo> {
    elements = computeUnusedDeclarationElements()
    val result = elements.map { UsageInfo(it) }.toTypedArray()
    return UsageViewUtil.removeDuplicatedUsages(result)
  }

  private fun computeUnusedDeclarationElements(): Array<PsiElement> {
    val unusedMap = computeUnusedMap()
    val unusedElements: MutableList<PsiElement> = mutableListOf()

    ApplicationManager.getApplication().assertReadAccessAllowed()

    val psiManager = PsiManager.getInstance(myProject)

    val localFileSystem = LocalFileSystem.getInstance()
    val files =
      unusedMap.values
        .flatMap { value -> value.keys }
        .distinct()
        .associateWith { javaFile ->
          localFileSystem
            .findFileByIoFile(javaFile)
            ?.takeUnless(VirtualFile::isDirectory)
            ?.let(psiManager::findFile)
        }
        .mapNotNull { (javaFile, psiFile) -> psiFile?.let { javaFile to it } }
        .toMap()

    val excludedFiles = files.values.filter { !filter.shouldProcessFile(it) }

    // We cannot just skip removing references in modules outside of the scope.
    // If an unused resource is referenced from outside the included scope,
    // then deleting it partially would result in a broken project. Therefore,
    // track which references appear in excluded files, which we'll then later
    // use to also skip removing references in included scopes that are referenced
    // from excluded files.
    val excludedResources =
      unusedMap.values
        .flatMap { fileMap -> fileMap.entries }
        .filter { (file, _) -> excludedFiles.contains(files[file]) }
        .flatMap { (_, problems) -> problems.mapNotNull { problem -> getResource(problem) } }
        .toSet()

    for ((issue, fileListMap) in unusedMap) {
      if (fileListMap.isEmpty() || files.isEmpty()) continue

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
          if (problems.any { filter.shouldProcessResource(getResource(it)) }) {
            unusedElements.add(psiFile)
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
                val resValues = buildList {
                  addAll(androidElement.defaultConfig().resValues())
                  addAll(androidElement.productFlavors().flatMap { it.resValues() })
                  addAll(androidElement.buildTypes().flatMap { it.resValues() })
                }

                for (resValue in resValues) {
                  val psiElement = resValue.getModel().getPsiElement() ?: continue

                  // See if this is one of the unused resources
                  val expectedResourceName =
                    "${SdkConstants.R_PREFIX}${resValue.type()}.${resValue.name()}"
                  val problemList =
                    requireNotNull(fileListMap[VfsUtilCore.virtualToIoFile(psiFile.virtualFile)])
                  for (problem in problemList) {
                    if (getResource(problem) == expectedResourceName) {
                      unusedElements.add(psiElement)
                      // Keep track of the current buildModel to apply refactoring later on.
                      buildModelMap[psiElement] = gradleBuildModel
                      resValue.remove()
                    }
                  }
                }
              }
            }
            ResourceFolderType.VALUES -> {
              unusedElements.addAll(getElementsInFile(psiFile, problems, excludedResources))
            }
            else -> {
              when (issue) {
                UnusedResourceDetector.ISSUE_IDS -> {
                  // Make sure it's not an unused id declaration in a layout/menu/etc file that's
                  // also being deleted as unused.
                  // The current `fileListMap` contains those identified as having unused ids. Get
                  // the other list containing resources, which could contain the layout/menu/etc
                  // file containing this id.
                  if (unusedMap[UnusedResourceDetector.ISSUE]?.containsKey(file) == true) {
                    // Skip the current id since it's containing file will be deleted.
                    continue
                  }

                  // Delete ranges within the file
                  unusedElements.addAll(getElementsInFile(psiFile, problems, excludedResources))
                }
                UnusedResourceDetector.ISSUE -> {
                  // Unused non-value resource file: Delete the whole file
                  if (problems.any { filter.shouldProcessResource(getResource(it)) }) {
                    unusedElements.add(psiFile)
                  }
                }
              }
            }
          }
        }
      }
    }

    return unusedElements.toTypedArray()
  }

  private fun getElementsInFile(
    psiFile: PsiFile,
    problems: List<LintProblemData>,
    excludedResources: Set<String>
  ): Sequence<PsiElement> {
    // Delete all the resources in the given file
    if (psiFile !is XmlFile || !psiFile.isValid()) return emptySequence()

    return problems
      .asSequence()
      .filter { problem ->
        val resource = getResource(problem)
        !excludedResources.contains(resource) && filter.shouldProcessResource(resource)
      }
      .map { problem -> problem.textRange.startOffset }
      .sortedDescending()
      .map { startOffset ->
        PsiTreeUtil.findElementOfClassAtOffset(
          psiFile,
          startOffset,
          XmlAttribute::class.java,
          false
        )
      }
      .mapNotNull { attribute ->
        when {
          attribute == null ->
            PsiTreeUtil.findElementOfClassAtOffset(psiFile, startOffset, XmlTag::class.java, false)
          SdkConstants.ATTR_ID != attribute.localName ->
            // If deleting a resource, delete the whole resource element, except for attribute
            // android:id="" declarations
            // where we remove the attribute, not the tag
            PsiTreeUtil.getParentOfType(attribute, XmlTag::class.java)
          else -> attribute
        }
      }
  }

  private fun computeUnusedMap(): Map<Issue, Map<File, List<LintProblemData>>> {
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

    // Make sure lint didn't put extra issues into the map
    val allowedIssues = setOf(UnusedResourceDetector.ISSUE, UnusedResourceDetector.ISSUE_IDS)
    return map.filterKeys(allowedIssues::contains)
  }

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
          buildModel.applyChanges()
        } else {
          element.delete()
        }
      }
    } catch (e: IncorrectOperationException) {
      RefactoringUIUtil.processIncorrectOperation(myProject, e)
    }
  }

  private val lazyCommandName by
    lazy(LazyThreadSafetyMode.NONE) {
      "Deleting " + RefactoringUIUtil.calculatePsiElementDescriptionList(elements)
    }

  override fun getCommandName() = lazyCommandName

  override fun skipNonCodeUsages() = true

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
