/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.google.common.collect.ImmutableList
import java.util.function.Consumer

interface PsModelCollection<T> {

  fun forEach(consumer: Consumer<T>)

  fun forEach(consumer: (T) -> Unit) = forEach(Consumer { consumer(it) })

  fun items(): List<T> {
    val result = ImmutableList.Builder<T>()
    forEach(Consumer { result.add(it) })
    return result.build()
  }
}
