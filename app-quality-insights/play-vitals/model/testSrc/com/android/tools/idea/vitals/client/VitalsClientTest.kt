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
package com.android.tools.idea.vitals.client

import com.android.testutils.time.FakeClock
import com.android.tools.idea.insights.Caption
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.ConnectionMode
import com.android.tools.idea.insights.DEFAULT_AI_INSIGHT
import com.android.tools.idea.insights.DataPoint
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.DeviceType
import com.android.tools.idea.insights.Event
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.FAKE_50_DAYS_AGO
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.FakeTimeProvider
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.ISSUE1
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Permission
import com.android.tools.idea.insights.PlayTrack
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.Stacktrace
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.StatsGroup
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.ai.AiInsight
import com.android.tools.idea.insights.ai.codecontext.CodeContext
import com.android.tools.idea.insights.ai.codecontext.CodeContextData
import com.android.tools.idea.insights.ai.codecontext.Language
import com.android.tools.idea.insights.client.AiInsightClient
import com.android.tools.idea.insights.client.AppConnection
import com.android.tools.idea.insights.client.AppInsightsCacheImpl
import com.android.tools.idea.insights.client.FakeAiInsightClient
import com.android.tools.idea.insights.client.Interval
import com.android.tools.idea.insights.client.IssueRequest
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.insights.client.QueryFilters
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.zeroCounts
import com.android.tools.idea.protobuf.Message
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.vitals.TEST_CONNECTION_1
import com.android.tools.idea.vitals.TEST_ISSUE1
import com.android.tools.idea.vitals.TEST_ISSUE2
import com.android.tools.idea.vitals.client.grpc.FakeErrorsService
import com.android.tools.idea.vitals.client.grpc.FakeReportingService
import com.android.tools.idea.vitals.client.grpc.FakeVitalsDatabase
import com.android.tools.idea.vitals.client.grpc.TestVitalsGrpcClient
import com.android.tools.idea.vitals.client.grpc.VitalsGrpcClientImpl
import com.android.tools.idea.vitals.client.grpc.createIssueRequest
import com.android.tools.idea.vitals.datamodel.DimensionType
import com.android.tools.idea.vitals.datamodel.DimensionsAndMetrics
import com.android.tools.idea.vitals.datamodel.Freshness
import com.android.tools.idea.vitals.datamodel.MetricType
import com.android.tools.idea.vitals.datamodel.TimeGranularity
import com.google.android.studio.gemini.CodeSnippet
import com.google.android.studio.gemini.GeminiInsightsRequest
import com.google.common.io.BaseEncoding
import com.google.common.truth.Truth.assertThat
import com.google.type.DateTime
import com.intellij.testFramework.ProjectRule
import com.studiogrpc.testutils.ForwardingInterceptor
import com.studiogrpc.testutils.GrpcConnectionRule
import kotlin.collections.listOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class VitalsClientTest {

  @get:Rule val projectRule = ProjectRule()

  private val database = FakeVitalsDatabase(TEST_CONNECTION_1)
  private val clock = FakeClock()

  @get:Rule
  val grpcConnectionRule =
    GrpcConnectionRule(
      listOf(
        FakeErrorsService(TEST_CONNECTION_1, database, FakeClock()),
        FakeReportingService(TEST_CONNECTION_1),
      )
    )

  init {
    database.addIssue(TEST_ISSUE1)
    database.addIssue(TEST_ISSUE2)
  }

  @Test
  fun checkAggregationUtils() {
    val list =
      listOf(
        Pair("a", 1L),
        Pair("b", 0L),
        Pair("c", 2L),
        Pair("a", 1L),
        Pair("b", 0L),
        Pair("c", 100L),
        Pair("d", 0L),
      )

    val aggregated = list.aggregateToWithCount()
    assertThat(aggregated)
      .containsExactlyElementsIn(
        listOf(WithCount(2L, "a"), WithCount(0L, "b"), WithCount(102L, "c"), WithCount(0L, "d"))
      )
  }

  @Test
  fun `client returns top cached issues when offline`() = runTest {
    val cache = AppInsightsCacheImpl()
    val grpcClient = TestVitalsGrpcClient() // return empty result for every API call.
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        cache,
        ForwardingInterceptor,
        grpcClient,
      )

    cache.populateIssues(TEST_CONNECTION_1, listOf(TEST_ISSUE1))

    assertThat(
        client.listTopOpenIssues(
          IssueRequest(
            TEST_CONNECTION_1,
            QueryFilters(
              interval = Interval(FAKE_50_DAYS_AGO, FakeTimeProvider.now),
              eventTypes = listOf(FailureType.FATAL),
              signal = SignalType.SIGNAL_UNSPECIFIED,
            ),
          ),
          null,
          ConnectionMode.OFFLINE,
        )
      )
      .isEqualTo(
        LoadingState.Ready(
          IssueResponse(
            listOf(TEST_ISSUE1.zeroCounts()),
            emptyList(),
            emptyList(),
            emptyList(),
            Permission.FULL,
          )
        )
      )
  }

  @Test
  fun `client caches events for use in the future`() = runTest {
    val cache = AppInsightsCacheImpl()
    val grpcClient =
      object : TestVitalsGrpcClient() {
        override suspend fun getErrorCountMetricsFreshnessInfo(connection: Connection) =
          listOf(Freshness(TimeGranularity.FULL_RANGE, DateTime.getDefaultInstance()))

        override suspend fun queryErrorCountMetrics(
          connection: Connection,
          filters: QueryFilters,
          issueId: IssueId?,
          dimensions: List<DimensionType>,
          metrics: List<MetricType>,
          freshness: Freshness,
          maxNumResults: Int,
        ) = emptyList<DimensionsAndMetrics>()

        override suspend fun searchErrorReports(
          connection: Connection,
          filters: QueryFilters,
          issueId: IssueId,
          maxNumResults: Int,
        ) = listOf(ISSUE1.sampleEvent)

        override suspend fun listTopIssues(
          connection: Connection,
          filters: QueryFilters,
          maxNumResults: Int,
          pageTokenFromPreviousCall: String?,
        ): List<IssueDetails> = listOf(ISSUE1.issueDetails)
      }
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        cache,
        ForwardingInterceptor,
        grpcClient,
      )

    val responseIssue =
      (client.listTopOpenIssues(
          IssueRequest(
            TEST_CONNECTION_1,
            QueryFilters(
              interval = Interval(FAKE_50_DAYS_AGO, FakeTimeProvider.now),
              eventTypes = listOf(FailureType.FATAL),
            ),
          ),
          null,
          ConnectionMode.ONLINE,
        ) as LoadingState.Ready)
        .value
        .issues
        .single()

    assertThat(responseIssue).isEqualTo(ISSUE1)

    val offlineResponse =
      (client.listTopOpenIssues(
          IssueRequest(
            TEST_CONNECTION_1,
            QueryFilters(
              interval = Interval(FAKE_50_DAYS_AGO, FakeTimeProvider.now),
              eventTypes = listOf(FailureType.FATAL),
            ),
          ),
          null,
          ConnectionMode.OFFLINE,
        ) as LoadingState.Ready)
        .value
        .issues
        .single()

    assertThat(offlineResponse)
      .isEqualTo(
        ISSUE1.copy(
          issueDetails = ISSUE1.issueDetails.copy(impactedDevicesCount = 0L, eventsCount = 0L)
        )
      )
  }

  @Test
  fun `client swallows no report found error`() = runTest {
    val cache = AppInsightsCacheImpl()
    val grpcClient =
      object : TestVitalsGrpcClient() {
        override suspend fun getErrorCountMetricsFreshnessInfo(connection: Connection) =
          listOf(Freshness(TimeGranularity.FULL_RANGE, DateTime.getDefaultInstance()))

        override suspend fun listTopIssues(
          connection: Connection,
          filters: QueryFilters,
          maxNumResults: Int,
          pageTokenFromPreviousCall: String?,
        ): List<IssueDetails> = listOf(ISSUE1.issueDetails)
      }
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        cache,
        ForwardingInterceptor,
        grpcClient,
      )

    val responseIssue =
      (client.listTopOpenIssues(
          IssueRequest(
            TEST_CONNECTION_1,
            QueryFilters(
              interval = Interval(FAKE_50_DAYS_AGO, FakeTimeProvider.now),
              eventTypes = listOf(FailureType.FATAL),
            ),
          ),
          null,
          ConnectionMode.ONLINE,
        ) as LoadingState.Ready)
        .value
        .issues
        .single()

    assertThat(responseIssue).isEqualTo(ISSUE1.copy(sampleEvent = Event.EMPTY))
  }

  @Test
  fun `list top open issues returns correct issues, events, versions, oses, and devices`() =
    runBlocking<Unit> {
      val client =
        VitalsClient(
          projectRule.project,
          projectRule.disposable,
          AppInsightsCacheImpl(),
          ForwardingInterceptor,
          VitalsGrpcClientImpl(grpcConnectionRule.channel, ForwardingInterceptor),
        )

      val result = client.listTopOpenIssues(createIssueRequest(clock = clock))

      assertThat(result).isInstanceOf(LoadingState.Ready::class.java)
      val value = (result as LoadingState.Ready).value

      assertThat(value.issues.map { it.issueDetails })
        .containsExactly(TEST_ISSUE1.issueDetails, TEST_ISSUE2.issueDetails)

      // The fake errors service does not reverse engineer the stack trace exactly,
      // so we cannot use == to compare the sample event here.
      val events = value.issues.map { it.sampleEvent.toString() }
      assertThat(events[0])
        .contains(
          "dev.firebase.appdistribution.api_service.ResponseWrapper\$Companion.build(ResponseWrapper.kt:23)"
        )
      assertThat(events[1])
        .contains(
          "com.android.org.conscrypt.ConscryptEngine.convertException(ConscryptEngine.java:1134)"
        )

      assertThat(value.devices)
        .containsExactly(
          WithCount(3, Device("samsung", "a32", "Galaxy A32", DeviceType("Phone"))),
          WithCount(2, Device("samsung", "greatlte", "Galaxy Note8", DeviceType("Tablet"))),
        )

      assertThat(value.operatingSystems)
        .containsExactly(
          WithCount(3, OperatingSystemInfo("33", "Android 13")),
          WithCount(2, OperatingSystemInfo("28", "Android 9")),
        )

      assertThat(value.versions)
        .containsExactly(
          WithCount(10, Version("6", "6", "6")),
          WithCount(5, Version("5", "5", "5", setOf(PlayTrack.OPEN_TESTING))),
        )
    }

  @Test
  fun `get device and os distribution stats`() =
    runBlocking<Unit> {
      val client =
        VitalsClient(
          projectRule.project,
          projectRule.disposable,
          AppInsightsCacheImpl(),
          ForwardingInterceptor,
          VitalsGrpcClientImpl(grpcConnectionRule.channel, ForwardingInterceptor),
        )
      val result = client.getIssueDetails(TEST_ISSUE1.id, createIssueRequest(clock = clock))

      assertThat(result).isInstanceOf(LoadingState.Ready::class.java)
      val value = (result as LoadingState.Ready).value

      val deviceStats = value!!.deviceStats
      assertThat(deviceStats.topValue).isEqualTo("a32")
      assertThat(deviceStats.groups).hasSize(1)
      assertThat(deviceStats.groups.single().groupName).isEqualTo("samsung")
      assertThat(deviceStats.groups.single().percentage).isEqualTo(100.0)
      assertThat(deviceStats.groups.single().breakdown)
        .containsExactly(DataPoint("a32", 60.0), DataPoint("greatlte", 40.0))

      val osStats = value.osStats
      assertThat(osStats.topValue).isEqualTo("Android 13")
      assertThat(osStats.groups)
        .containsExactly(
          StatsGroup("Android 13", 60.0, emptyList()),
          StatsGroup("Android 9", 40.0, emptyList()),
        )
    }

  @Test
  fun `list connections returns correct apps`() =
    runBlocking<Unit> {
      val client =
        VitalsClient(
          projectRule.project,
          projectRule.disposable,
          AppInsightsCacheImpl(),
          ForwardingInterceptor,
          VitalsGrpcClientImpl(grpcConnectionRule.channel, ForwardingInterceptor),
        )
      val result = client.listConnections()
      assertThat((result as LoadingState.Ready).value)
        .containsExactly(AppConnection(TEST_CONNECTION_1.appId, TEST_CONNECTION_1.displayName))
    }

  @Test
  fun `client uses the same cache for connections and issues`() = runTest {
    val cache = AppInsightsCacheImpl()
    val issueRequest =
      IssueRequest(
        TEST_CONNECTION_1,
        QueryFilters(
          interval = Interval(FAKE_50_DAYS_AGO, FakeTimeProvider.now),
          eventTypes = listOf(FailureType.FATAL),
          signal = SignalType.SIGNAL_UNSPECIFIED,
        ),
      )
    val grpcClient =
      object : TestVitalsGrpcClient() {
        override suspend fun listAccessibleApps(maxNumResults: Int) =
          listOf(AppConnection(TEST_CONNECTION_1.appId, TEST_CONNECTION_1.displayName))

        override suspend fun getErrorCountMetricsFreshnessInfo(connection: Connection) =
          listOf(Freshness(TimeGranularity.FULL_RANGE, DateTime.getDefaultInstance()))

        override suspend fun queryErrorCountMetrics(
          connection: Connection,
          filters: QueryFilters,
          issueId: IssueId?,
          dimensions: List<DimensionType>,
          metrics: List<MetricType>,
          freshness: Freshness,
          maxNumResults: Int,
        ) = emptyList<DimensionsAndMetrics>()

        override suspend fun searchErrorReports(
          connection: Connection,
          filters: QueryFilters,
          issueId: IssueId,
          maxNumResults: Int,
        ) = listOf(ISSUE1.sampleEvent)

        override suspend fun listTopIssues(
          connection: Connection,
          filters: QueryFilters,
          maxNumResults: Int,
          pageTokenFromPreviousCall: String?,
        ): List<IssueDetails> = listOf(ISSUE1.issueDetails)
      }
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        cache,
        ForwardingInterceptor,
        grpcClient,
      )

    // Verify list connections returns expected result
    val result = client.listConnections()
    assertThat((result as LoadingState.Ready).value)
      .containsExactly(AppConnection(TEST_CONNECTION_1.appId, TEST_CONNECTION_1.displayName))

    // Verify list top open issues returns expected result
    assertThat(client.listTopOpenIssues(issueRequest, null, ConnectionMode.ONLINE))
      .isEqualTo(
        LoadingState.Ready(
          IssueResponse(listOf(ISSUE1), emptyList(), emptyList(), emptyList(), Permission.READ_ONLY)
        )
      )

    // Verify the cache contains both connections and issues computed in the previous steps
    assertThat(cache.getRecentConnections()).containsExactly(TEST_CONNECTION_1)
    assertThat(cache.getTopIssues(issueRequest)).containsExactly(ISSUE1.zeroCounts())
  }

  @Test
  fun `fetch insight populates proto fields correctly`() = runBlocking {
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        AppInsightsCacheImpl(),
        ForwardingInterceptor,
        TestVitalsGrpcClient(),
        FakeAiInsightClient,
      )

    val insight =
      client.fetchInsight(
        TEST_CONNECTION_1,
        ISSUE1.id,
        null,
        ISSUE1.issueDetails.fatality,
        ISSUE1.sampleEvent,
        TimeIntervalFilter.ONE_DAY,
        CodeContextData(
          listOf(
            CodeContext(
              "com.example.MainActivity",
              "src/com/example/MainActivity.kt",
              "class MainActivity {}",
              Language.KOTLIN,
            ),
            CodeContext(
              "com.example.lib.Library",
              "src/com/example/lib/Library.kt",
              "class Library {}",
              Language.KOTLIN,
            ),
          ),
          Experiment.TOP_SOURCE,
        ),
      )

    val value = (insight as LoadingState.Ready).value
    val request = GeminiInsightsRequest.parseFrom(BaseEncoding.base64().decode(value.rawInsight))
    assertThat(request.deviceName).isEqualTo("Google Pixel 4a")
    assertThat(request.apiLevel).isEqualTo("12")
    assertThat(request.stackTrace)
      .isEqualTo(
        "retrofit2.HttpException: HTTP 401 " +
          "\n\tdev.firebase.appdistribution.api_service.ResponseWrapper\$Companion.build(ResponseWrapper.kt:23)" +
          "\n\tdev.firebase.appdistribution.api_service.ResponseWrapper\$Companion.fetchOrError(ResponseWrapper.kt:31)"
      )
    assertThat(request.codeSnippetsList)
      .containsExactly(
        CodeSnippet.newBuilder()
          .apply {
            codeSnippet = "class MainActivity {}"
            filePath = "src/com/example/MainActivity.kt"
          }
          .build(),
        CodeSnippet.newBuilder()
          .apply {
            codeSnippet = "class Library {}"
            filePath = "src/com/example/lib/Library.kt"
          }
          .build(),
      )
      .inOrder()
  }

  @Test
  fun `fetch insight caches new insights`() = runBlocking {
    val cache = AppInsightsCacheImpl()
    val fakeAiClient =
      object : AiInsightClient {
        override suspend fun fetchCrashInsight(
          projectId: String,
          additionalContextMsg: Message,
        ): AiInsight {
          return DEFAULT_AI_INSIGHT
        }
      }
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        cache,
        ForwardingInterceptor,
        TestVitalsGrpcClient(),
        fakeAiClient,
      )

    client.fetchInsight(
      TEST_CONNECTION_1,
      ISSUE1.id,
      null,
      ISSUE1.issueDetails.fatality,
      ISSUE1.sampleEvent,
      TimeIntervalFilter.ONE_DAY,
      CodeContextData.UNASSIGNED,
    )
    assertThat(
        cache.getAiInsight(TEST_CONNECTION_1, ISSUE1.id, null, DEFAULT_AI_INSIGHT.experiment)
      )
      .isEqualTo(DEFAULT_AI_INSIGHT.copy(isCached = true))
  }

  @Test
  fun `fetch insight uses cached insight when cached value is available`() = runBlocking {
    val cache = AppInsightsCacheImpl()
    cache.putAiInsight(TEST_CONNECTION_1, ISSUE1.id, null, DEFAULT_AI_INSIGHT)
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        cache,
        ForwardingInterceptor,
        TestVitalsGrpcClient(),
        FakeAiInsightClient,
      )

    assertThat(
        client.fetchInsight(
          TEST_CONNECTION_1,
          ISSUE1.id,
          null,
          ISSUE1.issueDetails.fatality,
          ISSUE1.sampleEvent,
          TimeIntervalFilter.ONE_DAY,
          CodeContextData.UNASSIGNED,
        )
      )
      .isEqualTo(LoadingState.Ready(DEFAULT_AI_INSIGHT.copy(isCached = true)))
  }

  @Test
  fun `test fetch insight on ANR returns unsupported operation`() = runBlocking {
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        AppInsightsCacheImpl(),
        ForwardingInterceptor,
        TestVitalsGrpcClient(),
        FakeAiInsightClient,
      )

    val insight =
      client.fetchInsight(
        TEST_CONNECTION_1,
        ISSUE1.id,
        null,
        FailureType.ANR,
        ISSUE1.sampleEvent,
        TimeIntervalFilter.ONE_DAY,
        CodeContextData.UNASSIGNED,
      )

    assertThat(insight)
      .isEqualTo(LoadingState.UnsupportedOperation("Insights are currently not available for ANRs"))
  }

  @Test
  fun `test fetch insight on native crash returns unsupported operation`() = runBlocking {
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        AppInsightsCacheImpl(),
        ForwardingInterceptor,
        TestVitalsGrpcClient(),
        FakeAiInsightClient,
      )

    val stackTraceGroup =
      StacktraceGroup(
        listOf(
          ExceptionStack(
            Stacktrace(Caption("*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***")),
            "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
            "",
            "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***",
          ),
          ExceptionStack(
            Stacktrace(Caption("pid", "0, tid: 2526 >>> com.android.vending <<<")),
            "pid",
            "0, tid: 2526 >>> com.android.vending <<<",
            "pid: 0, tid: 2526 >>> com.android.vending <<<",
          ),
          ExceptionStack(
            Stacktrace(
              Caption("backtrace", ")"),
              frames =
                listOf(
                  Frame(
                    rawSymbol = "#00  pc 0x00000000001f4cdc",
                    symbol = "#00  pc 0x00000000001f4cdc",
                  )
                ),
            ),
            type = "backtrace",
            rawExceptionMessage = "backtrace:",
          ),
        )
      )

    val insight =
      client.fetchInsight(
        TEST_CONNECTION_1,
        ISSUE1.id,
        null,
        FailureType.FATAL,
        Event(stacktraceGroup = stackTraceGroup),
        TimeIntervalFilter.ONE_DAY,
        CodeContextData.UNASSIGNED,
      )

    assertThat(insight)
      .isEqualTo(
        LoadingState.UnsupportedOperation("Insights are currently not available for native crashes")
      )
  }

  @Test
  fun `fetch insight returns AiInsight with experiment set`() = runBlocking {
    val fakeAiClient =
      object : AiInsightClient {
        override suspend fun fetchCrashInsight(projectId: String, additionalContextMsg: Message) =
          DEFAULT_AI_INSIGHT
      }
    val client =
      VitalsClient(
        projectRule.project,
        projectRule.disposable,
        AppInsightsCacheImpl(),
        ForwardingInterceptor,
        TestVitalsGrpcClient(),
        fakeAiClient,
      )

    val insight =
      client.fetchInsight(
        TEST_CONNECTION_1,
        ISSUE1.id,
        null,
        ISSUE1.issueDetails.fatality,
        ISSUE1.sampleEvent,
        TimeIntervalFilter.ONE_DAY,
        CodeContextData(emptyList(), Experiment.TOP_SOURCE),
      )
    assertThat(insight)
      .isEqualTo(LoadingState.Ready(DEFAULT_AI_INSIGHT.copy(experiment = Experiment.TOP_SOURCE)))
  }
}
