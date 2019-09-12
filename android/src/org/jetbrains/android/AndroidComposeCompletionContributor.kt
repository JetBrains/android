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
package org.jetbrains.android

import com.android.tools.idea.flags.StudioFlags.COMPOSE_COMPLETION_ICONS
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.ui.LayeredIcon
import icons.AndroidIcons
import icons.StudioIcons
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtNamedFunction

private const val COMPOSABLE = "androidx.compose.Composable"
private val COMPOSABLE_FQNAME = FqName(COMPOSABLE)

/** TODO: overlay android on some other icon? */
private val COMPOSABLE_FUNCTION_ICON = LayeredIcon(AndroidIcons.Android)

/**
 * Modifies [LookupElement]s for composable functions, to improve Compose editing UX.
 */
class AndroidComposeCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, resultSet: CompletionResultSet) {
    if (!customizeCompletionForCompose() || !isInsideComposableCode(parameters)) return

    ApplicationManager.getApplication().invokeLater {
      val completionProgress = CompletionServiceImpl.getCurrentCompletionProgressIndicator()
      if (completionProgress != null) {
        val advertiser = completionProgress.lookup.advertiser
        advertiser.clearAdvertisements()
        advertiser.addAdvertisement("Hello from Compose!", AndroidIcons.Android)
      }
    }

    resultSet.runRemainingContributors(parameters) { completionResult ->
      val psi = completionResult.lookupElement.psiElement
      val newResult = if (psi != null && psi.isComposableFunction()) {
        completionResult.withLookupElement(ComposeLookupElement(completionResult.lookupElement))
      }
      else {
        completionResult
      }

      resultSet.passResult(newResult)
    }
  }

  private fun PsiElement.isComposableFunction(): Boolean {
    return this is KtNamedFunction && annotationEntries.any { it.getQualifiedName() == COMPOSABLE }
  }

  private fun isInsideComposableCode(parameters: CompletionParameters): Boolean {
    // TODO: Figure this out.
    return parameters.originalFile.language == KotlinLanguage.INSTANCE
  }

  private fun customizeCompletionForCompose(): Boolean {
    val flags = listOf(
      COMPOSE_COMPLETION_ICONS
    )
    return flags.any { it.get() }
  }
}

/**
 * Wraps original Kotlin [LookupElement]s for composable functions to make them stand out more.
 */
private class ComposeLookupElement(original: LookupElement) : LookupElementDecorator<LookupElement>(original) {

  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)
    if (COMPOSE_COMPLETION_ICONS.get()) {
      presentation.icon = COMPOSABLE_FUNCTION_ICON
      val type = (psiElement as? KtNamedFunction)?.valueParameters?.lastOrNull()?.type()
      if (type != null && type.annotations.hasAnnotation(COMPOSABLE_FQNAME)) {
        presentation.setTypeText(null, StudioIcons.LayoutEditor.Palette.VIEW_STUB)
      }
      else {
        presentation.setTypeText(null, null)
      }
    }
  }
}
