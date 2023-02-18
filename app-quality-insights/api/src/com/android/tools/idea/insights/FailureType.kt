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

import icons.StudioIcons
import javax.swing.Icon

enum class FailureType {
  UNSPECIFIED,
  FATAL,
  NON_FATAL,
  ANR,
  USER_PERCEIVED_ONLY,
  FOREGROUND,
  BACKGROUND;

  fun getIcon(): Icon? =
    when (this) {
      FATAL -> StudioIcons.AppQualityInsights.FATAL
      NON_FATAL -> StudioIcons.AppQualityInsights.NON_FATAL
      ANR -> StudioIcons.AppQualityInsights.ANR
      USER_PERCEIVED_ONLY, FOREGROUND, BACKGROUND -> null // TODO: add icons
      // This scenario shouldn't ever be reached.
      UNSPECIFIED -> null
    }
}
