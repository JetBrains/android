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
package com.android.tools.idea.vitals.datamodel

import com.android.tools.idea.insights.Connection

data class VitalsConnection(
  override val appId: String,
  val displayName: String,
  val isPreferred: Boolean,
) : Connection {
  override val isConfigured: Boolean = true
  override val mobileSdkAppId = null
  override val projectId = null
  override val projectNumber = null
  override val clientId = "apps/${appId}"

  override fun isPreferredConnection() = isPreferred
}
