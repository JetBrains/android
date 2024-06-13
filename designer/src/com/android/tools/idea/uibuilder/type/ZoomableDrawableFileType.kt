/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.type

import com.android.SdkConstants

object ZoomableDrawableFileType :
  DrawableFileType(
    setOf(
      SdkConstants.TAG_BITMAP,
      SdkConstants.TAG_CLIP_PATH,
      SdkConstants.TAG_DRAWABLE,
      SdkConstants.TAG_GRADIENT,
      SdkConstants.TAG_INSET,
      SdkConstants.TAG_LAYER_LIST,
      SdkConstants.TAG_NINE_PATCH,
      SdkConstants.TAG_PATH,
      SdkConstants.TAG_RIPPLE,
      SdkConstants.TAG_ROTATE,
      SdkConstants.TAG_SHAPE,
      SdkConstants.TAG_TRANSITION,
      SdkConstants.TAG_VECTOR,
    )
  )
