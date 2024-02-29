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
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.testFramework.DumbModeTestUtils
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
  fun scopeDoesNotCacheStaleValuesInDumbMode() {
    assertThat(DumbService.isDumb(project)).isFalse()

    // In dumb mode, add a resource and then request the current scope. The enlarger will return a
    // stale value while in dumb mode, but should return an updated value after dumb mode completes.
    val (activityClass, dumbScope) =
      DumbModeTestUtils.computeInDumbModeSynchronously(project) {
        fixture.addFileToProject("res/layout/activity_main.xml", EMPTY_LAYOUT_FILE)
        val activityClass = fixture.addClass("public class MainActivity {}")
        val dumbScope = activityClass.resolveScope
        Pair(activityClass, dumbScope)
      }
    // Exit dumb mode and request our final enlarged scope. It should pick up the changes that
    // occurred while we were previously in dumb mode.
    val enlargedScope = activityClass.resolveScope

    val moduleCache = LayoutBindingModuleCache.getInstance(facet)
    assertThat(moduleCache.bindingLayoutGroups.map { it.mainLayout.qualifiedClassName })
      .containsExactly("test.db.databinding.ActivityMainBinding")

    moduleCache.bindingLayoutGroups.forEach { group ->
      moduleCache.getLightBindingClasses(group).forEach { bindingClass ->
        assertThat(PsiSearchScopeUtil.isInScope(dumbScope, bindingClass)).isFalse()
        assertThat(PsiSearchScopeUtil.isInScope(enlargedScope, bindingClass)).isTrue()
      }
    }
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

  @Test
  fun scopeValuesUpdateAfterDumbMode() {
    val appActivityClass =
      fixture
        .addFileToProject("app/src/main/src/AppActivity.java", "public class AppActivity {}")
        .getFirstJavaClass()
    val libActivityClass =
      fixture
        .addFileToProject("lib/src/main/src/LibActivity.java", "public class LibActivity {}")
        .getFirstJavaClass()

    fixture.addFileToProject("app/src/main/res/layout/activity1_app.xml", EMPTY_LAYOUT_FILE)
    fixture.addFileToProject("lib/src/main/res/layout/activity1_lib.xml", EMPTY_LAYOUT_FILE)

    var appActivity1LightClass =
      appFacet.getLightBindingClass("com.example.app.databinding.Activity1AppBinding")
    var libActivity1LightClass =
      libFacet.getLightBindingClass("com.example.lib.databinding.Activity1LibBinding")
    assertThat(PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, appActivity1LightClass))
      .isTrue()
    assertThat(PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, appActivity1LightClass))
      .isFalse()
    assertThat(PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, libActivity1LightClass))
      .isTrue()
    assertThat(PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, libActivity1LightClass))
      .isTrue()

    // In dumb mode, add a resource and then request the current scope. The enlarger will return a
    // stale value while in dumb mode, but should return an updated value after dumb mode completes.
    DumbModeTestUtils.computeInDumbModeSynchronously(project) {
      fixture.addFileToProject("app/src/main/res/layout/activity2_app.xml", EMPTY_LAYOUT_FILE)
      fixture.addFileToProject("lib/src/main/res/layout/activity2_lib.xml", EMPTY_LAYOUT_FILE)

      assertThat(
          PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, appActivity1LightClass)
        )
        .isTrue()
      assertThat(
          PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, appActivity1LightClass)
        )
        .isFalse()
      assertThat(
          PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, libActivity1LightClass)
        )
        .isTrue()
      assertThat(
          PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, libActivity1LightClass)
        )
        .isTrue()
    }

    // Light classes will be regenerated now, so need to refetch the first two.
    appActivity1LightClass =
      appFacet.getLightBindingClass("com.example.app.databinding.Activity1AppBinding")
    libActivity1LightClass =
      libFacet.getLightBindingClass("com.example.lib.databinding.Activity1LibBinding")
    val appActivity2LightClass =
      appFacet.getLightBindingClass("com.example.app.databinding.Activity2AppBinding")
    val libActivity2LightClass =
      libFacet.getLightBindingClass("com.example.lib.databinding.Activity2LibBinding")

    assertThat(PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, appActivity1LightClass))
      .isTrue()
    assertThat(PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, appActivity1LightClass))
      .isFalse()
    assertThat(PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, libActivity1LightClass))
      .isTrue()
    assertThat(PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, libActivity1LightClass))
      .isTrue()

    assertThat(PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, appActivity2LightClass))
      .isTrue()
    assertThat(PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, appActivity2LightClass))
      .isFalse()
    assertThat(PsiSearchScopeUtil.isInScope(appActivityClass.resolveScope, libActivity2LightClass))
      .isTrue()
    assertThat(PsiSearchScopeUtil.isInScope(libActivityClass.resolveScope, libActivity2LightClass))
      .isTrue()
  }

  private fun AndroidFacet.getLightBindingClasses() = runReadAction {
    LayoutBindingModuleCache.getInstance(this).let { cache ->
      cache.bindingLayoutGroups.flatMap { cache.getLightBindingClasses(it) }
    }
  }

  private fun AndroidFacet.getLightBindingClass(qualifiedName: String) =
    getLightBindingClasses().single { it.qualifiedName == qualifiedName }

  private fun PsiFile.getFirstJavaClass() = runReadAction { (this as PsiJavaFile).classes.first() }
}
