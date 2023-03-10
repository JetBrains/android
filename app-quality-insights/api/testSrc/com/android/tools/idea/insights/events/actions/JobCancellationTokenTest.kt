/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights.events.actions

import com.android.tools.idea.insights.NOTE1
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class JobCancellationTokenTest {
  @Test
  fun `cancelling a completed job always succeeds`() = runBlocking {
    val job = launch(start = CoroutineStart.LAZY) {}

    val token = job.toToken(Action.AddNote(NOTE1))

    assertThat(job.isCompleted).isFalse()
    assertThat(token.cancel(Action.Refresh)).isSameAs(token)
    job.join()
    assertThat(token.cancel(Action.Refresh)).isNull()
    assertThat(job.isCancelled).isFalse()
  }

  @Test
  fun `when token is cancelled, it cancels the underlying Job`() = runBlocking {
    val job = launch(start = CoroutineStart.LAZY) {}

    val token = job.toToken(Action.Refresh)

    assertThat(job.isCompleted).isFalse()
    assertThat(token.cancel(Action.CancelFetches)).isNull()
    job.join()
    assertThat(job.isCancelled).isTrue()
  }
}
