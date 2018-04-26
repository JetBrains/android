/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.builder.model.AaptOptions
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AbstractResourceRepository
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.LeafResourceRepository
import com.android.tools.idea.res.ResourceRepositoryManager
import com.google.common.collect.Maps
import com.google.common.collect.Table
import com.google.common.collect.Tables
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.*
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.text.nullize
import com.intellij.util.xml.DomManager
import com.intellij.util.xml.DomUtil
import com.intellij.util.xml.GenericDomValue
import com.intellij.util.xml.WrappingConverter
import org.jetbrains.android.dom.converters.AndroidResourceReference
import org.jetbrains.android.dom.converters.ResourceReferenceConverter
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils

private val DataContext.module: Module? get() = LangDataKeys.MODULE.getData(this)

/**
 * Action to perform the refactoring.
 *
 * Decides if the refactoring is available and constructs the right [MigrateToResourceNamespacesHandler] object if it is.
 */
class MigrateToResourceNamespacesAction : BaseRefactoringAction() {
  override fun getHandler(dataContext: DataContext) = MigrateToResourceNamespacesHandler()
  override fun isHidden() = StudioFlags.MIGRATE_TO_RESOURCE_NAMESPACES_REFACTORING_ENABLED.get().not()
  override fun isAvailableInEditorOnly() = false
  override fun isAvailableForLanguage(language: Language?) = true

  override fun isEnabledOnDataContext(dataContext: DataContext) = isEnabledOnModule(dataContext.module)
  override fun isEnabledOnElements(elements: Array<PsiElement>) = isEnabledOnModule(ModuleUtil.findModuleForPsiElement(elements.first()))

  private fun isEnabledOnModule(module: Module?): Boolean {
    return ResourceRepositoryManager.getOrCreateInstance(module ?: return false)?.namespacing == AaptOptions.Namespacing.DISABLED
  }
}

/**
 * [RefactoringActionHandler] for [MigrateToResourceNamespacesAction].
 *
 * Since there's no user input required to start the refactoring, it just runs a fresh [MigrateToResourceNamespacesProcessor].
 */
class MigrateToResourceNamespacesHandler : RefactoringActionHandler {
  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    dataContext?.module?.let(this::invoke)
  }

  override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) {
    dataContext?.module?.let(this::invoke)
  }

  private fun invoke(module: Module) {
    MigrateToResourceNamespacesProcessor(AndroidFacet.getInstance(module)!!).run {
      setPreviewUsages(true)
      run()
    }
  }
}

private abstract class ResourceUsageInfo(ref: PsiReference) : UsageInfo(ref) {
  abstract val resourceType: ResourceType
  abstract val name: String
}

private class DomUsageInfo(ref: PsiReference, val domValue: GenericDomValue<ResourceValue>) : ResourceUsageInfo(ref) {
  override val resourceType: ResourceType
    get() = domValue.value!!.type!!
  override val name: String
    get() = domValue.value!!.resourceName!!
}

/**
 * Implements the "migrate to resource namespaces" refactoring by finding all references to resources and rewriting them.
 */
