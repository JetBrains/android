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

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import kotlin.time.Clock
import kotlin.time.Instant

/** Persistently records data and state for server-driven notifications locally. */
@Service(Service.Level.APP)
@State(name = "ServerNotificationStore", storages = [Storage("server_notification_store.xml")])
class ServerNotificationStore : SimplePersistentStateComponent<ServerNotificationStore.State>(State()) {

  class State : BaseState() {
    // Maps the integer value of the StudioPushNotification.Requirement.FeatureUsage.Feature enum
    // to the last used timestamp in epoch milliseconds.
    var featureLastUsedTimestamps by map<Int, Long>()

    // Maps the push notification ID to the timestamp it was shown in epoch milliseconds.
    var serverPushNotificationShownTimestamps by map<String, Long>()
  }

  /**
   * Records the current timestamp as the last-used time for a specific feature enum value.
   *
   * @param featureValue The integer value of the Feature enum.
   */
  fun recordFeatureUsage(featureValue: Int) {
    state.featureLastUsedTimestamps[featureValue] = Clock.System.now().toEpochMilliseconds()
  }

  /**
   * Retrieves the last used timestamp for a specific feature enum value.
   *
   * @param featureValue The integer value of the Feature enum.
   * @return The last used Instant, or null if the feature has no recorded usage.
   */
  fun getLastUsedTimestamp(featureValue: Int): Instant? {
    val timestamp = state.featureLastUsedTimestamps[featureValue] ?: return null
    return Instant.fromEpochMilliseconds(timestamp)
  }

  /**
   * Records the given notification ID as already shown to the user and updates the last shown cooldown timestamp.
   *
   * @param notificationId The unique ID of the push notification.
   * @param clock The clock used to retrieve the current timestamp (defaults to Clock.System).
   */
  fun markNotificationAsShown(notificationId: String, clock: Clock = Clock.System) {
    state.serverPushNotificationShownTimestamps[notificationId] = clock.now().toEpochMilliseconds()
  }

  /**
   * Checks whether the given notification has already been shown.
   *
   * @param notificationId The unique ID of the push notification.
   * @return True if it has been shown once, false otherwise.
   */
  fun isNotificationShown(notificationId: String): Boolean {
    return state.serverPushNotificationShownTimestamps.containsKey(notificationId)
  }

  /**
   * Retrieves the last timestamp of the last server push notification shown.
   *
   * @return The last used Instant, or null if no notification has been shown yet.
   */
  fun getLastServerPushNotificationTimestamp(): Instant? {
    val maxTimestamp = state.serverPushNotificationShownTimestamps.values.maxOrNull() ?: return null
    return Instant.fromEpochMilliseconds(maxTimestamp)
  }

  companion object {
    fun getInstance(): ServerNotificationStore = service()
  }
}
