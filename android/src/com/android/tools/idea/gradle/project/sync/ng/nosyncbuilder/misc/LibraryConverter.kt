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
import com.intellij.util.io.exists
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

const val BUNDLE_DIR = "bundles"

// properOfflineRepo have to exist
class LibraryConverter(private val properOfflineRepo: Path, private val generateOfflineRepo: Boolean = true) {
  fun convertCachedLibraryToProper(library: NewAndroidLibrary): NewAndroidLibrary {
    val relativePath = artifactAddressToRelativePath(library.artifactAddress)

    val newArtifactFolder = properOfflineRepo.resolve(relativePath)
    val newBundleFolder = properOfflineRepo.resolve(BUNDLE_DIR).resolve(relativePath)
    val newLocalJarNames = library.localJars.map {library.bundleFolder.toPath().relativize(it.toPath())}
    val newLocalJars = newLocalJarNames.map { newBundleFolder.resolve(it)}

    if (generateOfflineRepo) {
      Files.createDirectories(newArtifactFolder)
      Files.createDirectories(newBundleFolder)

      FileUtils.copyDirectory(library.artifact.parentFile, newArtifactFolder.toFile())
      FileUtils.copyDirectory(library.bundleFolder, newBundleFolder.toFile())
    } else {
      if (!newArtifactFolder.exists()) {
        throw IOException("Artifact folder $newArtifactFolder does not exist in $properOfflineRepo")
      }
      if (!newBundleFolder.exists()) {
        throw IOException("Bundle folder $newBundleFolder does not exist in $properOfflineRepo")
      }
    }

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
