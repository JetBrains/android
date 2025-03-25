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
package com.android.tools.idea.gradle.dsl.model.configurations

import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationModel
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationsModel
import com.android.tools.idea.gradle.dsl.parser.elements.EmptyGradleBlockModel

class EmptyConfigurationsModelImpl: EmptyGradleBlockModel(), ConfigurationsModel {
  override fun all(): List<ConfigurationModel> = listOf()

  override fun addConfiguration(name: String): ConfigurationModel =
    throw UnsupportedOperationException("Call is not supported for Declarative")

  override fun removeConfiguration(name: String) =
    throw UnsupportedOperationException("Call is not supported for Declarative")
}