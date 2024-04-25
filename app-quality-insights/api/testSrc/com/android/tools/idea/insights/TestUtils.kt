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

private val TestTimeIntervals =
  listOf(
    TimeIntervalFilter.ONE_DAY,
    TimeIntervalFilter.SEVEN_DAYS,
    TimeIntervalFilter.THIRTY_DAYS,
    TimeIntervalFilter.SIXTY_DAYS,
    TimeIntervalFilter.NINETY_DAYS,
  )
private val TestFailureTypes = listOf(FailureType.FATAL, FailureType.ANR, FailureType.NON_FATAL)

val TEST_FILTERS =
  Filters(
    MultiSelection.emptySelection(),
    Selection(TimeIntervalFilter.THIRTY_DAYS, TestTimeIntervals),
    MultiSelection(TestFailureTypes.toSet(), TestFailureTypes),
    MultiSelection.emptySelection(),
    MultiSelection.emptySelection(),
    selectionOf(SignalType.SIGNAL_UNSPECIFIED),
  )

val CONNECTION1 = TestConnection("app1", "app-id1", "project1", "123", "variant1", "app1")
val CONNECTION2 = TestConnection("app2", "app-id2", "project2", "456", "variant2", "app2")
val PLACEHOLDER_CONNECTION =
  TestConnection("app3", "app-id3", "project3", "789", "variant3", "app3", isConfigured = false)

val DEFAULT_FETCHED_VERSIONS = WithCount(10, Version("1", "1.0"))

val DEFAULT_FETCHED_DEVICES = WithCount(10, Device("Google", "Pixel 6"))
val DEFAULT_FETCHED_OSES = WithCount(10, OperatingSystemInfo("11", "Android (11)"))

val DEFAULT_FETCHED_PERMISSIONS = Permission.FULL

const val NOTE1_BODY = "I don't know how to reproduce this issue."
const val NOTE2_BODY = "Update: I managed to reproduce this issue."

val NOW = Instant.parse("2022-06-08T10:00:00Z")

const val REVISION_74081e5f = "74081e5f56a58788f3243fe8410c4b66e9c7c902"

val REPO_INFO =
  RepoInfo(
    vcsKey = VCS_CATEGORY.TEST_VCS,
    rootPath = PROJECT_ROOT_PREFIX,
    revision = REVISION_74081e5f,
  )

val SAMPLE_KEYS = listOf(CustomKey("CSRF_TOKEN", "screen_view"), CustomKey("RAY_ID", "abcdeefg"))
val SAMPLE_LOGS = listOf(Log(FAKE_10_HOURS_AGO, "fake_log", mapOf("key" to "value")))

val ISSUE1 =
  AppInsightsIssue(
    IssueDetails(
      IssueId("1234"),
      "Issue1",
      "com.google.crash.Crash",
      FailureType.FATAL,
      "projects/814265703514/apps/1:814265703514:android:1199bc7542be2276/events/6525E9C102DC000122D0184211881FA3_1867297287814199602",
      "1.2.3",
      "2.0.0",
      8L,
      13L,
      3000L,
      50000000L,
      setOf(SignalType.SIGNAL_FRESH),
      "https://url.for-crash.com",
      0,
      emptyList(),
    ),
    Event(
      name =
        "projects/814265703514/apps/1:814265703514:android:1199bc7542be2276/events/6525E9C102DC000122D0184211881FA3_1867297287814199602",
      eventData =
        EventData(
          device = Device(manufacturer = "Google", model = "Pixel 4a"),
          operatingSystemInfo =
            OperatingSystemInfo(displayVersion = "12", displayName = "Android (12)"),
          eventTime = FAKE_6_DAYS_AGO,
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
                          rawSymbol =
                            "dev.firebase.appdistribution.api_service.ResponseWrapper\$Companion.build(ResponseWrapper.kt:23)",
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
                          rawSymbol =
                            "dev.firebase.appdistribution.api_service.ResponseWrapper\$Companion.fetchOrError(ResponseWrapper.kt:31)",
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
                rawExceptionMessage = "retrofit2.HttpException: HTTP 401 ",
              )
            )
        ),
      appVcsInfo = AppVcsInfo.ValidInfo(listOf(REPO_INFO)),
      customKeys = SAMPLE_KEYS,
      logs = SAMPLE_LOGS,
    ),
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
                DataPoint("Other", 0.0),
              ),
          )
        ),
    ),
    IssueStats(
      topValue = "Android (12)",
      groups =
        listOf(
          StatsGroup("Android (12)", 50.0, breakdown = emptyList()),
          StatsGroup("Android (11)", 40.0, breakdown = emptyList()),
          StatsGroup("Android (10)", 10.0, breakdown = emptyList()),
          StatsGroup("Other", 0.0, breakdown = listOf(DataPoint("Other", 0.0))),
        ),
    ),
  )

