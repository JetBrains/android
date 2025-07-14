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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel
import kotlin.test.fail

class WrapLogicTest {

  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun testWrapUnwrap() {
    val component = JPanel()
    val originalContainer = JPanel()
    val newContainer = JPanel()

    originalContainer.add(component)

    val wrapLogic = WrapLogic(disposableRule.disposable, component, originalContainer)

    wrapLogic.wrapComponent { disposable, component ->
      newContainer.add(component)
      newContainer
    }

    assertThat(component.parent).isEqualTo(newContainer)
    // new container contains component
    assertThat(newContainer.components.toList()).isEqualTo(listOf(component))
    // original container contains new container
    assertThat(originalContainer.components.toList()).isEqualTo(listOf(newContainer))

    Disposer.dispose(wrapLogic)

    assertThat(component.parent).isEqualTo(originalContainer)
    // new container contains component
    assertThat(newContainer.components.toList()).isEmpty()
    // original container contains new container
    assertThat(originalContainer.components.toList()).isEqualTo(listOf(component))
  }

  @Test
  fun testDoubleWrapThrows() {
    val component = JPanel()
    val originalContainer = JPanel()
    val newContainer = JPanel()

    originalContainer.add(component)

    val wrapLogic = WrapLogic(disposableRule.disposable, component, originalContainer)

    wrapLogic.wrapComponent { disposable, component ->
      newContainer.add(component)
      newContainer
    }

    assertThat(component.parent).isEqualTo(newContainer)
    // new container contains component
    assertThat(newContainer.components.toList()).isEqualTo(listOf(component))
    // original container contains new container
    assertThat(originalContainer.components.toList()).isEqualTo(listOf(newContainer))

    try {
      wrapLogic.wrapComponent { _, _ -> JPanel() }
      fail("Expected exception not thrown")
    } catch (_: IllegalStateException) {}
  }

  @Test
  fun testWrapLogicDisposedBeforeWrappingUi() {
    val component = JPanel()
    val originalContainer = JPanel()

    originalContainer.add(component)

    val wrapLogic = WrapLogic(disposableRule.disposable, component, originalContainer)
    // Should not throw
    Disposer.dispose(wrapLogic)
  }
}
