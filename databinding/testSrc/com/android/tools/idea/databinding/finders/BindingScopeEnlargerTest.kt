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
package com.android.tools.idea.databinding.finders

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private val EMPTY_LAYOUT_FILE =
  // language=xml
  """
  <?xml version="1.0" encoding="utf-8"?>
  <layout xmlns:android="http://schemas.android.com/apk/res/android">
    <LinearLayout />
  </layout>
  """
    .trimIndent()

@RunsInEdt
@RunWith(JUnit4::class)
class BindingScopeEnlargerTest {
  private val projectRule = AndroidProjectRule.onDisk()

  // We want to run tests on EDT, but we also need to make sure the project rule is not initialized
  // on EDT.
  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture

  private val facet
    get() = projectRule.module.androidFacet!!

  private val project
    get() = projectRule.project

  @Before
  fun setUp() {
    fixture.addFileToProject(
      "AndroidManifest.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """
        .trimIndent(),
    )

    LayoutBindingModuleCache.getInstance(facet).dataBindingMode = DataBindingMode.ANDROIDX
  }

  @Test
  fun scopeContainsAllLightClasses() {
    fixture.addFileToProject("res/layout/activity_main.xml", EMPTY_LAYOUT_FILE)

    // There should be exactly one light binding class.
    val moduleCache = LayoutBindingModuleCache.getInstance(facet)
    assertThat(moduleCache.bindingLayoutGroups.map { it.mainLayout.qualifiedClassName })
      .containsExactly("test.db.databinding.ActivityMainBinding")
    val allLightBindingClasses = moduleCache.getLightBindingClasses()
    assertThat(allLightBindingClasses).hasSize(1)

    // Resolve scope for a class in the module should contain the light binding class, as well as
    // the BR and DataBindingComponent classes.
    val activityClass = fixture.addClass("public class MainActivity {}")
    val scope = activityClass.resolveScope

    assertThat(PsiSearchScopeUtil.isInScope(scope, allLightBindingClasses.single()))
    assertThat(PsiSearchScopeUtil.isInScope(scope, requireNotNull(moduleCache.lightBrClass)))
    assertThat(
      PsiSearchScopeUtil.isInScope(
        scope,
        requireNotNull(moduleCache.lightDataBindingComponentClass),
      )
    )
  }
}

@RunWith(JUnit4::class)
class BindingScopeEnlargerMultiModuleTest {
  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModels(
        { dir ->
          assertThat(dir.resolve("app/src").mkdirs()).isTrue()
          assertThat(dir.resolve("app/res").mkdirs()).isTrue()
          assertThat(dir.resolve("lib/src").mkdirs()).isTrue()
          assertThat(dir.resolve("lib/res").mkdirs()).isTrue()
        },
        JavaModuleModelBuilder.rootModuleBuilder,
        AndroidModuleModelBuilder(
          ":app",
          "debug",
          AndroidProjectBuilder(
            androidModuleDependencyList = { _ -> listOf(AndroidModuleDependency(":lib", "debug")) },
            namespace = { "com.example.app" },
          ),
        ),
        AndroidModuleModelBuilder(
          ":lib",
          "debug",
          AndroidProjectBuilder(
            projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
            namespace = { "com.example.lib" },
          ),
        ),
      )
      .initAndroid(true)

  private val fixture by lazy { projectRule.fixture }

  private val project by lazy { projectRule.project }

  private val appModule by lazy {
    requireNotNull(project.gradleModule(":app", IdeModuleWellKnownSourceSet.MAIN))
  }
  private val libModule by lazy {
    requireNotNull(project.gradleModule(":lib", IdeModuleWellKnownSourceSet.MAIN))
  }

  private val appFacet by lazy { requireNotNull(appModule.androidFacet) }
  private val libFacet by lazy { requireNotNull(libModule.androidFacet) }

  @Before
  fun setUp() {
    LayoutBindingModuleCache.getInstance(appFacet).dataBindingMode = DataBindingMode.ANDROIDX
    LayoutBindingModuleCache.getInstance(libFacet).dataBindingMode = DataBindingMode.ANDROIDX
  }

  @Test
  fun appScopeContainsLayoutFromLib() {
    fixture.addFileToProject("app/src/main/res/layout/activity_app.xml", EMPTY_LAYOUT_FILE)
    fixture.addFileToProject("lib/src/main/res/layout/activity_lib.xml", EMPTY_LAYOUT_FILE)

    // Validate that light binding classes are generated for both layouts
    val appLightBindingClasses = appFacet.getLightBindingClasses()
    val libLightBindingClasses = libFacet.getLightBindingClasses()

    assertThat(appLightBindingClasses).hasSize(1)
    assertThat(libLightBindingClasses).hasSize(1)

    val appLightBindingClass = appLightBindingClasses.single()
    val libLightBindingClass = libLightBindingClasses.single()
    assertThat(appLightBindingClass.qualifiedName)
      .isEqualTo("com.example.app.databinding.ActivityAppBinding")
    assertThat(libLightBindingClass.qualifiedName)
      .isEqualTo("com.example.lib.databinding.ActivityLibBinding")

    // The app module should have both binding classes in scope, whereas the lib module should only
    // have its own class.
    val appActivityClass =
      fixture
        .addFileToProject("app/src/main/src/AppActivity.java", "public class AppActivity {}")
        .getFirstJavaClass()
    val libActivityClass =
      fixture
        .addFileToProject("lib/src/main/src/LibActivity.java", "public class LibActivity {}")
        .getFirstJavaClass()

    assertThat(PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, appLightBindingClass))
      .isTrue()
    assertThat(PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, libLightBindingClass))
      .isTrue()
    assertThat(PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, appLightBindingClass))
      .isFalse()
    assertThat(PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, libLightBindingClass))
      .isTrue()
  }

  private fun AndroidFacet.getLightBindingClasses() = runReadAction {
    LayoutBindingModuleCache.getInstance(this).getLightBindingClasses()
  }

  private fun PsiFile.getFirstJavaClass() = runReadAction { (this as PsiJavaFile).classes.first() }
}
