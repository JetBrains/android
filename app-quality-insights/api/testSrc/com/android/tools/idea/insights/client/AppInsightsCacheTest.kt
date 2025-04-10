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

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.Blames
import com.android.tools.idea.insights.Caption
import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.EventData
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.ISSUE2
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.NOTE1
import com.android.tools.idea.insights.NOTE2
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.Stacktrace
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.TestConnection
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.ContextSharingState
import com.android.tools.idea.insights.ai.codecontext.Language
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Test

class AppInsightsCacheTest {
  private val now = Instant.now()
  private val connection = TestConnection("blah", "1234", "project12", "12")
  private val connection2 = TestConnection("appId2", "1234", "project1", "1")

  private val testEvent =
    Event(
      eventData =
        EventData(
          device = Device(manufacturer = "Google", model = "Pixel 4a"),
          operatingSystemInfo = OperatingSystemInfo(displayVersion = "12", "Android (12)"),
          eventTime = now.minus(Duration.ofDays(7)),
        ),
      stacktraceGroup =
        StacktraceGroup(
          exceptions =
            listOf(
              ExceptionStack(
                stacktrace =
                  Stacktrace(
                    caption =
                      Caption(
                        title = "Non-fatal Exception: retrofit2.HttpException",
                        subtitle = "HTTP 401 ",
                      ),
                    blames = Blames.BLAMED,
                    frames =
                      listOf(
                        Frame(
                          line = 23,
                          file = "ResponseWrapper.kt",
                          symbol =
                            "dev.firebase.appdistribution.api_service.ResponseWrapper\$Companion.build",
                          offset = 23,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.BLAMED,
                        ),
                        Frame(
                          line = 31,
                          file = "ResponseWrapper.kt",
                          symbol =
                            "dev.firebase.appdistribution.api_service.ResponseWrapper\$Companion.fetchOrError",
                          offset = 31,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.NOT_BLAMED,
                        ),
                      ),
                  ),
                type = "retrofit2.HttpException",
                exceptionMessage = "HTTP 401 ",
              )
            )
        ),
    )

