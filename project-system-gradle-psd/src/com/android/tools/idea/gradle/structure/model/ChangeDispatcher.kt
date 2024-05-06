/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import java.util.EventListener

class ChangeDispatcher() {
  private val dispatcher = EventDispatcher.create(ChangeListener::class.java)

  fun changed() = dispatcher.multicaster.changed()
  fun add(parentDisposable: Disposable, listener: () -> Unit) = dispatcher.addListener(object : ChangeListener {
    override fun changed() = listener()
  }, parentDisposable)

  private interface ChangeListener : EventListener {
    fun changed()
  }
}