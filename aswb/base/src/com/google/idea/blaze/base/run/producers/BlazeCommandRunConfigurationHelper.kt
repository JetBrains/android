/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.producers

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.intellij.openapi.extensions.ExtensionPointName

/** Extension point decoupling language modules (JVM, Android) to encode FilterComponent into handler states. */
interface BlazeCommandRunConfigurationHelper {
  fun handlesConfiguration(config: BlazeCommandRunConfiguration): Boolean

  fun applyCommand(config: BlazeCommandRunConfiguration, command: CommandComponent)

  fun matchesCommand(config: BlazeCommandRunConfiguration, command: CommandComponent): Boolean

  fun applyFilter(config: BlazeCommandRunConfiguration, filter: FilterComponent)

  fun matchesFilter(config: BlazeCommandRunConfiguration, filter: FilterComponent): Boolean

  fun hasActiveFilter(config: BlazeCommandRunConfiguration): Boolean

  companion object {
    val EP_NAME = ExtensionPointName.create<BlazeCommandRunConfigurationHelper>("com.google.idea.blaze.BlazeCommandRunConfigurationHelper")
  }
}
