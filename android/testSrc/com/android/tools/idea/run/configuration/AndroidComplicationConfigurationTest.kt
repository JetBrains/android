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
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import java.io.IOException

class AndroidComplicationConfigurationTest : AndroidTestCase() {
  private val manifestString = """
        <manifest package="com.example.android.wearable.watchface"
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

  fun testExtractSupportedTypes() {
    val mergedManifest: MergedManifestSnapshot =
      getMergedManifest(String.format(manifestString, "RANGED_VALUE,, , INVALID, SHORT_TEXT, LONG_TEXT"))!!
    assertEquals(
      listOf(RANGED_VALUE.toString(), "", "", "INVALID", SHORT_TEXT.toString(), LONG_TEXT.toString()),
      extractComplicationSupportedTypes(mergedManifest,
                                        "com.example.android.wearable.watchface.provider.IncrementingNumberComplicationProviderService")
    )
  }

  fun testParseTypes() {
    val typesStr = listOf("RANGED_VALUE", "INVALID", "LONG_TEXT")
    assertEquals(
      listOf(RANGED_VALUE, LONG_TEXT),
      parseRawTypes(typesStr,)
    )
  }

  fun testParseTypesWarning() {
    val typesStr = listOf("RANGED_VALUE", "INVALID", "LONG_TEXT")
    assertThrows(RuntimeConfigurationWarning::class.java) {
      checkRawTypes(typesStr)
    }
  }

  @Throws(Exception::class)
  private fun getMergedManifest(manifestContents: String): MergedManifestSnapshot? {
    val path = "AndroidManifest.xml"
    val manifest: VirtualFile = myFixture.findFileInTempDir(path)
    ApplicationManager.getApplication().runWriteAction(object : Runnable {
      override fun run() {
        try {
          manifest.delete(this)
        }
        catch (e: IOException) {
          TestCase.fail("Could not delete manifest")
        }
      }
    })
    myFixture.addFileToProject(path, manifestContents)
    return MergedManifestManager.getMergedManifest(myModule).get()
  }
}