val ISSUE2 =
  AppInsightsIssue(
    IssueDetails(
      IssueId("2345"),
      "Issue2",
      "com.google.crash.Crash",
      FailureType.NON_FATAL,
      "projects/814265703514/apps/1:814265703514:android:1199bc7542be2276/events/652731D4013400016ABAED130A6E5D76_1867295448974866474",
      "1.0.0",
      "2.0.0",
      12L,
      14L,
      10L,
      100L,
      setOf(SignalType.SIGNAL_REGRESSED),
      "https://url.for-crash.com/2",
      0,
      emptyList(),
    ),
    Event(
      name =
        "projects/814265703514/apps/1:814265703514:android:1199bc7542be2276/events/652731D4013400016ABAED130A6E5D76_1867295448974866474",
      eventData =
        EventData(
          device = Device(manufacturer = "Samsung", model = "Galaxy 9000"),
          operatingSystemInfo =
            OperatingSystemInfo(displayVersion = "10", displayName = "Android (12)"),
          eventTime = FAKE_25_DAYS_AGO,
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
                        subtitle = "Trust anchor for certification path not found.",
                      ),
                    blames = Blames.NOT_BLAMED,
                    frames =
                      listOf(
                        Frame(
                          line = 362,
                          file = "SSLUtils.java",
                          rawSymbol =
                            "com.android.org.conscrypt.SSLUtils.toSSLHandshakeException(SSLUtils.java:362)",
                          symbol = "com.android.org.conscrypt.SSLUtils.toSSLHandshakeException",
                          offset = 23,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.NOT_BLAMED,
                        ),
                        Frame(
                          line = 1134,
                          file = "ConscryptEngine.java",
                          rawSymbol =
                            "com.android.org.conscrypt.ConscryptEngine.convertException(ConscryptEngine.java:1134)",
                          symbol = "com.android.org.conscrypt.ConscryptEngine.convertException",
                          offset = 31,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.NOT_BLAMED,
                        ),
                      ),
                  ),
                type = "javax.net.ssl.SSLHandshakeException",
                exceptionMessage = "Trust anchor for certification path not found ",
                rawExceptionMessage =
                  "javax.net.ssl.SSLHandshakeException: Trust anchor for certification path not found ",
              ),
              ExceptionStack(
                stacktrace =
                  Stacktrace(
                    caption =
                      Caption(
                        title = "java.security.cert.CertPathValidatorException",
                        subtitle = "Trust anchor for certification path not found.",
                      ),
                    blames = Blames.BLAMED,
                    frames =
                      listOf(
                        Frame(
                          line = 677,
                          file = "TrustManagerImpl.java",
                          rawSymbol =
                            "com.android.org.conscrypt.TrustManagerImpl.verifyChain(TrustManagerImpl.java:677)",
                          symbol = "com.android.org.conscrypt.TrustManagerImpl.verifyChain",
                          offset = 23,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.NOT_BLAMED,
                        ),
                        Frame(
                          line = 320,
                          file = "RealConnection.java",
                          rawSymbol =
                            "okhttp3.internal.connection.RealConnection.connectTls(RealConnection.java:320)",
                          symbol = "okhttp3.internal.connection.RealConnection.connectTls",
                          offset = 31,
                          address = 0,
                          library = "dev.firebase.appdistribution.debug",
                          blame = Blames.BLAMED,
                        ),
                      ),
                  ),
                type = "javax.net.ssl.SSLHandshakeException",
                exceptionMessage = "Trust anchor for certification path not found ",
                rawExceptionMessage =
                  "Caused by: javax.net.ssl.SSLHandshakeException: Trust anchor for certification path not found ",
              ),
            )
        ),
      appVcsInfo = AppVcsInfo.ValidInfo(listOf(REPO_INFO)),
    ),
  )

val NOTE1 =
  Note(
    id = NoteId(issueId = ISSUE1.id, noteId = "note_id_1"),
    timestamp = NOW,
    author = "Jane@google.com",
    body = NOTE1_BODY,
    state = NoteState.CREATED,
  )

val NOTE2 =
  Note(
    id = NoteId(issueId = ISSUE1.id, noteId = "note_id_2"),
    timestamp = NOW.plusMillis(Duration.ofHours(2).toMillis()),
    author = "Jane@google.com",
    body = NOTE2_BODY,
    state = NoteState.CREATED,
  )

val ISSUE_VARIANT =
  IssueVariant(
    id = "variant1",
    sampleEvent = "sample_event_1",
    uri = "firebase.google.com",
    impactedDevicesCount = 1,
    eventsCount = 1,
  )

val ISSUE_VARIANT2 =
  IssueVariant(
    id = "variant2",
    sampleEvent = "sample_event_2",
    uri = "firebase.google.com",
    impactedDevicesCount = 1,
    eventsCount = 1,
  )

// Used for testing cached issues because their counts are zeroed out.
fun IssueDetails.zeroCounts() = copy(impactedDevicesCount = 0, eventsCount = 0)

// Used for testing cached issues because their counts are zeroed out.
fun AppInsightsIssue.zeroCounts() = copy(issueDetails = issueDetails.zeroCounts())
