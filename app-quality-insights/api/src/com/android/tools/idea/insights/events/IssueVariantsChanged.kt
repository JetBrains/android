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
package com.android.tools.idea.insights.events

import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.InsightsProviderKey
import com.android.tools.idea.insights.IssueVariant
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.events.actions.Action

data class IssueVariantsChanged(val variants: LoadingState.Done<List<IssueVariant>>) : ChangeEvent {
  override fun transition(
    state: AppInsightsState,
    tracker: AppInsightsTracker,
    key: InsightsProviderKey,
  ) =
    StateTransition(
      state.copy(
        currentIssueVariants =
          if (variants is LoadingState.Failure) variants
          else {
            variants.map { Selection(null, it) }
          }
      ),
      if (state.selectedIssue != null) Action.FetchInsight(state.selectedIssue!!.id)
      else Action.NONE,
    )
}
