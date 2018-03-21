// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.projectsystem

import com.android.tools.idea.projectsystem.gradle.toProjectModel
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PROJECTMODEL_MULTIFLAVOR
import com.google.common.truth.Truth.assertThat

class GradleModelConverterMergeTest : AndroidGradleTestCase() {
  fun testResolvedConfiguration() {
    loadProject(PROJECTMODEL_MULTIFLAVOR)
    val input = model.androidProject
    val output = input.toProjectModel()

    for (variant in output.variants) {
      val artifact = variant.mainArtifact
      val constituentConfigs = output.configTable.configsIntersecting(variant.mainArtifactConfigPath)
      var manualResolvedConfig = constituentConfigs.reduce { a, b -> a.mergeWith(b) }
      assertThat(artifact.resolved.sources).isEqualTo(manualResolvedConfig.sources)
      assertThat(artifact.resolved.manifestValues.versionName).isEqualTo(manualResolvedConfig.manifestValues.versionName)
    }
  }
}