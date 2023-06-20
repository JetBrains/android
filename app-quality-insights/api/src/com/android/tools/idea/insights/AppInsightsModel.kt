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

/**
 * Models App Insight states.
 *
 * App Insights functionality currently has a hard dependency on the user being logged into Android
 * Studio with their Google account. Without it, none of the functionality is meaningful since we
 * are unable to make any API calls. To express this dependency the model provides [Unauthenticated]
 * and [Authenticated] states.
 */
sealed class AppInsightsModel {
  /** The system hasn't initialized yet. Represents the state before authentication is known. */
  object Uninitialized : AppInsightsModel()

  /** The user is not signed in, App Insights will not work until they do. */
  object Unauthenticated : AppInsightsModel()

  /**
   * When the user is signed in, we pass in [AppInsightsProjectLevelController] which manages App
   * Insights state data.
   */
  class Authenticated(val controller: AppInsightsProjectLevelController) : AppInsightsModel()
}
