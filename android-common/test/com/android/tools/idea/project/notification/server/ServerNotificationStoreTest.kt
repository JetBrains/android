/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.project.notification.server

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ServerNotificationStoreTest {

  private val store = ServerNotificationStore()

  @Test
  fun testRecordFeatureUsage_andStatePreservation() {
    val featureId = 1
    // Initially, there should be no usage recorded
    assertThat(store.getLastUsedTimestamp(featureId)).isNull()

    // Record usage
    store.recordFeatureUsage(featureId)

    // Verify usage is now recorded
    val timestamp = store.getLastUsedTimestamp(featureId)
    assertThat(timestamp).isNotNull()

    // Extract the state
    val state = store.state

    // Create a brand new store and load the extracted state into it
    val newStore = ServerNotificationStore()
    newStore.loadState(state)

    // Verify the new store has the same timestamp for the feature
    val newTimestamp = newStore.getLastUsedTimestamp(featureId)
    assertThat(newTimestamp).isEqualTo(timestamp)
  }

  @Test
  fun testRecordServerPushNotification_andStatePreservation() {
    // Initially, there should be no usage recorded
    assertThat(store.getLastServerPushNotificationTimestamp()).isNull()
    assertThat(store.isNotificationShown("test_id")).isFalse()

    // Record server push notification
    store.markNotificationAsShown("test_id")

    // Verify it is now recorded
    val timestamp = store.getLastServerPushNotificationTimestamp()
    assertThat(timestamp).isNotNull()
    assertThat(store.isNotificationShown("test_id")).isTrue()

    // Extract the state
    val state = store.state

    // Create a brand new store and load the extracted state into it
    val newStore = ServerNotificationStore()
    newStore.loadState(state)

    // Verify the new store has the same timestamp
    val newTimestamp = newStore.getLastServerPushNotificationTimestamp()
    assertThat(newTimestamp).isEqualTo(timestamp)
    assertThat(newStore.isNotificationShown("test_id")).isTrue()
  }
}
