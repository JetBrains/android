/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.client

interface ToSClient {

  /**
   * Gets the user setting for the provided [rootKey], [project] and [subKey]
   *
   * @param rootKey: Top level key of user setting
   * @param project: Cloud project associated with the setting
   * @param subKey: Sub level key of user setting
   */
  fun getUserSetting(rootKey: String, project: String? = null, subKey: String? = null): Boolean
}

/** Stub [ToSClient] that returns `true` for any requested setting */
class StubToSClient(private val response: Boolean) : ToSClient {
  override fun getUserSetting(rootKey: String, project: String?, subKey: String?) = response
}
