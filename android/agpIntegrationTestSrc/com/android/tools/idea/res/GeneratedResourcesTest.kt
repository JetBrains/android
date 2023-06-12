/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth
import com.intellij.openapi.project.guessProjectDir
import java.io.File


/**
 * Tests for resources registered as generated with Gradle.
 */
class GeneratedResourcesTest : AndroidGradleTestCase() {

  /**
   * Regression test for b/120750247.
   */
  fun testGeneratedRawResource() {
    val projectRoot = prepareProjectForImport(TestProjectPaths.PROJECT_WITH_APPAND_LIB)

    File(projectRoot, "app/build.gradle").appendText(
      """
      android {
        String resGeneratePath = "${"$"}{buildDir}/generated/my_generated_resources/res"
        def generateResTask = tasks.create(name: 'generateMyResources').doLast {
            def rawDir = "${"$"}{resGeneratePath}/raw"
            mkdir(rawDir)
            file("${"$"}{rawDir}/sample_raw_resource").write("sample text")
        }

        def resDir = files(resGeneratePath).builtBy(generateResTask)

        applicationVariants.all { variant ->
            variant.registerGeneratedResFolders(resDir)
        }
      }
      """.trimIndent())

    requestSyncAndWait()

    AndroidProjectRootListener.ensureSubscribed(project)
    Truth.assertThat(StudioResourceRepositoryManager.getAppResources(project.findAppModule())!!
                       .getResources(ResourceNamespace.RES_AUTO, ResourceType.RAW, "sample_raw_resource")).isEmpty()

    generateSources()

    Truth.assertThat(StudioResourceRepositoryManager.getAppResources(project.findAppModule())!!
                       .getResources(ResourceNamespace.RES_AUTO, ResourceType.RAW, "sample_raw_resource")).isNotEmpty()

    myFixture.openFileInEditor(
      project.guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/com/example/projectwithappandlib/app/MainActivity.java")!!)

    myFixture.moveCaret("int id = |item.getItemId();")
    myFixture.type("R.raw.")
    myFixture.completeBasic()

    Truth.assertThat(myFixture.lookupElementStrings).containsExactly("sample_raw_resource", "class")
  }
}