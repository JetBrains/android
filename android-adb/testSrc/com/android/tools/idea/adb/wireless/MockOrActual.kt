/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless

import org.mockito.Mockito

/**
 * Provides access to either a mock or an actual implementation of a class [T] through the [useMock] property
 */
class MockOrActual<T: Any>(clazz: Class<T>, val actualFactory: () -> T) {
  var useMock = false

  val instance: T by lazy {
    if (useMock) {
      Mockito.mock(clazz)
    }
    else {
      actualFactory()
    }
  }
}

inline fun <reified T: Any> mockOrActual(noinline actualFactory: () -> T): MockOrActual<T> {
  return MockOrActual(T::class.java, actualFactory)
}
