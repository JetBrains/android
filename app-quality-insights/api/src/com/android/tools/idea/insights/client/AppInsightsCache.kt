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
package com.android.tools.idea.insights.client

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.IssueState
import com.android.tools.idea.insights.Note
import com.android.tools.idea.insights.NoteId
import com.android.tools.idea.insights.SignalType
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.serviceContainer.NonInjectable
import java.util.SortedSet
import java.util.TreeSet

/** Cache for App Insights data. */
interface AppInsightsCache {
  /**
   * Returns the top reported [Issue]s stored in the cache. Returns null if no issues are cached for
   * this [FirebaseConnection].
   */
  fun getTopIssues(request: IssueRequest): List<AppInsightsIssue>?

  /** Returns the issues specified by [issueIds]. */
  fun getIssues(connection: Connection, issueIds: List<IssueId>): List<AppInsightsIssue>

  /** Populates the cache with recently fetched [Issue]s. */
  fun populateIssues(connection: Connection, issues: List<AppInsightsIssue>)

  /**
   * Returns an event that belongs to [issueId] and matches the filtering criteria. Null if such an
   * event does not exist.
   */
  fun getEvent(issueRequest: IssueRequest, issueId: IssueId): Event?

  /** Get cached notes based on [issueId]. Returns null if notes are not cached for this issue. */
  fun getNotes(connection: Connection, issueId: IssueId): List<Note>?

  /** Populate and overwrite the list of notes belonging to [issueId]. */
  fun populateNotes(connection: Connection, issueId: IssueId, notes: List<Note>)

  /** Adds a [Note] to the cache belonging to [issueId]. */
  fun addNote(connection: Connection, issueId: IssueId, note: Note)

  /** Removes the note matching [NoteId] from the cache. */
  fun removeNote(connection: Connection, noteId: NoteId)
}

private const val MAXIMUM_ISSUES_CACHE_SIZE = 1000L
private const val MAXIMUM_FIREBASE_CONNECTIONS_CACHE_SIZE = 20L

private data class IssueDetailsValue(
  val issueDetails: IssueDetails,
  val sampleEvents: SortedSet<Event>,
  val state: IssueState
) {
  fun toIssue() = AppInsightsIssue(issueDetails, sampleEvents.first(), state)
}

private data class CacheValue(val issueDetails: IssueDetailsValue?, val notes: List<Note>?)

