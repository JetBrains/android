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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.insights.IssueId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import org.junit.Test

class CancellationTokenTest {
  @Test
  fun `tokens composition is consistent with action composition`() {
    val mockJob = mock<Job>()
    val fetchDetails = Action.FetchDetails(IssueId("id"))
    val compositeToken = mockJob.toToken(Action.Refresh) and mockJob.toToken(fetchDetails)
    assertThat(compositeToken.action).isEqualTo(Action.Refresh and fetchDetails)
  }

  @Test
  fun `composing token with null returns the same token`() {
    val token = mock<Job>().toToken(Action.Refresh)
    assertThat(token.and(null)).isSameAs(token)
  }

  @Test
  fun `token cancellation is consistent with actions cancellation`() {
    val issue = IssueId("id")
    val issue2 = IssueId("id2")
    val closeIssue = Action.CloseIssue(issue)
    val closeIssue2 = Action.CloseIssue(issue2)
    val openIssue = Action.OpenIssue(issue)
    val mockToken1 = mock<Job>().toToken(Action.Refresh)
    val mockToken2 = mock<Job>().toToken(closeIssue)
    val mockToken3 = mock<Job>().toToken(closeIssue2)

    val result = (mockToken1 and mockToken2 and mockToken3).cancel(openIssue)
    assertThat(result?.action).isEqualTo(Action.Refresh and closeIssue2)
  }
}
