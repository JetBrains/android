/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.model

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.StyleItemResourceValue
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.ResourceVisitor
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.android.resources.aar.AarResourceRepository
import com.android.tools.idea.editors.theme.ResolutionUtils
import com.android.tools.idea.res.AndroidDependenciesCache
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.SampleDataResourceItem
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet

/**
 * Returns the list of local resources of the [forFacet] module.
 */
@Slow
fun getModuleResources(forFacet: AndroidFacet, type: ResourceType, typeFilters: List<TypeFilter>): ResourceSection {
  val moduleRepository = StudioResourceRepositoryManager.getModuleResources(forFacet)
  val sortedResources = moduleRepository.namespaces.flatMap { namespace ->
    moduleRepository.getResourcesAndApplyFilters(namespace, type, true, typeFilters, forFacet)
  }.sortedBy { it.name }

  return createResourceSection(forFacet.module.name, sortedResources)
}

/**
 * Returns a list of resources from other modules that [forFacet] depends on.
 *
 * Eg:
 *
 * If **moduleA** depends on **moduleB**, this function returns the list of local resources of **moduleB**.
 */
@Slow
fun getDependentModuleResources(forFacet: AndroidFacet,
                                type: ResourceType,
                                typeFilters: List<TypeFilter>): List<ResourceSection> {
  return AndroidDependenciesCache.getAndroidResourceDependencies(forFacet.module).asSequence()
    .flatMap { dependentFacet ->
      val moduleRepository = StudioResourceRepositoryManager.getModuleResources(dependentFacet)
      moduleRepository.namespaces.asSequence()
        .map { namespace -> moduleRepository.getResourcesAndApplyFilters(namespace, type, true, typeFilters, forFacet) }
        .filter { it.isNotEmpty() }
        .map { createResourceSection(dependentFacet.module.name, it.sortedBy(ResourceItem::getName)) }
    }.toList()
}

/**
 * Returns a map from the library name to its resource items
 */
@Slow
fun getLibraryResources(forFacet: AndroidFacet, type: ResourceType, typeFilters: List<TypeFilter>): List<ResourceSection> {
  val repoManager = StudioResourceRepositoryManager.getInstance(forFacet)
  return repoManager.libraryResources
    .flatMap { lib ->
      // Create a section for each library
      lib.namespaces.asSequence()
        .map { namespace -> return@map lib.getResourcesAndApplyFilters(namespace, type, false, typeFilters, forFacet) }
        .filter { it.isNotEmpty() }
        .map { createResourceSection(userReadableLibraryName(lib), it.sortedBy(ResourceItem::getName)) }.toList()
    }
}

/** Returns [ResourceType.SAMPLE_DATA] resources that match the content type of the requested [type]. E.g: Images for Drawables. */
@Slow
fun getSampleDataResources(forFacet: AndroidFacet, type: ResourceType): ResourceSection {
  val repoManager = StudioResourceRepositoryManager.getInstance(forFacet).appResources
  val resources = repoManager.namespaces.flatMap { namespace ->
    repoManager.getResources(namespace, ResourceType.SAMPLE_DATA) { sampleItem ->
      if (sampleItem is SampleDataResourceItem) {
        when (type) {
          ResourceType.SAMPLE_DATA -> true
          ResourceType.DRAWABLE -> sampleItem.contentType == SampleDataResourceItem.ContentType.IMAGE
          ResourceType.STRING -> sampleItem.contentType != SampleDataResourceItem.ContentType.IMAGE
          else -> false
        }
      }
      else false
    }
  }
  return createResourceSection(ResourceType.SAMPLE_DATA.displayName, resources)
}

/**
 * Returns a [ResourceSection] of the Android Framework resources.
 */
@Slow
fun getAndroidResources(forFacet: AndroidFacet, type: ResourceType, typeFilters: List<TypeFilter>): ResourceSection? {
  val repoManager = StudioResourceRepositoryManager.getInstance(forFacet)
  val languages = if (type == ResourceType.STRING) repoManager.languagesInProject else emptySet<String>()
  val frameworkRepo = repoManager.getFrameworkResources(languages) ?: return null
  val resources = frameworkRepo.namespaces.flatMap { namespace ->
    return@flatMap frameworkRepo.getResourcesAndApplyFilters(namespace, type, false, typeFilters, forFacet)
  }.sortedBy { it.name }
  return createResourceSection(SdkConstants.ANDROID_NS_NAME, resources)
}

