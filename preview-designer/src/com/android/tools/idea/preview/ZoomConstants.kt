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
package com.android.tools.idea.preview

import com.android.tools.idea.preview.representation.CommonPreviewRepresentation

/**
 * This object defines the constants to use for the Zoom controller in [CommonPreviewRepresentation]
 */
object ZoomConstants {
  const val MAX_ZOOM_TO_FIT_LEVEL = 2.0 // 200%
  const val MIN_SCALE = 0.01 // 1% zoom level
  const val MAX_SCALE = 5.0 // 500% zoom level
}
