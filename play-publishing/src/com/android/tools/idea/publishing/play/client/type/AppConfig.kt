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
package com.android.tools.idea.publishing.play.client.type

import com.google.api.client.util.Key
import com.google.api.client.util.Value

open class AppConfig(
  @field:Key var packageName: String = "",
  @field:Key var title: String = "",
  @field:Key var defaultLanguageCode: String = "",
  @field:Key var appType: AppType = AppType.APP_TYPE_UNSPECIFIED,
  @field:Key var paid: Boolean = false,
)

enum class AppType {
  @Value("APP_TYPE_UNSPECIFIED") APP_TYPE_UNSPECIFIED,
  @Value("APP_TYPE_APP") APP_TYPE_APP,
  @Value("APP_TYPE_GAME") APP_TYPE_GAME,
}
