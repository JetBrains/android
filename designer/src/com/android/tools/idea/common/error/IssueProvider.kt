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
package com.android.tools.idea.common.error

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableCollection
import com.intellij.util.messages.Topic

abstract class IssueProvider {

  // Unfortunately we have to use Runnable here for java interop
  @VisibleForTesting val listeners = mutableListOf<Runnable>()

  abstract fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>)

  fun addListener(listener: Runnable) = listeners.add(listener)

  fun removeListener(listener: Runnable) = listeners.remove(listener)

  fun notifyModified() {
    listeners.forEach { it.run() }
  }
}

fun interface IssueProviderListener {

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<IssueProviderListener> =
      Topic(
        IssueProviderListener::class.java.name,
        IssueProviderListener::class.java,
        Topic.BroadcastDirection.NONE
      )

    @Topic.ProjectLevel
    @JvmField
    val UI_CHECK: Topic<IssueProviderListener> =
      Topic("UI Check", IssueProviderListener::class.java, Topic.BroadcastDirection.NONE)
  }

  /** Updates the list of issues coming from the given source. */
  fun issueUpdated(source: Any, issues: List<Issue>)
}