  @Test
  fun `getTopIssues respects filters and returns top issues sorted by event count`() {
    val populateIssues = mutableListOf<AppInsightsIssue>()
    for (i in 0 until 5) {
      var issue =
        AppInsightsIssue(
          IssueDetails(
            IssueId("$i"),
            "Issue${i}",
            "com.google.crash.Crash",
            FailureType.FATAL,
            "Sample Event",
            "1.2.3",
            "1.2.3",
            6L,
            9L,
            5L,
            10L,
            setOf(SignalType.SIGNAL_UNSPECIFIED),
            "https://url.for-crash.com",
            0,
            emptyList(),
          ),
          testEvent,
        )

      issue =
        when (i) {
          0 ->
            issue.copy(
              issueDetails =
                issue.issueDetails.copy(eventsCount = 4, fatality = FailureType.NON_FATAL),
              sampleEvent =
                issue.sampleEvent.copy(
                  eventData =
                    issue.sampleEvent.eventData.copy(eventTime = now.minus(Duration.ofDays(31)))
                ),
            )
          1 ->
            issue.copy(
              issueDetails =
                issue.issueDetails.copy(
                  eventsCount = 22,
                  fatality = FailureType.FATAL,
                  signals = setOf(SignalType.SIGNAL_FRESH),
                ),
              sampleEvent =
                issue.sampleEvent.copy(
                  eventData =
                    issue.sampleEvent.eventData.copy(eventTime = now.minus(Duration.ofDays(5)))
                ),
            )
          2 ->
            issue.copy(
              issueDetails =
                issue.issueDetails.copy(eventsCount = 13, fatality = FailureType.NON_FATAL),
              sampleEvent =
                issue.sampleEvent.copy(
                  eventData =
                    issue.sampleEvent.eventData.copy(eventTime = now.minus(Duration.ofDays(3)))
                ),
            )
          3 ->
            issue.copy(
              issueDetails =
                issue.issueDetails.copy(
                  eventsCount = 44,
                  fatality = FailureType.NON_FATAL,
                  signals = setOf(SignalType.SIGNAL_REGRESSED),
                ),
              sampleEvent =
                issue.sampleEvent.copy(
                  eventData =
                    issue.sampleEvent.eventData.copy(eventTime = now.minus(Duration.ofDays(14)))
                ),
            )
          4 ->
            issue.copy(
              issueDetails =
                issue.issueDetails.copy(eventsCount = 67, fatality = FailureType.FATAL),
              sampleEvent =
                issue.sampleEvent.copy(
                  eventData =
                    issue.sampleEvent.eventData.copy(eventTime = now.minus(Duration.ofDays(89)))
                ),
            )
          else -> throw RuntimeException()
        }
      populateIssues.add(issue)
    }

    val cache = AppInsightsCacheImpl()
    assertThat(
        cache.getTopIssues(
          IssueRequest(
            connection,
            QueryFilters(
              Interval(now.minus(Duration.ofDays(60)), now),
              eventTypes = FailureType.values().toList(),
            ),
          )
        )
      )
      .isNull()

    cache.populateIssues(connection, populateIssues)

    // Check data range filter
    var topIssues =
      cache.getTopIssues(
        IssueRequest(
          connection,
          QueryFilters(
            Interval(now.minus(Duration.ofDays(60)), now),
            eventTypes = FailureType.values().toList(),
          ),
        )
      )!!
    assertThat(topIssues).hasSize(4)
    assertThat(topIssues.map { it.issueDetails.id })
      .containsExactlyElementsIn(listOf(IssueId("3"), IssueId("1"), IssueId("2"), IssueId("0")))
      .inOrder()
    assertThat(topIssues.map { it.issueDetails.eventsCount }).containsExactly(0L, 0L, 0L, 0L)
    assertThat(topIssues.map { it.issueDetails.impactedDevicesCount })
      .containsExactly(0L, 0L, 0L, 0L)

    // Check fatal even type
    topIssues =
      cache.getTopIssues(
        IssueRequest(
          connection,
          QueryFilters(
            Interval(now.minus(Duration.ofDays(90)), now),
            eventTypes = listOf(FailureType.FATAL),
          ),
        )
      )!!
    assertThat(topIssues).hasSize(2)
    assertThat(topIssues.map { it.issueDetails.id })
      .containsExactlyElementsIn(listOf(IssueId("4"), IssueId("1")))
      .inOrder()
    assertThat(topIssues.map { it.issueDetails.eventsCount }).containsExactly(0L, 0L)
    assertThat(topIssues.map { it.issueDetails.impactedDevicesCount }).containsExactly(0L, 0L)

    // Check non fatal event type
    topIssues =
      cache.getTopIssues(
        IssueRequest(
          connection,
          QueryFilters(
            Interval(now.minus(Duration.ofDays(30)), now),
            eventTypes = listOf(FailureType.NON_FATAL),
          ),
        )
      )!!
    assertThat(topIssues).hasSize(2)
    assertThat(topIssues.map { it.issueDetails.id })
      .containsExactlyElementsIn(listOf(IssueId("3"), IssueId("2")))
      .inOrder()
    assertThat(topIssues.map { it.issueDetails.eventsCount }).containsExactly(0L, 0L)
    assertThat(topIssues.map { it.issueDetails.impactedDevicesCount }).containsExactly(0L, 0L)

    // Check signal filter
    topIssues =
      cache.getTopIssues(
        IssueRequest(
          connection,
          QueryFilters(
            Interval(now.minus(Duration.ofDays(90)), now),
            eventTypes = FailureType.values().toList(),
            signal = SignalType.SIGNAL_FRESH,
          ),
        )
      )!!
    assertThat(topIssues).hasSize(1)
    assertThat(topIssues.map { it.issueDetails.id }).containsExactly(IssueId("1"))
  }

  @Test
  fun `add sample events to an existing cached issue, query returns correct event`() {
    val event = testEvent
    val recentEvent =
      testEvent.copy(
        eventData =
          testEvent.eventData.copy(
            eventTime = testEvent.eventData.eventTime.plus(Duration.ofDays(1))
          )
      )
    val evenMoreRecentEvent =
      testEvent.copy(
        eventData = testEvent.eventData.copy(eventTime = now.minus(Duration.ofDays(2)))
      )
    val issue =
      AppInsightsIssue(
        IssueDetails(
          IssueId("1"),
          "Issue1",
          "com.google.crash.Crash",
          FailureType.FATAL,
          "Sample Event",
          "1.2.3",
          "1.2.3",
          10L,
          12L,
          5L,
          10L,
          emptySet(),
          "https://url.for-crash.com",
          0,
          emptyList(),
        ),
        testEvent,
      )

    val cache = AppInsightsCacheImpl(5)
    cache.populateIssues(
      connection,
      listOf(
        issue.copy(sampleEvent = event),
        issue.copy(sampleEvent = recentEvent),
        issue.copy(sampleEvent = evenMoreRecentEvent),
      ),
    )

    // Assert the first event that satisfies the filter is returned.
    // In this case, it's the sample event with the most recent timestamp.
    val looseFilter =
      QueryFilters(
        Interval(now.minus(Duration.ofDays(30)), now.minus(Duration.ofDays(4))),
        eventTypes = FailureType.values().toList(),
        devices = setOf(testEvent.eventData.device),
        operatingSystems = setOf(testEvent.eventData.operatingSystemInfo),
      )
    var topIssues = cache.getTopIssues(IssueRequest(connection, looseFilter))
    assertThat(topIssues).hasSize(1)
    assertThat(topIssues!!.first().sampleEvent).isEqualTo(recentEvent)

    var cachedEvent = cache.getEvent(IssueRequest(connection, looseFilter), IssueId("1"))
    assertThat(cachedEvent).isEqualTo(recentEvent)

    // With a more strict filter, assert the last sample event
    // is returned.
    val strictFilter =
      QueryFilters(
        Interval(now.minus(Duration.ofDays(3)), now),
        eventTypes = FailureType.values().toList(),
        devices = setOf(testEvent.eventData.device),
        operatingSystems = setOf(testEvent.eventData.operatingSystemInfo),
      )
    topIssues = cache.getTopIssues(IssueRequest(connection, strictFilter))
    assertThat(topIssues).hasSize(1)
    assertThat(topIssues!!.first().sampleEvent).isEqualTo(evenMoreRecentEvent)

    cachedEvent = cache.getEvent(IssueRequest(connection, strictFilter), IssueId("1"))
    assertThat(cachedEvent).isEqualTo(evenMoreRecentEvent)
  }

