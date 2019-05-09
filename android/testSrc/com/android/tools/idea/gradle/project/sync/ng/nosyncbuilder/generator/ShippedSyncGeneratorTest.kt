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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.generator

import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.essentiallyEquals
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.LegacyAndroidProjectStub
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.ANDROID_PROJECT_CACHE_PATH
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.BUNDLE_PATH
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OFFLINE_REPO_PATH
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.VARIANTS_CACHE_DIR_PATH
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.NewVariant
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.AndroidProjectProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.repackage.com.google.protobuf.util.JsonFormat
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFailsWith

class ShippedSyncGeneratorTest : AndroidGradleTestCase() {

  // TODO(b/113098468): enable this once we know how to keep the JSON files up to date.
  override fun shouldRunTest() = false

  // Project folder created by AndroidGradleTestCase has the same name as the test which created it
  private val ANDROID_PROJECT_TEST_NAME = "testGeneratedSyncIsTheSameAsRealAndroidProject"
  @Throws(Exception::class)
  fun testGeneratedSyncIsTheSameAsRealAndroidProject() {
    loadProject(TestProjectPaths.TEST_NOSYNCBUILDER)
    val appModule = getModule("app")

    val oldAndroidProject = AndroidModuleModel.get(appModule)!!.androidProject

    // TODO(qumeric): do something saner
    val sdkRoot = File(oldAndroidProject.bootClasspath.iterator().next()).parentFile.parentFile.parentFile

    val outRoot = createTempDir()

    val repoPaths = EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths().map( File::toString)

    val shippedSyncGenerator = ShippedSyncGenerator(projectFolderPath, sdkRoot, repoPaths, false )

    shippedSyncGenerator.use {
      it.generateModels(outRoot.toPath())
      // TODO(qumeric): test generateOfflineRepo
    }

    val moduleConverter = PathConverter(projectFolderPath.resolve("app"), sdkRoot, File(OFFLINE_REPO_PATH), File(BUNDLE_PATH))

    val projectRoot = outRoot.resolve(ANDROID_PROJECT_TEST_NAME)
    val modulePath = projectRoot.resolve("app")

    val loadedAndroidProject = loadAndroidProjectFromJSON(modulePath.toPath(), moduleConverter)

    assertTrue(oldAndroidProject essentiallyEquals loadedAndroidProject)
  }

  @Throws(Exception::class)
  fun testClearRepositoriesOK() {
    val buildGradleContent = """
      repositories  { blah }
      { {
      repositories {
      { } abc }
      } }
    """.trimIndent()

    val expected = """
      repositories  {}
      { {
      repositories {}
      } }
    """.trimIndent()

    assertEquals(expected, clearRepositories(buildGradleContent))
  }

  @Throws(Exception::class)
  fun testClearRepositoriesMalformed() {
    val malformedBuildGradleContents = listOf(
      "repositories {{ }"
      // TODO(qumeric): think about other cases
    )
    for (badContent in malformedBuildGradleContents) {
      assertFailsWith<IllegalArgumentException> { clearRepositories(badContent)}
    }
  }
}

private fun loadAndroidProjectFromJSON(path: Path, converter: PathConverter): IdeAndroidProject {
  val androidProjectJSON = String(Files.readAllBytes(path.resolve(ANDROID_PROJECT_CACHE_PATH)))
  val debugVariantJSON = String(Files.readAllBytes(path.resolve(VARIANTS_CACHE_DIR_PATH).resolve("debug.json")))
  val androidProjectBuilder = AndroidProjectProto.AndroidProject.newBuilder()
  JsonFormat.parser().ignoringUnknownFields().merge(androidProjectJSON, androidProjectBuilder)

  val newAndroidProject = NewAndroidProject(androidProjectBuilder.build(), path.toFile(), converter)

  val variantBuilder = VariantProto.Variant.newBuilder()
  JsonFormat.parser().ignoringUnknownFields().merge(debugVariantJSON, variantBuilder)
  val newVariant = NewVariant(variantBuilder.build(), converter)

  return LegacyAndroidProjectStub(newAndroidProject, newVariant)
}

// TODO(qumeric): add test for the Java model
