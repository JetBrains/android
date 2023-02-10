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
import com.android.tools.idea.databinding.TestDataPaths.PROJECT_WITH_SAME_PACKAGE_MODULES
import com.android.tools.idea.databinding.finders.BindingClassFinder
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * This class compiles a real project with view binding that has multiple library modules with the
 * same package as each other and the layouts with the same name. The project compiles because only
 * one of the two modules is added as a dependency. We want to make sure that the IDE handles this
 * scenario well, since the layout binding cache is project wide and was previously only keeping
 * one class per fqcn around.
 *
 * See also: https://issuetracker.google.com/159948398
 */
class ViewBindingSamePackageModulesTest {

  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val chainedRule = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Test
  @RunsInEdt
  fun multipleBindingClassesCanHaveSameFqcn() {
    projectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(PROJECT_WITH_SAME_PACKAGE_MODULES)

    val project = projectRule.project
    val appFacet = projectRule.androidFacet(":app")
    val fixture = projectRule.fixture as JavaCodeInsightTestFixture

    val syncState = GradleSyncState.getInstance(project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()
    assertThat(appFacet.isViewBindingEnabled()).isTrue()

    // Trigger initialization
    StudioResourceRepositoryManager.getModuleResources(appFacet)

    val bindingClassFinder = PsiElementFinder.EP.findExtension(BindingClassFinder::class.java, project)!!

    val context = fixture.findClass("com.example.samepackage.MainActivity")
    val libBindingClassName = "com.example.samepackage.lib.databinding.ActivityLibBinding"
    assertThat(bindingClassFinder.findClasses(libBindingClassName, GlobalSearchScope.everythingScope(project))).hasLength(2)
    fixture.findClass(libBindingClassName, context)

    // Before, this would return null, because lib2's entry used to overwrite lib1's entry
    assertThat(JavaPsiFacade.getInstance(project).findClass(libBindingClassName, context.resolveScope)).isNotNull()
  }
}