  @Test
  fun `populate and get notes from cache`() {
    val issue =
      AppInsightsIssue(
        IssueDetails(
          IssueId("1"),
          "Issue1",
          "com.google.crash.Crash",
          FailureType.FATAL,
          "Sample Event",
          "1.2.3",
          "1.2.3",
          9L,
          9L,
          5L,
          10L,
          emptySet(),
          "https://url.for-crash.com",
          0,
          emptyList(),
        ),
        testEvent,
      )

    val cache = AppInsightsCacheImpl(5)
    cache.populateIssues(connection, listOf(issue))
    assertThat(cache.getNotes(connection, issue.issueDetails.id)).isNull()

    cache.populateNotes(connection, issue.issueDetails.id, listOf(NOTE1))
    with(cache.getNotes(connection, issue.issueDetails.id)!!) {
      assertThat(this).hasSize(1)
      assertThat(this.first()).isEqualTo(NOTE1)
    }

    cache.populateNotes(connection, issue.issueDetails.id, listOf(NOTE1, NOTE2))
    with(cache.getNotes(connection, issue.issueDetails.id)) {
      assertThat(this).hasSize(2)
      assertThat(this).containsExactly(NOTE1, NOTE2).inOrder()
    }
  }

  @Test
  fun `add and delete notes from cache`() {
    val issue = ISSUE1

    val cache = AppInsightsCacheImpl(5)
    cache.populateIssues(connection, listOf(issue))
    assertThat(cache.getNotes(connection, issue.issueDetails.id)).isNull()

    cache.addNote(connection, issue.issueDetails.id, NOTE1)
    with(cache.getNotes(connection, issue.issueDetails.id)!!) {
      assertThat(this).hasSize(1)
      assertThat(this.first()).isEqualTo(NOTE1)
    }

    cache.addNote(connection, issue.issueDetails.id, NOTE2)
    with(cache.getNotes(connection, issue.issueDetails.id)) {
      assertThat(this).hasSize(2)
      assertThat(this).containsExactly(NOTE2, NOTE1).inOrder()
    }

    cache.removeNote(connection, NOTE1.id)
    with(cache.getNotes(connection, issue.issueDetails.id)) {
      assertThat(this).hasSize(1)
      assertThat(this).containsExactly(NOTE2)
    }

    cache.removeNote(connection, NOTE2.id)
    assertThat(cache.getNotes(connection, issue.issueDetails.id)).isEmpty()
  }

  @Test
  fun `get issues based on issue Ids`() {
    val cache = AppInsightsCacheImpl(5)
    cache.populateIssues(connection, listOf(ISSUE1, ISSUE2))

    assertThat(cache.getIssues(connection, listOf(ISSUE2.id))).containsExactly(ISSUE2)
    assertThat(cache.getIssues(connection, listOf(ISSUE2.id, ISSUE1.id)))
      .containsExactly(ISSUE2, ISSUE1)
      .inOrder()
    assertThat(cache.getIssues(connection, emptyList())).isEmpty()
  }

  @Test
  fun `populate connections retains only current connections`() {
    val cache = AppInsightsCacheImpl()
    cache.populateIssues(connection, listOf(ISSUE1, ISSUE2))

    cache.populateConnections(listOf(connection2))

    assertThat(cache.getRecentConnections()).containsExactly(connection2)
  }

