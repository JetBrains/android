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
package com.ibm.google.onboardingandauthentication.data

import androidx.annotation.DrawableRes

/**
 * Represents a carousel item with an optional image resource ID and a non-null description text for
 * display.
 *
 * @property imageId Resource ID of the image to be shown in the carousel (nullable).
 * @property description Description text for the carousel item (non-null, defaults to empty
 *   string).
 */
data class WalkthroughStepModel(
  @DrawableRes val imageId: Int? = null,
  val description: String? = null,
)
