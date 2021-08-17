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

import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeJavaArtifact
import com.android.tools.idea.gradle.model.IdeSourceProvider
import java.io.File

data class IdeJavaArtifactImpl(
  override val name: IdeArtifactName,
  override val compileTaskName: String,
  override val assembleTaskName: String,
  override val classesFolder: File,
  override val additionalClassesFolders: Collection<File>,
  override val javaResourcesFolder: File?,
  override val variantSourceProvider: IdeSourceProvider?,
  override val multiFlavorSourceProvider: IdeSourceProvider?,
  override val ideSetupTaskNames: Collection<String>,
  private val mutableGeneratedSourceFolders: MutableList<File>,
  override val isTestArtifact: Boolean,
  override val level2Dependencies: IdeDependencies,
  override val mockablePlatformJar: File?
) : IdeJavaArtifact {
  override val generatedSourceFolders: Collection<File> get() = mutableGeneratedSourceFolders
  override fun addGeneratedSourceFolder(generatedSourceFolder: File) {
    mutableGeneratedSourceFolders.add(generatedSourceFolder)
  }
}
