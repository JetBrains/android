/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8.inspections

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8QualifiedName
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.androidProjectType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.JdkScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.util.Processor
import org.jetbrains.annotations.VisibleForTesting


@Service(Service.Level.PROJECT)
class AffectedClassesProjectService(private val project: Project) {

  private val logger get() = thisLogger()

  /**
   * @return The `count` of the number of affected classes that match a pattern encapsulated by the
   * incoming [ProguardR8QualifiedName].
   *
   * **Note:** This method does not handle complex rules like negation, and filters on
   * method / field specifiers given we want this check to be fast.
   */
  fun affectedClassesForQualifiedName(
    qualifiedName: ProguardR8QualifiedName,
    limit: Int,
  ): Int =
    affectedClassesForQualifiedName(qualifiedPattern = qualifiedName.text, limit = limit)

  // Helper to make testing easier.
  @VisibleForTesting
  fun affectedClassesForQualifiedName(
    qualifiedPattern: String,
    limit: Int,
  ): Int {
    val regex = qualifiedPattern.asRegex() ?: return 0

    val appModules = applicationModules()
    return if (appModules.isNotEmpty()) {
      appModules.maxOf { module ->
        val searchScope = module.getModuleWithDependenciesAndLibrariesScope(false)
          .intersectWith(GlobalSearchScope.notScope(androidSdkScope(module)))

        var counter = 0
        AllClassesSearch.search(searchScope, project)
          .forEach( Processor { psiClass ->
            psiClass.qualifiedName?.let { name ->
              if (regex.matches(input = name)) {
                counter += 1
              }
            }
            counter < limit
          })
        counter
      }
    } else {
      0
    }
  }

  /**
   * Finds the [List] of application [Module]s for a given project.
   *
   * Note: There may be multiple application modules, but we end up computing the number of
   * affected classes from the perspective of all application modules that we can find.
   */
  private fun applicationModules(): List<Module> {
    return project.modules.filter {
      it.getModuleSystem().isProductionAndroidModule() &&
      it.androidProjectType() == AndroidModuleSystem.Type.TYPE_APP
    }
  }

  private fun androidSdkScope(module: Module): GlobalSearchScope {
    val jdk = ModuleRootManager.getInstance(module).orderEntries
      .filterIsInstance<JdkOrderEntry>()
      .firstOrNull()

    return if (jdk?.jdk?.sdkType?.name == "Android SDK") {
      JdkScope(module.project, jdk)
    } else {
      GlobalSearchScope.EMPTY_SCOPE
    }
  }

  private val patternRegex = Regex(pattern = "((?<prefix>.*)\\.)?\\*\\*(\\.\\*)?$")

  private fun String.asRegex(): Regex? {
    val matchResult = patternRegex.matchEntire(input = this) ?: return null
    // Prefix is optional
    val prefix = matchResult.groups["prefix"]?.value ?: ""
    val builder = StringBuilder()
    var index = 0
    var groupIndex = 0
    while (index < prefix.length) {
      val char = prefix[index]
      when (char) {
        '.' -> {
          builder.append("\\.")
          index += 1
        }

        '*' -> {
          val next = prefix.getOrNull(index = index + 1)
          when (next) {
            '*' -> {
              // **
              builder.append("(?<double$groupIndex>.*)")
              groupIndex += 1
              index += 2
            }

            else -> {
              // *
              builder.append("(?<single$groupIndex>[^.]+)")
              groupIndex += 1
              index += 1
            }
          }
        }

        else -> {
          builder.append(char)
          index += 1
        }
      }
    }
    builder.append("(?<everyThingElse>.*)")
    val prefixPattern = builder.toString()

    val regexResult = runCatching { Regex(pattern = prefixPattern) }
    return when {
      regexResult.isFailure -> {
        val throwable = regexResult.exceptionOrNull()!!
        logger.warnWithDebug(
          "Pattern ($prefix) translated to $prefixPattern.",
          throwable
        )
        null
      }

      else -> regexResult.getOrNull()
    }
  }
}