/**
 * Returns a [ResourceSection] of attributes ([ResourceType.ATTR]) from the theme defined either in the module's manifest or from the
 * configuration in the current file (e.g: the theme defined in the Layout Editor).
 *
 * However, this approach means that it might not display some attributes that are expected to be used in a layout if the configuration is
 * not set up correctly.
 */
@Slow
fun getThemeAttributes(forFacet: AndroidFacet,
                       type: ResourceType,
                       typeFilters: List<TypeFilter>,
                       resourceResolver: ResourceResolver): ResourceSection {
  val repoManager = StudioResourceRepositoryManager.getInstance(forFacet)
  val projectThemeAttributes =
    repoManager.projectResources.let { resourceRepository ->
      resourceRepository.getNonPrivateAttributeResources(resourceRepository.namespaces.toList(),
                                                         resourceResolver,
                                                         forFacet,
                                                         type,
                                                         typeFilters)
    }
  val libraryThemeAttributes = hashMapOf<String, ResourceItem>()
  repoManager.libraryResources.forEach { resourceRepository ->
    resourceRepository.getNonPrivateAttributeResources(resourceRepository.namespaces.toList(),
                                                       resourceResolver,
                                                       forFacet,
                                                       type,
                                                       typeFilters).let { libraryThemeAttributes.putAll(it) }
  }
  // Framework resources should have visibility properly defined. So we only get the public ones.
  val languages = if (type == ResourceType.STRING) repoManager.languagesInProject else emptySet<String>()
  val frameworkResources = StudioResourceRepositoryManager.getInstance(forFacet).getFrameworkResources(languages)
  val androidThemeAttributes = frameworkResources?.let {
    frameworkResources.getPublicOnlyAttributeResources(listOf(ResourceNamespace.ANDROID),
                                                       resourceResolver,
                                                       forFacet,
                                                       type,
                                                       typeFilters)
  } ?: hashMapOf()

  // If any attributes are repeated, override them.
  // Project attributes takes precedence over external libraries attributes.
  // TODO: Eventually we'd want to give the user the option to use the namespace they want for overridden attributes.
  //  Eg: Choose '?attr/textColorPrimary' or '?android:attr/textColorPrimary'
  val themeAttributesMap: HashMap<String, ResourceItem> = androidThemeAttributes
  themeAttributesMap.putAll(libraryThemeAttributes)
  themeAttributesMap.putAll(projectThemeAttributes)
  val themeAttributes = themeAttributesMap.values.sortedBy { it.name }

  return createResourceSection("Theme attributes", themeAttributes, type)
}

private fun userReadableLibraryName(lib: AarResourceRepository) =
  lib.libraryName?.let {
    GradleCoordinate.parseCoordinateString(it)?.artifactId ?: it
  }
  ?: "library - failed name"

private fun ResourceRepository.getResourcesAndApplyFilters(namespace: ResourceNamespace,
                                                           type: ResourceType,
                                                           isLocalRepo: Boolean,
                                                           typeFilters: List<TypeFilter>,
                                                           facet: AndroidFacet): Collection<ResourceItem> {
  if (isLocalRepo) {
    if (typeFilters.isEmpty()) {
      return getResources(namespace, type).values()
    }
    else {
      return getResources(namespace, type) { resourceItem -> resourceItem.isValidForFilters(typeFilters, facet) }
    }
  }
  else {
    // Only public resources for external resources.
    val publicResources = getPublicResources(namespace, type)
    if (typeFilters.isEmpty()) {
      return publicResources
    }
    else {
      return publicResources.filter { resourceItem -> resourceItem.isValidForFilters(typeFilters, facet) }
    }
  }
}

