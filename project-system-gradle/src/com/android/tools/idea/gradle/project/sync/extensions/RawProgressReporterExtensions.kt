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
package com.android.tools.idea.gradle.project.sync.extensions

import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.util.progress.RawProgressReporter

fun RawProgressReporter.toBridgeIndicator(): ProgressIndicatorEx =
  object : ProgressIndicatorBase(), ProgressIndicatorEx {
    override fun setText(text: String?) {
      super.setText(text)
      this@toBridgeIndicator.text(text)
    }

    override fun setText2(text: String?) {
      super.setText2(text)
      this@toBridgeIndicator.text(text)
    }

    override fun setFraction(fraction: Double) {
      super.setFraction(fraction)
      // RawProgressReporter logs an error if the value is not in the interval 0.0-1.0,
      // but ProgressIndicator didn't have that check before (although, according to the documentation, it should be in the range),
      // so some indicators report the wrong value and this can cause error spam - IJPL-166399
      this@toBridgeIndicator.fraction(fraction.coerceIn(0.0, 1.0))
    }

    override fun setIndeterminate(indeterminate: Boolean) {
      super.setIndeterminate(indeterminate)
      if (indeterminate) this@toBridgeIndicator.fraction(null)
    }
  }
