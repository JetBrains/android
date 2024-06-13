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
package com.android.tools.idea.vitals

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.Blames
import com.android.tools.idea.insights.Caption
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.EventData
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.FAKE_25_DAYS_AGO
import com.android.tools.idea.insights.FAKE_6_DAYS_AGO
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Stacktrace
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.vitals.datamodel.VitalsConnection

val TEST_CONNECTION_1 = VitalsConnection("appId1", "Test App 1", true)
val TEST_CONNECTION_2 = VitalsConnection("appId2", "Test App 2", true)
val TEST_CONNECTION_3 = VitalsConnection("appId3", "Test App 3", false)

val MOST_AFFECTED_DEVICE = Device("Google", "Pixel 4a")
val TEST_ISSUE1 =
  AppInsightsIssue(
    IssueDetails(
      IssueId("1234"),
      "Issue1",
      "com.google.crash.Crash1",
      FailureType.FATAL,
      "",
      "12",
      "32",
      8L,
      13L,
      5L,
      50L,
      emptySet(),
      "https://url.for-crash.com",
      0,
      emptyList(),
    ),
    Event(
      name = "apps/com.labpixies.flood/MmRlYzYzZGRlODkyMTM4N2RkZTAxNTI0YWZlYzE4Mjc=",
      eventData =
        EventData(
          device = MOST_AFFECTED_DEVICE,
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
    ),
  )

val TEST_ISSUE2 =
  AppInsightsIssue(
    IssueDetails(
      IssueId("2345"),
      "Issue2",
      "com.google.crash.Crash2",
      FailureType.FATAL,
      "",
      "43",
      "55",
      12L,
      14L,
      10L,
      100L,
      emptySet(),
      "https://url.for-crash.com/2",
      0,
      emptyList(),
    ),
    Event(
      name = "apps/com.labpixies.flood/MzU4ZDdkMDc3YTljMzRlNWQ4MGY4MjQxYTFlMzdhODM=",
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
    ),
  )
