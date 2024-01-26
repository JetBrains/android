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
package org.jetbrains.android.util

import com.android.tools.idea.testing.DisposerExplorer
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import org.junit.Rule
import org.junit.Test

/** Tests for [MultiParentDisposable] and [runOnDisposalOfAnyOf]. */
class DisposableUtilsTest {

  @get:Rule val disposableRule = DisposableRule()

  /** Checks that [MultiParentDisposable] is disposed when any of its parents are disposed. */
  @Test
  fun testParentDisposal() {
    val d1 = createDisposable()
    val d2 = createDisposable()
    val d3 = createDisposable()
    val multiParentDisposable = object : MultiParentDisposable(d1, d2, d3) {
      var isDisposed = false

      override fun dispose() {
        isDisposed = true
      }
    }
    Disposer.dispose(d2)
    assertThat(multiParentDisposable.isDisposed).isTrue()
    assertThat(DisposerExplorer.getChildren(d1)).isEmpty()
    assertThat(DisposerExplorer.getChildren(d3)).isEmpty()
  }

  /** Checks that the [Disposer] tree remains clean after [MultiParentDisposable] is disposed. */
  @Test
  fun testDisposal() {
    val d1 = createDisposable()
    val d2 = createDisposable()
    val d3 = createDisposable()
    val multiParentDisposable = object : MultiParentDisposable(d1, d2, d3) {
      var isDisposed = false

      override fun dispose() {
        isDisposed = true
      }
    }
    Disposer.dispose(multiParentDisposable)
    assertThat(DisposerExplorer.getChildren(d1)).isEmpty()
    assertThat(DisposerExplorer.getChildren(d2)).isEmpty()
    assertThat(DisposerExplorer.getChildren(d3)).isEmpty()
  }

  /** Checks that unintended use of [MultiParentDisposable] triggers an [IllegalArgumentException]. */
  @Test(expected = IllegalArgumentException::class)
  fun testUnintendedUse() {
    MultiParentDisposable(Disposer.newDisposable())
  }

  /** Checks [runOnDisposalOfAnyOf]. */
  @Test
  fun testRunOnDisposalOfAnyOf() {
    val d1 = createDisposable()
    val d2 = createDisposable()
    val d3 = createDisposable()
    var executed = false
    runOnDisposalOfAnyOf(d1, d2, d3) {
      executed = true
    }
    Disposer.dispose(d3)
    assertThat(executed).isTrue()
    assertThat(DisposerExplorer.getChildren(d1)).isEmpty()
    assertThat(DisposerExplorer.getChildren(d2)).isEmpty()
    assertThat(DisposerExplorer.getChildren(d3)).isEmpty()
  }

  private fun createDisposable(): Disposable =
      Disposer.newDisposable().also { Disposer.register(disposableRule.disposable, it) }
}
