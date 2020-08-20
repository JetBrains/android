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
package com.android.tools.idea.lang.contentAccess

import com.android.tools.idea.lang.contentAccess.parser.ContentAccessLanguage
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.intellij.plugins.intelliLang.inject.config.BaseInjection
import org.jetbrains.kotlin.idea.caches.resolve.allowResolveInDispatchThread
import org.jetbrains.kotlin.idea.core.util.runInReadActionWithWriteActionPriority
import org.jetbrains.kotlin.idea.injection.KOTLIN_SUPPORT_ID
import org.jetbrains.kotlin.idea.injection.KotlinLanguageInjectionSupport
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass

private object ContentAccessKotlinInjection: BaseInjection(KOTLIN_SUPPORT_ID, ContentAccessLanguage.id, "", "")

/**
 * Provides injection of [ContentAccessLanguage] into parameters of ContentQuery/ContentDelete/ContentUpdate annotations.
 *
 * TODO(b/163135191): Delete ContentAccessInjectorImpl after KT-40924 is fixed and add injections to contentAccessInjections.xml
 */
class ContentAccessInjectorImpl(private val project: Project) : MultiHostInjector {

  companion object {
    private val ABSENT_KOTLIN_INJECTION = BaseInjection("ABSENT_KOTLIN_BASE_INJECTION")
  }

  private data class KotlinCachedInjection(val modificationCount: Long, val baseInjection: BaseInjection)

  private var KtStringTemplateExpression.cachedContentAccessInjectionWithModification: KotlinCachedInjection? by UserDataProperty(
    Key.create<KotlinCachedInjection>("CACHED_CONTENT_ACTION_INJECTION_WITH_MODIFICATION")
  )

  private val kotlinSupport: KotlinLanguageInjectionSupport? by lazy {
    ArrayList(InjectorUtils.getActiveInjectionSupports()).filterIsInstance(KotlinLanguageInjectionSupport::class.java).firstOrNull()
  }

  /**
   * Returns true if the given [KtStringTemplateExpression] is within a "where" or "selection" parameters of any of CONTENT..... annotations
   */
  private fun isContentAccessInjectionApplicable(ktHost: KtStringTemplateExpression): Boolean {
    val argument = ktHost.parent as? KtValueArgument ?: return false

    val callExpression: KtCallElement = PsiTreeUtil.getParentOfType(argument, KtCallElement::class.java) ?: return false

    val callee = (callExpression.calleeExpression as? KtConstructorCalleeExpression)?.constructorReferenceExpression as? KtNameReferenceExpression
                 ?: return false

    val primaryConstructor = allowResolveInDispatchThread { callee.mainReference.resolve() } as? KtPrimaryConstructor ?: return false
    val fqName = primaryConstructor.containingClass()?.fqName?.asString()
    if (fqName == CONTENT_QUERY_ANNOTATION || fqName == CONTENT_DELETE_ANNOTATION || fqName == CONTENT_UPDATE_ANNOTATION) {
      val ktParameterName = if (argument.isNamed()) {
        val argumentName = argument.getArgumentName()!!.asName.asString()
        primaryConstructor.valueParameters.find { it.name == argumentName }?.name
      } else {
        val argumentIndex = (argument.parent as KtValueArgumentList).arguments.indexOf(argument)
        primaryConstructor.valueParameters.getOrNull(argumentIndex)?.name
      }
      return ktParameterName == "where" || ktParameterName == "selection"
    }

    return false
  }

  // It's slightly modified copy-past from KotlinLanguageInjector#getLanguagesToInject
  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val ktHost: KtStringTemplateExpression = context as? KtStringTemplateExpression ?: return
    if (!context.isValidHost) return

    if (!ProjectRootsUtil.isInProjectOrLibSource(ktHost.containingFile.originalFile)) return
    val support = kotlinSupport ?: return

    val needImmediateAnswer = with(ApplicationManager.getApplication()) { isDispatchThread && !isUnitTestMode }
    val kotlinCachedInjection = ktHost.cachedContentAccessInjectionWithModification
    val modificationCount = PsiManager.getInstance(project).modificationTracker.modificationCount

    val baseInjection: BaseInjection = when {
      needImmediateAnswer -> {
        // Can't afford long counting or typing will be laggy. Force cache reuse even if it's outdated.
        kotlinCachedInjection?.baseInjection ?: ABSENT_KOTLIN_INJECTION
      }
      kotlinCachedInjection != null && (modificationCount == kotlinCachedInjection.modificationCount) ->
        // Cache is up-to-date
        kotlinCachedInjection.baseInjection
      else -> {
        fun computeAndCache(): BaseInjection {
          val injection = if (isContentAccessInjectionApplicable(ktHost)) ContentAccessKotlinInjection else ABSENT_KOTLIN_INJECTION
          ktHost.cachedContentAccessInjectionWithModification = KotlinCachedInjection(modificationCount, injection)
          return injection
        }

        if (ApplicationManager.getApplication().isReadAccessAllowed && ProgressManager.getInstance().progressIndicator == null) {
          // The action cannot be canceled by caller and by internal checkCanceled() calls.
          // Force creating new indicator that is canceled on write action start, otherwise there might be lags in typing.
          runInReadActionWithWriteActionPriority(::computeAndCache) ?: kotlinCachedInjection?.baseInjection ?: ABSENT_KOTLIN_INJECTION
        }
        else {
          computeAndCache()
        }
      }
    }

    if (baseInjection != ABSENT_KOTLIN_INJECTION) {
      InjectorUtils.registerInjectionSimple(ktHost, baseInjection, support, registrar)
    }
  }

  override fun elementsToInjectIn() = listOf(KtStringTemplateExpression::class.java)
}