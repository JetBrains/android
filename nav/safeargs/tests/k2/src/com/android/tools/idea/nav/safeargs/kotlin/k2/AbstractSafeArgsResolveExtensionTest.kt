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
import com.android.tools.idea.nav.safeargs.SafeArgsFeatureVersions
import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.xml.XmlFile
import kotlin.reflect.full.declaredMemberProperties
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbols
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.rendering.KaClassTypeQualification
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOption
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.session.useSiteSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.load.java.NULLABILITY_ANNOTATIONS
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Rule

abstract class AbstractSafeArgsResolveExtensionTest {
  @get:Rule val safeArgsRule = SafeArgsRule(SafeArgsMode.KOTLIN)

  protected fun addNavXml(@Language("xml") fileContent: String, fileName: String = "main"): XmlFile =
    safeArgsRule.fixture.addFileToProject("res/navigation/${fileName}.xml", fileContent).also { safeArgsRule.waitForPendingUpdates() }
      as XmlFile

  protected fun addKotlinSource(@Language("kotlin") fileContent: String, fileName: String = "analyzedFile.kt"): KtFile =
    safeArgsRule.fixture.addFileToProject("src/$fileName", fileContent).also {
      safeArgsRule.fixture.configureFromExistingVirtualFile(it.virtualFile)
    } as KtFile

  @OptIn(KaAllowAnalysisOnEdt::class)
  protected inline fun <reified TSymbol : KaSymbol, TResult> analyzeFileContent(
    @Language("kotlin") fileContent: String,
    fileName: String = "analyzedFile.kt",
    block: KaSession.(TSymbol) -> TResult,
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
              ktFile.symbol as TSymbol
            }

          useSiteSession.block(symbol)
        }
      }
    } finally {
      runWriteAction { ktFile.virtualFile.delete(this@AbstractSafeArgsResolveExtensionTest) }
    }
  }

  @OptIn(KaExperimentalApi::class)
  protected fun KaSession.getRenderedMemberFunctions(symbol: KaClassSymbol, renderer: KaRenderer = RENDERER): List<String> =
    symbol.declaredMemberScope.callables
      .filterIsInstance<KaNamedFunctionSymbol>()
      .filter { it.origin != KaSymbolOrigin.SOURCE_MEMBER_GENERATED }
      .map { renderer.renderToString(it) }
      .toList()

  protected fun KaSession.getPrimaryConstructorSymbol(symbol: KaClassSymbol): KaConstructorSymbol =
    symbol.declaredMemberScope.constructors.single { it.isPrimary }

  @OptIn(KaExperimentalApi::class)
  protected fun KaSession.getValueParameterNamesAndTypes(
    symbol: KaFunctionSymbol,
    renderer: KaRenderer = RENDERER,
  ): Map<String, String> =
    symbol.valueParameters.associate {
      val paramName = it.name.asString()
      val typeName = renderer.renderToString(it.returnType)
      paramName to typeName
    }

  @OptIn(KaExperimentalApi::class)
  protected fun KaSession.getResolveExtensionPsiNavigationTargets(symbol: KaSymbol): Collection<PsiElement> {
    assertThat(symbol.psi).isInstanceOf(KtElement::class.java)
    val ktElement = symbol.psi as KtElement
    assertThat(ktElement.isFromResolveExtension).isTrue()
    return ktElement.resolveExtensionNavigationElements
  }

  companion object {
    val KNOWN_SAFE_ARGS_VERSIONS: Map<String, Version> by lazy {
      SafeArgsFeatureVersions::class
        .declaredMemberProperties
        .mapNotNull { property -> (property.get(SafeArgsFeatureVersions) as? Version)?.let { property.name to it } }
        .sortedBy { it.second }
        .toMap()
    }

    @OptIn(KaExperimentalApi::class)
    val RENDERER: KaRenderer =
      KaRenderer.default.copy {
        set(KaRenderingOption.ClassTypeQualification, KaClassTypeQualification.FULLY_QUALIFIED)
        set(KaRenderingOption.Annotations) { annotated ->
          annotated.annotations.filter { annotation ->
            val fqName = annotation.classId?.asSingleFqName()
            fqName == null || (fqName !in NULLABILITY_ANNOTATIONS && fqName != StandardNames.FqNames.parameterName)
          }
        }
      }
  }
}
