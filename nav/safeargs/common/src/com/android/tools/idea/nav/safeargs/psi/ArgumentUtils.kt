// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package com.android.tools.idea.nav.safeargs.psi

import com.android.tools.idea.nav.safeargs.index.NavActionData
import com.android.tools.idea.nav.safeargs.index.NavArgumentData
import com.android.tools.idea.nav.safeargs.index.NavDestinationData
import com.android.tools.idea.nav.safeargs.index.NavXmlData
import com.android.tools.idea.nav.safeargs.psi.java.getPsiTypeStr
import com.intellij.openapi.diagnostic.thisLogger

object ArgumentUtils {
  fun NavActionData.getTargetDestination(data: NavXmlData): NavDestinationData? {
    val destinationId = resolveDestination() ?: return null
    return data.resolvedDestinations.firstOrNull { it.id == destinationId }
  }

  fun NavDestinationData.getActionsWithResolvedArguments(
    data: NavXmlData,
    modulePackage: String,
    adjustArgumentsWithDefaults: Boolean = false,
  ): List<NavActionData> =
    actions.mapNotNull { action ->
      if (action.destination == null) {
        return@mapNotNull if (action.popUpTo == null) {
          // No destination, no popUpTo: nothing we can do to resolve this, return untouched.
          action
        } else {
          // No destination, but has popUpTo: No args are supposed to be passed to this action.
          object : NavActionData by action {
            override val arguments: List<NavArgumentData> = emptyList()
          }
        }
      }

      val argsFromTargetDestination = action.getTargetDestination(data)?.arguments.orEmpty()

      val resolvedArguments =
        (action.arguments + argsFromTargetDestination)
          .groupBy { it.name }
          .map { entry ->
            if (entry.value.size > 1) checkArguments(entry, modulePackage)
            entry.value.first()
          }

      val adjustedArguments =
        if (adjustArgumentsWithDefaults) {
          resolvedArguments.sortedBy { it.defaultValue != null }
        } else {
          resolvedArguments
        }

      return@mapNotNull object : NavActionData by action {
        override val arguments: List<NavArgumentData> = adjustedArguments
      }
    }

  /**
   * Warn if incompatible types of argument exist. We still provide best results though it fails to
   * compile.
   */
  private fun checkArguments(
    entry: Map.Entry<String, List<NavArgumentData>>,
    modulePackage: String
  ) {
    val types =
      entry.value
        .asSequence()
        .map { arg -> getPsiTypeStr(modulePackage, arg.type, arg.defaultValue) }
        .toSet()

    if (types.size > 1)
      LOG.warn("Incompatible types of argument ${entry.key}: ${types.joinToString(", ")}.")
  }

  private val LOG = thisLogger()
}