private fun ResourceItem.isValidForFilters(typeFilters: List<TypeFilter>, facet: AndroidFacet): Boolean {
  val dataManager = ResourceDataManager(facet) // TODO(148630535): This should not depend on ResourceDataManager, should be refactored out.
  val psiElement = runReadAction { dataManager.findPsiElement(this) } ?: return false
  when (psiElement) {
    is XmlFile -> {
      val tag = runReadAction { psiElement.rootTag } ?: return false
      // To verify XML Tag filters, we just compare the XML root tag value to the filter value, but unless we are intentionally filtering
      // for data-binding layouts, we then take the first non-data XML tag.
      typeFilters.forEach { filter ->
        if (filter.kind == TypeFilterKind.XML_TAG) {
          if (tag.name == filter.value) {
            return true
          }
          else if (tag.name == SdkConstants.TAG_LAYOUT) {
            // Is data-binding, look for the non-data tag.
            val dataBindingViewTag = tag.childrenOfType<XmlTag>().firstOrNull { it.name != SdkConstants.TAG_DATA } ?: return false
            if (dataBindingViewTag.name == filter.value) {
              return true
            }
          }
        }
        if (filter.kind == TypeFilterKind.XML_TAG && tag.name == filter.value) {
          return true
        }
      }
      return false
    }
    is PsiBinaryFile -> {
      val name = psiElement.name
      // To verify File filters, we look for the file extension, but we take the extension as the string after the first '.', since the
      // VirtualFile#getExtension method does not consider '.9.png' as an extension (returns 'png').
      typeFilters.forEach { filter ->
        val isFileFilter = filter.kind == TypeFilterKind.FILE
        val extension = name.substring(name.indexOf('.'))
        if (isFileFilter && extension.equals(filter.value, true)) {
          return true
        }
      }
      return false
    }
    else -> return false
  }
}

/** Returns only the attributes in a [ResourceRepository] that are explicitly [ResourceVisibility.PUBLIC]. */
private fun ResourceRepository.getPublicOnlyAttributeResources(namespaces: List<ResourceNamespace>,
                                                               resourceResolver: ResourceResolver,
                                                               facet: AndroidFacet,
                                                               targetType: ResourceType,
                                                               typeFilters: List<TypeFilter>): HashMap<String, ResourceItem> {
  return getAttributeResources(namespaces, resourceResolver, facet, targetType, typeFilters) {
    return@getAttributeResources (it as ResourceItemWithVisibility).visibility == ResourceVisibility.PUBLIC
  }
}

/**
 * Returns the attributes in a [ResourceRepository] that are not explicitly [ResourceVisibility.PRIVATE]. So it's assumed that if they are
 * not [ResourceVisibility.PRIVATE] they're as good as public since visibility is not always specified.
 */
private fun ResourceRepository.getNonPrivateAttributeResources(namespaces: List<ResourceNamespace>,
                                                               resourceResolver: ResourceResolver,
                                                               facet: AndroidFacet,
                                                               targetType: ResourceType,
                                                               typeFilters: List<TypeFilter>): HashMap<String, ResourceItem> {
  return getAttributeResources(namespaces, resourceResolver, facet, targetType, typeFilters) {
    return@getAttributeResources if (it is ResourceItemWithVisibility) {
      it.visibility != ResourceVisibility.PRIVATE
    }
    else true
  }
}

/**
 * Common function to extract theme attributes resources. Returns a map of the resource name and the actual [ResourceItem].
 *
 * Returns a map since it's expected for some of these attributes to be overridden from different [ResourceRepository]s. The map
 * simplifies that process.
 */
private fun ResourceRepository.getAttributeResources(namespaces: List<ResourceNamespace>,
                                                     resourceResolver: ResourceResolver,
                                                     facet: AndroidFacet,
                                                     targetType: ResourceType,
                                                     typeFilters: List<TypeFilter>,
                                                     visibilityFilter: (ResourceItem) -> Boolean): HashMap<String, ResourceItem> {
  val attributesMap = hashMapOf<String, ResourceItem>()
  accept { resourceItem ->
    if (resourceItem.type == ResourceType.ATTR && namespaces.contains(resourceItem.namespace) && visibilityFilter(resourceItem)) {
      resourceResolver.findItemInTheme(resourceItem.referenceToSelf)?.let { attributeValue ->
        if (attributeValue is StyleItemResourceValue && (ResolutionUtils.getAttrType(attributeValue, resourceResolver) == targetType)) {
          if (typeFilters.isNotEmpty()) {
            // Should be filtered
            val resolvedThemeAttribute = resourceResolver.resolveResValue(attributeValue)
            if (resolvedThemeAttribute is ResourceItem && resolvedThemeAttribute.isValidForFilters(typeFilters, facet)) {
              attributesMap[resourceItem.name] = resourceItem
            }
          }
          else {
            attributesMap[resourceItem.name] = resourceItem
          }
        }
      }
    }
    return@accept ResourceVisitor.VisitResult.CONTINUE
  }
  return attributesMap
}