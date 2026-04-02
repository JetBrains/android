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
package com.android.tools.idea.avd.glassespairing

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * A minimal application-level service to hold global state for AI Glasses pairing.
 *
 * It tracks whether a pairing wizard is open to allow suspending background activity, and manages a lock to ensure only one background
 * discovery loop runs at a time.
 */
@Service(Service.Level.APP)
class GlassesPairingLockService {
  private val _isWizardOpen = MutableStateFlow(false)
  val isWizardOpen = _isWizardOpen.asStateFlow()

  internal fun setWizardOpen(open: Boolean) {
    _isWizardOpen.value = open
  }

  val discoveryLock = Mutex()
}
