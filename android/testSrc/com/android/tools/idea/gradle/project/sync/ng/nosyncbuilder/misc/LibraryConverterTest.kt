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
import junit.framework.TestCase
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

class LibraryConverterTest : TestCase() {
  private lateinit var testFolder: TemporaryFolder
  private lateinit var offlineRepoPath: Path
  private lateinit var libraryConverter: LibraryConverter

  override fun setUp() {
    testFolder = TemporaryFolder().apply { create() }
    offlineRepoPath = testFolder.newFolder("offline_repo").toPath()
    libraryConverter = LibraryConverter(offlineRepoPath)
  }

  @Throws(Exception::class)
  fun testProcessNewAndroidLibrary() {
    val artifact = testFolder.newFile("artifact-1.0.aar")
    val bundleFolder = testFolder.root
    // localJars should actually be in $bundleFolder/jars/libs but it does not changes behavior
    val localJars = listOf(testFolder.newFile("a.jar"), testFolder.newFile("b.jar"))
    val artifactAddress = "com.google.test:artifact:1.0@aar"

    val cachedAndroidLibrary = NewAndroidLibrary(artifact, localJars, bundleFolder, artifactAddress)
    val properAndroidLibrary = libraryConverter.convertCachedLibraryToProper(cachedAndroidLibrary)

    assertTrue(properAndroidLibrary.artifact.exists())
    properAndroidLibrary.localJars.forEach { it.exists() }
    assertEquals(cachedAndroidLibrary.artifactAddress, properAndroidLibrary.artifactAddress)
  }

  @Throws(Exception::class)
  fun testProcessNewJavaLibrary() {
    val artifact = testFolder.newFile("artifact-1.0.jar")
    val artifactAddress = "com.google.test:artifact:1.0@jar"

    val cachedJavaLibrary = NewJavaLibrary(artifact, artifactAddress)
    val properJavaLibrary = libraryConverter.convertCachedLibraryToProper(cachedJavaLibrary)

    assertTrue(properJavaLibrary.artifact.exists())
    assertEquals(cachedJavaLibrary.artifactAddress, properJavaLibrary.artifactAddress)
  }
}