  @Test
  fun `getEvent filters on interval, device, and operating system`() {
    val issue =
      AppInsightsIssue(
        IssueDetails(
          IssueId("1"),
          "Issue1",
          "com.google.crash.Crash",
          FailureType.FATAL,
          "Sample Event",
          "1.2.3",
          "1.2.3",
          10L,
          12L,
          5L,
          10L,
          emptySet(),
          "https://url.for-crash.com",
          0,
          emptyList(),
        ),
        testEvent,
      )

    val cache = AppInsightsCacheImpl()
    cache.populateIssues(connection, listOf(issue))

    // Mismatch device
    assertThat(
        cache.getEvent(
          IssueRequest(
            connection,
            QueryFilters(
              interval = Interval(now.minus(Duration.ofDays(10)), now),
              devices = setOf(Device("Google", "Pixel 5"), Device("Samsung", "Galaxy S7")),
              operatingSystems = setOf(testEvent.eventData.operatingSystemInfo),
            ),
          ),
          issue.id,
        )
      )
      .isNull()

    // Mismatch OS
    assertThat(
        cache.getEvent(
          IssueRequest(
            connection,
            QueryFilters(
              interval = Interval(now.minus(Duration.ofDays(10)), now),
              devices = setOf(testEvent.eventData.device),
              operatingSystems =
                setOf(
                  OperatingSystemInfo("Android 11", "11"),
                  OperatingSystemInfo("Android 12", "12"),
                ),
            ),
          ),
          issue.id,
        )
      )
      .isNull()

    // Mismatch interval
    assertThat(
        cache.getEvent(
          IssueRequest(
            connection,
            QueryFilters(
              interval = Interval(now.minus(Duration.ofDays(5)), now),
              devices = setOf(testEvent.eventData.device),
              operatingSystems = setOf(testEvent.eventData.operatingSystemInfo),
            ),
          ),
          issue.id,
        )
      )
      .isNull()

    // Match
    assertThat(
        cache.getEvent(
          IssueRequest(
            connection,
            QueryFilters(
              interval = Interval(now.minus(Duration.ofDays(14)), now),
              devices =
                setOf(
                  Device("Google", "Pixel 5"),
                  Device("Samsung", "Galaxy S7"),
                  testEvent.eventData.device,
                ),
              operatingSystems =
                setOf(
                  OperatingSystemInfo("Android 11", "11"),
                  OperatingSystemInfo("Android 12", "12"),
                  testEvent.eventData.operatingSystemInfo,
                ),
            ),
          ),
          issue.id,
        )
      )
      .isEqualTo(testEvent)
  }

  @Test
  fun `get and put AI insights`() {
    val cache = AppInsightsCacheImpl()
    val context =
      CodeContextData(
        listOf(CodeContext("abc", "/path", "abc", Language.JAVA)),
        contextSharingState = ContextSharingState.ALLOWED,
      )
    cache.populateIssues(connection, listOf(ISSUE1))

    assertThat(cache.getAiInsight(connection, ISSUE1.id, null, CodeContextData.DISABLED)).isNull()

    cache.putAiInsight(connection, ISSUE1.id, null, DEFAULT_AI_INSIGHT)
    assertThat(cache.getAiInsight(connection, ISSUE1.id, null, CodeContextData.DISABLED))
      .isEqualTo(DEFAULT_AI_INSIGHT.copy(isCached = true))

    assertThat(cache.getAiInsight(connection, ISSUE1.id, "variant1", CodeContextData.DISABLED))
      .isNull()
    assertThat(cache.getAiInsight(connection, ISSUE1.id, null, context)).isNull()

    val newInsight = AiInsight("blah", codeContextData = context)
    cache.putAiInsight(connection, ISSUE1.id, null, newInsight)
    assertThat(cache.getAiInsight(connection, ISSUE1.id, null, context))
      .isEqualTo(newInsight.copy(isCached = true))

    cache.putAiInsight(connection, ISSUE1.id, "variant1", DEFAULT_AI_INSIGHT)
    assertThat(
        cache.getAiInsight(connection, ISSUE1.id, "variant1", DEFAULT_AI_INSIGHT.codeContextData)
      )
      .isEqualTo(DEFAULT_AI_INSIGHT.copy(isCached = true))
  }

  @Test
  fun `removeIssue removes the issue from cache`() {
    val cache = AppInsightsCacheImpl()
    cache.populateIssues(connection, listOf(ISSUE1))
    cache.addNote(connection, ISSUE1.issueDetails.id, NOTE1)
    cache.putAiInsight(connection, ISSUE1.id, null, DEFAULT_AI_INSIGHT)
    cache.putAiInsight(
      connection,
      ISSUE1.id,
      "variant2",
      AiInsight("blah", codeContextData = CodeContextData.DISABLED),
    )

    cache.removeIssue(connection, ISSUE1.id)
    assertThat(cache.getIssues(connection, listOf(ISSUE1.id))).isEmpty()
    assertThat(cache.getNotes(connection, ISSUE1.id)).isNull()
    assertThat(cache.getAiInsight(connection, ISSUE1.id, null, CodeContextData.DISABLED)).isNull()
    assertThat(cache.getAiInsight(connection, ISSUE1.id, "variant2", CodeContextData.DISABLED))
      .isNull()
  }
}
