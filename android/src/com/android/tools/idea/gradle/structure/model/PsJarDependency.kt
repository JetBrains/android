/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.navigation.PsJarDependencyNavigationPath
import com.intellij.util.PlatformIcons
import javax.swing.Icon

interface PsJarDependency : PsBaseDependency {
  enum class Kind { FILE, FILE_TREE }

  override val parent: PsModule
  val kind: Kind
  val filePath: String
  val includes: List<String>
  val excludes: List<String>

  override val path: PsJarDependencyNavigationPath get() = PsJarDependencyNavigationPath(this)
  override val icon: Icon get() = PlatformIcons.JAR_ICON
}

interface PsDeclaredJarDependency : PsJarDependency, PsDeclaredDependency {
  override fun toKey() = filePath
}

interface PsResolvedJarDependency : PsJarDependency, PsResolvedDependency {
  override val kind: PsJarDependency.Kind get() = PsJarDependency.Kind.FILE
  override val includes: List<String> get() = listOf()
  override val excludes: List<String> get() = listOf()
}
