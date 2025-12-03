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
package com.android.tools.idea.diagnostics

import com.google.common.truth.Truth
import java.util.UUID
import java.util.prefs.Preferences
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val NODE_NAME = "google"
private const val INSTALLATION_ID_KEY = "user_id_on_machine"

@RunWith(JUnit4::class)
class InstallationIdTest {

  private val mockUserRootPrefs = mock<Preferences>()
  private val mockNodePrefs = mock<Preferences>()

  @Test
  fun get_existingValidId_returnsExistingId() {
    val existingUuid = UUID.randomUUID().toString()
    whenever(mockUserRootPrefs.node(NODE_NAME)).thenReturn(mockNodePrefs)
    mockNodePrefs.stub {
      on { get(eq(INSTALLATION_ID_KEY), any()) } doReturn existingUuid
    }

    val result = InstallationId.get(mockUserRootPrefs)

    Truth.assertThat(result).isEqualTo(existingUuid)
    verify(mockUserRootPrefs).node(NODE_NAME)
    verify(mockNodePrefs).get(INSTALLATION_ID_KEY, "")
    verify(mockNodePrefs, never()).put(any(), any())
  }

  @Test
  fun get_noExistingId_generatesAndSavesNewId() {
    whenever(mockUserRootPrefs.node(NODE_NAME)).thenReturn(mockNodePrefs)
    mockNodePrefs.stub {
      on { get(eq(INSTALLATION_ID_KEY), any()) } doReturn ""
    }

    val result = InstallationId.get(mockUserRootPrefs)

    Truth.assertThat(result).isNotEmpty()
    Truth.assertThat(result.isValidUuid()).isTrue() // Check if the result is a valid UUID
    verify(mockUserRootPrefs).node(NODE_NAME)
    verify(mockNodePrefs).get(INSTALLATION_ID_KEY, "")
    verify(mockNodePrefs).put(eq(INSTALLATION_ID_KEY), argThat { isValidUuid() })
  }

  @Test
  fun get_invalidExistingId_generatesAndSavesNewId() {
    val invalidUuid = "not-a-valid-uuid"
    whenever(mockUserRootPrefs.node(NODE_NAME)).thenReturn(mockNodePrefs)
    mockNodePrefs.stub {
      on { get(eq(INSTALLATION_ID_KEY), any()) } doReturn invalidUuid
    }

    val result = InstallationId.get(mockUserRootPrefs)

    Truth.assertThat(result).isNotEmpty()
    Truth.assertThat(result).isNotEqualTo(invalidUuid)
    Truth.assertThat(result.isValidUuid()).isTrue() // Check if the result is a valid UUID
    verify(mockUserRootPrefs).node(NODE_NAME)
    verify(mockNodePrefs).get(INSTALLATION_ID_KEY, "")
    verify(mockNodePrefs).put(eq(INSTALLATION_ID_KEY), argThat { isValidUuid() })
  }

  private fun String.isValidUuid(): Boolean {
    return try {
      UUID.fromString(this)
      true
    }
    catch (_: IllegalArgumentException) {
      false
    }
  }
}