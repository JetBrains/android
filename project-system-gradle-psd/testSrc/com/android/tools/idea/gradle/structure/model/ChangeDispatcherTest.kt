/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.intellij.openapi.util.Disposer
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class ChangeDispatcherTest {

  @Test
  fun deliversNotifications() {
    val dispatcher = ChangeDispatcher()
    val disposable = Disposer.newDisposable()
    try {
      var delivered = 0
      dispatcher.add(disposable) { delivered++ }

      dispatcher.changed()
      dispatcher.changed()

      assertThat(delivered, equalTo(2))
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun deliversNotificationsToMultipleSubscribers() {
    val dispatcher = ChangeDispatcher()
    val disposable = Disposer.newDisposable()
    try {
      var delivered1 = 0
      var delivered2 = 0
      dispatcher.add(disposable) { delivered1++ }
      dispatcher.add(disposable) { delivered2++ }

      dispatcher.changed()
      Disposer.dispose(disposable)

      assertThat(delivered1, equalTo(1))
      assertThat(delivered2, equalTo(1))
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun dropsSubscribersOnDispose() {
    val dispatcher = ChangeDispatcher()
    val disposable1 = Disposer.newDisposable()
    try {
      var delivered1 = 0
      var delivered2 = 0
      val disposable2 = Disposer.newDisposable()
      try {
        dispatcher.add(disposable1) { delivered1++ }
        dispatcher.add(disposable2) { delivered2++ }

        dispatcher.changed()
      }
      finally {
        Disposer.dispose(disposable2)
      }
      dispatcher.changed()

      assertThat(delivered1, equalTo(2))
      assertThat(delivered2, equalTo(1))
    }
    finally {
      Disposer.dispose(disposable1)
    }
  }
}