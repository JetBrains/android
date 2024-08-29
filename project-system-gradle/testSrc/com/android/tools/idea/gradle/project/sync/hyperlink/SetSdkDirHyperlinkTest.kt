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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.TestProjectPaths.COMPOSITE_BUILD
import com.android.utils.SdkUtils.escapePropertyValue
import com.intellij.openapi.util.io.FileUtil.loadFile
import java.io.File
import javax.swing.event.HyperlinkEvent

class SetSdkDirHyperlinkTest : AndroidGradleTestCase() {
  fun testSdkDirHyperlinkUpdatesOnePropertiesFile() {
    IdeComponents(myFixture).replaceApplicationService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())
    prepareProjectForImport(COMPOSITE_BUILD)

    // Delete the main local.properties file
    val localPropertiesPath = File(projectFolderPath, FN_LOCAL_PROPERTIES)
    deletePropertiesFile(localPropertiesPath)

    val hyperlink = SetSdkDirHyperlink(project, listOf(localPropertiesPath.absolutePath))
    assertTrue(hyperlink.executeIfClicked(project, HyperlinkEvent(this, null, null, hyperlink.url)))
    assertTrue(localPropertiesPath.exists())
    assertTrue("Local properties must contain sdk.dir", loadFile(localPropertiesPath)
      .contains("sdk.dir=${escapePropertyValue(AndroidSdks.getInstance().tryToChooseAndroidSdk()!!.location.toString())}"))
  }

  fun testSdkDirHyperlinkUpdatesMultiplePropertiesFiles() {
    IdeComponents(myFixture).replaceApplicationService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())
    prepareProjectForImport(COMPOSITE_BUILD)

    // Delete all the properties files we want to re-create
    val localPropertiesPath = File(projectFolderPath, FN_LOCAL_PROPERTIES)
    val localPropertiesPathTwo = File(projectFolderPath.parent, "TestCompositeLib1/$FN_LOCAL_PROPERTIES")
    val localPropertiesPathThree = File(projectFolderPath.parent, "TestCompositeLib3/$FN_LOCAL_PROPERTIES")
    deletePropertiesFile(localPropertiesPath)
    deletePropertiesFile(localPropertiesPathTwo)
    deletePropertiesFile(localPropertiesPathThree)

    val hyperlink = SetSdkDirHyperlink(project,
      listOf(localPropertiesPath.absolutePath, localPropertiesPathTwo.absolutePath, localPropertiesPathThree.absolutePath))
    assertTrue(hyperlink.executeIfClicked(project, HyperlinkEvent(this, null, null, hyperlink.url)))

    val sdkLocation = escapePropertyValue(AndroidSdks.getInstance().tryToChooseAndroidSdk()!!.location.toString())
    assertTrue(localPropertiesPath.exists())
    assertTrue("Local properties must contain sdk.dir", loadFile(localPropertiesPath)
      .contains("sdk.dir=${sdkLocation}"))
    assertTrue(localPropertiesPathTwo.exists())
    assertTrue("Local properties must contain sdk.dir", loadFile(localPropertiesPathTwo)
      .contains("sdk.dir=${sdkLocation}"))
    assertTrue(localPropertiesPathThree.exists())
    assertTrue("Local properties must contain sdk.dir", loadFile(localPropertiesPathThree)
      .contains("sdk.dir=${sdkLocation}"))
  }

  private fun deletePropertiesFile(localPropertiesPath: File) {
    if (localPropertiesPath.exists()) {
      assertTrue(localPropertiesPath.delete())
    }
  }
}