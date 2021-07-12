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

import com.android.tools.idea.appinspection.ide.resolver.TestArtifactResolver
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolver
import com.android.tools.idea.appinspection.test.TEST_JAR_PATH
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.google.common.truth.Truth.assertThat
import com.intellij.util.io.createFile
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class BlazeArtifactResolverTest {
  @get:Rule
  val temporaryDirectoryRule = TemporaryDirectoryRule()

  @Test
  fun resolveInspectorJarOverLibraryAarAndHttp() = runBlocking {
    val artifactCoordinate = ArtifactCoordinate("androidx.work", "work-runtime", "2.5.0-beta01", ArtifactCoordinate.Type.AAR)
    val artifactDir = temporaryDirectoryRule.newPath("test")
    // blaze artifact resolver should always take inspector.jar over other options.
    artifactDir.resolve("inspector.jar").createFile()
    artifactDir.resolve("library.aar").createFile()
    val moduleSystemResolver = object : ArtifactResolver {
      override suspend fun resolveArtifact(artifactCoordinate: ArtifactCoordinate): Path {
        return artifactDir
      }
    }
    val httpResolver = TestArtifactResolver { null }
    val resolver = BlazeArtifactResolver(httpResolver, moduleSystemResolver)
    val inspectorJar = resolver.resolveArtifact(artifactCoordinate)
    assertThat(inspectorJar!!.fileName.toString()).isEqualTo("inspector.jar")
  }

  @Test
  fun resolveInspectorLibraryAar() = runBlocking {
    val artifactCoordinate = ArtifactCoordinate("androidx.work", "work-runtime", "2.5.0-beta01", ArtifactCoordinate.Type.AAR)
    val artifactDir = temporaryDirectoryRule.newPath("test")
    // when inspector.jar is not present, resolver should take the library.aar
    artifactDir.resolve("library.aar").createFile()
    val moduleSystemResolver = object : ArtifactResolver {
      override suspend fun resolveArtifact(artifactCoordinate: ArtifactCoordinate): Path {
        return artifactDir
      }
    }
    val httpResolver = TestArtifactResolver { null }
    val resolver = BlazeArtifactResolver(httpResolver, moduleSystemResolver)
    val inspectorJar = resolver.resolveArtifact(artifactCoordinate)
    assertThat(inspectorJar!!.fileName.toString()).isEqualTo("library.aar")
  }

  @Test
  fun fallbackToHttp() = runBlocking {
    val artifactCoordinate = ArtifactCoordinate("androidx.work", "work-runtime", "2.5.0-beta01", ArtifactCoordinate.Type.AAR)
    val httpResolver = TestArtifactResolver { TEST_JAR_PATH }
    val moduleSystemArtifactResolver = object : ArtifactResolver {
      override suspend fun resolveArtifact(artifactCoordinate: ArtifactCoordinate): Path {
        return temporaryDirectoryRule.newPath("test")
      }
    }
    val resolver = BlazeArtifactResolver(httpResolver, moduleSystemArtifactResolver)
    assertThat(resolver.resolveArtifact(artifactCoordinate)).isEqualTo(TEST_JAR_PATH)
  }
}