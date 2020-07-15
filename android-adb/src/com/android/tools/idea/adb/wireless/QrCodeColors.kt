/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless

import java.awt.Color

/**
 * List of colors used for displaying a QR code and its surroundings.
 * The colors are hard-coded (i.e. not theme aware) on purpose, since
 * a QR Code should always be displayed with black dots on white background
 * to be "scanner friendly".
 */
interface QrCodeColors {
  companion object {
    /**
     * The background os a QR Code is always white
     */
    @JvmField
    val BACKGROUND: Color = Color.WHITE

    /**
     * The foreground (i.e. "dots") of a QR Code are always black
     */
    @JvmField
    val FOREGROUND: Color = Color.BLACK

    @JvmField
    val LABEL_FOREGROUND: Color = Color.BLACK

    @JvmField
    val BORDER: Color = Color.GRAY
  }
}
