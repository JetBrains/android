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

import com.android.tools.idea.appinspection.ide.resolver.AppInspectorJarPaths
import com.android.tools.idea.appinspection.ide.resolver.TestArtifactResolver
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.service.TestFileService
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolver
import com.android.tools.idea.appinspection.test.TEST_JAR_PATH
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

class BlazeArtifactResolverTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.inMemory()

  @Test
  fun resolveHttpFailsAndThenResolvesAgainstGoogle3() = runBlocking {
    val fileService = TestFileService()
    val jarPaths = AppInspectorJarPaths(fileService)
    val httpResolver = TestArtifactResolver { null }
    val artifactResolvedDeferred = CompletableDeferred<Unit>()
    val blazeArtifactResolver = object : ArtifactResolver {
      override suspend fun resolveArtifact(artifactCoordinate: ArtifactCoordinate, project: Project): Path? {
        artifactResolvedDeferred.complete(Unit)
        return TEST_JAR_PATH
      }
    }
    val resolver = BlazeArtifactResolver(jarPaths, httpResolver, blazeArtifactResolver)
    resolver.resolveArtifact(
      ArtifactCoordinate("androidx.work", "work-runtime", "2.5.0-beta01", ArtifactCoordinate.Type.AAR),
      androidProjectRule.project
    )
    artifactResolvedDeferred.await()
  }
}