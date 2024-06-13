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
package com.android.tools.idea.configurations

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.DumbModeTestUtils
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.SwingUtilities

@Language("XML")
private val appManifest = """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="google.simpleapplication">
        <application android:allowBackup="true"
            android:label="@string/app_name"
            android:theme="@style/AppTheme"
            android:supportsRtl="true">
            <activity
                android:name=".MyActivity"
                android:theme="@style/AppTheme">
                <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                </intent-filter>
            </activity>
        </application>
    </manifest>
  """.trimIndent()

class ThemeUtilsIndexQueryTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val facet: AndroidFacet
    get() = projectRule.module.androidFacet!!

  @Before
  fun setUp() {
    projectRule.fixture.addFileToProject("AndroidManifest.xml", appManifest)
  }

  @Test
  fun testQueryFromIndex() {
    Truth.assertThat(facet.module.getAppThemeName()).isEqualTo("@style/AppTheme")
    Truth.assertThat(facet.module.getAllActivityThemeNames()).containsExactly("@style/AppTheme")
    Truth.assertThat(facet.module.getThemeNameForActivity("google.simpleapplication.MyActivity")).isEqualTo(
      "@style/AppTheme")
  }

  /**
   * Regression test for b/322507246.
   *
   * [getAllActivityThemeNames] should not deadlock if called in the UI thread without the read lock.
   */
  @Test
  fun testQueryDoesNotDeadlock() {
    DumbModeTestUtils.runInDumbModeSynchronously(projectRule.project) {
      ApplicationManager.getApplication().invokeAndWait {
        Truth.assertThat(facet.module.getAllActivityThemeNames()).containsExactly("@style/AppTheme")
      }
    }

    // Run in non-smart mode in the UI thread. These conditions will cause a lock if the methods block to
    // wait for the smart mode.
    DumbModeTestUtils.runInDumbModeSynchronously(projectRule.project) {
      SwingUtilities.invokeAndWait {
        @Suppress("UnstableApiUsage")
        (ApplicationManager.getApplication() as ApplicationEx).releaseWriteIntentLock()
        println(ApplicationManager.getApplication().isReadAccessAllowed)
        DumbService.getInstance(projectRule.project).isDumb
        Truth.assertThat(facet.module.getAllActivityThemeNames()).containsExactly("@style/AppTheme")
      }
    }
  }
}