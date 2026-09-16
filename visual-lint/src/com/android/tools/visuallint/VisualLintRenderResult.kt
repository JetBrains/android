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
import com.android.ide.common.resources.Locale
import com.android.resources.Density
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State

/**
 * Interface encapsulating the result of a layout rendering that is required for Visual Lint analysis.
 *
 * It contains the view hierarchy, configuration context, and optional results from the Layout Validator (Accessibility Test Framework).
 */
data class VisualLintRenderResult(
  /** The list of root [ViewInfo] objects representing the view hierarchy. */
  val rootViews: List<ViewInfo>,

  /** The configuration used for the rendering. */
  val configuration: VisualLintConfiguration?,

  /**
   * The optional result from the validator (e.g., Accessibility Test Framework). This may be null if validation was not performed or not
   * available.
   *
   * Note: This is typed as [Any]? instead of a specific Validator class because the underlying RenderResult implementation in the IDE
   * stores this as an Object. The actual runtime type can vary (e.g., ValidatorResult or ValidatorHierarchy), and analyzers are expected to
   * check and cast this value as needed. This maintains compatibility with the existing RenderResult architecture.
   */
  val validatorResult: Any?,
)

/**
 * Interface defining the configuration parameters for a Visual Lint analysis.
 *
 * This configuration provides context about the environment in which the layout was rendered, such as screen density and locale.
 */
data class VisualLintConfiguration(val device: Device?, val deviceState: State?, val density: Density, val locale: Locale)
