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

import com.android.SdkConstants
import com.android.tools.idea.nav.safeargs.index.NavArgumentData
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.module.NavInfo
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.android.tools.idea.nav.safeargs.psi.java.toCamelCase
import com.android.tools.idea.nav.safeargs.psi.xml.findChildTagElementByNameAttr
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.name.ClassId

/*
 * data class <ARGS_CLASS>(
 *   val firstArgument: FirstArgumentType,
 *   ...
 * ) : androidx.navigation.NavArgs {
 *   fun toBundle(): android.os.Bundle { ... }
 *
 *   #if TO_SAVED_STATE_HANDLE
 *   fun toSavedStateHandle(): androidx.lifecycle.SavedStateHandle { ... }
 *   #endif
 *
 *   companion object {
 *     fun fromBundle(bundle: android.os.Bundle): <ARGS_CLASS> { ... }
 *
 *     #if TO_SAVED_STATE_HANDLE
 *     fun fromSavedStateHandle(savedStateHandle: androidx.lifecycle.SavedStateHandle): <ARGS_CLASS> { ... }
 *     #endif
 *   }
 * }
 *
 */

internal class ArgsClassResolveExtensionFile(
  private val navInfo: NavInfo,
  destination: NavDestinationData,
  classId: ClassId,
  private val destinationXmlTag: XmlTag?,
) : SafeArgsResolveExtensionFile(classId) {
  private val resolvedArguments =
    if (navInfo.navVersion >= SafeArgsFeatureVersions.ADJUST_PARAMS_WITH_DEFAULTS) {
      destination.arguments.sortedBy { it.defaultValue != null }
    } else {
      destination.arguments
    }

  override fun KaSession.getNavigationElementForDeclaration(
    symbol: KaDeclarationSymbol
  ): PsiElement? =
    when (symbol) {
      is KtVariableLikeSymbol -> getNavigationElementForVariableLikeSymbol(symbol)
      else -> destinationXmlTag
    }

  private fun KtAnalysisSession.getNavigationElementForVariableLikeSymbol(
    symbol: KtVariableLikeSymbol
  ): PsiElement? {
    val matchingArgument =
      resolvedArguments.firstOrNull {
        it.name.toCamelCase() == symbol.name.identifierOrNullIfSpecial
      }
    return matchingArgument?.argumentTag ?: destinationXmlTag
  }

  override val fallbackPsi
    get() = destinationXmlTag

  override fun StringBuilder.buildClassBody() {
    appendLine("data class ${classId.shortClassName}(")
    for (arg in resolvedArguments) {
      append("  val ${arg.name.toCamelCase()}: ${arg.resolveKotlinType(navInfo.packageName)}")
      if (arg.defaultValue != null) {
        appendLine(" = TODO(),  // ${arg.defaultValue}")
      } else {
        appendLine(",")
      }
    }
    appendLine(") : androidx.navigation.NavArgs {")
    appendLine("  fun toBundle(): android.os.Bundle = TODO()")
    if (navInfo.navVersion >= SafeArgsFeatureVersions.TO_SAVED_STATE_HANDLE) {
      appendLine("  fun toSavedStateHandle(): androidx.lifecycle.SavedStateHandle = TODO()")
    }
    appendLine()
    appendLine("  companion object {")
    appendLine("    @kotlin.jvm.JvmStatic")
    appendLine(
      "    fun fromBundle(bundle: android.os.Bundle): ${classId.asFqNameString()} = TODO()"
    )
    if (navInfo.navVersion >= SafeArgsFeatureVersions.FROM_SAVED_STATE_HANDLE) {
      appendLine("    @kotlin.jvm.JvmStatic")
      appendLine(
        "    fun fromSavedStateHandle(handle: androidx.lifecycle.SavedStateHandle): ${classId.asFqNameString()} = TODO()"
      )
    }
    appendLine("  }")
    appendLine("}")
  }

  private val NavArgumentData.argumentTag: XmlTag?
    get() =
      destinationXmlTag?.findChildTagElementByNameAttr(SdkConstants.TAG_ARGUMENT, name)
        ?: destinationXmlTag
}
