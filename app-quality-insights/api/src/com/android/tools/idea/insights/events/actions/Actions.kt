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

import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.Note
import com.android.tools.idea.insights.NoteId
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent

/**
 * Describes all the actions available in App Insights.
 *
 * Notably, actions are just values and have no inherent semantics, instead they need to be
 * interpreted, see [ActionDispatcher].
 */
sealed class Action {
  /** Denotes this action can be queued when AQI is in offline mode. */
  open val supportsOfflineQueueing: Boolean = false

  /** Denotes a single(non-composite) action. */
  sealed class Single : Action()

  /** An action that is associated with a single issue. */
  sealed class IssueAction : Single() {
    abstract val id: IssueId
  }

  /**
   * Conditionally cancels the [Action], returns null if cancellation succeeds.
   *
   * Otherwise, the returned action can be either returned unmodified or partially cancelled in the
   * case where some actions of a [composite][Multiple] action are cancelled.
   */
  fun maybeCancel(other: Action): Action? =
    when (other) {
      is Single -> maybeDoCancel(listOf(other))
      is Multiple -> maybeDoCancel(other.actions)
    }
  protected abstract fun maybeDoCancel(reasons: List<Single>): Action?

  /** Refresh all data in App Insights. */
  object Refresh : Single() {
    override fun maybeDoCancel(reasons: List<Single>) = cancelIf(reasons, ::shouldCancelFetch)

    override fun toString(): String = "Refresh"
  }

  /**
   * Fetch data.
   *
   * Notes: it's different from [Refresh] in that it does not indicate an explicit intent to make a
   * "remote" fetch, cache can be used instead.
   */
  data class Fetch(
    val reason: AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.FetchSource
  ) : Single() {

    override fun maybeDoCancel(reasons: List<Single>) = cancelIf(reasons, ::shouldCancelFetch)
  }

  /** Fetch issue details. */
  data class FetchDetails(override val id: IssueId) : IssueAction() {
    override fun maybeDoCancel(reasons: List<Single>) =
      cancelIf(reasons) { it is FetchDetails || shouldCancelFetch(it) }
  }

  /** Open an issue. */
  data class OpenIssue(override val id: IssueId) : IssueAction() {
    override fun maybeDoCancel(reasons: List<Single>) =
      cancelIf(reasons) {
        when (it) {
          is CloseIssue -> id == it.id
          is OpenIssue -> id == it.id
          else -> false
        }
      }
  }

  /** Close an issue. */
  data class CloseIssue(override val id: IssueId) : IssueAction() {
    override fun maybeDoCancel(reasons: List<Single>) =
      cancelIf(reasons) {
        when (it) {
          is CloseIssue -> id == it.id
          is OpenIssue -> id == it.id
          else -> false
        }
      }
  }

  /** Fetch notes for an issue. */
  data class FetchNotes(override val id: IssueId) : IssueAction() {
    override fun maybeDoCancel(reasons: List<Single>) =
      cancelIf(reasons) { it is FetchNotes || shouldCancelFetch(it) }
  }

  /** Add a note to an issue. */
  data class AddNote(val note: Note) : IssueAction() {
    override val id = note.id.issueId
    override val supportsOfflineQueueing = true
    override fun maybeDoCancel(reasons: List<Single>) = this
  }

  /** Delete note. */
  data class DeleteNote(val noteId: NoteId) : IssueAction() {
    override val id = noteId.issueId
    override val supportsOfflineQueueing = true
    override fun maybeDoCancel(reasons: List<Single>) =
      cancelIf(reasons) { it is DeleteNote && it.noteId == noteId }
  }

  /** Retry pending requests. Currently only note requests are retried. */
  object RetryPendingActions : Single() {
    override fun maybeDoCancel(reasons: List<Single>) =
      cancelIf(reasons) { it is RetryPendingActions }
  }

  /** Cancel all outstanding fetches. */
  object CancelFetches : Single() {
    override fun maybeDoCancel(reasons: List<Single>) = cancelIf(reasons, ::shouldCancelFetch)

    override fun toString(): String = "CancelFetches"
  }

  /**
   * Composite action that contains 0+ actions.
   *
   * Note: while composition order is preserved for deduplication purposes(see [ActionDispatcher]),
   * execution is not guaranteed to happen in that order and can be done concurrently.
   */
  class Multiple
  @Deprecated(
    "This is an internal constructor. Use and() to compose actions",
    level = DeprecationLevel.ERROR
  )
  internal constructor(val actions: List<Single>) : Action() {

    override fun toString(): String = "Multiple(${actions.joinToString()}"

    override fun maybeDoCancel(reasons: List<Single>): Action? {
      val notCancelled =
        actions.flatMap {
          when (val a = it.maybeDoCancel(reasons)) {
            null -> listOf()
            is Single -> listOf(a)
            is Multiple -> a.actions
          }
        }
      @Suppress("DEPRECATION_ERROR")
      return when {
        notCancelled.isEmpty() -> null
        notCancelled.size == 1 -> notCancelled[0]
        else -> Multiple(notCancelled)
      }
    }

    override fun equals(other: Any?): Boolean = (other as? Multiple)?.actions == actions
    override fun hashCode(): Int = actions.hashCode()
  }

  /**
   * Allows composing actions.
   *
   * Note: this method "flattens" actions, i.e. there are not going to be any nested [Multiple]s.
   */
  infix fun and(other: Action): Action {
    val notCancelled = maybeCancel(other) ?: return other
    return when (notCancelled) {
      is Multiple ->
        @Suppress("DEPRECATION_ERROR")
        when (other) {
          is Multiple -> Multiple(notCancelled.actions + other.actions)
          is Single -> Multiple(notCancelled.actions + other)
        }
      is Single ->
        @Suppress("DEPRECATION_ERROR")
        when (other) {
          is Multiple -> Multiple(listOf(notCancelled) + other.actions)
          is Single -> Multiple(listOf(notCancelled, other))
        }
    }
  }

  val isNoop: Boolean
    get() = this is Multiple && this.actions.isEmpty()

  companion object {
    /** Action that does not do anything */
    @Suppress("DEPRECATION_ERROR") val NONE: Action = Multiple(listOf())

    private fun shouldCancelFetch(reason: Action): Boolean =
      reason is Fetch || reason is Refresh || reason is CancelFetches

    private fun Action.cancelIf(
      reasons: Iterable<Single>,
      predicate: (Action) -> Boolean
    ): Action? = if (reasons.any(predicate)) null else this
  }
}
