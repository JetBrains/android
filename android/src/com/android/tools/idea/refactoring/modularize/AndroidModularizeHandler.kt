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

import com.android.SdkConstants
import com.android.annotations.concurrency.UiThread
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.projectsystem.containsFile
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getManifestFiles
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.findResourceFieldsForFileResource
import com.android.tools.idea.res.findResourceFieldsForValueResource
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.res.getItemPsiFile
import com.android.tools.idea.res.getItemTag
import com.google.common.annotations.VisibleForTesting
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys.TARGET_MODULE
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
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiReferenceExpression
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
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.structuralsearch.visitor.KotlinRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import java.util.Locale

class AndroidModularizeHandler : RefactoringActionHandler {
  @UiThread
  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    invoke(project, BaseRefactoringAction.getPsiElementArray(dataContext), dataContext)
  }

  @UiThread
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
      "Computing references", false, project)

    return AndroidModularizeProcessor(project,
                                      elements,
                                      scanner.classReferences,
                                      scanner.resourceReferences,
                                      scanner.manifestReferences,
                                      scanner.codeFileReferences,
                                      scanner.referenceGraph
    )
  }

  private class CodeAndResourcesReferenceCollector(private val myProject: Project) {

    val classReferences = LinkedHashSet<PsiElement>()
    val resourceReferences = LinkedHashSet<ResourceItem>(RESOURCE_SET_INITIAL_SIZE)
    val manifestReferences = HashSet<PsiElement>()
    val codeFileReferences = LinkedHashSet<PsiFile>()

    private val myVisitQueue = ArrayDeque<PsiElement>()
    private val myGraphBuilder = AndroidCodeAndResourcesGraph.Builder()

    private val PsiElement.isClass: Boolean
      get() = when (this.language) {
        JavaLanguage.INSTANCE -> this is PsiClass
        KotlinLanguage.INSTANCE -> this is KtClass
        else -> false
      }

    fun accumulate(vararg roots: PsiElement) {
      myVisitQueue.clear()
      for (element in roots) {
        if (element is PsiClass || element is KtClass) {
          classReferences.add(element)
        }
        element.containingFile?.let {
          if (codeFileReferences.add(it)) {
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

          if (element !is PsiFile) {
            // Must be a resource or manifest entry

            // Scope building: we try to be as precise as possible when computing the enclosing scope. For example we include the (selected)
            // activity tags in a manifest file but not the entire file, which may contain references to resources we would otherwise move.
            elementScope.add(element)
            element.accept(XmlResourceReferenceVisitor(facet, element))
            continue
          }

          fileScope.add(element.virtualFile)

          when (element.language) {
            JavaLanguage.INSTANCE -> element.accept(JavaReferenceVisitor(facet, element))
            KotlinLanguage.INSTANCE -> element.accept(KotlinReferenceVisitor(facet, element))
            else -> {
              element.accept(XmlResourceReferenceVisitor(facet, element)); continue
            }
          }

          // Check for manifest entries referencing classes within this file. (this applies to activities, content providers, etc).
          val classes = when (element) {
            is PsiJavaFile -> element.classes
            is KtFile -> element.classes
            else -> null
          }!!

          for (clazz in classes) {
            val manifestScope = GlobalSearchScope.filesScope(myProject, facet.getManifestFiles())

            ReferencesSearch.search(clazz, manifestScope).forEach { reference ->
              val tag: PsiElement = reference.element

              PsiTreeUtil.getParentOfType(tag, XmlTag::class.java)?.let { parentTag ->
                if (manifestReferences.add(parentTag)) {
                  // Scan the tag because we might have references to other resources.
                  myVisitQueue.add(parentTag)
                }
                myGraphBuilder.markReference(element, parentTag)
              }
            }
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
          // If the class has a companion object, we want to count references to it
          // as references to the class itself
          if (clazz is KtClass) {
            clazz.companionObjects.forEach { companion ->
              ReferencesSearch.search(companion, globalSearchScope).forEach { reference ->
                myGraphBuilder.markReferencedOutsideScope(clazz)
                LOGGER.debug("$clazz referenced from ${reference.element.containingFile}")
              }
            }
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
        val url = ResourceUrl.parse(text) ?: return
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
          // Perhaps this is a reference to a Java or Kotlin class
          val psiClass = JavaPsiFacade.getInstance(myProject).findClass(text, myFacet.module.getModuleScope(false)) ?: return
          val file = psiClass.containingFile ?: return
          classReferences.add(psiClass)
          if (codeFileReferences.add(file)) {
            myVisitQueue.add(file)
          }
          myGraphBuilder.markReference(mySource, file)
        }
      }
    }

    private inner class JavaReferenceVisitor(private val myFacet: AndroidFacet,
                                             private val mySource: PsiElement) : JavaRecursiveElementWalkingVisitor() {
      private val myResourceRepository: LocalResourceRepository = ResourceRepositoryManager.getModuleResources(myFacet)

      override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        expression.resolve()?.let { element -> commonVisitReferenceExpression(expression, element, mySource, myResourceRepository) }
        super.visitReferenceExpression(expression)
      }

      override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
        commonVisitReferenceElement(reference.advancedResolve(false).element ?: return, mySource, myFacet)
      }
    }

    private inner class KotlinReferenceVisitor(private val myFacet: AndroidFacet,
                                               private val mySource: PsiElement) : KotlinRecursiveElementWalkingVisitor() {
      private val myResourceRepository: LocalResourceRepository = ResourceRepositoryManager.getModuleResources(myFacet)

      override fun visitReferenceExpression(expression: KtReferenceExpression) {
        expression.references.forEach {
          it?.resolve()?.let { element ->
            commonVisitReferenceExpression(it.element, element, mySource, myResourceRepository)
            commonVisitReferenceElement(element, mySource, myFacet)
          }
        }
        super.visitReferenceExpression(expression)
      }
    }

    private fun commonVisitReferenceExpression(reference: PsiElement,
                                               element: PsiElement,
                                               mySource: PsiElement,
                                               myResourceRepository: LocalResourceRepository) {
      if (element is PsiField || element is KtProperty) {
        if (isAppResource(element)) {
          // This is a resource we might be able to move
          getResourceType(element)?.let { type ->
            if (type != ResourceType.ID) {
              val name = if (reference.language == JavaLanguage.INSTANCE) {
                AndroidPsiUtils.getResourceName(reference)
              }
              else {
                reference.text
              }

              val matches = myResourceRepository.getResources(ResourceNamespace.TODO(), type, name)
              for (match in matches) {
                getResourceDefinition(match)?.let { target ->
                  if (resourceReferences.add(match)) {
                    myVisitQueue.add(target)
                  }
                  myGraphBuilder.markReference(
                    mySource, target)
                }
              }
            }
          }
          return
        }
      }
    }

    private fun commonVisitReferenceElement(target: PsiElement, mySource: PsiElement, myFacet: AndroidFacet) {
      if ((target is PsiClass
           || target is KtClass
           || target is KtDeclaration && target.containingClass() == null
           || target is KtObjectDeclaration && target.isCompanion())
          && target !is KtTypeParameter
          && target !is SyntheticElement) {

        val file = target.containingFile
        if (SourceProviderManager.getInstance(myFacet).sources.containsFile(file.virtualFile)) {
          // This is a local source file, therefore a candidate to be moved
          if (target is KtClass || target is PsiClass) {
            classReferences.add(target)
          }

          // Since we are only moving entire code files, we need to mark all the classes in the file
          // TODO: this code isn't the best
          when (file) {
            is PsiJavaFile -> file.classes
            is KtFile -> (file.classes).map { (((it as KtLightClass).kotlinOrigin) as? KtClass) }.toTypedArray()
            else -> null
          }!!.forEach {
            classReferences.add(it ?: return@forEach)
          }
          if (codeFileReferences.add(file)) {
            myVisitQueue.add(file)
          }
          if (target != mySource && file != mySource) { // Don't add self-references
            myGraphBuilder.markReference(mySource, file)
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

// TODO: Both of these functions are modified from Java-specific methods in AndroidPsiUtils. Can we avoid this?
// There is the extension function AndroidUtil.getResourceReferenceType() whose receiver is a property, but it is marked internal
// See AndroidKotlinResourceExternalAnnotator for an example use

// Mirrors AndroidPsiUtils.getResourceReferenceType
fun isAppResource(resolvedElement: PsiElement): Boolean {
  // Examples of valid resources references are my.package.R.string.app_name or my.package.R.color.my_black
  // First parent is the resource type - eg string or color, etc
  val elementType = resolvedElement.parent as? PsiClass ?: return false

  // Second parent is the package
  val elementPackage = elementType.parent as? PsiClass ?: return false
  if (SdkConstants.R_CLASS == elementPackage.name) {
    val elemParent3 = elementPackage.parent
    return !(elemParent3 is PsiClassOwner && SdkConstants.ANDROID_PKG == elemParent3.packageName)
  }
  return false
}

// Mirrors AndroidPsiUtils.getResourceType
fun getResourceType(resolvedElement: PsiElement): ResourceType? {
  val elemParent = resolvedElement.parent
  return if (elemParent !is PsiClass) {
    null
  }
  else ResourceType.fromClassName(elemParent.name!!)
}

