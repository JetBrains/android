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
package com.android.tools.idea.common.model

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider

abstract class NlDataProvider(val keys: Set<DataKey<*>>) : UiDataProvider {
  constructor(vararg keys: DataKey<*>) : this(setOf(*keys))

  abstract fun getData(dataId: String): Any?

  open fun <T> getData(key: DataKey<T>): T? {
    @Suppress("UNCHECKED_CAST") return getData(key.name) as T?
  }

  override fun uiDataSnapshot(sink: DataSink) {
    @Suppress("UNCHECKED_CAST")
    keys.forEach { key -> getData(key as DataKey<Any>)?.let { sink[key] = it } }
  }
}
