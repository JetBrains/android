/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_NAME
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.res.AndroidRClassBase
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.res.getResourceName
import com.android.tools.idea.res.isValueBased
import com.android.tools.idea.res.resolve
import com.android.tools.idea.res.resourceNamespace
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.refactoring.rename.RenameHandler
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper
import org.jetbrains.android.util.AndroidResourceUtil

/**
 * A fake PsiElement that wraps a [ResourceReference].
 *
 * Android resources can have multiple definitions, but most editor operations should not need to know how many definitions a given resource
 * has or which one was used to start a refactoring, e.g. a rename. A [ResourceReferencePsiElement] implements [PsiElement] so can be used
 * in editor APIs, but abstracts away how the resource was actually defined.
 *
 * Most [PsiReference]s related to Android resources resolve to instances of this class (other than R fields, since we don't control
 * references in Java/Kotlin). We do this instead of using [PsiPolyVariantReference] and resolving to all definitions, because checking if a
 * resource is defined at all can be done using resource repositories and is faster than determining the exact XML attribute that defines
 * it. Another reason is that most refactorings (e.g. renaming) are only passed one [PsiElement] as their "target". This class is used to
 * recognize all mentions of Android resources (XML, * "@+id", R fields) as early as possible. Custom [FindUsagesHandler],
 * [GotoDeclarationHandler] and [RenameHandler] are used to handle all these cases uniformly.
 */
data class ResourceReferencePsiElement(
  val resourceReference: ResourceReference,
  val psiManager: PsiManager
) : FakePsiElement() {

  companion object {

    @JvmStatic
    fun create(element: PsiElement): ResourceReferencePsiElement? {
      return when (element) {
        is ResourceReferencePsiElement -> element
        is AndroidLightField -> convertAndroidLightField(element)
        is XmlAttributeValue -> convertXmlAttributeValue(element)
        is PsiFile -> convertPsiFile(element)
        is LazyValueResourceElementWrapper -> ResourceReferencePsiElement(element.resourceInfo.resource.referenceToSelf, element.manager)
        else -> null
      }
    }

    private fun convertPsiFile(element: PsiFile) : ResourceReferencePsiElement? {
      if (!AndroidResourceUtil.isInResourceSubdirectory(element, null)) {
        return null
      }
      val resourceFolderType = getFolderType(element) ?: return null
      val resourceType = FolderTypeRelationship.getNonIdRelatedResourceType(resourceFolderType)
      val resourceNamespace = element.resourceNamespace ?: return null
      val resourceName = getResourceName(element)
      return ResourceReferencePsiElement(ResourceReference(resourceNamespace, resourceType, resourceName), element.manager)
    }

    private fun convertAndroidLightField(element: AndroidLightField) : ResourceReferencePsiElement? {
      if (element.containingClass.containingClass !is AndroidRClassBase) {
        return null
      }
      val resourceClassName = AndroidResourceUtil.getResourceClassName(element) ?: return null
      val resourceType = ResourceType.fromClassName(resourceClassName) ?: return null
      return ResourceReferencePsiElement(ResourceReference(ResourceNamespace.TODO(), resourceType, element.name), element.manager)
    }

    /**
     * Attempts to convert an XmlAttributeValue into ResourceReferencePsiElement, if the attribute references or defines a resources.
     *
     * These are cases where the [com.intellij.util.xml.ResolvingConverter] does not provide a PsiElement and so the dom provides the
     * invocation element.
     * TODO: Implement resolve in all ResolvingConverters so that we never get the underlying element.
     */
    private fun convertXmlAttributeValue(element: XmlAttributeValue) : ResourceReferencePsiElement? {
      val resUrl = ResourceUrl.parse(element.value)
      if (resUrl != null) {
        val resourceReference = resUrl.resolve(element) ?: return null
        return ResourceReferencePsiElement(resourceReference, element.manager)
      }
      else {
        // Instances of value resources
        val tag = element.parentOfType<XmlTag>() ?: return null
        if ((element.parent as XmlAttribute).name != ATTR_NAME) return null
        val type = AndroidResourceUtil.getResourceTypeForResourceTag(tag) ?: return null
        if (!type.isValueBased()) return null
        val name = element.value
        val resourceReference = ResourceReference(ResourceNamespace.TODO(), type, name)
        return ResourceReferencePsiElement(resourceReference, element.manager)
      }
    }
  }

  override fun getManager(): PsiManager = psiManager

  override fun getProject() = psiManager.project

  override fun getParent(): PsiElement? = null

  override fun isValid() = true

  override fun isWritable() = false

  override fun getContainingFile(): PsiFile? = null

  override fun getName() = resourceReference.name

  override fun isEquivalentTo(element: PsiElement) = create(element) == this

  /**
   * Element description for Android resources.
   */
  class ResourceReferencePsiElementDescriptorProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
      val resourceReference = (element as? ResourceReferencePsiElement)?.resourceReference ?: return null
      return when (location) {
        is UsageViewTypeLocation -> "${resourceReference.resourceType.displayName} Resource"
        else -> resourceReference.name
      }
    }
  }
}