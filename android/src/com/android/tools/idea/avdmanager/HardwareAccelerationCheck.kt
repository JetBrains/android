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
package com.android.tools.idea.avdmanager

import com.intellij.openapi.util.SystemInfo.isChromeOS
import java.io.File

internal object HardwareAccelerationCheck {
  /**
   * This should only be executed on Crostini. On any other OS, it will throw an [UnsupportedOperationException].
   */
  private val isHWAccelerated: Boolean by lazy(fun(): Boolean {
    return if (isChromeOS) File("/dev/kvm").exists()
    else throw UnsupportedOperationException("Can only check for existence of /dev/kvm on Crostini")
  })

  /**
   * With the introduction of nested hardware assisted virtualization (`/dev/kvm`) in Crostini (starting w/ Chrome OS M82), machines that
   * support this now behave like Linux machines when it comes to Studio's emulator functionality. Use this method instead of [isChromeOS]
   * when making choices about virtual devices.
   */
  @kotlin.jvm.JvmStatic
  fun isChromeOSAndIsNotHWAccelerated() = isChromeOS && !isHWAccelerated
}