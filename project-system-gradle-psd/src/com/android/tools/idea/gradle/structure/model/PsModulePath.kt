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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.navigation.PsBuildTypesNavigationPath
import com.android.tools.idea.gradle.structure.navigation.PsBuildVariantsNavigationPath
import com.android.tools.idea.gradle.structure.navigation.PsDependenciesNavigationPath
import com.android.tools.idea.gradle.structure.navigation.PsProductFlavorsNavigationPath
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.navigation.Place

data class PsModulePath @VisibleForTesting constructor (val gradlePath: String) : PsPlaceBasedPath() {
  constructor (module: PsModule) : this(module.gradlePath)
  override fun toString(): String = gradlePath
  override fun queryPlace(place: Place, context: PsContext) = throw UnsupportedOperationException()
  override fun getHyperlinkDestination(context: PsContext): String? = null
  private val buildVariantsPath: PsBuildVariantsNavigationPath = PsBuildVariantsNavigationPath(this)
  val dependenciesPath: PsDependenciesNavigationPath = PsDependenciesNavigationPath(this)
  val buildTypesPath: PsBuildTypesNavigationPath = PsBuildTypesNavigationPath(buildVariantsPath)
  val productFlavorsPath: PsProductFlavorsNavigationPath = PsProductFlavorsNavigationPath(buildVariantsPath)
}
