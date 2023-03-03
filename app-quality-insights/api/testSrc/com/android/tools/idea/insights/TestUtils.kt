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

typealias TestAppInsight = AppInsight<TestIssue>

data class TestIssue(override val issueDetails: IssueDetails, override val sampleEvent: Event) :
  Issue

data class TestIssueDetails(
  override val id: IssueId,
  override val title: String,
  override val subtitle: String,
  override val fatality: FailureType,
  override val sampleEvent: String,
  override val firstSeenVersion: String,
  override val lastSeenVersion: String,
  override val impactedDevicesCount: Long,
  override val eventsCount: Long,
  override val uri: String,
) : IssueDetails

val TEST_ISSUE1 =
  TestIssue(
    TestIssueDetails(
      IssueId("1234"),
      "Issue1",
      "com.google.crash.Crash",
      FailureType.FATAL,
      "Sample Event",
      "1.2.3",
      "1.2.3",
      5L,
      50L,
      "https://url.for-crash.com"
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
val TEST_ISSUE1_DETAILS =
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

val TEST_ISSUE2 =
  TestIssue(
    TestIssueDetails(
      IssueId("2345"),
      "Issue2",
      "com.google.crash.Crash",
      FailureType.ANR,
      "Sample Event 2",
      "1.0.0",
      "2.0.0",
      10L,
      100L,
      "https://url.for-crash.com/2"
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
