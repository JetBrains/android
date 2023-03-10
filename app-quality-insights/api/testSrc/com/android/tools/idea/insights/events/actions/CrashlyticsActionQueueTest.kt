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
import com.android.tools.idea.insights.NOTE2
import com.android.tools.idea.insights.NoteState
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Test

class CrashlyticsActionQueueTest {

  @Test
  fun `apply pending note requests`() {
    val queue = AppInsightsActionQueueImpl(ConcurrentLinkedQueue())

    queue.offer(Action.AddNote(NOTE2))
    queue.offer(Action.DeleteNote(NOTE1.id))

    val currentNotes = listOf(NOTE1)
    assertThat(queue.applyPendingNoteRequests(currentNotes, NOTE1.id.issueId))
      .isEqualTo(listOf(NOTE2, NOTE1.copy(state = NoteState.DELETING)))
  }
}
