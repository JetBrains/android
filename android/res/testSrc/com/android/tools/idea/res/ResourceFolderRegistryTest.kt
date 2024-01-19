/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.facet.ResourceFolderManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ResourceFolderRegistryTest {
  private val appModuleBuilder =
    AndroidModuleModelBuilder(
      gradlePath = ":app",
      selectedBuildVariant = "debug",
      createAndroidProjectBuilderForDefaultTestProjectStructure(
          IdeAndroidProjectType.PROJECT_TYPE_APP,
          "p1.p2",
        )
        .withAndroidModuleDependencyList { _ -> listOf(AndroidModuleDependency(":mylib", "debug")) },
    )

  private val libModuleBuilder =
    AndroidModuleModelBuilder(
      gradlePath = ":mylib",
      selectedBuildVariant = "debug",
      createAndroidProjectBuilderForDefaultTestProjectStructure(
        IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
        "com.example.mylib",
      ),
    )

  @get:Rule
  val androidProjectRule =
    AndroidProjectRule.withAndroidModels(
        { dir ->
          assertThat(dir.resolve("app/src").mkdirs()).isTrue()
          assertThat(dir.resolve("app/res").mkdirs()).isTrue()
          assertThat(dir.resolve("mylib/src").mkdirs()).isTrue()
          assertThat(dir.resolve("mylib/res").mkdirs()).isTrue()
        },
        JavaModuleModelBuilder.rootModuleBuilder,
        appModuleBuilder,
        libModuleBuilder,
      )
      .initAndroid(true)

  private val fixture by lazy { androidProjectRule.fixture /*.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    }*/ }

  private val project by lazy { fixture.project }

  private val appModule by lazy { project.gradleModule(":app", IdeModuleWellKnownSourceSet.MAIN)!! }
  private val libModule by lazy {
    project.gradleModule(":mylib", IdeModuleWellKnownSourceSet.MAIN)!!
  }

  private val appFacet by lazy { appModule.androidFacet!! }
  private val libFacet by lazy { libModule.androidFacet!! }

  @Test
  fun resetInvalidatesPerFacet() {
    val registry = ResourceFolderRegistry.getInstance(project)

    val appResourceFolders = ResourceFolderManager.getInstance(appFacet).folders
    val appResourceRepositories =
      appResourceFolders.associateWith { registry[appFacet, it, ResourceNamespace.RES_AUTO] }

    val libResourceFolders = ResourceFolderManager.getInstance(libFacet).folders
    val libResourceRepositories =
      libResourceFolders.associateWith { registry[libFacet, it, ResourceNamespace.RES_AUTO] }

    // Repositories should be cached.
    for ((folder, repository) in appResourceRepositories) {
      assertThat(registry[appFacet, folder, ResourceNamespace.RES_AUTO]).isSameAs(repository)
    }
    for ((folder, repository) in libResourceRepositories) {
      assertThat(registry[libFacet, folder, ResourceNamespace.RES_AUTO]).isSameAs(repository)
    }

    // Clearing appFacet only should clear those directories.
    registry.reset(appFacet)
    appResourceFolders.forEach { assertThat(registry.getCached(it, Namespacing.DISABLED)).isNull() }
    libResourceFolders.forEach {
      assertThat(registry.getCached(it, Namespacing.DISABLED)).isNotNull()
    }

    // Clearing libFacet finished the job.
    registry.reset(libFacet)
    appResourceFolders.forEach { assertThat(registry.getCached(it, Namespacing.DISABLED)).isNull() }
    libResourceFolders.forEach { assertThat(registry.getCached(it, Namespacing.DISABLED)).isNull() }

    // All repositories fetched now should be new objects.
    for ((folder, repository) in appResourceRepositories) {
      assertThat(registry[appFacet, folder, ResourceNamespace.RES_AUTO]).isNotSameAs(repository)
    }
    for ((folder, repository) in libResourceRepositories) {
      assertThat(registry[libFacet, folder, ResourceNamespace.RES_AUTO]).isNotSameAs(repository)
    }
  }
}
