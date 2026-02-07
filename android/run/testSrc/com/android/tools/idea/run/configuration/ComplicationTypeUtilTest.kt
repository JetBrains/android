/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.testutils.AssumeUtil
import com.android.tools.deployer.model.component.Complication.ComplicationType.LONG_TEXT
import com.android.tools.deployer.model.component.Complication.ComplicationType.RANGED_VALUE
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase.assertEquals
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findAppModule
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComplicationTypeUtilsTest {
  @get:Rule val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

  private val manifestString =
    """
        <manifest package="google.simpleapplication"
          xmlns:android="http://schemas.android.com/apk/res/android">
          <application
          android:allowBackup="true"
          android:icon="@drawable/ic_launcher"
          android:label="@string/app_name">
            <service
                android:name=".provider.IncrementingNumberComplicationProviderService"
                android:icon="@drawable/icn_complications"
                android:label="@string/complications_provider_incrementing_number"
                android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">
                <intent-filter>
                    <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/>
                </intent-filter>
                <meta-data
                    android:name="android.support.wearable.complications.SUPPORTED_TYPES"
                    android:value="%s"/>
                <meta-data
                    android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
                    android:value="0"/>
            </service>
          </application>
        </manifest>
"""

  @Before
  fun assumeNotWindows() {
    AssumeUtil.assumeNotWindows() // TODO(b/418084011): fix on windows
  }

  @Test
  fun testParseComplicationTypes() {
    val typesStr = listOf("RANGED_VALUE", "INVALID", "LONG_TEXT")
    assertEquals(listOf(RANGED_VALUE, LONG_TEXT), parseRawComplicationTypes(typesStr))
  }

  @Test
  fun testParseComplicationTypesWarning() {
    val typesStr = listOf("RANGED_VALUE", "INVALID", "LONG_TEXT")
    assertThrows(RuntimeConfigurationWarning::class.java) { checkRawComplicationTypes(typesStr) }
  }

  @Test
  fun testGetComplicationTypesFromManifest() {
    val module = projectRule.project.findAppModule()
    addManifest(manifestString.format("RANGED_VALUE, LONG_TEXT, SHORT_TEXT"))
    val complicationTypes =
      getComplicationTypesFromManifest(module, "google.simpleapplication.provider.IncrementingNumberComplicationProviderService")
    assertEquals(
      """
      RANGED_VALUE
      LONG_TEXT
      SHORT_TEXT
      """
        .trimIndent(),
      complicationTypes?.joinToString("\n") ?: "",
    )
  }

  private fun addManifest(manifestContents: String) {
    val path = "app/src/main/AndroidManifest.xml"
    projectRule.fixture.addFileToProject(path, manifestContents)
  }
}
