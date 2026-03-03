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
package com.android.tools.visuallint

import com.android.ide.common.rendering.api.ViewInfo

/**
 * Provider for information about [ViewInfo] that might be specific to certain rendering environments (e.g. XML vs Compose, Android Studio).
 */
interface VisualLintViewInfoProvider {
  /** Returns the simple name for the given [ViewInfo]. */
  fun simpleName(viewInfo: ViewInfo): String

  /** Returns the name with ID for the given [ViewInfo]. */
  fun nameWithId(viewInfo: ViewInfo): String
}
