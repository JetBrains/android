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
package com.android.tools.idea.util

import com.intellij.openapi.Disposable
import java.util.EventListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An object that lazily subscribes a [listener] to respond to file changes after [ensureSubscribed] has been called.
 */
abstract class LazyFileListenerSubscriber<T : EventListener>(val listener: T, val parent: Disposable) {
  private val subscribed = AtomicBoolean(false)

  /** Subscribes the listener. [ensureSubscribed] guarantees this function will only be called once.*/
  protected abstract fun subscribe()

  /** Ensures that the [EventListener] is actively listening for file changes.*/
  fun ensureSubscribed() {
    if (subscribed.compareAndSet(false, true)) {
      subscribe()
    }
  }
}
