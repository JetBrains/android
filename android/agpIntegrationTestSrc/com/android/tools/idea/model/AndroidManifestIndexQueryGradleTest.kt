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
package com.android.tools.idea.model

import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.android.facet.AndroidRootUtil
import java.util.concurrent.TimeUnit

class AndroidManifestIndexQueryGradleTest: AndroidGradleTestCase() {

  private lateinit var modificationListener: MergedManifestModificationListener

  override fun setUp() {
    super.setUp()
    MergedManifestModificationListener.ensureSubscribed(project)
    modificationListener = MergedManifestModificationListener(project)
  }

  fun testQueryActivitiesNoPackageInManifest() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)

    assertThat(myAndroidFacet.getModuleSystem().getPackageName()).isEqualTo("google.simpleapplication")

    val manifestContent =
      // language=xml
      """
    <?xml version='1.0' encoding='utf-8'?>
    <manifest xmlns:android='http://schemas.android.com/apk/res/android'>
      <application android:theme='@style/Theme.AppCompact'>
        <activity android:name=".MainActivityWithPackageFromGradle" android:enabled='true' android:exported='true'/>
      </application>
    </manifest>
    """.trimIndent()

    // update manifest
    runWriteActionAndWait {
      AndroidRootUtil.getPrimaryManifestFile(myAndroidFacet)!!.delete(this)
      VfsTestUtil.createFile(project.guessProjectDir()!!, "app/src/main/AndroidManifest.xml", manifestContent)
    }
    modificationListener.waitAllUpdatesCompletedWithTimeout(1, TimeUnit.SECONDS)

    val activities = myAndroidFacet.queryActivitiesFromManifestIndex().getJoined()
    assertThat(activities).hasSize(1)
    val activity = activities[0]
    assertThat(activity.qualifiedName).isEqualTo("google.simpleapplication.MainActivityWithPackageFromGradle")
  }
}
