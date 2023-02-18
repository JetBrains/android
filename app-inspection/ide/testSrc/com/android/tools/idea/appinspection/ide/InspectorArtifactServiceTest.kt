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
package com.android.tools.idea.appinspection.ide

import com.android.testutils.TestUtils
import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.inspector.api.service.TestFileService
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolver
import com.android.tools.idea.appinspection.inspector.ide.resolver.ArtifactResolverFactory
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class InspectorArtifactServiceTest {

  @get:Rule val androidProjectRule = AndroidProjectRule.inMemory()

  private val libraryPath =
    TestUtils.resolveWorkspacePath(
      "tools/adt/idea/app-inspection/ide/testData/libraries/androidx/work/work-runtime/2.5.0-beta01/work-runtime-2.5.0-beta01.aar"
    )

  @Test
  fun getInspectorJar() =
    runBlocking<Unit> {
      val fileService = TestFileService()
      val artifactResolverFactory =
        object : ArtifactResolverFactory {
          override fun getArtifactResolver(project: Project): ArtifactResolver {
            return object : ArtifactResolver {
              override suspend fun resolveArtifact(artifactCoordinate: ArtifactCoordinate): Path {
                return libraryPath
              }
            }
          }
        }
      val artifactService = InspectorArtifactServiceImpl(fileService, artifactResolverFactory)

      val resolvedArtifactPath =
        artifactService.getOrResolveInspectorArtifact(
          ArtifactCoordinate(
            "androidx.work",
            "work-runtime",
            "2.5.0-beta01",
            ArtifactCoordinate.Type.AAR
          ),
          androidProjectRule.project
        )

      assertThat(resolvedArtifactPath).isNotNull()
      assertThat(resolvedArtifactPath.fileName.toString())
        .isEqualTo("work-runtime-2.5.0-beta01.aar")
    }
}
