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
package com.android.tools.idea.nav.safeargs.module

import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.extensions.replaceWithSaving
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.nav.safeargs.psi.java.LightArgsClass
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DumbModeTest {
  @get:Rule
  val safeArgsRule = SafeArgsRule()

  @Before
  fun setUp() {
    NavigationResourcesModificationListener.ensureSubscribed(safeArgsRule.project)
  }

  @Test
  fun indexWhenDumbMode() {
    val project = safeArgsRule.project
    val xmlContent =
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/fragment1">

          <fragment
              android:id="@+id/fragment"
              android:name="test.safeargs.Fragment"
              android:label="Fragment1">

              <argument
                  android:name="arg1"
                  app:argType="string" />
          </fragment>
        </navigation>
      """.trimIndent()
    val navFile = safeArgsRule.fixture.addFileToProject("res/navigation/main.xml", xmlContent)
    safeArgsRule.waitForResourceRepositoryUpdates()
    val moduleCache = SafeArgsCacheModuleService.getInstance(safeArgsRule.androidFacet)
    // 1 NavArgumentData
    assertThat(getNumberOfArgs(moduleCache.args)).isEqualTo(1)

    // enter dumb mode and update this newly added nav file by adding another argument
    DumbServiceImpl.getInstance(project).isDumb = true
    val replaceXmlContent =
      """
          <argument
              android:name="arg2"
              app:argType="integer" />
        </fragment>
      """.trimIndent()
    WriteCommandAction.runWriteCommandAction(project) {
      navFile.virtualFile.replaceWithSaving("</fragment>", replaceXmlContent, project)
    }
    // still 1 NavArgumentData due to dumb mode --previously cached results are returned
    assertThat(getNumberOfArgs(moduleCache.args)).isEqualTo(1)

    DumbServiceImpl.getInstance(project).isDumb = false
    // fresh results are generated since smart mode
    assertThat(getNumberOfArgs(moduleCache.args)).isEqualTo(2)
  }

  private fun getNumberOfArgs(args: List<LightArgsClass>) = args.sumOf { it.destination.arguments.size }

  @Test
  fun scopeDoesNotCacheStaleValuesInDumbMode() {
    val dumbService = DumbServiceImpl.getInstance(safeArgsRule.project)
    assertThat(dumbService.isDumb).isFalse()

    // In dumb mode, add a resource and then request the current scope. In the past, this would cause
    // the scope enlarger to internally cache stale values (because the service that the enlarger
    // queries into aborts early in dumb mode).
    dumbService.isDumb = true
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/nav_main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/main"
            app:startDestination="@id/main_fragment" >

          <fragment
              android:id="@+id/main_fragment"
              android:name="test.safeargs.MainFragment"
              android:label="MainFragment">
              
              <action
                android:id="@+id/action_main_fragment_to_main"
                app:destination="@id/main" />
          </fragment>
        </navigation>
      """.trimIndent())
    safeArgsRule.waitForResourceRepositoryUpdates()
    val fragmentClass = safeArgsRule.fixture.addClass("public class MainFragment {}")

    val dumbScope = fragmentClass.resolveScope

    // Exit dumb mode and request our final enlarged scope. It should pick up the changes that
    // occurred while we were previously in dumb mode.
    dumbService.isDumb = false
    val enlargedScope = fragmentClass.resolveScope

    val moduleCache = SafeArgsCacheModuleService.getInstance(safeArgsRule.androidFacet)
    assertThat(moduleCache.directions.map { it.name }).containsExactly("MainFragmentDirections")

    moduleCache.directions.forEach { directionsClass ->
      assertThat(PsiSearchScopeUtil.isInScope(dumbScope, directionsClass)).isFalse()
      assertThat(PsiSearchScopeUtil.isInScope(enlargedScope, directionsClass)).isTrue()
    }
  }
}