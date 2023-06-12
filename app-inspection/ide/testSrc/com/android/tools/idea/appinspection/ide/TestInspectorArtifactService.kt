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

import com.android.tools.idea.appinspection.inspector.api.launch.ArtifactCoordinate
import com.android.tools.idea.appinspection.test.TEST_JAR_PATH
import com.intellij.openapi.project.Project
import java.nio.file.Path

class TestInspectorArtifactService : InspectorArtifactService {
  override suspend fun getOrResolveInspectorArtifact(
    artifactCoordinate: ArtifactCoordinate,
    project: Project
  ): Path {
    return TEST_JAR_PATH
  }
}
