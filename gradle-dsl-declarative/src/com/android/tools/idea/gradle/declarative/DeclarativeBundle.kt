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
package com.android.tools.idea.gradle.declarative

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier


object DeclarativeBundle {
  private const val BUNDLE_NAME: @NonNls String = "messages.DeclarativeBundle"

  private val BUNDLE: DynamicBundle = DynamicBundle(DeclarativeBundle::class.java, BUNDLE_NAME)


  fun message(@NonNls @PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }

  fun messagePointer(key: @PropertyKey(resourceBundle = BUNDLE_NAME) String, vararg params: Any): Supplier<String> {
    return Supplier { message(key, *params) }
  }
}