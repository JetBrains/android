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
package com.android.tools.adtui.swing

import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import java.awt.GraphicsConfiguration
import org.junit.rules.ExternalResource

/** Controls the JRE HiDPI flag and the values of user and system scales in [JBUIScale]. */
class HiDpiRule : ExternalResource() {

  private var originalJreHiDpi = false
  private var originalSysScale = 1.0f
  private var originalUserScale = 1.0f

  override fun before() {
    originalJreHiDpi = JreHiDpiUtil.isJreHiDPI(null as GraphicsConfiguration?)
    originalSysScale = JBUIScale.sysScale()
    originalUserScale = JBUIScale.scale(1.0f)
  }

  override fun after() {
    setJreHiDpi(originalJreHiDpi)
    setSysScale(originalSysScale)
    setUserScale(originalUserScale)
  }

  /** Enables or disables the JRE HiDPI flag. */
  fun setJreHiDpi(value: Boolean) {
    JreHiDpiUtil.test_jreHiDPI().set(value)
  }

  /** Sets the value of the system scale in [JBUIScale]. */
  fun setSysScale(value: Float) {
    JBUIScale.setSystemScaleFactor(value)
  }

  /** Sets the value of the user scale in [JBUIScale]. */
  fun setUserScale(value: Float) {
    JBUIScale.setUserScaleFactorForTest(value)
  }

  /** Simulates Mac graphics environment. */
  fun setRetinaMode() {
    setJreHiDpi(true)
    setSysScale(2.0f)
  }
}
