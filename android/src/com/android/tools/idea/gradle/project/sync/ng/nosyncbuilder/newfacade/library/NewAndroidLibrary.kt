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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library

import com.android.ide.common.gradle.model.level2.IdeAndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.AndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.LibraryProto
import java.io.File

data class NewAndroidLibrary(
  override val artifact: File,
  override val localJars: Collection<File>,
  override val bundleFolder: File,
  override val artifactAddress: String
) : AndroidLibrary {
  constructor(library: IdeAndroidLibrary) : this(
    library.artifact,
    library.localJars.map { File(it) },
    library.folder,
    library.artifactAddress
  )

  constructor(proto: LibraryProto.AndroidLibrary, converter: PathConverter) : this(
    converter.fileFromProto(proto.artifact),
    proto.localJarsList.map { converter.fileFromProto(it) },
    converter.fileFromProto(proto.bundleFolder),
    proto.library.artifactAddress
  )
}
