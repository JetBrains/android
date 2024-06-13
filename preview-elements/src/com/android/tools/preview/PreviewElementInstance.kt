/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.preview

/**
 * Some preview elements can generate multiple other preview elements, such as preview elements with
 * preview parameters. This interface represents the unique preview element that is instantiated.
 * The [instanceId] should be unique.
 */
interface PreviewElementInstance<T> : ConfigurablePreviewElement<T> {
  /** Unique identifier that can be used for filtering. */
  val instanceId: String

  /**
   * Derives a new [PreviewElementInstance] from an existing one, replacing the
   * [PreviewDisplaySettings] and the [PreviewConfiguration].
   */
  fun createDerivedInstance(
    displaySettings: PreviewDisplaySettings,
    config: PreviewConfiguration,
  ): PreviewElementInstance<T>
}
