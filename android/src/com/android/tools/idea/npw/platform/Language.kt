/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.platform

/**
 * Representations of supported programming languages we can target when building an app.
 */
enum class Language(private val string: String, val extension: String) {
  KOTLIN("Kotlin", "kt"), // Recommended at the top
  JAVA("Java", "java");

  override fun toString(): String = string

  companion object {
    /**
     * Finds a language matching the requested name. Returns specified 'defaultValue' if not found.
     */
    @JvmStatic
    fun fromName(name: String?, defaultValue: Language): Language =
      values().firstOrNull { it.string == name } ?: defaultValue
  }
}
