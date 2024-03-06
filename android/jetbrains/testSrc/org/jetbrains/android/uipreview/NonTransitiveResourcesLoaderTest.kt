/*
 * Copyright (C) 2022 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.structure.model.getModuleByGradlePath
import com.android.tools.idea.layoutlib.LayoutLibrary
import com.android.tools.idea.rendering.AndroidFacetRenderModelModule
import com.android.tools.idea.rendering.classloading.loaders.NameRemapperLoader
import com.android.tools.idea.res.StudioResourceIdManager
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.util.androidFacet
import com.android.tools.rendering.IRenderLogger
import com.android.tools.rendering.ViewLoader
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.classloading.loaders.StaticLoader
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.roots.ModuleRootModificationUtil
import org.jetbrains.android.uipreview.nontransitive.app.R
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

class NonTransitiveResourcesLoaderTest() {
  @get:Rule
  val androidProject = AndroidProjectRule.withAndroidModels(
    { root ->
      root.resolve("lib/src/main/res/values/strings.xml")
        .also { it.parentFile.mkdirs() }
        .writeText(
          // language=xml
          """
          <resources>
            <string name="lib_name">lib Name</string>
          </resources>
          """.trimIndent()
        )
      root.resolve("app/src/main/res/values/strings.xml")
        .also { it.parentFile.mkdirs() }
        .writeText(
          // language=xml
          """
          <resources>
            <string name="app_name">app name</string>
          </resources>
          """.trimIndent()
        )
    },
    JavaModuleModelBuilder.rootModuleBuilder,
    AndroidModuleModelBuilder(
      gradlePath = ":lib",
      selectedBuildVariant = "debug",
      projectBuilder = AndroidProjectBuilder(
        namespace = { "com.example.lib" },
        projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
      )
    ),
    AndroidModuleModelBuilder(
      gradlePath = ":app",
      selectedBuildVariant = "debug",
      projectBuilder = AndroidProjectBuilder(
        projectType = { IdeAndroidProjectType.PROJECT_TYPE_APP },
        namespace = { "com.example.app" },
        androidModuleDependencyList = {
          listOf(
            AndroidModuleDependency(moduleGradlePath = ":lib", variant = "debug"),
          )
        }
      ),
    ),
  )


  /**
   * Regression test for b/206862224.
   *
   * This test ensures that, when using non transitive R classes, the compiled R class is looked up even for dependencies and not just
   * the main module.
   * Before the fix for b/206862224, project library dependencies would be considered as regular external libraries and its ids to be
   * non-final. This is incorrect as, with non transitive R classes enabled, the library is still treated as part of the project and the
   * ids inlined (because they are final). The difference is just that the library now has its own R class, but the ids are still inlined.
   */
  @Test
  fun testNonTransitiveRClassesAreInitializedCorrectly() {
    val app = androidProject.project.getModuleByGradlePath(":app") ?: fail("Could not find app")
    val lib = androidProject.project.getModuleByGradlePath(":lib") ?: fail("Could not find lib")
    // TODO(b/280427949): Should this be done by the withAndroidModels factory?
    ModuleRootModificationUtil.addDependency(app, lib)

    val staticLoader = StaticLoader(
      R::class.java.name to loadClassBytes(
        R::class.java),
      org.jetbrains.android.uipreview.nontransitive.lib.R::class.java.name to loadClassBytes(
        org.jetbrains.android.uipreview.nontransitive.lib.R::class.java)
    )
    val delegateClassLoader = DelegatingClassLoader(
      NonTransitiveResourcesLoaderTest::class.java.classLoader,
      NameRemapperLoader(
        staticLoader
      ) {
        it.replace("com.example.lib.R", org.jetbrains.android.uipreview.nontransitive.lib.R::class.java.name)
          .replace("com.example.app.R", R::class.java.name)
      }
    )

    // We do not need any of the services offered by LayoutLibrary in this test so just mock it.
    val layoutlib = mock<LayoutLibrary>()
    val facet = app.androidFacet ?: fail(":app does not have an android facet")
    val viewLoader = ViewLoader(layoutlib, AndroidFacetRenderModelModule(facet), IRenderLogger.NULL_LOGGER, null, delegateClassLoader)
    viewLoader.loadAndParseRClassSilently()
    val idManager = StudioResourceIdManager.get(app)
    assertThat(idManager).isNotNull()

    assertThat(idManager.getCompiledId(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "app_name")))
      .isEqualTo(0x7f011111)
    assertThat(idManager.getCompiledId(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "lib_name")))
      .isEqualTo(0x7f022222)

  }
}