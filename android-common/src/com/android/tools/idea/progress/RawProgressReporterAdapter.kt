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
package com.android.tools.idea.progress

import com.intellij.platform.util.progress.RawProgressReporter

class RawProgressReporterAdapter(
  val reporter: RawProgressReporter,
  cls: Class<*> = RawProgressReporterAdapter::class.java,
) : StudioLoggerProgressIndicator(cls) {

  private var fraction: Double = 0.0
  private var indeterminate: Boolean = false

  override fun getFraction(): Double = fraction

  override fun setFraction(v: Double) {
    fraction = v
    if (!indeterminate) {
      reporter.fraction(v)
    }
  }

  override fun isIndeterminate(): Boolean = indeterminate

  override fun setIndeterminate(indeterminate: Boolean) {
    this.indeterminate = indeterminate
    reporter.fraction(fraction.takeUnless { indeterminate })
  }

  override fun setText(s: String?) {
    reporter.text(s)
  }

  override fun setSecondaryText(s: String?) {
    reporter.details(s)
  }
}
