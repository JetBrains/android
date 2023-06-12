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
package com.android.tools.idea.res.psi

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.sampledata.SampleDataManager
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.ResourceFolderRepository
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.ResourceUpdateTracer
import com.android.tools.idea.res.getDeclaringAttributeValue
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.res.isIdDefinition
import com.android.tools.idea.res.resolve
import com.android.utils.TraceUtils
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.ResolveScopeManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.xml.XmlElement
import com.intellij.util.containers.toArray
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ResourceRepositoryToPsiResolver : AndroidResourceToPsiResolver {
  override fun getGotoDeclarationFileBasedTargets(resourceReference: ResourceReference, context: PsiElement): Array<PsiFile> {
    val studioResourceRepositoryManager = StudioResourceRepositoryManager.getInstance(context) ?: return PsiFile.EMPTY_ARRAY
    return getRelevantResourceRepository(resourceReference, studioResourceRepositoryManager)
      ?.getResources(resourceReference)
      ?.filter { it.isFileBased }
      ?.mapNotNull { resolveToDeclaration(it, context.project) }
      ?.filterIsInstance(PsiFile::class.java)
      .orEmpty()
      .toTypedArray()
  }

  override fun resolveToDeclaration(resource: ResourceItem, project: Project): PsiElement? {
    return if (resource.isFileBased) {
      resource.getSourceAsVirtualFile()?.let(PsiManager.getInstance(project)::findFile)
    }
    else {
      getDeclaringAttributeValue(project, resource)
    }
  }

  /**
   * Resolves the reference to a {@link ResourceReferencePsiElement} if any matching resources exist. We should avoid using the
   * [ResourceValue] parameter and instead provide [ResourceReference] via the other implementation below.
   */
  override fun resolveReference(
    resourceValue: ResourceValue,
    context: XmlElement,
    facet: AndroidFacet
  ): Array<out ResolveResult> {
    return resolveReference(resourceValue, context, facet, false)
  }

  override fun resolveReferenceWithDynamicFeatureModules(
    resourceValue: ResourceValue,
    element: XmlElement,
    facet: AndroidFacet
  ): Array<out ResolveResult> {
    return resolveReference(resourceValue, element, facet, true)
  }

  fun resolveReference(
    resourceValue: ResourceValue,
    context: XmlElement,
    facet: AndroidFacet,
    includeDynamicFeatures: Boolean
  ): Array<out ResolveResult> {
    var resourceName = resourceValue.resourceName ?: return ResolveResult.EMPTY_ARRAY
    val resourceType = resourceValue.type ?: return ResolveResult.EMPTY_ARRAY
    if (resourceType == ResourceType.SAMPLE_DATA) {
      resourceName = SampleDataManager.getResourceNameFromSampleReference(resourceName)
    }
    val resourceReference =
      ResourceUrl.create(resourceValue.`package`, resourceType, resourceName).resolve(context) ?: return ResolveResult.EMPTY_ARRAY
    return resolveReference(resourceReference, context, facet, includeDynamicFeatures)
  }

  @JvmOverloads
  fun resolveReference(
    resourceReference: ResourceReference,
    context: PsiElement,
    facet: AndroidFacet,
    includeDynamicFeatures: Boolean = false
  ): Array<out ResolveResult> {
    val studioResourceRepositoryManager = StudioResourceRepositoryManager.getInstance(facet)
    val resourceRepository = getRelevantResourceRepository(resourceReference, studioResourceRepositoryManager) ?: return ResolveResult.EMPTY_ARRAY
    val allItems = mutableListOf<ResolveResult>()
    if (resourceRepository.hasResources(resourceReference.namespace, resourceReference.resourceType, resourceReference.name)) {
      allItems.add(PsiElementResolveResult(ResourceReferencePsiElement(context, resourceReference)))
    }
    if (includeDynamicFeatures) {
      val moduleSystem = context.getModuleSystem() ?: return ResolveResult.EMPTY_ARRAY
      val dynamicFeatureModules = moduleSystem.getDynamicFeatureModules()
      for (module in dynamicFeatureModules) {
        val moduleResources = StudioResourceRepositoryManager.getModuleResources(module) ?: continue
        if (moduleResources.hasResources(resourceReference.namespace, resourceReference.resourceType, resourceReference.name)) {
          allItems.add(PsiElementResolveResult(ResourceReferencePsiElement(context, resourceReference)))
        }
      }
    }

    if (allItems.isEmpty() && ResourceUpdateTracer.isTracingActive()) {
      resourceRepository.traceHasResources(resourceReference)
      for (repository in resourceRepository.leafResourceRepositories) {
        if (repository is ResourceFolderRepository) {
          repository.traceHasResources(resourceReference)
        }
      }
      val file = ResourceUpdateTracer.pathForLogging(context.getParentOfType<PsiFile>(true)) ?: "unknown file"
      ResourceUpdateTracer.dumpTrace("Unresolved resource reference \"${resourceReference.resourceUrl}\" in $file")
    }

    return allItems.toArray(ResolveResult.EMPTY_ARRAY)
  }

  private fun ResourceRepository.traceHasResources(resourceReference: ResourceReference) {
    if (this !is SingleNamespaceResourceRepository || resourceReference.namespace == namespace) {
      ResourceUpdateTracer.log {
        TraceUtils.getSimpleId(this) + ".hasResources(" + resourceReference.namespace + ", " + resourceReference.resourceType +
            ", " + resourceReference.name + ") returned " +
            hasResources(resourceReference.namespace, resourceReference.resourceType, resourceReference.name)
      }
    }
  }

  override fun getXmlAttributeNameGotoDeclarationTargets(
    attributeName: String,
    namespace: ResourceNamespace,
    context: PsiElement
  ): Array<out PsiElement> {
    return getGotoDeclarationTargets(ResourceReference.attr(namespace, attributeName), context)
  }

  override fun getGotoDeclarationTargets(resourceReference: ResourceReference, context: PsiElement): Array<out PsiElement> {
    return getGotoDeclarationElements(resourceReference, context).toTypedArray()
  }

  override fun getGotoDeclarationTargetsWithDynamicFeatureModules(resourceReference: ResourceReference,
                                                                  context: PsiElement): Array<PsiElement> {
    val mainResources = getGotoDeclarationElements(resourceReference, context)
    val dynamicFeatureResources = getGotoDeclarationElementsFromDynamicFeatureModules(resourceReference, context)
    return (mainResources + dynamicFeatureResources).toTypedArray()
  }

  private fun getGotoDeclarationElements(resourceReference: ResourceReference, context: PsiElement): List<PsiElement> {
    val studioResourceRepositoryManager = StudioResourceRepositoryManager.getInstance(context) ?: return emptyList()
    val repository = getRelevantResourceRepository(resourceReference, studioResourceRepositoryManager) ?: return emptyList()
    return repository
      .getResources(resourceReference)
      .filterDefinitions(context.project)
      .mapNotNull { resolveToDeclaration(it, context.project) }
  }

  private fun getGotoDeclarationElementsFromDynamicFeatureModules(
    resourceReference: ResourceReference,
    context: PsiElement
  ): List<PsiElement> {
    val resourceList = mutableListOf<PsiElement>()
    val moduleSystem = context.getModuleSystem() ?: return emptyList()
    val dynamicFeatureModules = moduleSystem.getDynamicFeatureModules()
    for (module in dynamicFeatureModules) {
      val moduleResources = StudioResourceRepositoryManager.getModuleResources(module) ?: continue
      resourceList.addAll(moduleResources.getResources(resourceReference).mapNotNull { resolveToDeclaration(it, context.project) })
    }
    return resourceList
  }

  /**
   * Returns the [SearchScope] for a resource based on the context element. This scope contains files that can contain references to the
   * same resource as the context element. This is necessary for a ReferencesSearch to only find references to resources that are in
   * modules which are in use scope.
   *
   * @param resourceReference [ResourceReference] of a resource.
   * @param context           [PsiElement] context element from which an action is being performed.
   * @return [SearchScope] a scope that contains the files of the project which can reference same resource as context element.
   */
  @Slow
  @JvmStatic
  fun getResourceSearchScope(resourceReference: ResourceReference, context: PsiElement): SearchScope {
    val gotoDeclarationTargets = getGotoDeclarationTargets(resourceReference, context)
    val allScopes = gotoDeclarationTargets.map { ResolveScopeManager.getElementUseScope(it) }
    return if (allScopes.isEmpty()) {
      ProjectScope.getAllScope(context.project)
    }
    else {
      GlobalSearchScope.union(allScopes)
    }
  }

  /**
   * For areas of the IDE which want to pick a single resource declaration to navigate to, we pick the best possible [ResourceItem] based on
   * the supplied [FolderConfiguration], and if none exists, returns the first item returned by the [ResourceRepository]
   *
   * @param resourceReference [ResourceReference] of a resource.
   * @param context           [PsiElement] context element from which an action is being performed.
   * @param configuration     [FolderConfiguration] configuration provided that is used to pick a matching [ResourceItem].
   * @return [PsiElement] of the best matching resource declaration.
   */
  fun getBestGotoDeclarationTarget(
    resourceReference: ResourceReference,
    context: PsiElement,
    configuration: FolderConfiguration
  ): PsiElement? {
    val studioResourceRepositoryManager = StudioResourceRepositoryManager.getInstance(context) ?: return null
    val resources =
      getRelevantResourceRepository(resourceReference, studioResourceRepositoryManager)?.getResources(resourceReference) ?: return null
    val resourceItem = configuration.findMatchingConfigurable(resources) ?: resources.firstOrNull() ?: return null
    return resolveToDeclaration(resourceItem, context.project)
  }

  private fun getRelevantResourceRepository(
    resourceReference: ResourceReference,
    studioResourceRepositoryManager: StudioResourceRepositoryManager
  ): ResourceRepository? {
    return studioResourceRepositoryManager.getResourcesForNamespace(resourceReference.namespace)
  }
}

/**
 * Filters a collection of resources of the same type. If the collection contains non-ID
 * resources, returns the whole collection. If the collection contains ID resources, returns
 * a subset corresponding to ID definitions, unless this subset is empty, in which case
 * returns all resources.
 */
internal fun Collection<ResourceItem>.filterDefinitions(project: Project): Collection<ResourceItem> {
  if (size <= 1 || first().type != ResourceType.ID) {
    return this // Nothing to filter out.
  }
  val definitions = filter { it.isIdDefinition(project) }
  return definitions.ifEmpty { this }
}
