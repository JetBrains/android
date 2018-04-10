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
package com.android.tools.idea.gradle.structure.model.java

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.model.java.JarLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsDependency
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.intellij.util.PlatformIcons.LIBRARY_ICON
import javax.swing.Icon

class PsLibraryJavaDependency(
  parent: PsJavaModule,
  override val spec: PsArtifactDependencySpec,
  override val resolvedModel: JarLibraryDependency?,
  parsedModels: Collection<ArtifactDependencyModel>
) : PsJavaDependency(parent, parsedModels), PsLibraryDependency {

  override val name: String get() = spec.name

  override val icon: Icon get() = LIBRARY_ICON

  override fun hasPromotedVersion(): Boolean = false

  override fun toText(type: PsDependency.TextType): String = spec.toString()
}
