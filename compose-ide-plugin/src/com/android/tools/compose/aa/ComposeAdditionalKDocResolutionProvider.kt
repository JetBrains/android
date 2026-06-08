/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.compose.aa

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.KaSpiExtensionPoint
import org.jetbrains.kotlin.analysis.api.symbols.KaAdditionalKDocResolutionProvider
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement

@OptIn(KaSpiExtensionPoint::class)
class ComposeAdditionalKDocResolutionProvider : KaAdditionalKDocResolutionProvider {
  override fun resolveKdocFqName(analysisSession: KaSession, fqName: FqName, contextElement: KtElement): Collection<KaSymbol> {
    if (fqName.isRoot) return emptyList()

    val project = contextElement.project

    // Query library sources scope specifically to resolve samples inside secondary *-samples-sources.jar files.
    val scope = KotlinSourceFilterScope.librarySources(GlobalSearchScope.everythingScope(project), project)
    val shortName = fqName.shortName().asString()

    val functions = KotlinFunctionShortNameIndex[shortName, project, scope]
    val classes = KotlinClassShortNameIndex[shortName, project, scope]
    val properties = KotlinPropertyShortNameIndex[shortName, project, scope]

    with(analysisSession) {
      return (functions + classes + properties)
        .filter { it.fqName == fqName }
        .mapNotNull {
          try {
            it.symbol
          } catch (e: IllegalArgumentException) {
            // Catch IllegalArgumentException to handle cases where the symbol's declaration is out of
            // scope for the current analysis session (e.g. KaBaseIllegalPsiException).
            null
          }
        }
    }
  }
}
