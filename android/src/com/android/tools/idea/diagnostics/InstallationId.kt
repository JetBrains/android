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

import java.util.UUID
import java.util.prefs.Preferences

private const val INSTALLATION_ID_KEY = "user_id_on_machine"
private const val NODE_NAME = "google"

/**
 * Manages a persistent unique installation ID, scoped to a user-machine pair.
 *
 * This object retrieves a stored UUID from [java.util.Preferences]. If the ID
 * doesn't exist or is not a valid UUID, it generates a new random UUID,
 * persists it, and returns it.
 */
object InstallationId {
  @JvmStatic
  fun get() = get(Preferences.userRoot())

  fun get(userRoot: Preferences): String {
    val preferences = userRoot.node(NODE_NAME);
    var installationId = preferences.get(INSTALLATION_ID_KEY, "")

    if (!installationId.isValidUuid()) {
      installationId = UUID.randomUUID().toString()
      preferences.put(INSTALLATION_ID_KEY, installationId)
    }

    return installationId
  }
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