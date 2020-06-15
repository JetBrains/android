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
package com.android.tools.idea.gradle.structure.model.pom

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE_REPO
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.core.IsEqual.equalTo
import org.jetbrains.android.AndroidTestBase
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File

class MavenPomsTest : AndroidGradleTestCase() {

  private val sampleRepo = File(AndroidTestBase.getTestDataPath(), toSystemDependentName(PSD_SAMPLE_REPO))

  @Test
  fun testPomDependencies() {
    val lib1Pom = File(sampleRepo, "com/example/libs/lib1/1.0/lib1-1.0.pom")
    assertThat(lib1Pom.exists(), equalTo(true))
    val dependencies1 = MavenPoms.findDependenciesInPomFile(lib1Pom.absoluteFile).map { it.compactNotation() }
    assertThat(dependencies1, hasItems("com.android.support:appcompat-v7:[27.0.2,)", "com.example.libs:lib2:1.0"))

    val lib2Pom = File(sampleRepo, "com/example/libs/lib2/1.0/lib2-1.0.pom")
    assertThat(lib2Pom.exists(), equalTo(true))
    val dependencies2 = MavenPoms.findDependenciesInPomFile(lib2Pom.absoluteFile).map { it.compactNotation() }
    assertThat(dependencies2, hasItems("com.android.support:appcompat-v7:[27.0.2,)", "com.example.jlib:lib3:1.0"))

    val lib3Pom = File(sampleRepo, "com/example/jlib/lib3/1.0/lib3-1.0.pom")
    assertThat(lib3Pom.exists(), equalTo(true))
    val dependencies3 = MavenPoms.findDependenciesInPomFile(lib3Pom.absoluteFile).map { it.compactNotation() }
    assertThat(dependencies3, hasItems("com.example.jlib:lib4:1.0"))

    val lib4Pom = File(sampleRepo, "com/example/jlib/lib4/1.0/lib4-1.0.pom")
    assertThat(lib4Pom.exists(), equalTo(true))
    val dependencies4 = MavenPoms.findDependenciesInPomFile(lib4Pom.absoluteFile).map { it.compactNotation() }
    assertThat(dependencies4.isEmpty(), equalTo(true))
  }
}