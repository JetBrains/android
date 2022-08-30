/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.android.tools.idea.refactoring.modularize

import com.android.tools.idea.projectsystem.containsFile
import com.android.tools.idea.projectsystem.getManifestFiles
import com.intellij.openapi.actionSystem.LangDataKeys.TARGET_MODULE

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.res.*
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.SyntheticElement
import com.intellij.psi.XmlRecursiveElementWalkingVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.annotations.NotNull
import java.util.Locale

class AndroidModularizeHandler : RefactoringActionHandler {
  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    invoke(project, BaseRefactoringAction.getPsiElementArray(dataContext), dataContext)
  }

  override fun invoke(@NotNull project: Project, @NotNull elements: Array<PsiElement>, dataContext: DataContext) {
    val processor: AndroidModularizeProcessor = createProcessor(project, elements)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      val targetModule: Module? = TARGET_MODULE.getData(dataContext)
      if (targetModule != null) processor.setTargetModule(targetModule)
      processor.run()
    }
    else {
      val suitableModules = mutableListOf<Module>()
      // Only offer modules that have an Android facet, otherwise we don't know where to move resources.
      for (facet in project.getAndroidFacets()) {
        if (ResourceFolderManager.getInstance(facet).folders.isNotEmpty()) {
          suitableModules.add(facet.module.getMainModule())
        }
      }
      for (root: PsiElement in elements) {
        val sourceModule: Module? = ModuleUtilCore.findModuleForPsiElement(root)
        if (sourceModule != null) suitableModules.remove(sourceModule)
      }
      val dialog = AndroidModularizeDialog(project, suitableModules, processor)
      dialog.show()
    }
  }

  @VisibleForTesting
  fun createProcessor(project: Project, elements: Array<PsiElement>): AndroidModularizeProcessor {
    val scanner = CodeAndResourcesReferenceCollector(project)

    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      { ApplicationManager.getApplication().runReadAction { scanner.accumulate(*elements) } },
      "Computing References", false, project)

    return AndroidModularizeProcessor(project,
                                      elements,
                                      scanner.classReferences,
                                      scanner.resourceReferences,
                                      scanner.manifestReferences,
                                      scanner.referenceGraph
    )
  }

  private class CodeAndResourcesReferenceCollector(private val myProject: Project) {

    val classReferences = LinkedHashSet<PsiClass>()
    val resourceReferences = LinkedHashSet<ResourceItem>(RESOURCE_SET_INITIAL_SIZE)
    val manifestReferences = HashSet<PsiElement>()

    private val myVisitQueue = ArrayDeque<PsiElement>()
    private val myGraphBuilder = AndroidCodeAndResourcesGraph.Builder()

    fun accumulate(vararg roots: PsiElement) {
      myVisitQueue.clear()
      for (element in roots) {
        val ownerClass = if (element is PsiClass) element else PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        ownerClass?.let {
          if (classReferences.add(it)) {
            myVisitQueue.add(it)
            myGraphBuilder.addRoot(it)
          }
        }
      }

      val facetSet = mutableSetOf<AndroidFacet>()
      val fileScope = mutableSetOf<VirtualFile>()
      val elementScope = mutableSetOf<PsiElement>()

      val indicator = ProgressManager.getInstance().progressIndicator

      if (indicator != null) {
        indicator.pushState()
        indicator.isIndeterminate = false
      }

      try {
        var numVisited = 0

        while (myVisitQueue.isNotEmpty()) {
          val element = myVisitQueue.removeFirstOrNull() ?: continue
          numVisited++

          val facet = AndroidFacet.getInstance(element) ?: continue

          facetSet.add(facet)

          if (indicator != null) {
            indicator.text = (String.format(Locale.US, "Scanning definition %1\$d of %2\$d", numVisited, numVisited + myVisitQueue.size))
            indicator.fraction = ((numVisited.toDouble()) / (numVisited + myVisitQueue.size))
          }

          if (element is PsiClass) {
            element.accept(JavaReferenceVisitor(facet, element))

            // Check for manifest entries referencing this class (this applies to activities, content providers, etc).
            val manifestScope = GlobalSearchScope.filesScope(myProject, facet.getManifestFiles())

            ReferencesSearch.search(element, manifestScope).forEach { reference ->
              val tag: PsiElement = reference.element

              PsiTreeUtil.getParentOfType(tag, XmlTag::class.java)?.let { parentTag ->
                if (manifestReferences.add(parentTag)) {
                  // Scan the tag because we might have references to other resources.
                  myVisitQueue.add(parentTag)
                }
                myGraphBuilder.markReference(element, parentTag)
              }
            }

            // Scope building: we try to be as precise as possible when computing the enclosing scope. For example we include the (selected)
            // activity tags in a manifest file but not the entire file, which may contain references to resources we would otherwise move.

            if (element.containingClass != null) {
              fileScope.add(element.containingFile.virtualFile)
            }
            else {
              elementScope.add(element)
            }
          }
          else {
            if (element is PsiFile) {
              fileScope.add(element.virtualFile)
            }
            else {
              elementScope.add(element)
            }

            element.accept(XmlResourceReferenceVisitor(facet, element))
          }
        }

        var globalSearchScope = GlobalSearchScope.EMPTY_SCOPE
        for (facet in facetSet) {
          globalSearchScope = globalSearchScope.union(facet.module.getModuleScope(false))
        }

        val visitedScope = GlobalSearchScope.filesScope(myProject, fileScope)
          .union(LocalSearchScope(elementScope.toTypedArray()))
        globalSearchScope = globalSearchScope.intersectWith(GlobalSearchScope.notScope(visitedScope))

        for (clazz in classReferences) {
          ReferencesSearch.search(clazz, globalSearchScope).forEach { reference ->
            myGraphBuilder.markReferencedOutsideScope(clazz)
            LOGGER.debug("$clazz referenced from ${reference.element.containingFile}")
          }
        }

        val seenResources = HashSet<ResourceReference>(resourceReferences.size)

        for (item in resourceReferences) {
          val ref = item.referenceToSelf
          if (seenResources.add(ref)) {
            val fields: Array<PsiField>
            val elm = getResourceDefinition(item)
            fields = when (elm) {
              is PsiFile -> findResourceFieldsForFileResource(elm, true)
              is XmlTag -> findResourceFieldsForValueResource(elm, true)
              else -> continue
            }

            for (field in fields) {
              ReferencesSearch.search(field, globalSearchScope).forEach { reference ->
                myGraphBuilder.markReferencedOutsideScope(elm)
                LOGGER.debug("$item referenced from ${reference.element.containingFile}")
              }
            }
          }
        }
      }
      finally {
        indicator?.popState()
      }
    }

    val referenceGraph: AndroidCodeAndResourcesGraph get() = myGraphBuilder.build()

    fun getResourceDefinition(resource: ResourceItem): PsiElement? =
      getItemPsiFile(myProject, resource)?.let { file -> // Psi file could be null if this is dynamically defined, so nothing to visit...
        if (getFolderType(file) == ResourceFolderType.VALUES) {
          // This is just a value, so we'll just scan its corresponding XmlTag
          getItemTag(myProject, resource)
        }
        else {
          file
        }
      }

    private inner class XmlResourceReferenceVisitor(private val myFacet: AndroidFacet,
                                                    private val mySource: PsiElement) : XmlRecursiveElementWalkingVisitor() {
      private val myResourceRepository: LocalResourceRepository = ResourceRepositoryManager.getModuleResources(myFacet)

      override fun visitXmlAttributeValue(element: XmlAttributeValue) = processPotentialReference(element.value)

      override fun visitXmlToken(token: XmlToken) = processPotentialReference(token.text)

      private fun processPotentialReference(text: String) {
        ResourceUrl.parse(text)?.let { url ->
          if (!url.isFramework && !url.isCreate && url.type != ResourceType.ID) {
            val matches: List<ResourceItem> = myResourceRepository.getResources(ResourceNamespace.TODO(), url.type, url.name)
            for (match in matches) {
              getResourceDefinition(match)?.let { target ->
                if (resourceReferences.add(match)) {
                  myVisitQueue.add(target)
                }
                myGraphBuilder.markReference(mySource, target)
              }
            }
          }
          else {
            // Perhaps this is a reference to a Java class
            JavaPsiFacade.getInstance(myProject).findClass(
              text, myFacet.module.getModuleScope(false)
            )?.let { target ->
              if (classReferences.add(target)) {
                myVisitQueue.add(target)
              }
              myGraphBuilder.markReference(mySource, target)
            }
          }
        }
      }
    }

    private inner class JavaReferenceVisitor(private val myFacet: AndroidFacet,
                                             private val mySource: PsiElement) : JavaRecursiveElementWalkingVisitor() {
      private val myResourceRepository: LocalResourceRepository = ResourceRepositoryManager.getModuleResources(myFacet)

      override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        val element = expression.resolve()
        if (element is PsiField) {
          val referenceType = AndroidPsiUtils.getResourceReferenceType(expression)

          if (referenceType == AndroidPsiUtils.ResourceReferenceType.APP) {
            // This is a resource we might be able to move
            AndroidPsiUtils.getResourceType(expression)?.let { type ->
              if (type != ResourceType.ID) {
                val name = AndroidPsiUtils.getResourceName(expression)

                val matches = myResourceRepository.getResources(ResourceNamespace.TODO(), type, name)
                for (match in matches) {
                  getResourceDefinition(match)?.let { target ->
                    if (resourceReferences.add(match)) {
                      myVisitQueue.add(target)
                    }
                    myGraphBuilder.markReference(mySource, target)
                  }
                }
              }
            }
            return
          }
        }
        super.visitReferenceExpression(expression)
      }

      override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
        val target = reference.advancedResolve(false).element
        if (target is PsiClass) {
          if (target !is PsiTypeParameter && target !is SyntheticElement) {
            val source = target.getContainingFile().virtualFile
            if (SourceProviderManager.getInstance(myFacet).sources.containsFile(source)) {
              // This is a local source file, therefore a candidate to be moved
              if (classReferences.add(target)) {
                myVisitQueue.add(target)
              }
              if (target != mySource) { // Don't add self-references
                myGraphBuilder.markReference(mySource, target)
              }
            }
          }
        }
      }
    }
  }

  companion object {
    private val LOGGER = Logger.getInstance(AndroidModularizeHandler::class.java)
    private const val RESOURCE_SET_INITIAL_SIZE = 100
  }
}