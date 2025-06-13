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

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8File
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Flag
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8FlagArgument
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Rule
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Visitor
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.androidProjectType
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.util.module

/**
 * Reports when bad directives (implying global optimizations) for R8 are embedded in Library consumer rules.
 *
 * E.g.
 *
 * ```
 * -dontobfuscate
 * -dontoptimize
 * -dontshrink
 * -repackageclasses
 * -flattenpackagehierarchy
 * -allowaccessmodification
 * ```
 */

// We are using the name of the file as a heuristic to know if its being used to define consumer proguard rules.
// This is because there is no mechanism in the project model that tells provides this information.
// We might be able to use the PSI of the groovy build script or the kotlin build script but that is equally error-prone given
// there are multiple different ways that this information flows through. (defaultConfig, variant specific config etc.)
private val CONSUMER_RULES_FILE_NAME_PATTERN = Regex(pattern = "consumer[_|-]?[r|R]ules?[_|-]?(\\w*)?\\.(pro|text|cfg)")

private val BANNED_DIRECTIVES_IN_CONSUMER_RULES = setOf(
  "-dontobfuscate",
  "-dontoptimize",
  "-dontshrink",
  "-repackageclasses",
  "-flattenpackagehierarchy",
  "-allowaccessmodification",
  "-renamesourcefileattribute",
)

private const val FLAG_KEEP_ATTRIBUTES = "-keepattributes"

private val BANNED_KEEP_ATTRIBUTES_VALUES = setOf(
  "LineNumberTable",
  "RuntimeInvisibleAnnotations",
  "RuntimeInvisibleTypeAnnotations",
  "RuntimeInvisibleParameterAnnotations",
  "SourceFile"
)

class ConsumerRulesInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : ProguardR8Visitor() {
      override fun visitFlag(flag: ProguardR8Flag) {
        if (!isLibraryModule(element = flag)) {
          return
        }

        // Not a perfect check, but a good enough check nevertheless.
        if (!fileLikelyHasConsumerRules(element = flag)) {
          return
        }

        val directive = flag.text
        if (directive in BANNED_DIRECTIVES_IN_CONSUMER_RULES) {
          holder.registerProblem(
            /* psiElement = */ flag,
            /* descriptionTemplate = */
            "Global flags should never be placed in library consumer rules, since they prevent optimizations in apps using the library"
          )
          return
        }
        if (FLAG_KEEP_ATTRIBUTES == directive) {
          // Check for banned attributes
          val rule = flag.parentOfType<ProguardR8Rule>()
          val arguments = rule?.childrenOfType<ProguardR8FlagArgument>()
          val argumentValues = arguments?.flatMap { it.childrenOfType<ProguardR8File>() } ?: emptyList()
          val bannedArgumentValues = argumentValues.filter { proguardR8FileElement ->
            val argumentValue = proguardR8FileElement.text
            if (BANNED_KEEP_ATTRIBUTES_VALUES.contains(argumentValue)) {
              // Fast check
              true
            } else {
              val regex = argumentValue.wildCardAsRegexOrNull() // Check for wildcard matches.
              regex?.matches(argumentValue) ?: false
            }
          }
          if (bannedArgumentValues.isNotEmpty()) {
            bannedArgumentValues.forEach { argument ->
              holder.registerProblem(
                /* psiElement = */ argument,
                /* descriptionTemplate = */
                "Attribute ${argument.text} should never be placed in library consumer rules, since it prevents optimizations in apps using the library"
              )
            }
            return
          }
        }
      }
    }
  }
}

private fun isLibraryModule(element: PsiElement): Boolean {
  val projectType = element.module?.androidProjectType()
  return projectType != null && projectType == AndroidModuleSystem.Type.TYPE_LIBRARY
}

private fun fileLikelyHasConsumerRules(element: PsiElement): Boolean {
  val fileName = element.containingFile.name
  return fileName.matches(regex = CONSUMER_RULES_FILE_NAME_PATTERN)
}

private fun String.wildCardAsRegexOrNull(): Regex? {
  return if (contains(char = '*')) {
    Regex(pattern = replace(oldValue = "*", newValue = "(.*)?")) // Replace it with groups.
  } else null
}
