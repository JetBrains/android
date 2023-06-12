/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.compose.code

import androidx.compose.compiler.plugins.kotlin.isComposableInvocation
import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.isComposeEnabled
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import icons.StudioIcons
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.editor.fixers.range
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

/** [LineMarkerProviderDescriptor] that adds a gutter icon on @Composable function invocations. */
class ComposeLineMarkerProviderDescriptor : LineMarkerProviderDescriptor() {

  override fun getName() = ComposeBundle.message("composable.line.marker.tooltip")
  override fun isEnabledByDefault() = false
  override fun getId() = "ComposeLineMarkerProviderDescriptor"
  override fun getIcon() = StudioIcons.Compose.Editor.COMPOSABLE_FUNCTION

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (element !is LeafPsiElement || element.elementType != KtTokens.IDENTIFIER || !isComposeEnabled(element)) return null

    val parentFunction = element.parent.parent as? KtCallExpression ?: return null
    if (!isComposableInvocation(parentFunction)) return null

    return LineMarkerInfo<PsiElement>(element,
                                      element.range,
                                      StudioIcons.Compose.Editor.COMPOSABLE_FUNCTION,
                                      { ComposeBundle.message("composable.line.marker.tooltip") },
                                      /* navHandler = */ null,
                                      GutterIconRenderer.Alignment.RIGHT,
                                      { ComposeBundle.message("composable.line.marker.tooltip") })
  }

  companion object {
    private val ANALYSIS_RESULT_KEY = Key<CachedValue<AnalysisResult>>("ComposeLineMarkerProviderDescriptor.AnalysisResult")

    private fun isComposableInvocation(parentFunction: KtCallExpression): Boolean {
      val containingFile = parentFunction.containingFile as? KtFile ?: return false

      val analysisResult = CachedValuesManager.getManager(parentFunction.project).getCachedValue(
        containingFile,
        ANALYSIS_RESULT_KEY,
        getCachedValueProvider(containingFile),
        /* trackValue = */ false)

      return parentFunction.getResolvedCall(analysisResult.bindingContext)?.isComposableInvocation() ?: false
    }

    private fun getCachedValueProvider(ktFile: KtFile) = CachedValueProvider {
      CachedValueProvider.Result.create(ktFile.analyzeWithAllCompilerChecks(), ktFile, PsiModificationTracker.MODIFICATION_COUNT)
    }
  }
}
