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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.LibraryProto
import java.io.File

interface AndroidLibrary : Library {
  /** The path to the android artifact. */
  val artifact: File
  /** The list of local Jar files that are included in the dependency. */
  val localJars: Collection<File>
  /** The location of the unzipped bundle folder. */
  val bundleFolder: File

  fun toProto(converter: PathConverter) = LibraryProto.AndroidLibrary.newBuilder()
    .setLibrary(LibraryProto.Library.newBuilder().setArtifactAddress(artifactAddress))
    .setArtifact(converter.fileToProto(artifact, PathConverter.DirType.OFFLINE_REPO))
    .setBundleFolder(converter.fileToProto(bundleFolder, PathConverter.DirType.OFFLINE_REPO))
    .addAllLocalJars(localJars.map { converter.fileToProto(it, PathConverter.DirType.OFFLINE_REPO) })
    .build()!!
}
