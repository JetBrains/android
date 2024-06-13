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
package com.android.tools.idea.rendering

import com.android.tools.idea.layoutlib.LayoutLibrary
import com.intellij.mock.MockProjectEx
import com.intellij.openapi.util.Disposer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.times

class StudioLayoutlibContextTest {
  @Test
  fun testNoDoubleDisposal() {
    val disposable = Disposer.newDisposable()
    val project = MockProjectEx(disposable)

    val layoutLibrary = Mockito.mock(LayoutLibrary::class.java)

    val layoutlibContext = StudioLayoutlibContext(project)

    layoutlibContext.register(layoutLibrary)
    assertThrows(AssertionError::class.java) {
      layoutlibContext.register(layoutLibrary)
    }

    Disposer.dispose(disposable)

    Mockito.verify(layoutLibrary, times(1)).dispose()
  }
}