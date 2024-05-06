/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.rendering.webp

import com.android.testutils.waitForCondition
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.AndroidTestCase
import java.util.concurrent.TimeUnit

class ConvertToWebpActionTest : AndroidTestCase() {
  val notifications = mutableListOf<Notification>()

  override fun setUp() {
    super.setUp()
    project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        notifications.add(notification)
      }
    })
  }

  fun testConvert() {
    // Regression test for issue 226893
    // Ensure that images that are too large to encode are encoded anyway if the user asked for it
    val settings = WebpConversionSettings()
    settings.previewConversion = false
    settings.skipTransparentImages = false
    settings.skipLargerImages = true
    settings.quality = 90
    val mdpi = myFixture.copyFileToProject("webp/ic_action_name-mdpi.png", "res/drawable-mdpi/ic_action_name.png")
    val xhdpi = myFixture.copyFileToProject("webp/ic_action_name-xhdpi.png", "res/drawable-xhdpi/ic_action_name.png")
    val mdpiFolder = mdpi.parent
    val xhdpiFolder = xhdpi.parent
    val action = ConvertToWebpAction()
    action.convert(project, settings, true, listOf(mdpi, xhdpi))

    waitForCondition(2, TimeUnit.SECONDS) { notifications.isNotEmpty() }
    assertThat(notifications).hasSize(1)
    assertThat(notifications[0].content).isEqualTo(
      "1 file was converted<br/>55 bytes saved<br>1 file was skipped because there was no net space saving")
    // Check that we only converted the xhdpi image (the mdpi image encodes to a larger image)
    assertThat(xhdpiFolder.findChild("ic_action_name.png")).isNull()
    assertThat(xhdpiFolder.findChild("ic_action_name.webp")).isNotNull()
    assertThat(mdpiFolder.findChild("ic_action_name.png")).isNotNull()
    assertThat(mdpiFolder.findChild("ic_action_name.webp")).isNull()
  }

  fun testIncludeLargerImages() {
    // Regression test for issue 226893
    // Ensure that images that are too large to encode are encoded anyway if the user asked for it
    val settings = WebpConversionSettings()
    settings.previewConversion = false
    settings.skipTransparentImages = false
    settings.skipLargerImages = false
    settings.quality = 100
    val mdpi = myFixture.copyFileToProject("webp/ic_action_name-mdpi.png", "res/drawable-mdpi/ic_action_name.png")
    // test conversion of a transparent gray issue
    val gray = myFixture.copyFileToProject("webp/ic_arrow_back.png", "res/drawable-mdpi/ic_arrow_back.png")
    val xhdpi = myFixture.copyFileToProject("webp/ic_action_name-xhdpi.png", "res/drawable-xhdpi/ic_action_name.png")
    val mdpiFolder = mdpi.parent
    val xhdpiFolder = xhdpi.parent
    val action = ConvertToWebpAction()
    action.convert(project, settings, true, listOf(mdpi, xhdpi, gray))

    waitForCondition(2, TimeUnit.SECONDS) { notifications.isNotEmpty() }
    assertThat(notifications).hasSize(1)
    assertThat(notifications[0].content).isEqualTo("3 files were converted<br/>size increased by 139 bytes")
    // Check that we converted both images
    assertThat(xhdpiFolder.findChild("ic_action_name.png")).isNull()
    assertThat(xhdpiFolder.findChild("ic_action_name.webp")).isNotNull()
    assertThat(mdpiFolder.findChild("ic_action_name.png")).isNull()
    assertThat(mdpiFolder.findChild("ic_action_name.webp")).isNotNull()
    assertThat(mdpiFolder.findChild("ic_arrow_back.png")).isNull()
    assertThat(mdpiFolder.findChild("ic_arrow_back.webp")).isNotNull()
  }

  fun testVisibility() {
    val action = ConvertToWebpAction()
    val resFolder = myFixture.findFileInTempDir("res")
    val pngFile = myFixture.addFileToProject("folder/image.png", "").virtualFile
    val assetFolder = myFixture.copyDirectoryToProject("webp", "assets")
    val nonResFolder = myFixture.copyDirectoryToProject("webp", "folder")
    val dataContext = object : DataContext {
      var files: Array<VirtualFile> = arrayOf()

      override fun getData(dataId: String): Any? {
        return null
      }

      override fun <T> getData(key: DataKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return when (key) {
          CommonDataKeys.VIRTUAL_FILE_ARRAY -> files as T
          CommonDataKeys.PROJECT -> myFixture.project as T
          else -> null
        }
      }
    }
    val presentation = Presentation()
    val event = AnActionEvent.createFromDataContext("", presentation, dataContext)

    dataContext.files = arrayOf(resFolder)
    action.update(event)
    assertTrue(presentation.isVisible)

    dataContext.files = arrayOf(pngFile)
    action.update(event)
    assertTrue(presentation.isVisible)

    dataContext.files = arrayOf(assetFolder)
    action.update(event)
    assertTrue(presentation.isVisible)

    dataContext.files = arrayOf(nonResFolder)
    action.update(event)
    assertFalse(presentation.isVisible)
  }
}