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
package com.android.tools.idea.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import java.awt.Component

/** A UI component that host a Run Configuration Editor section */
interface RunConfigSection {
  /**  Create a UI component */
  fun getComponent(parentDisposable: Disposable): Component

  /** Reset UI from a [RunConfiguration] */
  fun resetFrom(runConfiguration: RunConfiguration)

  /** Apply a [RunConfiguration] to the UI */
  fun applyTo(runConfiguration: RunConfiguration)

  /** Validate a [RunConfiguration]. */
  fun validate(runConfiguration: RunConfiguration): List<ValidationError>
}
