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
import com.android.tools.idea.lint.common.LintBatchResult
import com.android.tools.idea.lint.common.LintIdeRequest
import com.android.tools.idea.lint.common.LintIdeSupport.Companion.get as lint
import com.android.tools.idea.lint.common.LintProblemData
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.android.tools.idea.res.getFolderType
import com.android.tools.lint.checks.UnusedResourceDetector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.utils.associateWithNotNull
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
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
import java.io.File

class UnusedResourcesProcessor(
  project: Project,
  filter: Filter? = null,
  private val includeIds: Boolean = false,
) : BaseRefactoringProcessor(project, null) {

  interface Filter {
    fun shouldProcessFile(psiFile: PsiFile): Boolean

    fun shouldProcessResource(resource: String?): Boolean
  }

  private object AllFilter : Filter {
    override fun shouldProcessFile(psiFile: PsiFile) = true

    override fun shouldProcessResource(resource: String?) = true
  }

  class FileFilter
  private constructor(private val files: Set<PsiFile>, private val directories: Set<PsiDirectory>) :
    Filter {

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
      fun from(elements: Collection<PsiElement>): FileFilter {
        val files = elements.mapNotNull { it.containingFile }.toSet()
        val dirs = elements.mapNotNull { it as? PsiDirectory }.toSet()

        return FileFilter(files, dirs)
      }
    }
  }

  private var elements = PsiElement.EMPTY_ARRAY
  private val performerMap: MutableMap<PsiElement, UnusedResourcesPerformer> = mutableMapOf()
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
    val fileToPsiFile =
      unusedMap.values
        .flatMap { value -> value.keys }
        .distinct()
        .associateWithNotNull { javaFile ->
          localFileSystem
            .findFileByIoFile(javaFile)
            ?.takeUnless(VirtualFile::isDirectory)
            ?.let(psiManager::findFile)
        }

    // TODO(b/223643511): This refactoring can break the project if it removes unused resources that
    //  are referenced by other unused resources that are not included in the filter. For example, a
    //  string resource can reference a string resource from a different module. A previous version
    //  of this code claimed to handle this, but it did not. This would require the resource graph.

    // Resources can be declared in multiple locations (e.g. string resources can have
    // translations). We want to include resource locations that are outside "includedFiles" if we
    // see that the resource is (also) declared in "includedFiles".
    // So, we compute includedResources, and use this for filtering (rather than includedFiles).
    val includedResources = HashSet<String>()
    run {
      val includedFiles = fileToPsiFile.values.filter { filter.shouldProcessFile(it) }
      for ((_, fileToProblems) in unusedMap) {
        for ((file, problems) in fileToProblems) {
          val psiFile = fileToPsiFile[file] ?: continue
          if (psiFile !in includedFiles) continue
          for (problem in problems) {
            val resource = getResource(problem)
            if (resource != null && filter.shouldProcessResource(resource)) {
              includedResources.add(resource)
            }
          }
        }
      }
    }

    // Used to avoid reporting the same file more than once.
    val unusedVirtualFiles = HashSet<VirtualFile>()
    fun addUnusedFile(file: PsiFile) {
      val virtualFile = file.virtualFile
      // Unlike PsiFile, VirtualFiles always support equals, hashCode, etc.
      if (virtualFile != null) {
        val newlyAdded = unusedVirtualFiles.add(virtualFile)
        if (!newlyAdded) return
      }
      unusedElements.add(file)
    }

    for ((issue, fileToProblems) in unusedMap) {
      for ((file, problems) in fileToProblems) {
        val psiFile = fileToPsiFile[file] ?: continue
        if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, psiFile)) continue

        val projectSystem = myProject.getProjectSystem()
        val performer =
          projectSystem
            .getTokenOrNull(UnusedResourcesToken.EP_NAME)
            ?.getPerformerFor(projectSystem, psiFile)
        if (performer != null) {
          val problemNames = problems.mapNotNull { getResource(it) }.toSet()
          val elements = performer.computeUnusedElements(psiFile, problemNames)
          unusedElements.addAll(elements)
          elements.forEach { performerMap[it] = performer }
          continue
        }

        if (psiFile.fileType.isBinary) {
          // Delete the whole file.
          if (problems.any { problemData -> getResource(problemData) in includedResources }) {
            addUnusedFile(psiFile)
          }
        } else {
          when (getFolderType(psiFile)) {
            // Not found in a resource folder and not handled by the Project System;
            // ignore this resource (it would be dangerous to just delete the file; see
            // for example http://b.android.com/220069).
            null -> {}
            ResourceFolderType.VALUES -> {
              unusedElements.addAll(getElementsInFile(psiFile, problems, includedResources))
            }
            else -> {
              when (issue) {
                UnusedResourceDetector.ISSUE_IDS -> {
                  // Make sure it's not an unused id declaration in a layout/menu/etc file that's
                  // also being deleted as unused.
                  // The `problems` list only contains unused ids. Get the other list containing
                  // resources, which could contain the layout/menu/etc. file containing this id.
                  if (unusedMap[UnusedResourceDetector.ISSUE]?.get(file)?.any { problemData -> getResource(problemData) in includedResources } == true) {
                    // Skip the current id since its containing file will be deleted.
                    continue
                  }

                  // Delete ranges within the file.
                  unusedElements.addAll(getElementsInFile(psiFile, problems, includedResources))
                }
                UnusedResourceDetector.ISSUE -> {
                  // Unused non-value resource file. Delete the whole file.
                  if (problems.any { problemData -> getResource(problemData) in includedResources }) {
                    addUnusedFile(psiFile)
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
    includedResources: Set<String>,
  ): Sequence<PsiElement> {
    if (psiFile !is XmlFile || !psiFile.isValid) return emptySequence()

    return problems
      .asSequence()
      .filter { problem -> includedResources.contains(getResource(problem)) }
      .map { problem -> problem.textRange.startOffset }
      .sortedDescending()
      .mapNotNull { startOffset ->
        val attribute =
          PsiTreeUtil.findElementOfClassAtOffset(
            psiFile,
            startOffset,
            XmlAttribute::class.java,
            false,
          )
        when {
          attribute == null ->
            PsiTreeUtil.findElementOfClassAtOffset(psiFile, startOffset, XmlTag::class.java, false)
          SdkConstants.ATTR_ID != attribute.localName ->
            // If deleting a resource, delete the whole resource element, except for
            // attribute android:id="" declarations where we remove the attribute, not
            // the tag
            PsiTreeUtil.getParentOfType(attribute, XmlTag::class.java)
          else -> attribute
        }
      }
  }

  private fun computeUnusedMap(): Map<Issue, Map<File, List<LintProblemData>>> {
    val map: MutableMap<Issue, Map<File, List<LintProblemData>>> = mutableMapOf()
    val enabledIssues =
      if (includeIds) setOf(UnusedResourceDetector.ISSUE, UnusedResourceDetector.ISSUE_IDS)
      else setOf(UnusedResourceDetector.ISSUE)

    val scope = AnalysisScope(myProject)

    val lintResult = LintBatchResult(myProject, map, scope, enabledIssues, null)
    val issueRegistry = lint().getIssueRegistry(enabledIssues.toList())
    val client = lint().createIsolatedClient(myProject, lintResult, issueRegistry)

    // This forces the detector to include all resource versions (e.g. for a string resource with
    // translations, every translation will be included). When run as an inspection (or from command
    // line Lint), the UnusedResourceDetector only reports the default version of each unused
    // resource, but the refactoring needs to include all resource versions. This also affects
    // UnusedResourcesQuickFix, ensuring that the quick-fix removes all versions of resources.
    client.putClientProperty(UnusedResourceDetector.KEY_INCLUDE_ALL_RESOURCE_VERSIONS, true)

    // Note: We pass in *all* modules in the project here, not just those in the scope of the
    // resource refactoring. If you for example are running the unused resource refactoring on a
    // library module, we want to only remove unused resources from the specific library
    // module, but we still have to have lint analyze all modules such that it doesn't consider
    // resources in the library as unused when they could be referenced from other modules.
    // So, we analyze all modules with lint, and then in computeUnusedDeclarationElements we filter
    // the matches down to only those included by "filter".
    val modules = ModuleManager.getInstance(myProject).modules.toList()
    val request =
      LintIdeRequest(client, myProject, null, modules, false).apply { setScope(Scope.ALL) }

    // Make sure we don't remove resources that are still referenced from
    // tests (though these should probably be in a test resource source
    // set instead.)
    with(client.createDriver(request, issueRegistry)) {
      checkTestSources = true
      analyze()
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
        when (val performer = performerMap[element]) {
          null -> element.delete()
          else -> performer.perform(element)
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

interface UnusedResourcesToken<P : AndroidProjectSystem> : Token {
  companion object {
    val EP_NAME =
      ExtensionPointName<UnusedResourcesToken<AndroidProjectSystem>>(
        "org.jetbrains.android.refactoring.unusedResourcesToken"
      )
  }

  fun getPerformerFor(projectSystem: P, psiFile: PsiFile): UnusedResourcesPerformer?
}

interface UnusedResourcesPerformer {
  fun computeUnusedElements(psiFile: PsiFile, names: Set<String>): Collection<PsiElement>

  fun perform(element: PsiElement)
}
