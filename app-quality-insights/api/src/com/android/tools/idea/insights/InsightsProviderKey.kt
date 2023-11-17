/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights

val VITALS_KEY = InsightsProviderKey("Android Vitals")
val CRASHLYTICS_KEY = InsightsProviderKey("Firebase Crashlytics")

// Use Crashlytics in testing because it exposes more functionality.
val TEST_KEY = CRASHLYTICS_KEY

/** Represents the identifier of the source of the issue data, e.g. Crashlytics, Play Vitals etc. */
data class InsightsProviderKey(val displayName: String) : Comparable<InsightsProviderKey> {
  override fun compareTo(other: InsightsProviderKey): Int {
    return compareValuesBy(this, other) { it.displayName }
  }
}
