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
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.RunsInEdt
import kotlin.test.assertFailsWith
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DumbModeTest {
  @get:Rule val safeArgsRule = SafeArgsRule()

  @Before
  fun setUp() {
    NavigationResourcesModificationListener.ensureSubscribed(safeArgsRule.project)
  }

  @Test
  fun indexWhenDumbMode() {
    val project = safeArgsRule.project
    val xmlContent =
      // language=XML
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
      """
        .trimIndent()
    val navFile = safeArgsRule.fixture.addFileToProject("res/navigation/main.xml", xmlContent)
    safeArgsRule.waitForResourceRepositoryUpdates()
    val moduleCache = SafeArgsCacheModuleService.getInstance(safeArgsRule.androidFacet)
    // 1 NavArgumentData
    assertThat(getNumberOfArgs(moduleCache.args)).isEqualTo(1)

    // enter dumb mode and update this newly added nav file by adding another argument
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      val replaceXmlContent =
        """
            <argument
                android:name="arg2"
                app:argType="integer" />
          </fragment>
        """
          .trimIndent()
      WriteCommandAction.runWriteCommandAction(project) {
        navFile.virtualFile.replaceWithSaving("</fragment>", replaceXmlContent, project)
      }
      assertFailsWith<AssertionError> {
        // In real code, this logs an error, but continues executing with potentially bad values. In
        // test scenarios, logging an error ends up failing the test instead; catch the error as
        // expected for this scenario. What we care about is that after dumb mode ends, the returned
        // value recovers and is correct.
        moduleCache.args
      }
    }

    // fresh results are generated since smart mode
    assertThat(getNumberOfArgs(moduleCache.args)).isEqualTo(2)
  }

  private fun getNumberOfArgs(args: List<LightArgsClass>) =
    args.sumOf { it.destination.arguments.size }
}