// TODO(b/249510375): persist cache
/** Cache for storing issues used in offline and online mode. */
class AppInsightsCacheImpl @NonInjectable constructor(private val maxIssuesCount: Int) :
  AppInsightsCache {

  constructor() : this(50)

  private val compositeIssuesCache: Cache<Connection, Cache<IssueId, CacheValue>> =
    createNew(MAXIMUM_FIREBASE_CONNECTIONS_CACHE_SIZE)

  // TODO(b/249297282): Fetch top issues for "default" filter in the background
  override fun getTopIssues(request: IssueRequest): List<AppInsightsIssue>? {
    if (!StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) return null
    val allIssues =
      compositeIssuesCache.getIfPresent(request.connection)?.asMap()?.values ?: return null
    return allIssues
      .asSequence()
      .mapNotNull {
        val cachedIssue = it.issueDetails ?: return@mapNotNull null
        val matchingEvent =
          cachedIssue.sampleEvents.firstOrNull { event ->
            event.matchInterval(request.filters.interval)
          }
            ?: return@mapNotNull null
        if (
          cachedIssue.issueDetails.matchErrorType(request.filters.eventTypes) &&
            cachedIssue.issueDetails.matchSignalType(request.filters.signal)
        ) {
          AppInsightsIssue(cachedIssue.issueDetails, matchingEvent, cachedIssue.state)
        } else {
          null
        }
      }
      .sortedWith(
        // Although we don't show actual occurrence counts in offline mode as they might not be
        // accurate, in order to present issues in approximately the right order, we maintain
        // the event counts here.
        //
        // Also, although issues fetched from server are not in the deterministic order when it
        // comes to multiple issues with the same event count#, we still force some determinism
        // here by extra sorting on id.
        compareBy({ -(it.issueDetails.eventsCount) }, { it.issueDetails.id.value })
      )
      .map {
        it.copy(issueDetails = it.issueDetails.copy(eventsCount = 0, impactedDevicesCount = 0))
      }
      .take(maxIssuesCount)
      .toList()
  }

  override fun getIssues(connection: Connection, issueIds: List<IssueId>): List<AppInsightsIssue> {
    val cache = compositeIssuesCache.getIfPresent(connection)?.asMap() ?: return emptyList()
    return issueIds.mapNotNull {
      cache[it]?.let { cacheValue -> cacheValue.issueDetails?.toIssue() }
    }
  }

  override fun populateIssues(connection: Connection, issues: List<AppInsightsIssue>) {
    if (!StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) return
    val issuesCache = getOrCreateIssuesCache(connection).asMap()
    issues.forEach { newIssue ->
      issuesCache.compute(newIssue.issueDetails.id) { _, oldValue ->
        CacheValue(oldValue?.issueDetails.reconcileWith(newIssue), oldValue?.notes)
      }
    }
  }

  override fun getEvent(issueRequest: IssueRequest, issueId: IssueId): Event? {
    if (!StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) return null
    val cachedEvents =
      compositeIssuesCache
        .getIfPresent(issueRequest.connection)
        ?.getIfPresent(issueId)
        ?.issueDetails
        ?.sampleEvents
        ?: return null
    return cachedEvents.firstOrNull { it.matchInterval(issueRequest.filters.interval) }
  }

  override fun getNotes(connection: Connection, issueId: IssueId): List<Note>? {
    if (!StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) return null
    return compositeIssuesCache.getIfPresent(connection)?.getIfPresent(issueId)?.notes
  }

  override fun populateNotes(connection: Connection, issueId: IssueId, notes: List<Note>) {
    if (!StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED.get()) return
    val issuesCache = getOrCreateIssuesCache(connection).asMap()

    issuesCache.compute(issueId) { _, oldValue ->
      checkNotNull(oldValue) { "Notes are always populated after the issues." }
      oldValue.copy(notes = notes)
    }
  }

  override fun addNote(connection: Connection, issueId: IssueId, note: Note) {
    val issuesCache = getOrCreateIssuesCache(connection).asMap()
    issuesCache.compute(issueId) { _, oldValue ->
      checkNotNull(oldValue) { "Issue should exist for this note by this time." }
      oldValue.copy(notes = oldValue.notes?.insertByDecreasingTimestamp(note) ?: listOf(note))
    }
  }

  override fun removeNote(connection: Connection, noteId: NoteId) {
    val issuesCache = getOrCreateIssuesCache(connection).asMap()
    issuesCache.compute(noteId.issueId) { _, oldValue ->
      checkNotNull(oldValue) { "Issue should exist for this note by this time." }
      checkNotNull(oldValue.notes) { "Notes should already be populated." }
      oldValue.copy(notes = oldValue.notes.filterNot { it.id.noteId == noteId.noteId })
    }
  }

  private fun IssueDetailsValue?.reconcileWith(issue: AppInsightsIssue): IssueDetailsValue {
    if (this == null) {
      return issue.toNewIssueDetailsValue()
    }
    return IssueDetailsValue(
      // The events and users count are not used for display purposes.
      // However, we do use them for sorting, so take the one with the
      // greatest count.
      if (issue.issueDetails.eventsCount < issueDetails.eventsCount) {
        issue.issueDetails.copy(
          eventsCount = issueDetails.eventsCount,
          impactedDevicesCount = issueDetails.impactedDevicesCount
        )
      } else issue.issueDetails,
      sampleEvents.apply { add(issue.sampleEvent) },
      issue.state
    )
  }

  private val comparator =
    Comparator<Event> { e1, e2 -> e2.eventData.eventTime.compareTo(e1.eventData.eventTime) }

  // The list is sorted in decreasing order of timestamp.
  private fun List<Note>.insertByDecreasingTimestamp(newNote: Note): List<Note> {
    val result = this.toMutableList()
    forEachIndexed { index, note ->
      if (note.timestamp.isBefore(newNote.timestamp)) {
        result.add(index, newNote)
        return result
      }
    }
    result.add(newNote)
    return result
  }

  private fun AppInsightsIssue.toNewIssueDetailsValue(): IssueDetailsValue =
    IssueDetailsValue(issueDetails, TreeSet(comparator).apply { add(sampleEvent) }, state)

  private fun Event.matchInterval(interval: Interval): Boolean {
    val time = eventData.eventTime
    return time.isAfter(interval.startTime) && time.isBefore(interval.endTime)
  }

  private fun IssueDetails.matchErrorType(eventTypes: List<FailureType>): Boolean {
    return fatality in eventTypes
  }

  private fun IssueDetails.matchSignalType(signal: SignalType) =
    signal in signals || signal == SignalType.SIGNAL_UNSPECIFIED

  private fun getOrCreateIssuesCache(firebaseConnection: Connection): Cache<IssueId, CacheValue> =
    compositeIssuesCache.get(firebaseConnection) { createNew(MAXIMUM_ISSUES_CACHE_SIZE) }

  private fun <K, V> createNew(maximumSize: Long): Cache<K, V> {
    // TODO(peterx): consider adding back weak keys support, which does not
    // work with kotlin String keys.
    return Caffeine.newBuilder().maximumSize(maximumSize).build()
  }
}
