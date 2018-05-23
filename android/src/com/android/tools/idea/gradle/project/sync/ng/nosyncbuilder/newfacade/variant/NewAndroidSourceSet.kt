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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant

import com.android.builder.model.SourceProvider
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.AndroidSourceSet
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import java.io.File

data class NewAndroidSourceSet(
  override val name: String,
  override val manifestFile: File,
  override val javaDirectories: Collection<File>,
  override val javaResourcesDirectories: Collection<File>,
  override val aidlDirectories: Collection<File>,
  override val renderscriptDirectories: Collection<File>,
  override val cDirectories: Collection<File>,
  override val cppDirectories: Collection<File>,
  override val androidResourcesDirectories: Collection<File>,
  override val assetsDirectories: Collection<File>,
  override val jniLibsDirectories: Collection<File>,
  override val shadersDirectories: Collection<File>
) : AndroidSourceSet {
  constructor(sourceProvider: SourceProvider) : this(
    sourceProvider.name,
    sourceProvider.manifestFile,
    sourceProvider.javaDirectories,
    sourceProvider.resourcesDirectories,
    sourceProvider.aidlDirectories,
    sourceProvider.renderscriptDirectories,
    sourceProvider.cDirectories,
    sourceProvider.cppDirectories,
    sourceProvider.resDirectories,
    sourceProvider.assetsDirectories,
    sourceProvider.jniLibsDirectories,
    sourceProvider.shadersDirectories
  )

  constructor(proto: VariantProto.AndroidSourceSet, converter: PathConverter) : this(
    proto.name,
    converter.fileFromProto(proto.manifestFile),
    proto.javaDirectoriesList.map { converter.fileFromProto(it) },
    proto.javaResourcesDirectoriesList.map { converter.fileFromProto(it) },
    proto.aidlDirectoriesList.map { converter.fileFromProto(it) },
    proto.renderscriptDirectoriesList.map { converter.fileFromProto(it) },
    proto.cDirectoriesList.map { converter.fileFromProto(it) },
    proto.cppDirectoriesList.map { converter.fileFromProto(it) },
    proto.androidResourcesDirectoriesList.map { converter.fileFromProto(it) },
    proto.assetsDirectoriesList.map { converter.fileFromProto(it) },
    proto.jniLibsDirectoriesList.map { converter.fileFromProto(it) },
    proto.shadersDirectoriesList.map { converter.fileFromProto(it) }
  )
}