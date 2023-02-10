// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.StyleableResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.res.AndroidRClassBase
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.res.requiresDynamicFeatureModuleResources
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.execution.impl.NAME_ATTR
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.compiled.ClsFieldImpl
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.augment.ManifestClass
import org.jetbrains.android.augment.ManifestInnerClass
import org.jetbrains.android.augment.StyleableAttrLightField
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.dom.manifest.ManifestElementWithRequiredName
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import java.util.ArrayList

/**
 * GotoDeclarationHandler for Resources. This class handles multiple cases:
 * * R and Manifest fields in Java and Kotlin files. See [AndroidLightField] and [ClsFieldImpl]
 * * Resource references in XML files. See [ResourceReferencePsiElement]
 */
class AndroidGotoDeclarationHandler : GotoDeclarationHandler {

  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
    if (sourceElement == null) {
      return PsiElement.EMPTY_ARRAY
    }
    return when (val targetElement = TargetElementUtil.getInstance().findTargetElement(editor,
                                                                                       TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED,
                                                                                       offset)) {
      is ResourceReferencePsiElement -> {
        // Depending on the context, we might need to check DynamicFeature modules.
        if (requiresDynamicFeatureModuleResources(sourceElement)) {
          AndroidResourceToPsiResolver.getInstance().getGotoDeclarationTargetsWithDynamicFeatureModules(
            targetElement.resourceReference,
            sourceElement.containingFile)
        } else {
          // We take the containing file of the source element as context as ModuleUtilCore can only find the module of an AAR file, not an
          // element in that file.
          AndroidResourceToPsiResolver.getInstance().getGotoDeclarationTargets(targetElement.resourceReference, sourceElement.containingFile)
        }
      }
      is StyleableAttrLightField -> {
        // For Styleable Attr fields, we go to the reference of the attr inside the declare styleable, not necessarily the attr definition
        val styleableAttrUrl = targetElement.styleableAttrFieldUrl
        val styleables = StudioResourceRepositoryManager.getInstance(sourceElement)
            ?.getResourcesForNamespace(styleableAttrUrl.styleable.namespace)
            ?.getResources(styleableAttrUrl.styleable) ?: return PsiElement.EMPTY_ARRAY
        return findAttrElementsInStyleables(styleables, targetElement)
      }
      is AndroidLightField -> {
        return when (targetElement.containingClass.containingClass) {
          is AndroidRClassBase -> {
            val referencePsiElement = ResourceReferencePsiElement.create(targetElement) ?: return PsiElement.EMPTY_ARRAY
            AndroidResourceToPsiResolver.getInstance().getGotoDeclarationTargets(referencePsiElement.resourceReference, sourceElement)
          }
          is ManifestClass -> {
            val androidFacet = sourceElement.androidFacet ?: return PsiElement.EMPTY_ARRAY
            val field = targetElement.name
            val innerClassName = (targetElement.parent as ManifestInnerClass).name
            collectManifestElements(innerClassName, field, androidFacet)
          }
          else -> PsiElement.EMPTY_ARRAY
        }
      }
      is ClsFieldImpl -> {
        // This is the case for framework resources in Java and Kotlin files.
        val containingClass = targetElement.containingClass ?: return PsiElement.EMPTY_ARRAY
        val resourceType = containingClass.name?.let { ResourceType.fromClassName(it) } ?: return PsiElement.EMPTY_ARRAY
        return if (SdkConstants.CLASS_R == containingClass.containingClass?.qualifiedName) {
          AndroidResourceToPsiResolver.getInstance().getGotoDeclarationTargets(
            ResourceReference(ResourceNamespace.ANDROID, resourceType, targetElement.name), sourceElement)
        }
        else {
          PsiElement.EMPTY_ARRAY
        }
      }
      else -> PsiElement.EMPTY_ARRAY
    }
  }

  private fun findAttrElementsInStyleables(styleables: List<ResourceItem>, targetElement: StyleableAttrLightField): Array<PsiElement> {
    val result = mutableListOf<PsiElement>()
    val styleableAttrUrl = targetElement.styleableAttrFieldUrl
    for (styleable in styleables) {
      val resourceValue = styleable.resourceValue
      if (resourceValue is StyleableResourceValue) {
        for (attributeValue in resourceValue.allAttributes) {
          if (attributeValue.asReference() == styleableAttrUrl.attr) {
            val declaration =
              AndroidResourceToPsiResolver.getInstance()
                .resolveToDeclaration(styleable, targetElement.project) as? XmlAttributeValue ?: continue
            val attributeInStyleable = findAttributeResourceInStyleableTag(declaration, attributeValue.asReference()) ?: continue
            result.add(attributeInStyleable)
          }
        }
      }
    }
    return result.toTypedArray()
  }

  private fun findAttributeResourceInStyleableTag(
    styleableDeclaration: XmlAttributeValue,
    asReference: ResourceReference
  ): XmlAttributeValue? {
    val resourceType = getFolderType(styleableDeclaration.containingFile)
    if (resourceType != ResourceFolderType.VALUES) return null
    val xmlAttribute = styleableDeclaration.parentOfType<XmlAttribute>() ?: return null
    if (xmlAttribute.name != SdkConstants.ATTR_NAME) return null
    val xmlTag = xmlAttribute.parentOfType<XmlTag>() ?: return null
    if (xmlTag.name != SdkConstants.TAG_DECLARE_STYLEABLE) return null
    val subTags = xmlTag.subTags
    for (subTag in subTags) {
      val attributeValue = subTag.getAttribute(NAME_ATTR)?.valueElement ?: continue
      val resourceReferencePsiElement = ResourceReferencePsiElement.create(attributeValue)
      if (asReference == resourceReferencePsiElement?.resourceReference) {
        return attributeValue
      }
    }
    return null
  }

  private fun collectManifestElements(nestedClassName: String,
                                      fieldName: String,
                                      facet: AndroidFacet): Array<PsiElement> {
    val result = ArrayList<PsiElement>()
    val manifest = Manifest.getMainManifest(facet) ?: return PsiElement.EMPTY_ARRAY

    val list: List<ManifestElementWithRequiredName> = when (nestedClassName) {
      "permission" ->  manifest.permissions
      "permission_group" ->  manifest.permissionGroups
      else -> return PsiElement.EMPTY_ARRAY
    }

    for (domElement in list) {
      val nameAttribute = domElement.name
      val unqualifiedName = StringUtil.getShortName(StringUtil.notNullize(nameAttribute.value))

      if (AndroidUtils.equal(unqualifiedName, fieldName, false)) {
        val psiElement = nameAttribute.xmlAttributeValue

        if (psiElement != null) {
          result.add(psiElement)
        }
      }
    }

    return result.toTypedArray()
  }
}
