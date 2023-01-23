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
package com.android.tools.idea.dagger.index

/**
 * An index value for the Dagger index. Each [DaggerConcept] is responsible for defining the exact data that it needs to store for its
 * entries.
 */
abstract class IndexValue(val dataType: DataType) {
  /**
   * Type of value being stored. This is required to be centralized to ensure that each type has a unique integer representation that can be
   * used for serialization and storage.
   */
  enum class DataType {
    INJECTED_CONSTRUCTOR,
    INJECTED_CONSTRUCTOR_PARAMETER,
    PROVIDES_METHOD,
    PROVIDES_METHOD_PARAMETER,
    INJECTED_FIELD,
  }
}
