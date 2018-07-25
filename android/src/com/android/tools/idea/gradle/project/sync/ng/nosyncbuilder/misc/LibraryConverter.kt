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

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewAndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewJavaLibrary
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

// properOfflineRepo have to exist
class LibraryConverter(private val properOfflineRepo: Path) {
  fun convertCachedLibraryToProper(library: NewAndroidLibrary): NewAndroidLibrary {
    val newBundleFolder = properOfflineRepo.resolve(library.artifactAddress)

    val newLocalJarNames = library.localJars.map {library.bundleFolder.toPath().relativize(it.toPath())}
    val newLocalJars = newLocalJarNames.map { newBundleFolder.resolve(it)}

    val newArtifactFolder = properOfflineRepo.resolve(artifactAddressToRelativePath(library.artifactAddress))
    Files.createDirectories(newArtifactFolder)

    FileUtils.copyDirectory(library.bundleFolder, newBundleFolder.toFile())
    FileUtils.copyDirectory(library.artifact.parentFile, newArtifactFolder.toFile())

    return NewAndroidLibrary(
      newArtifactFolder.resolve(library.artifact.name).toFile(),
      newLocalJars.map {it.toFile()},
      newBundleFolder.toFile(),
      library.artifactAddress
    )
  }

  fun convertCachedLibraryToProper(library: NewJavaLibrary): NewJavaLibrary {
    val newArtifactFolder = properOfflineRepo.resolve(artifactAddressToRelativePath(library.artifactAddress))
    Files.createDirectories(newArtifactFolder)

    FileUtils.copyDirectory(library.artifact.parentFile, newArtifactFolder.toFile())

    return NewJavaLibrary(
      newArtifactFolder.resolve(library.artifact.name).toFile(),
      library.artifactAddress
    )
  }
}

private fun artifactAddressToRelativePath(artifactAddress: String): String {
  val (pkg, name, version) = artifactAddress.dropLast(4).split(':')

  return pkg.replace('.', File.separatorChar) + File.separator + name + File.separator + version
}
