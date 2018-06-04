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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc

import com.android.builder.model.SourceProvider
import com.android.builder.model.level2.Library.*
import com.android.ide.common.gradle.model.IdeAndroidArtifact
import com.android.ide.common.gradle.model.IdeJavaArtifact
import com.android.ide.common.gradle.model.level2.IdeAndroidLibrary
import com.android.ide.common.gradle.model.level2.IdeDependencies
import com.android.ide.common.gradle.model.level2.IdeJavaLibrary
import com.android.ide.common.gradle.model.level2.IdeModuleLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.Library
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.Dependencies
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewAndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewJavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewModuleDependency
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.*

// These helpers are used for .? syntax support

fun IdeAndroidArtifact.toNew(aspFactory: NewArtifactSourceProviderFactory) = NewAndroidArtifact(this, aspFactory)
fun IdeJavaArtifact.toNew(aspFactory: NewArtifactSourceProviderFactory) = NewJavaArtifact(this, aspFactory)
fun OldApiVersion.toNew() = NewApiVersion(this)
fun OldTestOptions.toNew() = NewTestOptions(this)
fun SourceProvider.toAndroidSourceSet() = NewAndroidSourceSet(this)

fun Level2Library.toNew(): Library = when (this.type) {
  LIBRARY_ANDROID -> NewAndroidLibrary(this as IdeAndroidLibrary)
  LIBRARY_JAVA -> NewJavaLibrary(this as IdeJavaLibrary)
  LIBRARY_MODULE -> NewModuleDependency(this as IdeModuleLibrary)
  else -> throw IllegalStateException("Level 2 library has unknown type")
}

fun IdeDependencies.toNew(): Dependencies = NewDependencies(this, listOf())
