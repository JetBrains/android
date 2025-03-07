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
package com.android.tools.idea.googleapis

import com.intellij.openapi.extensions.ExtensionPointName

interface GoogleApiKeyProvider {
  enum class GoogleApi(val apiName: String) {
    CONTENT_SERVING("developerscontentserving-pa")
  }

  fun getApiKey(api: GoogleApi) : String?

  companion object {
    val EP_NAME = ExtensionPointName.create<GoogleApiKeyProvider>("com.android.googleapis.googleApiKeyProvider")

    fun getApiKey(api: GoogleApi) = EP_NAME.extensionList.firstNotNullOfOrNull { it.getApiKey(api) }
  }
}