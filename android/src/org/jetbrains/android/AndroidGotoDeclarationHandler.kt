// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.res.AndroidRClassBase
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.compiled.ClsFieldImpl
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.augment.ManifestClass
import org.jetbrains.android.augment.ManifestInnerClass
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
        AndroidResourceToPsiResolver.getInstance().getGotoDeclarationTargets(targetElement.resourceReference, sourceElement)
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

  private fun collectManifestElements(nestedClassName: String,
                                      fieldName: String,
                                      facet: AndroidFacet): Array<PsiElement> {
    val result = ArrayList<PsiElement>()
    val manifest = facet.manifest ?: return PsiElement.EMPTY_ARRAY

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
