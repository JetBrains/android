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
package com.android.tools.layoutlib

import com.android.tools.idea.layoutlib.LayoutLibrary

/** Context required to create Layoutlib instance. */
interface LayoutlibContext {
  /** Based on the environment-specific signals detects whether Layoutlib has crashed or not. */
  fun hasLayoutlibCrash(): Boolean

  /** Registers a layoutlib instance. This is done in order to dispose the instance when it is no longer needed. */
  fun register(layoutlib: LayoutLibrary)
}