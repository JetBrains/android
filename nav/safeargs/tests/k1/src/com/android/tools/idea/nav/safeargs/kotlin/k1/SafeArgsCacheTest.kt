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
package com.android.tools.idea.nav.safeargs.kotlin.k1

import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.module.SafeArgsCacheModuleService
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SafeArgsCacheTest {
  @get:Rule val safeArgsRule = SafeArgsRule(SafeArgsMode.NONE)

  @Test
  fun cachesAreClearedWhenPluginModeChanges() {
    safeArgsRule.fixture.addFileToProject(
      "res/navigation/nav_main.xml",
      // language=XML
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
      """
        .trimIndent(),
    )

    val javaCache = SafeArgsCacheModuleService.getInstance(safeArgsRule.androidFacet)
    val ktCache = KtDescriptorCacheModuleService.getInstance(safeArgsRule.module)
    val moduleDescriptor = safeArgsRule.module.toDescriptor()!!

    assertThat(safeArgsRule.androidFacet.safeArgsMode).isEqualTo(SafeArgsMode.NONE)
    assertThat(javaCache.directions).isEmpty()
    assertThat(ktCache.getDescriptors(moduleDescriptor)).isEmpty()

    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.JAVA
    assertThat(javaCache.directions).isNotEmpty()
    assertThat(ktCache.getDescriptors(moduleDescriptor)).isEmpty()

    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.KOTLIN
    assertThat(javaCache.directions).isEmpty()
    assertThat(ktCache.getDescriptors(moduleDescriptor)).isNotEmpty()

    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.NONE
    assertThat(javaCache.directions).isEmpty()
    assertThat(ktCache.getDescriptors(moduleDescriptor)).isEmpty()
  }
}
