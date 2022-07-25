/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.quickFixes

import com.android.SdkConstants.ATTR_TARGET_API
import com.android.SdkConstants.FQCN_TARGET_API
import com.android.SdkConstants.TOOLS_URI
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.lint.AndroidLintBundle
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.idea.lint.common.isAnnotationTarget
import com.android.tools.idea.lint.common.isNewLineNeededForAnnotation
import com.android.tools.idea.res.ensureNamespaceImported
import com.android.tools.idea.util.mapAndroidxName
import com.android.tools.lint.detector.api.VersionChecks.Companion.REQUIRES_API_ANNOTATION
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import java.util.Locale

/** Fix which adds a `@TargetApi` annotation at the nearest surrounding method or class  */
class AddTargetApiQuickFix(private val api: Int,
                           private val requiresApi: Boolean,
                           private val element: PsiElement,
                           private val requireClass: Boolean = false) : DefaultLintQuickFix("") { // overriding getName() below

  private fun getAnnotationValue(fullyQualified: Boolean): String {
    return AddTargetVersionCheckQuickFix.getVersionField(api, fullyQualified)
  }

  override fun getName(): String {
    return when {
      element.language == XMLLanguage.INSTANCE ->
        // The quickfixes are sorted alphabetically, but for resources we really don't want
        // this quickfix (Add Target API) to appear before Override Resource, which is
        // usually the better solution. So instead of "Add tools:targetApi" we use a label
        // which sorts later alphabetically.
        "Suppress with tools:targetApi attribute"
      requiresApi -> AndroidLintBundle.message("android.lint.fix.add.requires.api", getAnnotationValue(false))
      else -> AndroidLintBundle.message("android.lint.fix.add.target.api", getAnnotationValue(false))
    }
  }

  override fun isApplicable(startElement: PsiElement,
                            endElement: PsiElement,
                            contextType: AndroidQuickfixContexts.ContextType): Boolean {
    return getAnnotationContainer(startElement) != null
  }

  override fun apply(startElement: PsiElement, endElement: PsiElement, context: AndroidQuickfixContexts.Context) {
    when (startElement.language) {
      JavaLanguage.INSTANCE -> handleJava(startElement)
      KotlinLanguage.INSTANCE -> handleKotlin(startElement)
      XMLLanguage.INSTANCE -> handleXml(startElement)
    }
  }

  private fun handleXml(startElement: PsiElement) {
    // Find nearest method or class; can't add @TargetApi on modifier list owners like variable declarations
    // XML file? Set attribute
    val element = getAnnotationContainer(startElement) as? XmlTag ?: return
    val file = PsiTreeUtil.getParentOfType(element, XmlFile::class.java, false)
    if (file != null) {
      ensureNamespaceImported(file, TOOLS_URI)
      val codeName = SdkVersionInfo.getBuildCode(api)?.lowercase(Locale.US) ?: api.toString()
      element.setAttribute(ATTR_TARGET_API, TOOLS_URI, codeName)
    }
  }

  private fun handleJava(startElement: PsiElement) {
    // Find nearest method or class; can't add @TargetApi on modifier list owners like variable declarations
    val container = getAnnotationContainer(startElement) as? PsiModifierListOwner ?: return

    val modifierList = container.modifierList
    if (modifierList != null) {
      val project = startElement.project
      val elementFactory = JavaPsiFacade.getInstance(project).elementFactory
      val fqcn: String
      val annotationText: String
      if (requiresApi) {
        val module = ModuleUtilCore.findModuleForPsiElement(startElement)
        fqcn = module.mapAndroidxName(REQUIRES_API_ANNOTATION)
        annotationText = "@" + fqcn + "(api=" + getAnnotationValue(true) + ")"
      }
      else {
        fqcn = FQCN_TARGET_API
        annotationText = "@" + fqcn + "(" + getAnnotationValue(true) + ")"
      }
      val newAnnotation = elementFactory.createAnnotationFromText(annotationText, container)
      val annotation = AnnotationUtil.findAnnotation(container, FQCN_TARGET_API)
      if (annotation != null && annotation.isPhysical) {
        annotation.replace(newAnnotation)
      }
      else {
        val attributes = newAnnotation.parameterList.attributes
        val fix = AddAnnotationFix(fqcn, container, attributes)
        fix.invoke(project, null, container.containingFile)
      }
    }
  }

  private fun handleKotlin(startElement: PsiElement) {
    val annotationContainer = getAnnotationContainer(startElement) ?: return
    if (!FileModificationService.getInstance().preparePsiElementForWrite(annotationContainer)) {
      return
    }

    if (annotationContainer is KtModifierListOwner) {
      annotationContainer.addAnnotation(
        if (requiresApi) {
          val module = ModuleUtilCore.findModuleForPsiElement(startElement)
          FqName(module.mapAndroidxName(REQUIRES_API_ANNOTATION))
        }
        else {
          FqName(FQCN_TARGET_API)
        },
        getAnnotationValue(true),
        whiteSpaceText = if (annotationContainer.isNewLineNeededForAnnotation()) "\n" else " ")
    }
  }

  private fun getAnnotationContainer(element: PsiElement): PsiElement? {
    return when (element.language) {
      JavaLanguage.INSTANCE -> {
        var container: PsiModifierListOwner?
        if (requireClass) {
          container = PsiTreeUtil.getParentOfType<PsiModifierListOwner>(element, PsiClass::class.java)
        }
        else {
          container = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, PsiClass::class.java)
        }
        while (container != null && container is PsiAnonymousClass) {
          container = PsiTreeUtil.getParentOfType(container, PsiMethod::class.java, true, PsiClass::class.java)
        }

        container
      }
      XMLLanguage.INSTANCE -> {
        PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false)
      }
      KotlinLanguage.INSTANCE -> {
        PsiTreeUtil.findFirstParent(element) {
          if (requiresApi)
            it.isAnnotationTarget()
          else
            it.isTargetApiAnnotationValidTarget()
        }
      }
      else -> null
    }
  }

  private fun PsiElement.isTargetApiAnnotationValidTarget(): Boolean {
    return this is KtClassOrObject ||
           (this is KtFunction && this !is KtFunctionLiteral) ||
           this is KtPropertyAccessor
  }
}
