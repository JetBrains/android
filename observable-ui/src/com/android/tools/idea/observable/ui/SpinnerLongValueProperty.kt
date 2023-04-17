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
package com.android.tools.idea.observable.ui

import com.android.tools.idea.observable.AbstractProperty
import javax.swing.JSpinner
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * [AbstractProperty] that wraps a [JSpinner] and exposes its value.
 */
class SpinnerLongValueProperty(private val spinner: JSpinner) : AbstractProperty<Long>(), ChangeListener {

  init {
    spinner.addChangeListener(this)
  }

  override fun stateChanged(e: ChangeEvent?) {
    notifyInvalidated()
  }

  override fun setDirectly(value: Long) {
    spinner.value = value
  }

  override fun get(): Long = spinner.value as Long
}