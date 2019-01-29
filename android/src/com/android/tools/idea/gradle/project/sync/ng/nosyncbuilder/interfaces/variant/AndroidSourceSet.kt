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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.AndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import java.io.File

/** Represents a set of sources for given configuration for [AndroidProject]. */
interface AndroidSourceSet {
  val name: String
  val manifestFile: File
  val javaDirectories: Collection<File>
  val javaResourcesDirectories: Collection<File>
  val aidlDirectories: Collection<File>
  val renderscriptDirectories: Collection<File>
  val cDirectories: Collection<File>
  val cppDirectories: Collection<File>
  val androidResourcesDirectories: Collection<File>
  val assetsDirectories: Collection<File>
  val jniLibsDirectories: Collection<File>
  val shadersDirectories: Collection<File>

  fun toProto(converter: PathConverter) = VariantProto.AndroidSourceSet.newBuilder()
    .setName(name)
    .setManifestFile(converter.fileToProto(manifestFile))
    .addAllJavaDirectories(javaDirectories.map { converter.fileToProto(it) })
    .addAllJavaResourcesDirectories(javaResourcesDirectories.map { converter.fileToProto(it) })
    .addAllAidlDirectories(aidlDirectories.map { converter.fileToProto(it) })
    .addAllRenderscriptDirectories(renderscriptDirectories.map { converter.fileToProto(it) })
    .addAllCDirectories(cDirectories.map { converter.fileToProto(it) })
    .addAllCppDirectories(cppDirectories.map { converter.fileToProto(it) })
    .addAllAndroidResourcesDirectories(androidResourcesDirectories.map { converter.fileToProto(it) })
    .addAllAssetsDirectories(assetsDirectories.map { converter.fileToProto(it) })
    .addAllJniLibsDirectories(jniLibsDirectories.map { converter.fileToProto(it) })
    .addAllShadersDirectories(shadersDirectories.map { converter.fileToProto(it) })
    .build()!!
}
