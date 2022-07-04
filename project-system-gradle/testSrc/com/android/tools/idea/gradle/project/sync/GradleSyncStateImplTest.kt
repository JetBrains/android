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
package com.android.tools.idea.gradle.project.sync

import com.android.testutils.MockitoKt.mockStatic
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder.Companion.MESSAGE_MULTIPLE_GRADLE_DAEMONS
import com.android.tools.idea.gradle.project.sync.hyperlink.DoNotShowJdkHomeWarningAgainHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.android.tools.idea.gradle.ui.SdkUiStrings.JDK_LOCATION_WARNING_URL
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.withSettings
import org.mockito.Mockito.CALLS_REAL_METHODS

class GradleSyncStateImplTest : AndroidGradleTestCase() {

  private lateinit var mockIdeSdks: MockedStatic<IdeSdks>

  private val notifications = mutableListOf<Notification>()

  override fun setUp() {
    super.setUp()
    mockIdeSdks = mockStatic(withSettings().defaultAnswer(CALLS_REAL_METHODS))
    project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        notifications.add(notification)
      }
    })
  }

  override fun tearDown() {
    try {
      mockIdeSdks.close()
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun `test Given undefined jdkFromJavaHomePath When gradle synchronized Then MultipleGradleDaemons warning is displayed`() {
    val jdkFromJavaHomePath: String? = null
    mockIdeSdks.whenever<String> { IdeSdks.getJdkFromJavaHome() }.thenReturn(jdkFromJavaHomePath)

    loadSimpleApplication()

    notifications
      .filter { GradleSyncState.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
      .run {
        assertThat(this).hasSize(1)
        assertEquals(createWarningMessageMultipleGradleDaemons(jdkFromJavaHomePath), first().content)
      }
  }

  @Test
  fun `test Given different jdkFromJavaHomePath and jdkPath When gradle synchronized Then MultipleGradleDaemons warning is displayed`() {
    val jdkFromJavaHomePath = "/test/jdk/path"
    mockIdeSdks.whenever<String> { IdeSdks.getJdkFromJavaHome() }.thenReturn(jdkFromJavaHomePath)

    loadSimpleApplication()

    notifications
      .filter { GradleSyncState.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
      .run {
        assertThat(this).hasSize(1)
        assertEquals(createWarningMessageMultipleGradleDaemons(jdkFromJavaHomePath), first().content)
      }
  }

  @Test
  fun `test Given same jdkFromJavaHomePath and jdkPath When gradle synchronized Then MultipleGradleDaemons warning isn't displayed`() {
    val jdkFromJavaHomePath = GradleInstallationManager.getInstance().getGradleJvmPath(project, project.basePath.orEmpty())
    mockIdeSdks.whenever<String> { IdeSdks.getJdkFromJavaHome() }.thenReturn(jdkFromJavaHomePath)

    loadSimpleApplication()

    notifications
      .filter { GradleSyncState.JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId == it.groupId }
      .run { assertTrue(isEmpty()) }
  }

  private fun createWarningMessageMultipleGradleDaemons(
    jdkFromJavaHomePath: String?
  ) = StringBuilder().apply {
    appendLine(
      String.format(MESSAGE_MULTIPLE_GRADLE_DAEMONS,
                    project.name,
                    GradleInstallationManager.getInstance().getGradleJvmPath(project, project.basePath.orEmpty()),
                    jdkFromJavaHomePath ?: "Undefined"
      ).trimIndent()
    )
    appendLine(OpenUrlHyperlink(JDK_LOCATION_WARNING_URL, "More info...").toHtml())
    appendLine(DoNotShowJdkHomeWarningAgainHyperlink().toHtml())
  }.toString().trim()
}
