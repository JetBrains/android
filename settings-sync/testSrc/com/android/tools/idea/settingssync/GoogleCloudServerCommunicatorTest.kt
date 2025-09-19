/*
 * Copyright (C) 2025 The Android Open Source Project
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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.settingssync

import com.intellij.settingsSync.core.SettingsSnapshot
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

class GoogleCloudServerCommunicatorTest {
  private val driveClient: GoogleDriveClient = mock()
  private val communicator = GoogleCloudServerCommunicator("test@example.com", driveClient)
  @get:Rule val disposableRule = DisposableRule()
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun testWriteFile() {
    val snapshot =
      SettingsSnapshot(
        SettingsSnapshot.MetaInfo(Instant.now(), null),
        setOf(),
        null,
        mapOf(),
        setOf(),
      )
    communicator.push(snapshot, true, "1234")
    verify(driveClient).write(eq("studio/settings.sync.snapshot.zip"), any())
  }

  @Test
  fun testDelete() {
    val snapshot =
      SettingsSnapshot(
        SettingsSnapshot.MetaInfo(Instant.now(), null, isDeleted = true),
        setOf(),
        null,
        mapOf(),
        setOf(),
      )
    communicator.push(snapshot, true, "1234")
    verify(driveClient).delete("studio/settings.sync.snapshot.zip")
  }
}
