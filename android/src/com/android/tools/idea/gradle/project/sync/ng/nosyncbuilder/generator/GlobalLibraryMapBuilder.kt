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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.generator

import com.android.ide.common.gradle.model.level2.IdeDependencies
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.Level2Library
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldVariant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.getLevel2Dependencies

class GlobalLibraryMapBuilder {
  private val globalLibraryMap: MutableMap<String, Level2Library> = HashMap()

  fun addLibrariesFromVariantToGLM(variant: OldVariant) {
    val mainArtifact = variant.mainArtifact
    val extraAndroidArtifacts = variant.extraAndroidArtifacts
    val extraJavaArtifacts = variant.extraJavaArtifacts

    addLibrariesFromDependenciesToGLM(mainArtifact.getLevel2Dependencies())
    extraAndroidArtifacts.forEach {
      addLibrariesFromDependenciesToGLM(it.getLevel2Dependencies())
    }
    extraJavaArtifacts.forEach {
      addLibrariesFromDependenciesToGLM(it.getLevel2Dependencies())
    }
  }

  fun build(): Map<String, Level2Library> = globalLibraryMap.toMap()

  private fun addLibrariesFromDependenciesToGLM(dependencies: IdeDependencies) {
    addLibrariesToGLM(dependencies.androidLibraries)
    addLibrariesToGLM(dependencies.javaLibraries)
    addLibrariesToGLM(dependencies.moduleDependencies)
  }

  private fun addLibrariesToGLM(libraries: Collection<Level2Library>) {
    libraries.forEach { globalLibraryMap[it.artifactAddress] = it }
  }
}