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

private val EMPTY_DATA_PROVIDER = NlDataProviderBuilder().build()

abstract class NlDataProvider : UiDataProvider {
  abstract fun <T> getData(key: DataKey<T>): T?

  companion object {
    @JvmStatic fun empty() = EMPTY_DATA_PROVIDER
  }
}

class NlDataProviderBuilder {
  private var delegate: NlDataProvider? = null
  private val data = mutableMapOf<DataKey<*>, Any?>()

  fun withDelegate(delegate: NlDataProvider): NlDataProviderBuilder {
    this.delegate = delegate
    return this
  }

  fun <T> add(key: DataKey<T>, value: T?): NlDataProviderBuilder {
    data[key] = value
    return this
  }

  fun build(): NlDataProvider = SimpleNlDataProviderImpl(delegate, data)

  class SimpleNlDataProviderImpl(
    private val delegate: NlDataProvider?,
    private val data: Map<DataKey<*>, Any?>,
  ) : NlDataProvider() {
    override fun <T> getData(key: DataKey<T>): T? {
      delegate?.getData(key)?.let {
        return it
      }
      @Suppress("UNCHECKED_CAST") return data[key] as T?
    }

    override fun uiDataSnapshot(sink: DataSink) {
      data.entries.forEach { (key, data) ->
        @Suppress("UNCHECKED_CAST")
        sink[key as DataKey<Any>] = data
      }
      delegate?.uiDataSnapshot(sink)
    }
  }
}
