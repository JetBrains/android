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
package com.android.tools.idea.databinding

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ModificationTracker
import java.util.concurrent.atomic.AtomicLong
import net.jcip.annotations.ThreadSafe

/**
 * Service that owns an atomic counter for how many times the view binding setting is changed. This
 * allows it to provide a [ModificationTracker] which is used by IntelliJ for knowing when to clear
 * caches, etc.
 */
@ThreadSafe
class ViewBindingEnabledTrackingService : ModificationTracker {
  companion object {
    @JvmStatic
    val instance: ViewBindingEnabledTrackingService
      get() =
        ApplicationManager.getApplication()
          .getService(ViewBindingEnabledTrackingService::class.java)
  }

  private val modificationCount = AtomicLong(0)

  fun incrementModificationCount() {
    modificationCount.incrementAndGet()
  }

  override fun getModificationCount() = modificationCount.get()
}
