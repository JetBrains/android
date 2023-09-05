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
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel
import com.android.tools.idea.lint.common.LintBatchResult
import com.android.tools.idea.lint.common.LintIdeRequest
import com.android.tools.idea.lint.common.LintIdeSupport.Companion.get
import com.android.tools.idea.lint.common.LintProblemData
import com.android.tools.idea.res.getFolderType
import com.android.tools.lint.checks.UnusedResourceDetector
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix.Companion.getString
import com.android.tools.lint.detector.api.Scope
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager.modules
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
import com.intellij.util.ArrayUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import java.io.File
import java.util.Arrays
import java.util.Collections
import java.util.stream.Collectors

class UnusedResourcesProcessor(project: Project, private val myModules: Array<Module>, private val myFilter: String?) :
  BaseRefactoringProcessor(project, null) {
  var elements = PsiElement.EMPTY_ARRAY
    private set
  private var myIncludeIds = false
  private var myCachedCommandName: String? = null
  private val myBuildModelMap: MutableMap<PsiElement?, GradleBuildModel?>

  init {
    myBuildModelMap = HashMap()
  }

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor {
    return UnusedResourcesUsageViewDescriptor(elements)
  }

  public override fun findUsages(): Array<UsageInfo> {
    val map = computeUnusedMap()
    val elements = computeUnusedDeclarationElements(map)
    this.elements = elements.toArray(PsiElement.EMPTY_ARRAY)
    val result = arrayOfNulls<UsageInfo>(elements.size)
    var i = 0
    val n = elements.size
    while (i < n) {
      result[i] = UsageInfo(elements[i]!!)
      i++
    }
    return UsageViewUtil.removeDuplicatedUsages(result)
  }

  private fun computeUnusedDeclarationElements(map: MutableMap<Issue, Map<File, List<LintProblemData>>>): List<PsiElement?> {
    val elements: MutableList<PsiElement?> = ArrayList()

    // Make sure lint didn't put extra issues into the map
    for (issue in Lists.newArrayList(map.keys)) {
      if (issue !== UnusedResourceDetector.ISSUE && issue !== UnusedResourceDetector.ISSUE_IDS) {
        map.remove(issue)
      }
    }
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val manager = PsiManager.getInstance(myProject)
    val files: MutableMap<File, PsiFile> = Maps.newHashMap()
    val excludedFiles: MutableSet<PsiFile?> = HashSet()
    for ((_, value) in map) {
      for (file in value.keys) {
        if (!files.containsKey(file)) {
          val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)
          if (virtualFile != null) {
            if (!virtualFile.isDirectory) { // Gradle model errors currently don't have source positions
              val psiFile = manager.findFile(virtualFile)
              if (psiFile != null) {
                files[file] = psiFile

                // See whether the file with the warnings is in module that is not included
                // in this scope. If so, record it into the list of excluded files such that
                // we can skip removing these references later on.
                val module = ModuleUtilCore.findModuleForFile(psiFile)
                if (module != null) {
                  if (ArrayUtil.find<Module>(myModules, module) == -1) {
                    excludedFiles.add(psiFile)
                  }
                }
              }
            }
          }
        }
      }
    }

    // We cannot just skip removing references in modules outside of the scope.
    // If an unused resource is referenced from outside the included scope,
    // then deleting it partially would result in a broken project. Therefore,
    // track which references appear in excluded files, which we'll then later
    // use to also skip removing references in included scopes that are referenced
    // from excluded files.
    val excludedResources: MutableSet<String?> = HashSet()
    if (!excludedFiles.isEmpty()) {
      for ((_, fileMap) in map) {
        for (file in fileMap.keys) {
          val psiFile = files[file]
          if (excludedFiles.contains(psiFile)) {
            val list = fileMap[file]
            if (list != null) {
              for (problem in list) {
                val resource = getResource(problem)
                if (resource != null) {
                  excludedResources.add(resource)
                }
              }
            }
          }
        }
      }
    }
    for (issue in arrayOf<Issue>(UnusedResourceDetector.ISSUE, UnusedResourceDetector.ISSUE_IDS)) {
      val fileListMap = map[issue]
      if (fileListMap != null && !fileListMap.isEmpty()) {
        if (!files.isEmpty()) {
          for (file in files.keys) {
            val psiFile = files[file]
              ?: // Ignore for now; currently this happens for build.gradle resValue definitions
              // where we only had the project directory as the location from the Gradle model
              continue
            if (excludedFiles.contains(psiFile)) {
              continue
            }
            if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, psiFile)) {
              continue
            }
            val problems = fileListMap[file] ?: continue
            if (psiFile.fileType.isBinary) {
              // Delete the whole file
              if (matchesFilter(fileListMap, file)) {
                elements.add(psiFile)
              }
            } else {
              val folderType = getFolderType(psiFile)
              if (folderType == null) {
                // Not found in a resource folder. This happens for example for
                // matches in build.gradle.
                //
                // Attempt to find the resource in the build file. If we can't,
                // we'll ignore this resource (it would be dangerous to just delete the
                // file; see for example http://b.android.com/220069.)
                if ((psiFile is GroovyFile || psiFile is KtFile) &&
                  (psiFile.name.endsWith(SdkConstants.EXT_GRADLE) || psiFile.name.endsWith(SdkConstants.EXT_GRADLE_KTS))
                ) {
                  val gradleBuildModel = GradleModelProvider.getInstance().parseBuildFile(psiFile.virtualFile, myProject)

                  // Get all the resValue declared within the android block.
                  val androidElement = gradleBuildModel.android()
                  val resValues = androidElement.defaultConfig().resValues()
                  resValues.addAll(
                    androidElement.productFlavors().stream().flatMap { e: ProductFlavorModel -> e.resValues().stream() }
                      .collect(Collectors.toList()))
                  resValues.addAll(
                    androidElement.buildTypes().stream().flatMap { e: BuildTypeModel -> e.resValues().stream() }
                      .collect(Collectors.toList()))
                  for (resValue in resValues) {
                    val typeString: Any = resValue.type()
                    val nameString: Any = resValue.name()
                    // See if this is one of the unused resources
                    val lintProblems = fileListMap[VfsUtilCore.virtualToIoFile(psiFile.virtualFile)]!!
                    if (problems != null) {
                      for (problem in lintProblems) {
                        val unusedResource = getResource(problem)
                        if (unusedResource != null && unusedResource == SdkConstants.R_PREFIX + typeString + '.' + nameString) {
                          if (resValue.getModel().getPsiElement() != null) {
                            elements.add(resValue.getModel().getPsiElement())
                            // Keep track of the current buildModel to apply refactoring later on.
                            myBuildModelMap[resValue.getModel().getPsiElement()] = gradleBuildModel
                            resValue.remove()
                          }
                        }
                      }
                    }
                  }
                }
                continue
              }
              if (folderType != ResourceFolderType.VALUES) {
                // Make sure it's not an unused id declaration in a layout/menu/etc file that's
                // also being deleted as unused
                if (issue === UnusedResourceDetector.ISSUE_IDS) {
                  val m = map[UnusedResourceDetector.ISSUE]
                  if (m != null && m.containsKey(file)) {
                    // Yes - skip
                    continue
                  }

                  // Delete ranges within the file
                  addElementsInFile(elements, psiFile, problems, excludedResources)
                } else {
                  // Unused non-value resource file: Delete the whole file
                  if (matchesFilter(fileListMap, file)) {
                    elements.add(psiFile)
                  }
                }
              } else {
                addElementsInFile(elements, psiFile, problems, excludedResources)
              }
            }
          }
        }
      }
    }
    return elements
  }

  private fun addElementsInFile(
    elements: MutableList<PsiElement?>,
    psiFile: PsiFile,
    problems: List<LintProblemData>,
    excludedResources: Set<String?>
  ) {
    // Delete all the resources in the given file
    if (psiFile is XmlFile) {
      val starts: MutableList<Int> = Lists.newArrayListWithCapacity(problems.size)
      for (problem in problems) {
        if (excludedResources.contains(getResource(problem))) {
          continue
        }
        if (matchesFilter(problem)) {
          starts.add(problem.textRange.startOffset)
        }
      }
      starts.sort(Collections.reverseOrder())
      for (offset in starts) {
        if (psiFile.isValid()) {
          val attribute = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, XmlAttribute::class.java, false)
          var remove: PsiElement? = attribute
          if (attribute == null) {
            remove = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, XmlTag::class.java, false)
          } else if (SdkConstants.ATTR_ID != attribute.localName) {
            // If deleting a resource, delete the whole resource element, except for attribute android:id="" declarations
            // where we remove the attribute, not the tag
            remove = PsiTreeUtil.getParentOfType(attribute, XmlTag::class.java)
          }
          if (remove != null) {
            elements.add(remove)
          }
        }
      }
    }
  }

  private fun computeUnusedMap(): MutableMap<Issue, Map<File, List<LintProblemData>>> {
    val map: MutableMap<Issue, Map<File, List<LintProblemData>>> = Maps.newHashMap()
    val issues: Set<Issue>
    issues = if (myIncludeIds) {
      ImmutableSet.of(
        UnusedResourceDetector.ISSUE,
        UnusedResourceDetector.ISSUE_IDS
      )
    } else {
      ImmutableSet.of(UnusedResourceDetector.ISSUE)
    }
    val scope = AnalysisScope(myProject)
    val unusedWasEnabled = UnusedResourceDetector.ISSUE.isEnabledByDefault()
    val unusedIdsWasEnabled = UnusedResourceDetector.ISSUE_IDS.isEnabledByDefault()
    UnusedResourceDetector.ISSUE.setEnabledByDefault(true)
    UnusedResourceDetector.ISSUE_IDS.setEnabledByDefault(myIncludeIds)
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
      val modules = Arrays.asList<Module>(*getInstance.getInstance(myProject).modules)
      val request: LintRequest = LintIdeRequest(client, myProject, null, modules, false)
      request.setScope(Scope.ALL)
      val lint = client.createDriver(request, get().getIssueRegistry())
      // Make sure we don't remove resources that are still referenced from
      // tests (though these should probably be in a test resource source
      // set instead.)
      lint.checkTestSources = true
      lint.analyze()
    } finally {
      UnusedResourceDetector.ISSUE.setEnabledByDefault(unusedWasEnabled)
      UnusedResourceDetector.ISSUE_IDS.setEnabledByDefault(unusedIdsWasEnabled)
    }
    return map
  }

  private fun matchesFilter(fileListMap: Map<File, List<LintProblemData>>, file: File): Boolean {
    if (myFilter != null) {
      val problems = fileListMap[file]!!
      for (problem in problems) {
        if (myFilter == getResource(problem)) {
          return true
        }
      }
      return false
    }
    return true
  }

  private fun matchesFilter(problem: LintProblemData): Boolean {
    return myFilter == null || myFilter == getResource(problem)
  }

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    return true
  }

  override fun refreshElements(elements: Array<PsiElement>) {
    System.arraycopy(elements, 0, this.elements, 0, elements.size)
  }

  override fun getBeforeData(): RefactoringEventData? {
    val beforeData = RefactoringEventData()
    beforeData.addElements(elements)
    return beforeData
  }

  override fun getRefactoringId(): String? {
    return "refactoring.unused.resources"
  }

  override fun performRefactoring(usages: Array<UsageInfo>) {
    try {
      for (usage in usages) {
        val element = usage.element
        if (element != null && element.isValid) {
          if (myBuildModelMap[element] != null && myBuildModelMap[element]!!.isModified()) {
            WriteCommandAction.runWriteCommandAction(myProject) {
              myBuildModelMap[element]!!
                .applyChanges()
            }
          } else {
            element.delete()
          }
        }
      }
    } catch (e: IncorrectOperationException) {
      RefactoringUIUtil.processIncorrectOperation(myProject, e)
    }
  }

  private fun calcCommandName(): String {
    return "Deleting " + RefactoringUIUtil.calculatePsiElementDescriptionList(elements)
  }

  override fun getCommandName(): String {
    if (myCachedCommandName == null) {
      myCachedCommandName = calcCommandName()
    }
    return myCachedCommandName!!
  }

  override fun skipNonCodeUsages(): Boolean {
    return true
  }

  fun setIncludeIds(includeIds: Boolean) {
    myIncludeIds = includeIds
  }

  override fun isToBeChanged(usageInfo: UsageInfo): Boolean {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // Automatically exclude/deselect elements that contain the string "AUTO-EXCLUDE".
      // This is our simple way to unit test the UI operation of users deselecting certain
      // elements in the refactoring UI.
      val element = usageInfo.element
      if (element != null && element.text.contains("AUTO-EXCLUDE")) {
        return false
      }
    }
    return super.isToBeChanged(usageInfo)
  }

  companion object {
    private fun getResource(problem: LintProblemData): String? {
      return getString(problem.quickfixData, UnusedResourceDetector.KEY_RESOURCE_FIELD, null)
    }
  }
}
