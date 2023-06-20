/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers

/**
 * Notification data that will ultimately be displayed to the user in a toast or bubble.
 *
 * If optional URL data is included, it should be shown on a separate line as its own link.
 */
data class Notification(val severity: Severity, val title: String, val text: String, val urlData: UrlData?) {

  enum class Severity { INFO, WARNING, ERROR }

  data class UrlData(val url: String, val text: String)
}