class MigrateToResourceNamespacesProcessor(
  private val invokingFacet: AndroidFacet
) : BaseRefactoringProcessor(invokingFacet.module.project) {

  override fun getCommandName() = "Migrate to resource namespaces"

  private val allFacets = AndroidUtils.getAllAndroidDependencies(invokingFacet.module, true) + invokingFacet

  private val inferredNamespaces: Table<ResourceType, String, String> =
    Tables.newCustomTable(Maps.newEnumMap(ResourceType::class.java), { mutableMapOf<String, String>() })

  override fun findUsages(): Array<out UsageInfo> {
    val progressIndicator = ProgressManager.getInstance().progressIndicator

    progressIndicator.text = "Analyzing XML resource files..."
    val result = mutableListOf<ResourceUsageInfo>()
    result.addAll(findResUsages())

    progressIndicator.text = "Analyzing manifest files..."
    result.addAll(findManifestUsages())

    progressIndicator.text = "Analyzing code files..."
    result.addAll(findCodeUsages())

    progressIndicator.text = "Inferring namespaces..."
    progressIndicator.text2 = null

    val leafRepos = mutableListOf<AbstractResourceRepository>()
    ResourceRepositoryManager.getAppResources(invokingFacet).getLeafResourceRepositories(leafRepos)
    leafRepos.retainAll { it is LeafResourceRepository } // TODO: enforce this in MultiResourceRepository.

    val total = result.size.toDouble()
    // TODO: try doing this in parallel using a thread pool.
    result.forEachIndexed { index, resourceUsageInfo ->
      ProgressManager.checkCanceled()

      inferredNamespaces.row(resourceUsageInfo.resourceType).computeIfAbsent(resourceUsageInfo.name) {
        for (repo in leafRepos) {
          if (repo.hasResourceItem(ResourceNamespace.RES_AUTO, resourceUsageInfo.resourceType, resourceUsageInfo.name)) {
            // TODO: check other repos and build a list of unresolved or conflicting references, to display in a UI later.
            return@computeIfAbsent (repo as LeafResourceRepository).packageName
          }
        }

        null
      }

      progressIndicator.fraction = (index + 1) / total
    }

    return result.toTypedArray()
  }

  private fun findResUsages(): Collection<ResourceUsageInfo> {
    val result = mutableListOf<ResourceUsageInfo>()
    val psiManager = PsiManager.getInstance(myProject)

    for (facet in allFacets) {
      val repositoryManager = ResourceRepositoryManager.getOrCreateInstance(facet)
      if (repositoryManager.namespacing != AaptOptions.Namespacing.DISABLED) continue

      for (resourceDir in repositoryManager.getModuleResources(true)!!.resourceDirs) {
        VfsUtil.processFilesRecursively(resourceDir) { vf ->
          if (vf.fileType == StdFileTypes.XML) {
            val psiFile = psiManager.findFile(vf)
            if (psiFile is XmlFile) {
              result.addAll(findXmlUsages(psiFile, facet))
            }
          }

          true // continue processing
        }
      }
    }
    return result
  }

  private fun findManifestUsages(): Collection<ResourceUsageInfo> {
    return emptySet()
  }

  private fun findXmlUsages(xmlFile: XmlFile, currentFacet: AndroidFacet): Collection<ResourceUsageInfo> {
    ProgressManager.checkCanceled()
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    progressIndicator.text2 = xmlFile.virtualFile.path

    val result = mutableSetOf<ResourceUsageInfo>()
    val domManager = DomManager.getDomManager(myProject)
    val moduleRepo = ResourceRepositoryManager.getModuleResources(currentFacet)

    xmlFile.accept(object : XmlRecursiveElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        val domValue = domManager.getDomElement(tag) as? GenericDomValue<*>
        if (domValue != null) {
          handleDomValue(domValue)
        }
        super.visitXmlTag(tag)
      }

      override fun visitXmlAttribute(attribute: XmlAttribute) {
        handleDomValue(domManager.getDomElement(attribute) as? GenericDomValue<*> ?: return)
        super.visitXmlAttribute(attribute)
      }

      private fun handleDomValue(domValue: GenericDomValue<*>) {
        val converter = WrappingConverter.getDeepestConverter(domValue.converter, domValue)
        val psiElement = DomUtil.getValueElement(domValue) ?: return
        when (converter) {
          is ResourceReferenceConverter -> {
            references@ for (reference in psiElement.references) {
              if (reference !is AndroidResourceReference) continue@references

              val resourceValue = reference.resourceValue
              when {
                resourceValue.`package` != null -> {
                  // Leave as-is, this is either a reference to a framework resource or sample data.
                }
                resourceValue.resourceType?.startsWith('+') == true -> {
                  // This defines a new id, no need to change.
                }
                else -> {
                  // See if this resource is defined in the same module, otherwise it needs to be rewritten.
                  val name = resourceValue.resourceName.nullize(nullizeSpaces = true) ?: continue@references
                  val resourceType = resourceValue.type ?: continue@references
                  if (!moduleRepo.hasResourceItem(ResourceNamespace.RES_AUTO, resourceType, name)) {
                    // We know this GenericDomValue used ResourceReferenceConverter, which is for ResourceValue.
                    @Suppress("UNCHECKED_CAST")
                    result.add(DomUsageInfo(reference, domValue as GenericDomValue<ResourceValue>))
                  }
                }
              }
            }
          }
        // TODO: handle other relevant converters.
        }
      }
    })

    progressIndicator.text2 = null
    return result
  }

  private fun findCodeUsages(): Collection<ResourceUsageInfo> {
    return emptySet()
  }

  override fun performRefactoring(usages: Array<UsageInfo>) {
    // TODO: update build.gradle files

    val total = usages.size.toDouble()
    usages.forEachIndexed { index, usageInfo ->
      when (usageInfo) {
        is DomUsageInfo -> {
          val oldResourceValue = usageInfo.domValue.value ?: return@forEachIndexed
          val inferredNamespace = inferredNamespaces[oldResourceValue.type, oldResourceValue.resourceName] ?: return@forEachIndexed

          val newResourceValue = ResourceValue.referenceTo(
            oldResourceValue.prefix,
            inferredNamespace,
            oldResourceValue.resourceType,
            oldResourceValue.resourceName
          )

          usageInfo.domValue.value = newResourceValue
        }
        else -> error("Don't know how to handle ${usageInfo.javaClass.name}.")
      }
      ProgressManager.getInstance().progressIndicator.fraction = (index + 1) / total
    }
  }

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    // TODO: Report conflicts and any other issues. This method runs on the UI thread, so we need to do the actual work in [findUsages].
    return if (refUsages.get().isNotEmpty()) {
      true
    }
    else {
      Messages.showInfoMessage(myProject, "No cross-namespace resource references found", "Migrate to resource namespaces")
      false
    }
  }

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor = object : UsageViewDescriptorAdapter() {
    override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY
    override fun getProcessedElementsHeader() = "Resource references to migrate"
  }
}

