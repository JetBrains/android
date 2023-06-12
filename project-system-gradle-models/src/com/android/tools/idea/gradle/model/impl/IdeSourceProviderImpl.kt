/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeCustomSourceDirectory
import com.android.tools.idea.gradle.model.IdeSourceProvider
import java.io.File
import java.io.Serializable

data class IdeSourceProviderImpl(
  private val myName: String,
  private val myFolder: File?,
  private val myManifestFile: String,
  private val myJavaDirectories: Collection<String>,
  private val myKotlinDirectories: Collection<String>,
  private val myResourcesDirectories: Collection<String>,
  private val myAidlDirectories: Collection<String>,
  private val myRenderscriptDirectories: Collection<String>,
  private val myResDirectories: Collection<String>,
  private val myAssetsDirectories: Collection<String>,
  private val myJniLibsDirectories: Collection<String>,
  private val myShadersDirectories: Collection<String>,
  private val myMlModelsDirectories: Collection<String>,
  private val myCustomSourceDirectories: Collection<IdeCustomSourceDirectory>,
  private val myBaselineProfileDirectories: Collection<String>,
) : Serializable, IdeSourceProvider {
  private fun String.translate(): File = (myFolder?.resolve(this) ?: File(this)).normalize()
  private fun Collection<String>.translate(): Collection<File> = map { it.translate() }

  // Used for serialization by the IDE.
  constructor() : this(
    myName = "",
    myFolder = File(""),
    myManifestFile = "",
    myJavaDirectories = mutableListOf(),
    myKotlinDirectories = mutableListOf(),
    myResourcesDirectories = mutableListOf(),
    myAidlDirectories = mutableListOf(),
    myRenderscriptDirectories = mutableListOf(),
    myResDirectories = mutableListOf(),
    myAssetsDirectories = mutableListOf(),
    myJniLibsDirectories = mutableListOf(),
    myShadersDirectories = mutableListOf(),
    myMlModelsDirectories = mutableListOf(),
    myCustomSourceDirectories = mutableListOf(),
    myBaselineProfileDirectories = mutableListOf(),
  )

  fun appendDirectories(
    javaDirectories: Collection<File> = emptyList(),
    kotlinDirectories: Collection<File> = emptyList(),
    resourcesDirectories: Collection<File> = emptyList(),
    aidlDirectories: Collection<File> = emptyList(),
    renderscriptDirectories: Collection<File> = emptyList(),
    resDirectories: Collection<File> = emptyList(),
    assetsDirectories: Collection<File> = emptyList(),
    jniLibsDirectories: Collection<File> = emptyList(),
    shadersDirectories: Collection<File> = emptyList(),
    mlModelsDirectories: Collection<File> = emptyList(),
    baselineProfileDirectories: Collection<File> = emptyList(),
  ): IdeSourceProviderImpl = copy(
    myJavaDirectories = myJavaDirectories + javaDirectories.map { normalize(it) },
    myKotlinDirectories = myKotlinDirectories + kotlinDirectories.map { normalize(it) },
    myResourcesDirectories = myResourcesDirectories + resourcesDirectories.map { normalize(it) },
    myAidlDirectories = myAidlDirectories + aidlDirectories.map { normalize(it) },
    myRenderscriptDirectories = myRenderscriptDirectories + renderscriptDirectories.map { normalize(it) },
    myResDirectories = myResDirectories + resDirectories.map { normalize(it) },
    myAssetsDirectories = myAssetsDirectories + assetsDirectories.map { normalize(it) },
    myJniLibsDirectories = myJniLibsDirectories + jniLibsDirectories.map { normalize(it) },
    myShadersDirectories = myShadersDirectories + shadersDirectories.map { normalize(it) },
    myMlModelsDirectories = myMlModelsDirectories + mlModelsDirectories.map { normalize(it) },
    myBaselineProfileDirectories = myBaselineProfileDirectories + baselineProfileDirectories.map { normalize(it) },
  )

  private fun normalize(it: File) = if (myFolder != null) it.relativeToOrSelf(myFolder).path else it.path

  override val name: String get() = myName
  override val manifestFile: File get() = myManifestFile.translate()
  override val javaDirectories: Collection<File> get() = myJavaDirectories.translate()
  override val kotlinDirectories: Collection<File> get() = myKotlinDirectories.translate()
  override val resourcesDirectories: Collection<File> get() = myResourcesDirectories.translate()
  override val aidlDirectories: Collection<File> get() = myAidlDirectories.translate()
  override val renderscriptDirectories: Collection<File> get() = myRenderscriptDirectories.translate()
  override val resDirectories: Collection<File> get() = myResDirectories.translate()
  override val assetsDirectories: Collection<File> get() = myAssetsDirectories.translate()
  override val jniLibsDirectories: Collection<File> get() = myJniLibsDirectories.translate()
  override val shadersDirectories: Collection<File> get() = myShadersDirectories.translate()
  override val mlModelsDirectories: Collection<File> get() = myMlModelsDirectories.translate()
  override val customSourceDirectories: Collection<IdeCustomSourceDirectory>
    get() = myCustomSourceDirectories
  override val baselineProfileDirectories: Collection<File>
    get() = myBaselineProfileDirectories.translate()
}
