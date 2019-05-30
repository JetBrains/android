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
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An object that lazily subscribes a [listener] to respond to VFS changes after [ensureSubscribed] has been called.
 */
open class LazyVirtualFileListenerSubscriber<T : VirtualFileListener>(val listener: T, val parent: Disposable) {
  private val subscribed = AtomicBoolean(false)

  /** Subscribes the listener. [ensureSubscribed] guarantees this function will only be called once.*/
  protected open fun subscribe() {
    VirtualFileManager.getInstance().addVirtualFileListener(listener, parent)
  }

  /** Ensures that the [VirtualFileListener] is actively listening for VFS changes.*/
  fun ensureSubscribed() {
    if (subscribed.compareAndSet(false, true)) {
      subscribe()
    }
  }
}
