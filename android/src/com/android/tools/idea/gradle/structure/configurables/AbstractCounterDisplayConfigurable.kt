/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.structure.dialog.CounterDisplayConfigurable
import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher

abstract class AbstractCounterDisplayConfigurable protected constructor(context: PsContext) :
    BasePerspectiveConfigurable(context), CounterDisplayConfigurable {

  private val myEventDispatcher = EventDispatcher.create(CounterDisplayConfigurable.CountChangeListener::class.java)

  protected fun fireCountChangeListener() {
    myEventDispatcher.multicaster.countChanged()
  }

  override fun add(listener: CounterDisplayConfigurable.CountChangeListener, parentDisposable: Disposable) {
    myEventDispatcher.addListener(listener, parentDisposable)
  }
}
