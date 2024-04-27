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
package com.android.tools.idea.nav.safeargs.cache.gradle

import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.extensions.getContents
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ShortNamesCacheTestSingleJavaModule {
  @get:Rule val safeArgsRule = SafeArgsRule()

  @Test
  fun getShortNamesCache() {
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
                  
              <action
                  android:id="@+id/action_Fragment_to_Main"
                  app:destination="@id/main" />                  
          </fragment>
        </navigation>
      """
        .trimIndent()
    safeArgsRule.fixture.addFileToProject("res/navigation/main.xml", xmlContent)
    val cache = PsiShortNamesCache.getInstance(project)

    // Check light arg classes
    assertThat(cache.getContents("FragmentArgs", project))
      .containsExactly("test.safeargs.FragmentArgs")

    // Check light direction classes
    assertThat(cache.getContents("FragmentDirections", project))
      .containsExactly("test.safeargs.FragmentDirections")

    // Check light builder classes
    assertThat(cache.getContents("Builder", project)).contains("test.safeargs.FragmentArgs.Builder")
  }
}
