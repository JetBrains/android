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
import com.android.tools.idea.nav.safeargs.index.NavActionData
import com.android.tools.idea.nav.safeargs.index.NavArgumentData
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.module.NavEntry
import com.android.tools.idea.nav.safeargs.module.NavInfo
import com.android.tools.idea.nav.safeargs.psi.ArgumentUtils.getActionsWithResolvedArguments
import com.android.tools.idea.nav.safeargs.psi.ArgumentUtils.getTargetDestination
import com.android.tools.idea.nav.safeargs.psi.SafeArgsFeatureVersions
import com.android.tools.idea.nav.safeargs.psi.java.toCamelCase
import com.android.tools.idea.nav.safeargs.psi.xml.findChildTagElementByNameAttr
import com.android.tools.idea.nav.safeargs.psi.xml.findFirstMatchingElementByTraversingUp
import com.android.tools.idea.nav.safeargs.psi.xml.findXmlTagById
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.name.ClassId

/*
 * class ${Name}Directions {
 *   companion object {
 *     // One method for each outbound action.
 *     fun someActionName(): NavDirections
 *     // Args can be defined by the destination or overridden by the action.
 *     // See [ArgumentUtils.getActionsWithResolvedArguments] for full argument resolution logic.
 *     fun someActionWithArgs(arg1: Arg1Type, arg2: Arg2Type = defaultValue(), ...): NavDirections
 *   }
 * }
 */
internal class DirectionsClassResolveExtensionFile(
  private val navInfo: NavInfo,
  private val navEntry: NavEntry,
  destination: NavDestinationData,
  classId: ClassId,
  private val destinationXmlTag: XmlTag?,
) : SafeArgsResolveExtensionFile(classId) {
  private val actionsWithResolvedArguments =
    destination.getActionsWithResolvedArguments(
      navEntry.data,
      navInfo.packageName,
      adjustArgumentsWithDefaults =
        (navInfo.navVersion >= SafeArgsFeatureVersions.ADJUST_PARAMS_WITH_DEFAULTS),
    )

  override fun StringBuilder.buildClassBody() {
    appendLine("class ${classId.shortClassName} private constructor() {")
    appendLine("  companion object {")
    for (action in actionsWithResolvedArguments) {
      appendLine("    // action ${action.id}")
      appendLine("    fun ${action.id.toCamelCase()}(")
      for (argument in action.arguments) {
        appendLine(
          "        " +
            "// argument ${argument.name}: " +
            "${argument.type ?: "<undefined type>"} " +
            "(nullable: ${argument.nullable ?: "<null>"})" +
            " = ${argument.defaultValue ?: "<no default>"}"
        )
        val argumentType = argument.resolveKotlinType(navInfo.packageName)
        append("        ${argument.name.toCamelCase()}: ${argumentType}")
        if (argument.defaultValue != null) {
          appendLine(" = TODO(),  // ${argument.defaultValue}")
        } else {
          appendLine(",")
        }
      }
      appendLine("    ): androidx.navigation.NavDirections = TODO()")
      appendLine()
    }
    appendLine("  }") // companion object
    appendLine("}") // class
  }

  override val fallbackPsi
    get() = destinationXmlTag

  override fun KaSession.getNavigationElementForDeclaration(
    symbol: KaDeclarationSymbol
  ): PsiElement? =
    when (symbol) {
      // Containing class or its companion object -> overall destination.
      is KaClassSymbol -> destinationXmlTag
      // Function on companion object -> matching action tag.
      is KaNamedFunctionSymbol -> findMatchingAction(symbol)?.actionTag ?: destinationXmlTag
      // Argument of companion object function -> argument under action (preferred) or destination.
      is KaValueParameterSymbol -> getTagForValueParameterSymbol(symbol) ?: destinationXmlTag
      else -> null
    }

  private fun KaSession.getTagForValueParameterSymbol(
    symbol: KaValueParameterSymbol
  ): XmlTag? {
    val declaringFunctionSymbol = symbol.containingDeclaration as? KaNamedFunctionSymbol ?: return null
    val matchingAction = findMatchingAction(declaringFunctionSymbol) ?: return null
    val actionTag = matchingAction.actionTag

    val originalArgument =
      matchingAction.arguments.firstOrNull {
        it.name.toCamelCase() == symbol.name.identifierOrNullIfSpecial
      } ?: return actionTag

    // Search arguments under action first.
    actionTag?.findChildArgumentTag(originalArgument)?.let {
      return it
    }

    // Next, search arguments under destination.
    val destination = matchingAction.getTargetDestination(navEntry.data) ?: return actionTag
    val destinationTag = navEntry.backingXmlFile?.findXmlTagById(destination.id) ?: return actionTag
    destinationTag.findChildArgumentTag(originalArgument)?.let {
      return it
    }

    return actionTag
  }

  private fun KaSession.findMatchingAction(symbol: KaNamedFunctionSymbol): NavActionData? =
    actionsWithResolvedArguments.firstOrNull {
      it.id.toCamelCase() == symbol.name.identifierOrNullIfSpecial
    }

  private fun XmlTag.findChildArgumentTag(argument: NavArgumentData): XmlTag? =
    findChildTagElementByNameAttr(SdkConstants.TAG_ARGUMENT, argument.name)

  private val NavActionData.actionTag: XmlTag?
    get() = destinationXmlTag?.findFirstMatchingElementByTraversingUp(SdkConstants.TAG_ACTION, id)
}
