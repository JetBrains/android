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

data class TestConnection(
  override val appId: String,
  override val mobileSdkAppId: String?,
  override val projectId: String?,
  override val projectNumber: String?,
  val variantName: String = "variant1",
  val moduleShortName: String = "app1",
  override val isConfigured: Boolean = true,
  val isPreferred: Boolean = true,
) : Connection {
  override val clientId: String = "android:${appId}"

  override fun isPreferredConnection() = isPreferred
}
