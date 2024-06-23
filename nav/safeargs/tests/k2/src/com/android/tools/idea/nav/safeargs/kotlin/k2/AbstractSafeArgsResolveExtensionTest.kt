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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.android.ide.common.gradle.Version
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.KotlinPluginRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.xml.XmlFile
import kotlin.reflect.full.declaredMemberProperties
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KaParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.bodies.KtParameterDefaultValueRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KtValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Rule
import org.junit.rules.RuleChain

abstract class AbstractSafeArgsResolveExtensionTest {
  protected val safeArgsRule = SafeArgsRule(SafeArgsMode.KOTLIN)

  @get:Rule
  val ruleChain = RuleChain.outerRule(KotlinPluginRule(KotlinPluginMode.K2)).around(safeArgsRule)!!

  protected fun addNavXml(
    @Language("xml") fileContent: String,
    fileName: String = "main",
  ): XmlFile =
    safeArgsRule.fixture.addFileToProject("res/navigation/${fileName}.xml", fileContent).also {
      // Initialize repository after creating resources, needed for codegen to work
      StudioResourceRepositoryManager.getInstance(safeArgsRule.androidFacet).moduleResources
    } as XmlFile

  protected fun addKotlinSource(
    @Language("kotlin") fileContent: String,
    fileName: String = "analyzedFile.kt",
  ): KtFile =
    safeArgsRule.fixture.addFileToProject("src/$fileName", fileContent).also {
      safeArgsRule.fixture.configureFromExistingVirtualFile(it.virtualFile)
    } as KtFile

  @OptIn(KtAllowAnalysisOnEdt::class)
  protected inline fun <reified TSymbol : KtSymbol, TResult> analyzeFileContent(
    @Language("kotlin") fileContent: String,
    fileName: String = "analyzedFile.kt",
    block: KtAnalysisSession.(TSymbol) -> TResult,
  ): TResult {
    val ktFile = addKotlinSource(fileContent, fileName)
    val caretReference =
      if (caret in fileContent) {
        when (val ref = safeArgsRule.fixture.getReferenceAtCaretPosition()) {
          is KtReference -> ref
          is PsiMultiReference -> ref.references.firstIsInstance<KtReference>()
          null -> error("No reference at caret")
          else -> error("Unknown reference type ${ref}")
        }
      } else {
        null
      }

    try {
      return allowAnalysisOnEdt {
        analyze(ktFile) {
          val symbol =
            if (caretReference != null) {
              caretReference.resolveToSymbols().single() as TSymbol
            } else {
              ktFile.getFileSymbol() as TSymbol
            }

          block(symbol)
        }
      }
    } finally {
      runWriteAction { ktFile.virtualFile.delete(this@AbstractSafeArgsResolveExtensionTest) }
    }
  }

  protected fun KtAnalysisSession.getRenderedMemberFunctions(
    symbol: KtNamedClassOrObjectSymbol,
    renderer: KtDeclarationRenderer = RENDERER,
  ): List<String> =
    symbol
      .getDeclaredMemberScope()
      .getCallableSymbols()
      .filterIsInstance<KtFunctionSymbol>()
      .filter { it.origin != KaSymbolOrigin.SOURCE_MEMBER_GENERATED }
      .map { it.render(renderer) }
      .toList()

  protected fun KtAnalysisSession.getPrimaryConstructorSymbol(
    symbol: KtClassOrObjectSymbol
  ): KtConstructorSymbol = symbol.getDeclaredMemberScope().getConstructors().single { it.isPrimary }

  protected fun KtAnalysisSession.getResolveExtensionPsiNavigationTargets(
    symbol: KtSymbol
  ): Collection<PsiElement> {
    assertThat(symbol.psi).isInstanceOf(KtElement::class.java)
    val ktElement = symbol.psi as KtElement
    assertThat(ktElement.isFromResolveExtension).isTrue()
    return ktElement.getResolveExtensionNavigationElements()
  }

  companion object {
    val KNOWN_SAFE_ARGS_VERSIONS: Map<String, Version> by lazy {
      SafeArgsFeatureVersions::class
        .declaredMemberProperties
        .mapNotNull { property ->
          (property.get(SafeArgsFeatureVersions) as? Version)?.let { property.name to it }
        }
        .sortedBy { it.second }
        .toMap()
    }

    val RENDERER =
      KtDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
        valueParameterRenderer = KaValueParameterSymbolRenderer.AS_SOURCE
        parameterDefaultValueRenderer = KaParameterDefaultValueRenderer.THREE_DOTS
      }
  }
}
