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
package com.android.tools.idea.login

import com.intellij.openapi.extensions.ExtensionPointName

interface GoogleLoginCredentialProvider {

  /**
   * Returns a fresh OAuth2 access token for the "Gemini" feature as a json object containing
   * two name/value pairs: an `access_token` and an `expires_in`.
   * ```
   * {
   *   "access_token" : "...",
   *   "expires_in" : 3599
   * }
   * ```
   */
  fun getGeminiAccessTokenAsJson() : String?

  /**
   * Checks if the user is currently logged in to the "Gemini" feature.
   *
   * @return `true` if the user is logged in, `false` otherwise.
   */
  fun isUserLoggedIntoGemini(): Boolean

  companion object {
    val EP_NAME = ExtensionPointName.create<GoogleLoginCredentialProvider>("com.android.tools.idea.login.googleLoginCredentialProvider")

    fun getGeminiAccessTokenAsJson() : String? = EP_NAME.extensionList.firstNotNullOfOrNull { it.getGeminiAccessTokenAsJson() }

    fun isUserLoggedIntoGemini(): Boolean = EP_NAME.extensionList.firstNotNullOfOrNull { it.isUserLoggedIntoGemini() } ?: false
  }
}
