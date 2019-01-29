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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade

import com.android.builder.model.SourceProvider
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.AndroidSourceSet
import java.io.File

open class LegacySourceProvider(private val androidSourceSet: AndroidSourceSet) : SourceProvider {
  override fun getName(): String = androidSourceSet.name
  override fun getManifestFile(): File = androidSourceSet.manifestFile
  override fun getJavaDirectories(): Collection<File> = androidSourceSet.javaDirectories
  override fun getResourcesDirectories(): Collection<File> = androidSourceSet.javaResourcesDirectories
  override fun getAidlDirectories(): Collection<File> = androidSourceSet.aidlDirectories
  override fun getRenderscriptDirectories(): Collection<File> = androidSourceSet.renderscriptDirectories
  override fun getCDirectories(): Collection<File> = androidSourceSet.cDirectories
  override fun getCppDirectories(): Collection<File> = androidSourceSet.cppDirectories
  override fun getResDirectories(): Collection<File> = androidSourceSet.androidResourcesDirectories
  override fun getAssetsDirectories(): Collection<File> = androidSourceSet.assetsDirectories
  override fun getJniLibsDirectories(): Collection<File> = androidSourceSet.jniLibsDirectories
  override fun getShadersDirectories(): Collection<File> = androidSourceSet.shadersDirectories

  override fun toString(): String = "LegacyBaseArtifact{" +
                                    "manifestFile=$manifestFile," +
                                    "javaDirectories=$javaDirectories," +
                                    "resourcesDirectories=$resourcesDirectories," +
                                    "aidlDirectories=$aidlDirectories," +
                                    "renderscriptDirectories=$renderscriptDirectories," +
                                    "cDirectories=$cDirectories," +
                                    "cppDirectories=$cppDirectories," +
                                    "resDirectories=$resDirectories," +
                                    "assetsDirectories=$assetsDirectories," +
                                    "jniLibsDirectories=$jniLibsDirectories," +
                                    "shadersDirectories=$shadersDirectories" +
                                    "}"
}
