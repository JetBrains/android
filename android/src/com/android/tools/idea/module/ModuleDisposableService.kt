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
package com.android.tools.idea.module

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module

/**
 * A service that can be used as a parent disposable instead of a [Module].
 */
class ModuleDisposableService(module: Module) : Disposable.Default {
  companion object {
    @JvmStatic
    fun getInstance(module: Module): ModuleDisposableService =
      module.getService(ModuleDisposableService::class.java)
  }
}