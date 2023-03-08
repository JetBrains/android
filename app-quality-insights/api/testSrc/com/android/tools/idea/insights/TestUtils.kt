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
package com.android.tools.idea.insights

import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.delay

suspend fun waitForCondition(timeoutMs: Long = 500, condition: () -> Boolean) {
  val waitIntervalMs = 50L
  var index = 0

  while (index * waitIntervalMs < timeoutMs) {
    if (condition()) return
    index++
    delay(waitIntervalMs)
  }
  throw TimeoutException()
}

val CONNECTION1 = Connection("app1", "app-id1", "project1", "123")
val CONNECTION2 = Connection("app2", "app-id2", "project2", "456")
val VARIANT1 = VariantConnection("app1", "variant1", CONNECTION1)
val VARIANT2 = VariantConnection("app2", "variant2", CONNECTION2)
val PLACEHOLDER_CONNECTION = VariantConnection("app3", "", null)

val DEFAULT_FETCHED_VERSIONS = WithCount(10, Version("1", "1.0"))

val DEFAULT_FETCHED_DEVICES = WithCount(10, Device("Google", "Pixel 6"))
val DEFAULT_FETCHED_OSES = WithCount(10, OperatingSystemInfo("11", "Android (11)"))

val DEFAULT_FETCHED_PERMISSIONS = Permission.FULL

const val NOTE1_BODY = "I don't know how to reproduce this issue."
const val NOTE2_BODY = "Update: I managed to reproduce this issue."

val NOW = Instant.parse("2022-06-08T10:00:00Z")

val ISSUE1 =
  AppInsightsIssue(
    IssueDetails(
      IssueId("1234"),
      "Issue1",
      "com.google.crash.Crash",
      FailureType.FATAL,
      "Sample Event",
      "1.2.3",
      "1.2.3",
      5L,
      50L,
      setOf(SignalType.SIGNAL_FRESH),
      "https://url.for-crash.com",
      0
    ),
    Event(
      eventData =
        EventData(
          device = Device(manufacturer = "Google", model = "Pixel 4a"),
          operatingSystemInfo =
            OperatingSystemInfo(displayVersion = "12", displayName = "Android (12)"),
          eventTime = FAKE_6_DAYS_AGO
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
                        subtitle = "HTTP 401 "
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
                          blame = Blames.BLAMED
                        ),
                        Frame(
                          line = 31,
                          file = "ResponseWrapper.kt",
                          symbol =
                            "dev.firebase.appdistribution.api_service.ResponseWrapper\$Companion.fetchOrError",
                          offset = 31,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.NOT_BLAMED
                        )
                      )
                  ),
                type = "retrofit2.HttpException",
                exceptionMessage = "HTTP 401 "
              )
            )
        )
    )
  )
val ISSUE1_DETAILS =
  DetailedIssueStats(
    IssueStats(
      topValue = "Pixel 4a",
      groups =
        listOf(
          StatsGroup(
            groupName = "Google",
            percentage = 50.0,
            breakdown =
              listOf(
                DataPoint("Pixel 4a", 40.0),
                DataPoint("Pixel 5", 10.0),
                DataPoint("Other", 0.0)
              )
          )
        )
    ),
    IssueStats(
      topValue = "Android (12)",
      groups =
        listOf(
          StatsGroup("Android (12)", 50.0, breakdown = emptyList()),
          StatsGroup("Android (11)", 40.0, breakdown = emptyList()),
          StatsGroup("Android (10)", 10.0, breakdown = emptyList()),
          StatsGroup("Other", 0.0, breakdown = listOf(DataPoint("Other", 0.0)))
        )
    )
  )

val ISSUE2 =
  AppInsightsIssue(
    IssueDetails(
      IssueId("2345"),
      "Issue2",
      "com.google.crash.Crash",
      FailureType.NON_FATAL,
      "Sample Event 2",
      "1.0.0",
      "2.0.0",
      10L,
      100L,
      setOf(SignalType.SIGNAL_REGRESSED),
      "https://url.for-crash.com/2",
      0
    ),
    Event(
      eventData =
        EventData(
          device = Device(manufacturer = "Samsung", model = "Galaxy 9000"),
          operatingSystemInfo =
            OperatingSystemInfo(displayVersion = "10", displayName = "Android (12)"),
          eventTime = FAKE_25_DAYS_AGO
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
                        title = "javax.net.ssl.SSLHandshakeException",
                        subtitle = "Trust anchor for certification path not found."
                      ),
                    blames = Blames.NOT_BLAMED,
                    frames =
                      listOf(
                        Frame(
                          line = 362,
                          file = "SSLUtils.java",
                          symbol = "com.android.org.conscrypt.SSLUtils.toSSLHandshakeException",
                          offset = 23,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.NOT_BLAMED
                        ),
                        Frame(
                          line = 1134,
                          file = "ConscryptEngine.java",
                          symbol = "com.android.org.conscrypt.ConscryptEngine.convertException",
                          offset = 31,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.NOT_BLAMED
                        )
                      )
                  ),
                type = "javax.net.ssl.SSLHandshakeException",
                exceptionMessage = "Trust anchor for certification path not found "
              ),
              ExceptionStack(
                stacktrace =
                  Stacktrace(
                    caption =
                      Caption(
                        title = "java.security.cert.CertPathValidatorException",
                        subtitle = "Trust anchor for certification path not found."
                      ),
                    blames = Blames.BLAMED,
                    frames =
                      listOf(
                        Frame(
                          line = 677,
                          file = "TrustManagerImpl.java",
                          symbol = "com.android.org.conscrypt.TrustManagerImpl.verifyChain",
                          offset = 23,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.NOT_BLAMED
                        ),
                        Frame(
                          line = 320,
                          file = "RealConnection.java",
                          symbol = "okhttp3.internal.connection.RealConnection.connectTls",
                          offset = 31,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.BLAMED
                        )
                      )
                  ),
                type = "javax.net.ssl.SSLHandshakeException",
                exceptionMessage = "Trust anchor for certification path not found "
              ),
            )
        )
    )
  )

val NOTE1 =
  Note(
    id = NoteId(issueId = ISSUE1.id, noteId = "note_id_1"),
    timestamp = NOW,
    author = "Jane@google.com",
    body = NOTE1_BODY,
    state = NoteState.CREATED
  )

val NOTE2 =
  Note(
    id = NoteId(issueId = ISSUE1.id, noteId = "note_id_2"),
    timestamp = NOW.plusMillis(Duration.ofHours(2).toMillis()),
    author = "Jane@google.com",
    body = NOTE2_BODY,
    state = NoteState.CREATED
  )
