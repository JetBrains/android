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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.loaders

import com.android.ide.common.gradle.model.IdeVariant
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.essentiallyEquals
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.ANDROID_PROJECT_CACHE_PATH
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.VARIANTS_CACHE_DIR_PATH
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.toJson
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.modulePath
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.NewVariant
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import java.io.File
import java.nio.file.Files

private const val VARIANT_NAME = "debug"

class SimpleJsonLoaderTest(): AndroidGradleTestCase() {
  @Throws(Exception::class)
  fun testLoadAndroidProject() {
    loadProject(TestProjectPaths.TEST_NOSYNCBUILDER)
    val appModule = getModule("app")
    val oldAndroidProject = AndroidModuleModel.get(appModule)!!.androidProject

    val moduleDir = oldAndroidProject.buildFolder.parentFile
    val sdkDir = File(oldAndroidProject.bootClasspath.first()).parentFile
    val offlineRepo = File("/")

    val converter = PathConverter(moduleDir, sdkDir, offlineRepo)

    val newAndroidProject = NewAndroidProject(oldAndroidProject, modulePath)
    val androidProjectJson = newAndroidProject.toJson(converter)

    val oldVariant = oldAndroidProject.variants.first()!! as IdeVariant
    val newVariant = NewVariant(oldVariant, oldAndroidProject)
    val variantJson = newVariant.toJson(converter)

    val tmpFolder = createTempDir().toPath()

    Files.write(tmpFolder.resolve(ANDROID_PROJECT_CACHE_PATH), androidProjectJson.toByteArray())

    val variantsDir = tmpFolder.resolve(VARIANTS_CACHE_DIR_PATH)
    Files.createDirectories(variantsDir)
    Files.write(variantsDir.resolve("$VARIANT_NAME.json"), variantJson.toByteArray())

    val simpleJsonLoader = SimpleJsonLoader(tmpFolder, converter)

    val loadedAndroidProject = simpleJsonLoader.loadAndroidProject(VARIANT_NAME)

    assertTrue(loadedAndroidProject essentiallyEquals oldAndroidProject)
  }

  @Throws(Exception::class)
  fun testLoadGradleProject() {
    // TODO(qumeric)
  }

  @Throws(Exception::class)
  fun testLoadGlobalLibraryMap() {
    // TODO(qumeric)
  }
}
