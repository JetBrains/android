/*
 * Copyright (C) 2016 The Android Open Source Project
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

/**
 * Utilities shared between [GradleFileSimpleMerger] and [GradleFilePsiMerger].
 */
@file:JvmName("GradleFileMergers")

package com.android.tools.idea.templates

import com.android.ide.common.repository.GradleCoordinate
import com.google.common.collect.Multimap
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory

/**
 * Name of the dependencies DSL block.
 */
internal const val DEPENDENCIES = "dependencies"

/**
 * Name of the apply plugin DSL block. E.g. apply plugin: 'com.android.application'
 */
internal const val APPLY = "apply"

/**
 * Name of the android DSL block.
 */
internal const val ANDROID = "android"

private val KNOWN_CONFIGURATIONS_IN_ORDER = listOf(
  "feature", "api", "implementation", "compile",
  "testApi", "testImplementation", "testCompile",
  "androidTestApi", "androidTestImplementation", "androidTestCompile", "androidTestUtil")

private val CONFIGURATION_GROUPS = setOf(
  setOf("feature", "api", "implementation", "compile"),
  setOf("testApi", "testImplementation", "testCompile"),
  setOf("androidTestApi", "androidTestImplementation", "androidTestCompile"))

/**
 * Defined an ordering on gradle configuration names.
 */
@JvmField
val CONFIGURATION_ORDERING = compareBy<String> {
  val result = KNOWN_CONFIGURATIONS_IN_ORDER.indexOf(it)
  if (result != -1) result else KNOWN_CONFIGURATIONS_IN_ORDER.size
}.thenBy { it }

/**
 * Removes entries from `newDependencies` that are also in `existingDependencies`.
 * If `psiGradleCoordinates` and `factory` are supplied, it also increases the visibility of
 * `existingDependencies` if needed, for example from "implementation" to "api".
 */
fun updateExistingDependencies(
  newDependencies: Map<String, Multimap<String, GradleCoordinate>>,
  existingDependencies: Map<String, Multimap<String, GradleCoordinate>>,
  psiGradleCoordinates: Map<GradleCoordinate, PsiElement>?,
  factory: GroovyPsiElementFactory?
) {
  for (configuration in newDependencies.keys) {
    // If we already have an existing "compile" dependency, the same "implementation" or "api" dependency should not be added

    getConfigurationGroup(configuration).filter { existingDependencies.containsKey(it) }.forEach { possibleConfiguration ->
      for ((coordinateId, value) in existingDependencies.getValue(possibleConfiguration).entries()) {
        newDependencies.getValue(configuration).removeAll(coordinateId)

        // Check if we need to convert the existing configuration. eg from "implementation" to "api", but not the other way around.
        if (psiGradleCoordinates != null && factory != null &&
            CONFIGURATION_ORDERING.compare(configuration, possibleConfiguration) < 0) {
          psiGradleCoordinates.getValue(value).replace(factory.createExpressionFromText(configuration))
        }
      }
    }
  }
}

private fun getConfigurationGroup(configuration: String) =
  CONFIGURATION_GROUPS.firstOrNull { it.contains(configuration) } ?: setOf(configuration)
