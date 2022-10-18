/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.utils.environment

import com.intellij.openapi.externalSystem.util.environment.Environment

class TestEnvironment(
  private val fallbackEnvironment: Environment? = null
) : Environment {

  private val properties = hashMapOf<String, String?>()
  private val variables = hashMapOf<String, String?>()

  fun properties(vararg properties: Pair<String, String?>) {
    this.properties.putAll(properties)
  }

  fun variables(vararg variables: Pair<String, String?>) {
    this.variables.putAll(variables)
  }

  override fun property(name: String) =
    when (name) {
      in properties -> properties[name]
      else -> fallbackEnvironment?.property(name)
    }

  override fun variable(name: String) =
    when (name) {
      in variables -> variables[name]
      else -> fallbackEnvironment?.variable(name)
    }
}