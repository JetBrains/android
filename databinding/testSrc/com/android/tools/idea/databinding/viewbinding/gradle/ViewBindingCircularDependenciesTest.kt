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
package com.android.tools.idea.databinding.viewbinding.gradle

import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_CIRCULAR_DEPENDENCIES
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * This class compiles a real project with data binding with valid circular dependencies and makes
 * sure that valid bindings are generated for the layout files.
 *
 * See also: b/141255511
 */
class ViewBindingCircularDependenciesTest {

  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val chainedRule = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Test
  @RunsInEdt
  fun canFindBindingsCorrectlyDespiteCircularDependencies() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(PROJECT_WITH_CIRCULAR_DEPENDENCIES)

    val project = projectRule.project
    val facet = projectRule.androidFacet(":app")
    val fixture = projectRule.fixture as JavaCodeInsightTestFixture

    val syncState = GradleSyncState.getInstance(project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()
    assertThat(facet.isViewBindingEnabled()).isTrue()

    // app depends on module1 and module2
    // module1 depends on module2's test sources only
    // module2 depends on module1
    assertThat(projectRule.hasModule("app")).isTrue()
    assertThat(projectRule.hasModule("module1")).isTrue()
    assertThat(projectRule.hasModule("module2")).isTrue()

    val appScope = fixture.findClass("com.example.circulardependencies.MainActivity").resolveScope
    val module1Scope = fixture.findClass("com.example.module1.MainActivity").resolveScope
    val module2Scope = fixture.findClass("com.example.module2.MainActivity").resolveScope

    val appBindingClassName = "com.example.circulardependencies.databinding.ActivityMainBinding"
    val module1BindingClassName = "com.example.module1.databinding.ActivityMainBinding"
    val module2BindingClassName = "com.example.module2.databinding.ActivityMainBinding"

    // Trigger initialization
    StudioResourceRepositoryManager.getModuleResources(facet);
    val javaPsiFacade = JavaPsiFacade.getInstance(project);

    // Everything viewable from app module
    assertThat(javaPsiFacade.findClass(appBindingClassName, appScope)).isNotNull()
    assertThat(javaPsiFacade.findClass(module1BindingClassName, appScope)).isNotNull()
    assertThat(javaPsiFacade.findClass(module2BindingClassName, appScope)).isNotNull()

    // module1 can only see itself (since databinding classes are not part of a module2's test sources)
    assertThat(javaPsiFacade.findClass(appBindingClassName, module1Scope)).isNull()
    assertThat(javaPsiFacade.findClass(module1BindingClassName, module1Scope)).isNotNull()
    assertThat(javaPsiFacade.findClass(module2BindingClassName, module1Scope)).isNull()

    // module2 can see module1
    assertThat(javaPsiFacade.findClass(appBindingClassName, module2Scope)).isNull()
    assertThat(javaPsiFacade.findClass(module1BindingClassName, module2Scope)).isNotNull()
    assertThat(javaPsiFacade.findClass(module2BindingClassName, module2Scope)).isNotNull()
  }
}
