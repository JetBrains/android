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
package com.android.tools.idea.appinspection.ide.resolver.blaze

import com.android.tools.idea.appinspection.ide.resolver.ModuleSystemArtifactFinder
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.service.TestFileService
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.util.io.createFile
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.assertFailsWith

class BlazeArtifactResolverTest {
  @get:Rule
  val temporaryDirectoryRule = TemporaryDirectoryRule()

  @get:Rule
  val projectRule = ProjectRule()

  private lateinit var testFileService: TestFileService

  @Before
  fun setUp() {
    testFileService = TestFileService()
  }

  @Test
  fun resolveInspectorJarWithBlazeResolver() = runBlocking {
    val artifactCoordinate = ArtifactCoordinate("androidx.work", "work-runtime", "2.5.0-beta01", ArtifactCoordinate.Type.AAR)
    val artifactDir = temporaryDirectoryRule.newPath("test")
    val inspectorPath = artifactDir.resolve("inspector.jar").createFile()

    val uri = URI.create("jar:${artifactDir.resolve("library.aar").toUri()}")
    FileSystems.newFileSystem(uri, mapOf("create" to "true")).use { zipFs ->
      val pathInZipFile = zipFs.getPath("/inspector.jar")
      Files.copy(inspectorPath, pathInZipFile, StandardCopyOption.REPLACE_EXISTING)
    }
    val moduleSystemArtifactFinder = ModuleSystemArtifactFinder(projectRule.project) { artifactDir }
    val resolver = BlazeArtifactResolver(testFileService, moduleSystemArtifactFinder)
    val inspectorJar = resolver.resolveArtifact(artifactCoordinate)
    assertThat(inspectorJar.fileName.toString()).isEqualTo("inspector.jar")
  }

  @Test
  fun failToResolveInspector() = runBlocking<Unit> {
    val artifactCoordinate = ArtifactCoordinate("androidx.work", "work-runtime", "2.5.0-beta01", ArtifactCoordinate.Type.AAR)
    val resolver = BlazeArtifactResolver(testFileService, ModuleSystemArtifactFinder(projectRule.project) {
      throw AppInspectionArtifactNotFoundException("blah", artifactCoordinate)
    })
    assertFailsWith(AppInspectionArtifactNotFoundException::class) { resolver.resolveArtifact(artifactCoordinate) }
  }
}