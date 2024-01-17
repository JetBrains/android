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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SimpleModificationTracker

/**
 * Service that owns an atomic counter for how many times the data binding setting is changed. This
 * service implements the [com.intellij.openapi.util.ModificationTracker] interface, which is used
 * by IntelliJ for knowing when to clear caches, etc., to expose the counter value.
 */
@Service(Service.Level.APP)
class DataBindingModeTrackingService : SimpleModificationTracker() {
  companion object {
    @JvmStatic
    fun getInstance(): DataBindingModeTrackingService =
      ApplicationManager.getApplication().service()
  }
}
