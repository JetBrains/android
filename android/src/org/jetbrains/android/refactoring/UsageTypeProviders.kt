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

import com.android.SdkConstants
import com.android.tools.idea.res.AndroidRClassBase
import com.android.tools.idea.res.isResourceDeclaration
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.impl.compiled.ClsFieldImpl
import com.intellij.psi.xml.XmlFile
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import com.intellij.util.xml.DomManager
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.augment.ManifestClass
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

private val ANDROID_MANIFEST_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("android.usageType.manifest"))

/**
 * Recognizes Android XML files and provides a better description than [com.intellij.util.xml.DomUsageTypeProvider].
 * Categorises resource elements as either usages or declarations.
 */
class AndroidResourceXmlUsageProvider : UsageTypeProviderEx {
  override fun getUsageType(element: PsiElement): UsageType? = getUsageType(element, UsageTarget.EMPTY_ARRAY)

  override fun getUsageType(element: PsiElement?, targets: Array<out UsageTarget>): UsageType? {
    val xmlFile = element?.containingFile as? XmlFile ?: return null
    val domManager = DomManager.getDomManager(xmlFile.project) ?: return null
    val resourceReferencePsiElement = (targets.firstOrNull() as? PsiElementUsageTarget)?.element as? ResourceReferencePsiElement
    return when (domManager.getFileElement(xmlFile, AndroidDomElement::class.java)?.fileDescription) {
      null -> null
      is ManifestDomFileDescription -> ANDROID_MANIFEST_USAGE_TYPE
      else -> {
        return if (resourceReferencePsiElement != null && isResourceDeclaration(element, resourceReferencePsiElement)) {
          ANDROID_RESOURCES_XML_DECLARATION_TYPE
        } else {
          ANDROID_RESOURCES_XML_USAGE_TYPE
        }
      }
    }
  }

  companion object {
    private val ANDROID_RESOURCES_XML_DECLARATION_TYPE = UsageType(AndroidBundle.messagePointer("android.usageType.resource.declaration.xml"))
    private val ANDROID_RESOURCES_XML_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("android.usageType.resource.reference.xml"))
  }
}

/**
 * Recognizes references to `R` and `Manifest` classes in code and provides a more specific description than the default [UsageType.READ].
 *
 * @see com.intellij.usages.impl.rules.UsageTypeGroupingRule.getParentGroupFor
 */
class AndroidResourceReferenceInCodeUsageTypeProvider : UsageTypeProvider {
  override fun getUsageType(element: PsiElement): UsageType? {
    if (element !is PsiReferenceExpression && element !is KtSimpleNameExpression) {
      return null
    }
    return when (val field = element.references.asSequence().mapNotNull { it.resolve() as? PsiField }.firstOrNull()) {
      is AndroidLightField -> {
        when (field.containingClass.containingClass) {
          is ManifestClass -> PERMISSION_REFERENCE_IN_CODE
          is AndroidRClassBase -> RESOURCE_REFERENCE_IN_CODE
          else -> null
        }
      }
      is ClsFieldImpl -> {
        when (field.containingClass?.containingClass?.qualifiedName) {
          SdkConstants.CLASS_MANIFEST -> PERMISSION_REFERENCE_IN_CODE
          SdkConstants.CLASS_R -> RESOURCE_REFERENCE_IN_CODE
          else -> null
        }
      }
      else -> null
    }
  }

  companion object {
    private val RESOURCE_REFERENCE_IN_CODE = UsageType(AndroidBundle.messagePointer("android.usageType.resource.reference.code"))
    private val PERMISSION_REFERENCE_IN_CODE = UsageType(AndroidBundle.messagePointer("android.usageType.permission.reference.code"))
  }
}

/**
 * Recognizes image files being used as usages.
 */
class AndroidBinaryResourceFileUsageTypeProvider : UsageTypeProvider {
  companion object {
    private val ANDROID_RESOURCE_FILE = UsageType(AndroidBundle.messagePointer("android.usageType.resource.file"))
  }

  override fun getUsageType(element: PsiElement): UsageType? {
    return if (AndroidFallbackFindUsagesProvider.isBinaryResourceFile(element)) ANDROID_RESOURCE_FILE else null
  }
}

