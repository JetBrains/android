/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.model

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.run.activity.IndexedActivityWrapper
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import java.io.IOException

class AndroidManifestIndexQueryUtilsTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    MergedManifestModificationListener.ensureSubscribed(project)
  }

  fun testQueryMinSdkAndTargetSdk() {
    val manifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:debuggable="false" android:enabled='true'>
      <uses-sdk android:minSdkVersion='Lollipop' android:targetSdkVersion='28'/>
    </manifest>
    """.trimIndent()

    updateManifestContents(manifestContent)
    // not sure why it's 21 -1
    Truth.assertThat(myFacet.queryMinSdkAndTargetSdkFromManifestIndex().minSdk.apiLevel).isEqualTo(20)
    Truth.assertThat(myFacet.queryMinSdkAndTargetSdkFromManifestIndex().targetSdk.apiLevel).isEqualTo(28)
  }

  fun testQueryActivities() {
    val manifestContent = """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android' 
      package='com.example' android:debuggable="false" android:enabled='true'>
      <application android:theme='@style/Theme.AppCompat'>
        <activity android:name='.EnabledActivity' android:enabled='true'>
          <intent-filter>
            <action android:name='android.intent.action.MAIN'/>
            <category android:name='android.intent.category.DEFAULT'/>
          </intent-filter>
        </activity>
        <activity android:name='.DisabledActivity' android:enabled='false'>
        </activity>
        <activity-alias android:name='.EnabledAlias' android:enabled='true' android:targetActivity='.DisabledActivity'>
        </activity-alias>
        <activity-alias android:name='.DisabledAlias' android:enabled='false' android:targetActivity='.EnabledActivity'>
        </activity-alias>
      </application>
    </manifest>
    """.trimIndent()

    updateManifestContents(manifestContent)

    val activities = myFacet.queryActivitiesFromManifestIndex()

    val mainIntentFilter = IntentFilterRawText(
      actionNames = setOf("android.intent.action.MAIN"),
      categoryNames = setOf("android.intent.category.DEFAULT")
    )

    val overrides = ManifestOverrides(
      directOverrides = emptyMap(),
      placeholders = emptyMap()
    )

    Truth.assertThat(activities).containsExactly(
      IndexedActivityWrapper(
        name = ".EnabledActivity",
        enabled = "true",
        intentFilters = setOf(mainIntentFilter),
        overrides = overrides,
        resolvedPackage = "com.example"
      ),
      IndexedActivityWrapper(
        name = ".DisabledActivity",
        enabled = "false",
        intentFilters = emptySet(),
        overrides = overrides,
        resolvedPackage = "com.example"
      ),
      IndexedActivityWrapper(
        name = ".EnabledAlias",
        enabled = "true",
        intentFilters = emptySet(),
        overrides = overrides,
        resolvedPackage = "com.example"
      ),
      IndexedActivityWrapper(
        name = ".DisabledAlias",
        enabled = "false",
        intentFilters = emptySet(),
        overrides = overrides,
        resolvedPackage = "com.example"
      )
    )
  }

  private fun updateManifestContents(manifestContents: String) {
    val manifest = myFixture.findFileInTempDir(FN_ANDROID_MANIFEST_XML)
    ApplicationManager.getApplication().runWriteAction {
      if (manifest != null) {
        try {
          manifest.delete(this)
        }
        catch (e: IOException) {
          TestCase.fail("Could not delete manifest")
        }
      }
    }

    myFixture.addFileToProject(FN_ANDROID_MANIFEST_XML, manifestContents)
  }
}
