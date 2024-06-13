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

import com.android.tools.deployer.model.component.Complication.ComplicationType.LONG_TEXT
import com.android.tools.deployer.model.component.Complication.ComplicationType.RANGED_VALUE
import com.android.tools.deployer.model.component.Complication.ComplicationType.SHORT_TEXT
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase.assertEquals
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.onEdt
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertThrows
import junit.framework.TestCase
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import com.intellij.util.ui.UIUtil


class ComplicationTypeUtilsTest {
  @get:Rule
  val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.SIMPLE_APPLICATION).onEdt()

  private val manifestString = """
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

  @Test
  @RunsInEdt
  fun testExtractSupportedComplicationTypes() {
    val mergedManifest: MergedManifestSnapshot =
      getMergedManifest(String.format(manifestString, "RANGED_VALUE,, , INVALID, SHORT_TEXT, LONG_TEXT"))!!
    assertEquals(
      listOf(RANGED_VALUE.toString(), "", "", "INVALID", SHORT_TEXT.toString(), LONG_TEXT.toString()),
      extractSupportedComplicationTypes(mergedManifest,
                                        "google.simpleapplication.provider.IncrementingNumberComplicationProviderService")
    )
  }

  @Test
  fun testParseComplicationTypes() {
    val typesStr = listOf("RANGED_VALUE", "INVALID", "LONG_TEXT")
    assertEquals(
      listOf(RANGED_VALUE, LONG_TEXT),
      parseRawComplicationTypes(typesStr)
    )
  }

  @Test
  fun testParseComplicationTypesWarning() {
    val typesStr = listOf("RANGED_VALUE", "INVALID", "LONG_TEXT")
    assertThrows(RuntimeConfigurationWarning::class.java) {
      checkRawComplicationTypes(typesStr)
    }
  }

  @Test
  fun tesGetComplicationTypesFromManifestWhileHoldingReadLockReturnsNullIfNotReady() {
    val module = projectRule.projectRule.project.findAppModule()
    addMergedManifest(manifestString.format("RANGED_VALUE, LONG_TEXT, SHORT_TEXT"))
    UIUtil.pump()
    val readLockIsReady = CountDownLatch(1)
    val testIsComplete = CountDownLatch(1)

    try {
      // Ensure the read lock is blocked to the manifest merger does not have the chance to run
      thread {
        ApplicationManager.getApplication().runReadAction {
          readLockIsReady.countDown()
          testIsComplete.await()
        }
      }

      // Wait for the read lock to be ready
      readLockIsReady.await()
      val complicationTypes = ApplicationManager.getApplication().runReadAction(Computable<List<String>?> {
        getComplicationTypesFromManifest(
          module,
          "google.simpleapplication.provider.IncrementingNumberComplicationProviderService"
        )
      })
      assertNull(complicationTypes)
    } finally {
      testIsComplete.countDown()
    }
  }

  @Test
  fun tesGetComplicationTypesFromManifest() {
    val module = projectRule.projectRule.project.findAppModule()
    addMergedManifest(manifestString.format("RANGED_VALUE, LONG_TEXT, SHORT_TEXT"))
    val complicationTypes = getComplicationTypesFromManifest(module,
      "google.simpleapplication.provider.IncrementingNumberComplicationProviderService")
    assertEquals(
      """
        RANGED_VALUE
        LONG_TEXT
        SHORT_TEXT
      """.trimIndent(),
      complicationTypes?.joinToString("\n") ?: ""
    )
  }

  private fun addMergedManifest(manifestContents: String) {
    val path = "app/src/main/AndroidManifest.xml"
    val manifest: VirtualFile = projectRule.fixture.findFileInTempDir(path)
    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction(object : Runnable {
        override fun run() {
          try {
            manifest.delete(this)
          } catch (e: IOException) {
            TestCase.fail("Could not delete manifest")
          }
        }
      })
    }
    projectRule.fixture.addFileToProject(path, manifestContents)
  }

  @Throws(Exception::class)
  private fun getMergedManifest(manifestContents: String): MergedManifestSnapshot? {
    addMergedManifest(manifestContents)

    return MergedManifestManager.getMergedManifest(projectRule.projectRule.project.findAppModule()).get()
  }
}