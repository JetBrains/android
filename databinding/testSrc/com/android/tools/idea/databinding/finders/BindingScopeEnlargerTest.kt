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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.DumbService
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
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
    get() = projectRule.fixture as JavaCodeInsightTestFixture

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
        .trimIndent()
    )

    LayoutBindingModuleCache.getInstance(facet).dataBindingMode = DataBindingMode.ANDROIDX
  }

  @Test
  fun scopeDoesNotCacheStaleValuesInDumbMode() {
    assertThat(DumbService.isDumb(project)).isFalse()

    // In dumb mode, add a resource and then request the current scope. In the past, this would
    // cause
    // the scope enlarger to internally cache stale values (because the service that the enlarger
    // queries into aborts early in dumb mode).
    val (activityClass, dumbScope) =
      DumbModeTestUtils.computeInDumbModeSynchronously(project) {
        fixture.addFileToProject(
          "res/layout/activity_main.xml",
          // language=XML
          """
          <?xml version="1.0" encoding="utf-8"?>
          <layout xmlns:android="http://schemas.android.com/apk/res/android">
            <LinearLayout />
          </layout>
        """
            .trimIndent()
        )
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
