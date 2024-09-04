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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.time.FakeClock
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class AppInsightsProjectLevelControllerTest {

  private val projectRule = ProjectRule()
  private val executorsRule = AndroidExecutorsRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(executorsRule).around(controllerRule)!!

  private val client: TestAppInsightsClient
    get() = controllerRule.client

  private val clock: FakeClock
    get() = controllerRule.clock

  @Test
  fun `when controller is initialized it emits a loading state and starts an issue fetch`() =
    runBlocking {
      controllerRule.updateConnections(listOf(CONNECTION1, CONNECTION2, PLACEHOLDER_CONNECTION))
      val model = controllerRule.consumeNext()
      assertThat(model)
        .isEqualTo(
          AppInsightsState(
            Selection(CONNECTION1, listOf(CONNECTION1, CONNECTION2, PLACEHOLDER_CONNECTION)),
            TEST_FILTERS,
            LoadingState.Loading,
          )
        )

      client.completeIssuesCallWith(
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      )

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          AppInsightsState(
            connections =
              Selection(CONNECTION1, listOf(CONNECTION1, CONNECTION2, PLACEHOLDER_CONNECTION)),
            filters =
              TEST_FILTERS.copy(
                versions =
                  MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), listOf(DEFAULT_FETCHED_VERSIONS)),
                devices =
                  MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), listOf(DEFAULT_FETCHED_DEVICES)),
                operatingSystems =
                  MultiSelection(setOf(DEFAULT_FETCHED_OSES), listOf(DEFAULT_FETCHED_OSES)),
              ),
            issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())),
            permission = Permission.FULL,
          )
        )
      verify(client).listTopOpenIssues(any(), any(), any(), any())
      return@runBlocking
    }

  @Test
  fun `when placeholder connection gets selected it propagates to the model`() = runBlocking {
    // discard initial loading state, already tested above
    val model = controllerRule.consumeInitialState()

    controllerRule.selectFirebaseConnection(PLACEHOLDER_CONNECTION)

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          connections = model.connections.select(PLACEHOLDER_CONNECTION),
          issues =
            LoadingState.UnknownFailure(
              message = "Currently selected app is not configured with the current insights tool.",
              cause = UnconfiguredAppException,
            ),
          filters = TEST_FILTERS,
        )
      )
  }

  @Test
  fun `when connection gets selected it propagates to the model`() = runBlocking {
    // discard initial loading state, already tested above
    val model = controllerRule.consumeInitialState()

    controllerRule.selectFirebaseConnection(CONNECTION2)

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          connections = model.connections.select(CONNECTION2),
          issues = LoadingState.Loading,
          filters =
            model.filters.copy(
              versions = MultiSelection.emptySelection(),
              devices = MultiSelection.emptySelection(),
              operatingSystems = MultiSelection.emptySelection(),
            ),
        )
      )
    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          connections = model.connections.select(CONNECTION2),
          filters =
            TEST_FILTERS.copy(
              versions =
                MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), listOf(DEFAULT_FETCHED_VERSIONS)),
              devices =
                MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), listOf(DEFAULT_FETCHED_DEVICES)),
              operatingSystems =
                MultiSelection(setOf(DEFAULT_FETCHED_OSES), listOf(DEFAULT_FETCHED_OSES)),
              signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED),
            ),
          issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())),
        )
      )
    verify(client)
      .listTopOpenIssues(
        argThat {
          it.connection == CONNECTION2 &&
            it.filters.versions == setOf(Version.ALL) &&
            it.filters.interval.duration == Duration.ofDays(30)
        },
        any(),
        any(),
        any(),
      )
    return@runBlocking
  }

  @Test
  fun `when connections get changed it propagates to the model, active connection remains`() =
    runBlocking {
      // discard initial loading state, already tested above
      val model = controllerRule.consumeInitialState()

      // Ensure the initial state.
      assertThat(model.connections)
        .isEqualTo(Selection(CONNECTION1, listOf(CONNECTION1, CONNECTION2, PLACEHOLDER_CONNECTION)))

      // Available connections get changed but active connection remains, thus no new fetch.
      controllerRule.updateConnections(listOf((CONNECTION1)))

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          model.copy(
            connections = Selection(CONNECTION1, listOf(CONNECTION1)),
            filters =
              TEST_FILTERS.copy(
                versions =
                  MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), listOf(DEFAULT_FETCHED_VERSIONS)),
                devices =
                  MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), listOf(DEFAULT_FETCHED_DEVICES)),
                operatingSystems =
                  MultiSelection(setOf(DEFAULT_FETCHED_OSES), listOf(DEFAULT_FETCHED_OSES)),
                signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED),
              ),
            issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())),
          )
        )

      return@runBlocking
    }

  @Test
  fun `when connections get changed it propagates to the model, active connection is changed`() =
    runBlocking {
      // discard initial loading state, already tested above
      val model = controllerRule.consumeInitialState()

      // Ensure the initial state.
      assertThat(model.connections)
        .isEqualTo(Selection(CONNECTION1, listOf(CONNECTION1, CONNECTION2, PLACEHOLDER_CONNECTION)))

      // Available connections get changed. Since "VARIANT1" has been removed from the available
      // connections list, active connection falls to "VARIANT2". Thus, new fetch.
      controllerRule.updateConnections(listOf(CONNECTION2))

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          model.copy(
            connections = Selection(CONNECTION2, listOf(CONNECTION2)),
            issues = LoadingState.Loading,
            filters = TEST_FILTERS,
          )
        )
      client.completeIssuesCallWith(
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      )

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          model.copy(
            connections = Selection(CONNECTION2, listOf(CONNECTION2)),
            filters =
              TEST_FILTERS.copy(
                versions =
                  MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), listOf(DEFAULT_FETCHED_VERSIONS)),
                devices =
                  MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), listOf(DEFAULT_FETCHED_DEVICES)),
                operatingSystems =
                  MultiSelection(setOf(DEFAULT_FETCHED_OSES), listOf(DEFAULT_FETCHED_OSES)),
                signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED),
              ),
            issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())),
          )
        )
      verify(client)
        .listTopOpenIssues(
          argThat {
            it.connection == CONNECTION2 &&
              it.filters.versions == setOf(Version.ALL) &&
              it.filters.interval.duration == Duration.ofDays(30)
          },
          any(),
          any(),
          any(),
        )
      return@runBlocking
    }

  @Test
  fun `correct selection is inferred when connections are updated`() = runBlocking {
    // discard initial loading state, already tested above
    val model = controllerRule.consumeInitialState()

    val unpreferredConnection = CONNECTION1.copy(isPreferred = false)
    val preferredConnection = CONNECTION2.copy(isPreferred = true)

    // Available connections get changed. Since "VARIANT1" has been removed from the available
    // connections list, active connection should fall to the preferred connection given the
    // preference setting.
    controllerRule.updateConnections(listOf(unpreferredConnection, preferredConnection))

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          connections =
            Selection(preferredConnection, listOf(unpreferredConnection, preferredConnection)),
          issues = LoadingState.Loading,
          filters = TEST_FILTERS,
        )
      )
    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          connections =
            Selection(preferredConnection, listOf(unpreferredConnection, preferredConnection)),
          filters =
            TEST_FILTERS.copy(
              versions =
                MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), listOf(DEFAULT_FETCHED_VERSIONS)),
              devices =
                MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), listOf(DEFAULT_FETCHED_DEVICES)),
              operatingSystems =
                MultiSelection(setOf(DEFAULT_FETCHED_OSES), listOf(DEFAULT_FETCHED_OSES)),
              signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED),
            ),
          issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())),
        )
      )
    verify(client)
      .listTopOpenIssues(
        argThat {
          it.connection == preferredConnection &&
            it.filters.versions == setOf(Version.ALL) &&
            it.filters.interval.duration == Duration.ofDays(30)
        },
        any(),
        any(),
        any(),
      )
    return@runBlocking
  }

  @Test
  fun `when connections get changed it propagates to the model, placeholder connection is selected`() =
    runBlocking {
      // discard initial loading state, already tested above
      val model = controllerRule.consumeInitialState()

      // Ensure the initial state.
      assertThat(model.connections)
        .isEqualTo(Selection(CONNECTION1, listOf(CONNECTION1, CONNECTION2, PLACEHOLDER_CONNECTION)))

      // Available connections get changed. Since "VARIANT1" and "VARIANT2" have been removed
      // from the available connections list, active connection falls to the placeholder one,
      // thus no fetch (it's filtered out by [SafeFiltersAdapter]).
      controllerRule.updateConnections(listOf(PLACEHOLDER_CONNECTION))

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          model.copy(
            connections = Selection(PLACEHOLDER_CONNECTION, listOf(PLACEHOLDER_CONNECTION)),
            issues =
              LoadingState.UnknownFailure(
                message =
                  "Currently selected app is not configured with the current insights tool.",
                cause = UnconfiguredAppException,
              ),
            filters = TEST_FILTERS,
          )
        )
    }

  @Test
  fun `when version gets selected it propagates to the model`() = runBlocking {
    val newVersion = WithCount(42, Version("2", "2.0"))
    // discard initial loading state, already tested above
    val model =
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            listOf(DEFAULT_FETCHED_VERSIONS, newVersion),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      )

    controllerRule.selectVersions(setOf(newVersion.value))
    assertThat(controllerRule.consumeNext())
      .isEqualTo(model.selectVersions(setOf(newVersion.value)).copy(issues = LoadingState.Loading))

    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS, newVersion),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )
    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model
          .selectVersions(setOf(newVersion.value))
          .copy(issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())))
      )
    verify(client)
      .listTopOpenIssues(
        argThat {
          it.connection == CONNECTION1 &&
            it.filters.versions == setOf(newVersion.value) &&
            it.filters.interval.duration == Duration.ofDays(30) &&
            it.filters.signal == SignalType.SIGNAL_UNSPECIFIED
        },
        any(),
        any(),
        any(),
      )
    return@runBlocking
  }

  @Test
  fun `when interval gets selected it propagates to the model`() = runBlocking {
    val newVersion = WithCount(42, Version("2", "2.0"))
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS, newVersion),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    controllerRule.selectVersions(setOf(newVersion.value))

    val model = controllerRule.consumeNext()
    assertThat(model)
      .isEqualTo(model.selectVersions(setOf(newVersion.value)).copy(issues = LoadingState.Loading))

    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS, newVersion),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    val fetchedModel = controllerRule.consumeNext()
    assertThat(fetchedModel)
      .isEqualTo(
        model
          .selectVersions(setOf(newVersion.value))
          .copy(issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())))
      )

    controllerRule.selectTimeInterval(TimeIntervalFilter.ONE_DAY)

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        fetchedModel
          .selectTimeInterval(TimeIntervalFilter.ONE_DAY)
          .copy(issues = LoadingState.Loading)
      )

    val anotherFetchedVersion = WithCount(11, Version("3", "3.0"))
    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS, anotherFetchedVersion),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          filters =
            model.filters
              .withTimeInterval(TimeIntervalFilter.ONE_DAY)
              .copy(
                versions =
                  MultiSelection(
                    emptySet(),
                    listOf(DEFAULT_FETCHED_VERSIONS, anotherFetchedVersion),
                  )
              ),
          issues = LoadingState.UnknownFailure(null, NoVersionsSelectedException),
        )
      )

    verify(client)
      .listTopOpenIssues(
        argThat {
          it.connection == CONNECTION1 &&
            it.filters.versions == setOf(newVersion.value) &&
            it.filters.interval.duration == Duration.ofDays(1)
        },
        any(),
        any(),
        any(),
      )
    return@runBlocking
  }

  @Test
  fun `refresh() should fetch new data`() = runBlocking {
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(emptyList(), emptyList(), emptyList(), emptyList(), Permission.READ_ONLY)
      )
    )

    controllerRule.controller.refresh()
    val newModel = controllerRule.consumeNext()
    assertThat(newModel.issues).isEqualTo(LoadingState.Loading)

    val fetchedVersion = WithCount(42, Version("1", "1.0"))
    val fetchedDevice = WithCount(10, Device("Google", "Pixel 2"))
    val fetchedOs = WithCount(10, OperatingSystemInfo("11", "Android (11)"))
    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(fetchedVersion),
          listOf(fetchedDevice),
          listOf(fetchedOs),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        newModel.copy(
          filters =
            TEST_FILTERS.copy(
              versions = MultiSelection(setOf(fetchedVersion), listOf(fetchedVersion)),
              devices = MultiSelection(setOf(fetchedDevice), listOf(fetchedDevice)),
              operatingSystems = MultiSelection(setOf(fetchedOs), listOf(fetchedOs)),
              signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED),
            ),
          issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())),
          permission = DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    return@runBlocking
  }

  @Test
  fun `refresh() within 100ms of issue fetch should fetch new data`() = runBlocking {
    val fetchedVersion = WithCount(42, Version("1", "1.0"))
    val fetchedDevice = WithCount(10, Device("Google", "Pixel 2"))
    val fetchedOs = WithCount(10, OperatingSystemInfo("11", "Android (11)"))
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(fetchedVersion),
          listOf(fetchedDevice),
          listOf(fetchedOs),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    val newModel = controllerRule.refreshAndConsumeLoadingState()
    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(fetchedVersion),
          listOf(fetchedDevice),
          listOf(fetchedOs),
          Permission.FULL,
        )
      )
    )
    controllerRule.consumeNext()

    // refresh immediately after fetch completes
    controllerRule.refreshAndConsumeLoadingState()
    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(fetchedVersion),
          listOf(fetchedDevice),
          listOf(fetchedOs),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        newModel.copy(
          filters =
            TEST_FILTERS.copy(
              versions = MultiSelection(setOf(fetchedVersion), listOf(fetchedVersion)),
              devices = MultiSelection(setOf(fetchedDevice), listOf(fetchedDevice)),
              operatingSystems = MultiSelection(setOf(fetchedOs), listOf(fetchedOs)),
              signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED),
            ),
          issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())),
        )
      )
    return@runBlocking
  }

  @Test
  fun `refresh() after selecting issue should keep selected issue`() = runBlocking {
    val model =
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE2, ISSUE1),
            emptyList(),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      )

    controllerRule.selectIssue(ISSUE1, IssueSelectionSource.LIST)
    controllerRule.consumeNext()

    client.completeIssueVariantsCallWith(LoadingState.Ready(emptyList()))
    controllerRule.consumeNext()

    client.completeListEvents(LoadingState.Ready(EventPage(listOf(ISSUE1.sampleEvent), "")))
    controllerRule.consumeNext()

    client.completeDetailsCallWith(LoadingState.Ready(null))
    controllerRule.consumeNext()

    client.completeListNotesCallWith(LoadingState.Ready(emptyList()))
    controllerRule.consumeNext()

    client.completeFetchInsightCallWith(LoadingState.Ready(DEFAULT_AI_INSIGHT))
    controllerRule.consumeNext()

    controllerRule.refreshAndConsumeLoadingState()

    assertThat(
        controllerRule.consumeFetchState(
          state =
            LoadingState.Ready(
              IssueResponse(
                listOf(ISSUE2, ISSUE1),
                emptyList(),
                listOf(DEFAULT_FETCHED_DEVICES),
                listOf(DEFAULT_FETCHED_OSES),
                DEFAULT_FETCHED_PERMISSIONS,
              )
            ),
          detailsState = LoadingState.Ready(ISSUE1_DETAILS),
          notesState = LoadingState.Ready(emptyList()),
        )
      )
      .isEqualTo(
        model.copy(
          issues =
            LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE2, ISSUE1)), clock.instant())),
          currentIssueDetails = LoadingState.Ready(ISSUE1_DETAILS),
          currentNotes = LoadingState.Ready(emptyList()),
          currentInsight = LoadingState.Ready(DEFAULT_AI_INSIGHT),
          permission = Permission.FULL,
        )
      )
    return@runBlocking
  }

  @Test
  fun `when no fatalities are selected, state changes should fail`() = runBlocking {
    val newVersion = WithCount(42, Version("2", "2.0"))
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS, newVersion),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    controllerRule.toggleFatality(FailureType.FATAL)
    controllerRule.consumeNext()
    controllerRule.toggleFatality(FailureType.NON_FATAL)
    controllerRule.consumeNext()
    controllerRule.toggleFatality(FailureType.ANR)
    assertThat(controllerRule.consumeNext().issues)
      .isEqualTo(LoadingState.UnknownFailure(null, NoTypesSelectedException))
    controllerRule.selectVersions(emptySet())
    assertThat(controllerRule.consumeNext().issues)
      .isEqualTo(LoadingState.UnknownFailure(null, NoVersionsSelectedException))
    return@runBlocking
  }

  @Test
  fun `when no versions are selected, state changes should fail`() = runBlocking {
    val newVersion = WithCount(42, Version("2", "2.0"))
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS, newVersion),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )
    controllerRule.selectVersions(emptySet())
    assertThat(controllerRule.consumeNext().issues)
      .isEqualTo(LoadingState.UnknownFailure(null, NoVersionsSelectedException))
    controllerRule.toggleFatality(FailureType.FATAL)
    assertThat(controllerRule.consumeNext().issues)
      .isEqualTo(LoadingState.UnknownFailure(null, NoVersionsSelectedException))
    return@runBlocking
  }

  @Test
  fun `when no devices are selected, state changes should fail`() = runBlocking {
    val newDevice = WithCount(42, Device("Google", "Pixel 2"))
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES, newDevice),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )
    controllerRule.selectDevices(emptySet())
    assertThat(controllerRule.consumeNext().issues)
      .isEqualTo(LoadingState.UnknownFailure(null, NoDevicesSelectedException))
    controllerRule.toggleFatality(FailureType.FATAL)
    assertThat(controllerRule.consumeNext().issues)
      .isEqualTo(LoadingState.UnknownFailure(null, NoDevicesSelectedException))
    return@runBlocking
  }

  @Test
  fun `when issue gets selected it propagates to the model`() = runBlocking {
    // discard initial loading state, already tested above
    val model =
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1, ISSUE2),
            emptyList(),
            emptyList(),
            emptyList(),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      )

    assertThat(model.issues).isInstanceOf(LoadingState.Ready::class.java)
    assertThat((model.issues as LoadingState.Ready).value.value)
      .isEqualTo(Selection(ISSUE1, listOf(ISSUE1, ISSUE2)))
    assertThat(model.currentIssueVariants)
      .isEqualTo(LoadingState.Ready(Selection(null, emptyList())))
    assertThat(model.currentEvents).isEqualTo(LoadingState.Ready(null))
    assertThat(model.currentIssueDetails).isEqualTo(LoadingState.Ready(null))
    assertThat(model.currentNotes).isEqualTo(LoadingState.Ready(emptyList<Note>()))

    controllerRule.selectIssue(ISSUE2, IssueSelectionSource.LIST)

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          issues = model.issues.map { Timed(it.value.select(ISSUE2), clock.instant()) },
          currentIssueVariants = LoadingState.Loading,
          currentEvents = LoadingState.Loading,
          currentIssueDetails = LoadingState.Loading,
          currentNotes = LoadingState.Loading,
          currentInsight = LoadingState.Loading,
        )
      )

    client.completeIssueVariantsCallWith(LoadingState.Ready(listOf(ISSUE_VARIANT)))
    controllerRule.consumeNext()

    client.completeListEvents(LoadingState.Ready(EventPage(listOf(ISSUE2.sampleEvent), "")))
    controllerRule.consumeNext()

    client.completeDetailsCallWith(LoadingState.Ready(ISSUE1_DETAILS))
    controllerRule.consumeNext()

    client.completeListNotesCallWith(LoadingState.Ready(emptyList()))
    controllerRule.consumeNext()

    client.completeFetchInsightCallWith(LoadingState.Ready(DEFAULT_AI_INSIGHT))

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          issues = model.issues.map { Timed(it.value.select(ISSUE2), clock.instant()) },
          currentIssueVariants = LoadingState.Ready(Selection(null, listOf(ISSUE_VARIANT))),
          currentEvents =
            LoadingState.Ready(DynamicEventGallery(listOf(ISSUE2.sampleEvent), 0, "")),
          currentIssueDetails = LoadingState.Ready(ISSUE1_DETAILS),
          currentNotes = LoadingState.Ready(emptyList()),
          currentInsight = LoadingState.Ready(DEFAULT_AI_INSIGHT),
        )
      )
    return@runBlocking
  }

  @Test
  fun `when fatality is toggled it propagates to the model`() =
    runBlocking<Unit> {
      val issuesResponse =
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      controllerRule.consumeInitialState(issuesResponse)

      controllerRule.toggleFatality(FailureType.FATAL)
      assertThat(controllerRule.consumeNext().filters.failureTypeToggles.selected)
        .containsExactly(FailureType.ANR, FailureType.NON_FATAL)
      client.completeIssuesCallWith(issuesResponse)
      assertThat(controllerRule.consumeNext().filters.failureTypeToggles.selected)
        .containsExactly(FailureType.ANR, FailureType.NON_FATAL)
      verify(client)
        .listTopOpenIssues(
          argThat {
            it.filters.eventTypes.size == 2 &&
              it.filters.eventTypes.containsAll(listOf(FailureType.ANR, FailureType.NON_FATAL))
          },
          any(),
          any(),
          any(),
        )

      controllerRule.toggleFatality(FailureType.NON_FATAL)
      client.completeIssuesCallWith(issuesResponse)
      controllerRule.consumeNext()
      assertThat(controllerRule.consumeNext().filters.failureTypeToggles.selected)
        .containsExactly(FailureType.ANR)

      controllerRule.toggleFatality(FailureType.ANR)
      assertThat(controllerRule.consumeNext().filters.failureTypeToggles.selected).isEmpty()
      verify(client, never())
        .listTopOpenIssues(argThat { it.filters.eventTypes.isEmpty() }, any(), any(), any())

      controllerRule.toggleFatality(FailureType.FATAL)
      client.completeIssuesCallWith(issuesResponse)
      assertThat(controllerRule.consumeNext().filters.failureTypeToggles.selected)
        .containsExactly(FailureType.FATAL)
      verify(client)
        .listTopOpenIssues(
          argThat {
            it.filters.eventTypes.size == 1 && it.filters.eventTypes.contains(FailureType.FATAL)
          },
          any(),
          any(),
          any(),
        )
    }

  @Test
  fun `successful fetches should save snapshots, and persist them through failures`() =
    runBlocking {
      val fetchedVersion = WithCount(42, Version("1", "1.0"))
      val fetchedDevice = WithCount(10, Device("Google", "Pixel 2"))
      val fetchedOs = WithCount(10, OperatingSystemInfo("11", "Android (11)"))
      // discard initial loading state, already tested above
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            listOf(fetchedVersion),
            listOf(fetchedDevice),
            listOf(fetchedOs),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      )

      controllerRule.controller.refresh()
      var newModel = controllerRule.consumeNext()
      assertThat(newModel.issues).isEqualTo(LoadingState.Loading)
      client.completeIssuesCallWith(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1),
            listOf(fetchedVersion),
            listOf(fetchedDevice),
            listOf(fetchedOs),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      )

      newModel = controllerRule.consumeNext()
      assertThat(newModel)
        .isEqualTo(
          newModel.copy(
            filters =
              TEST_FILTERS.copy(
                versions = MultiSelection(setOf(fetchedVersion), listOf(fetchedVersion)),
                devices = MultiSelection(setOf(fetchedDevice), listOf(fetchedDevice)),
                operatingSystems = MultiSelection(setOf(fetchedOs), listOf(fetchedOs)),
                signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED),
              ),
            issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())),
            currentIssueDetails = LoadingState.Loading,
            currentNotes = LoadingState.Loading,
          )
        )

      client.completeDetailsCallWith(LoadingState.Ready(ISSUE1_DETAILS))
      controllerRule.consumeNext()
      client.completeListNotesCallWith(LoadingState.Ready(emptyList()))
      newModel = controllerRule.consumeNext()

      assertThat(newModel)
        .isEqualTo(
          newModel.copy(
            issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())),
            currentIssueDetails = LoadingState.Ready(ISSUE1_DETAILS),
            currentNotes = LoadingState.Ready(emptyList()),
          )
        )

      controllerRule.refreshAndConsumeLoadingState()
      client.completeIssuesCallWith(LoadingState.UnknownFailure(null))

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          newModel.copy(
            issues = LoadingState.UnknownFailure(null),
            currentIssueVariants = LoadingState.Ready(null),
            currentEvents = LoadingState.Ready(null),
            currentIssueDetails = LoadingState.Ready(null),
            currentNotes = LoadingState.Ready(null),
          )
        )
      return@runBlocking
    }

  @Test
  fun `snapshots can be loaded`() = runBlocking {
    val fetchedVersion = WithCount(42, Version("1", "1.0"))
    // discard initial loading state, already tested above
    val startingState =
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            listOf(fetchedVersion),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      )

    controllerRule.refreshAndConsumeLoadingState()
    val newModel =
      controllerRule.consumeFetchState(
        state =
          LoadingState.Ready(
            IssueResponse(
              listOf(ISSUE1),
              listOf(fetchedVersion),
              listOf(DEFAULT_FETCHED_DEVICES),
              listOf(DEFAULT_FETCHED_OSES),
              DEFAULT_FETCHED_PERMISSIONS,
            )
          ),
        issueVariantsState = LoadingState.Ready(emptyList()),
        detailsState = LoadingState.Ready(ISSUE1_DETAILS),
      )
    assertThat(newModel)
      .isEqualTo(
        startingState.copy(
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())),
          currentIssueVariants = LoadingState.Ready(Selection(null, emptyList())),
          currentIssueDetails = LoadingState.Ready(ISSUE1_DETAILS),
          currentNotes = LoadingState.Ready(emptyList()),
          currentInsight = LoadingState.Ready(AiInsight("")),
        )
      )

    controllerRule.refreshAndConsumeLoadingState()
    client.completeIssuesCallWith(LoadingState.UnknownFailure(null))
    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        newModel.copy(
          issues = LoadingState.UnknownFailure(null),
          currentIssueVariants = LoadingState.Ready(null),
          currentIssueDetails = LoadingState.Ready(null),
          currentNotes = LoadingState.Ready(null),
        )
      )

    controllerRule.revertToSnapshot(newModel)
    assertThat(controllerRule.consumeNext()).isEqualTo(newModel)
    return@runBlocking
  }

  @Test
  fun `state is cancellableTimeoutException`() {
    val state =
      LoadingState.UnknownFailure(
        null,
        cause = RevertibleException(snapshot = null, CancellableTimeoutException),
      )
    assertThat(state.isCancellableTimeoutException()).isTrue()
  }

  @Test
  fun `when issue gets closed or opened it propagates to the model`() = runBlocking {
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          listOf(ISSUE1, ISSUE2),
          emptyList(),
          emptyList(),
          emptyList(),
          Permission.READ_ONLY,
        )
      )
    )

    controllerRule.controller.closeIssue(ISSUE1)

    val closingIssue = ISSUE1.copy(state = IssueState.CLOSING)
    assertThat(controllerRule.consumeNext().selectedIssue).isEqualTo(closingIssue)

    client.completeUpdateIssueStateCallWith(LoadingState.Ready(Unit))
    val closedIssue = closingIssue.copy(state = IssueState.CLOSED)
    assertThat(controllerRule.consumeNext().selectedIssue).isEqualTo(closedIssue)

    controllerRule.controller.openIssue(closedIssue)
    assertThat(controllerRule.consumeNext().selectedIssue)
      .isEqualTo(ISSUE1.copy(state = IssueState.OPENING))

    client.completeUpdateIssueStateCallWith(LoadingState.Ready(Unit))
    assertThat(controllerRule.consumeNext().selectedIssue).isEqualTo(ISSUE1)
  }

  @Test
  fun `when a signal filter is selected it propagates to the model`() = runBlocking {
    val model =
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS,
          )
        )
      )

    controllerRule.selectSignal(SignalType.SIGNAL_FRESH)
    assertThat(controllerRule.consumeNext())
      .isEqualTo(model.selectSignal(SignalType.SIGNAL_FRESH).copy(issues = LoadingState.Loading))

    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          listOf(ISSUE1),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model
          .selectSignal(SignalType.SIGNAL_FRESH)
          .copy(
            issues =
              LoadingState.Ready(
                Timed(Selection(selected = ISSUE1, listOf(ISSUE1)), clock.instant())
              ),
            currentIssueVariants = LoadingState.Loading,
            currentEvents = LoadingState.Loading,
            currentIssueDetails = LoadingState.Loading,
            currentNotes = LoadingState.Loading,
            currentInsight = LoadingState.Loading,
          )
      )

    return@runBlocking
  }

  @Test
  fun `when new note gets created and deleted it propagates to the model`() = runBlocking {
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          listOf(ISSUE1, ISSUE2),
          emptyList(),
          emptyList(),
          emptyList(),
          Permission.FULL,
        )
      )
    )

    // Create a new note and check the state of the current notes.
    controllerRule.controller.addNote(ISSUE1, NOTE1_BODY)
    var newModel = controllerRule.consumeNext()
    (newModel.currentNotes as LoadingState.Ready)
      .value!!
      .map { it.body to it.state }
      .let { assertThat(it).containsExactlyElementsIn(listOf(NOTE1_BODY to NoteState.CREATING)) }

    client.completeCreateNoteCallWith(LoadingState.Ready(NOTE1))

    newModel = controllerRule.consumeNext()
    assertThat(newModel.currentNotes).isEqualTo(LoadingState.Ready(listOf(NOTE1)))

    // Delete this newly created note and check the state of the current notes.
    controllerRule.controller.deleteNote(NOTE1)
    newModel = controllerRule.consumeNext()
    (newModel.currentNotes as LoadingState.Ready)
      .value!!
      .map { it.body to it.state }
      .let { assertThat(it).containsExactlyElementsIn(listOf(NOTE1_BODY to NoteState.DELETING)) }

    client.completeDeleteNoteCallWith(LoadingState.Ready(Unit))

    newModel = controllerRule.consumeNext()
    assertThat(newModel.currentNotes).isEqualTo(LoadingState.Ready(emptyList<Note>()))
  }

  @Test
  fun `permission denied when creating a new note, rollback`() = runBlocking {
    controllerRule.consumeInitialState(
      state =
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1, ISSUE2),
            emptyList(),
            emptyList(),
            emptyList(),
            Permission.FULL,
          )
        ),
      // Ensure we have notes fetched before the following "add" or "delete" actions.
      notesState = LoadingState.Ready(listOf(NOTE1)),
    )

    // Create a new note and check the state of the current notes.
    controllerRule.controller.addNote(ISSUE1, NOTE2_BODY)
    var newModel = controllerRule.consumeNext()
    (newModel.currentNotes as LoadingState.Ready)
      .value!!
      .map { it.body to it.state }
      .let {
        assertThat(it)
          .containsExactlyElementsIn(
            listOf(NOTE2_BODY to NoteState.CREATING, NOTE1_BODY to NoteState.CREATED)
          )
      }
    client.completeCreateNoteCallWith(LoadingState.PermissionDenied("Permission Denied."))

    newModel = controllerRule.consumeNext()
    assertThat(newModel.permission).isEqualTo(Permission.READ_ONLY)
    assertThat(newModel.currentNotes).isEqualTo(LoadingState.Ready(listOf(NOTE1)))
  }

  @Test
  fun `permission denied when deleting a note, rollback`() = runBlocking {
    // Ensure we have notes fetched before the following "add" or "delete" actions.
    controllerRule.consumeInitialState(
      state =
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1, ISSUE2),
            emptyList(),
            emptyList(),
            emptyList(),
            Permission.FULL,
          )
        ),
      notesState = LoadingState.Ready(listOf(NOTE2, NOTE1)),
    )

    // Delete this note and check the state of the current notes.
    controllerRule.controller.deleteNote(NOTE1)
    var newModel = controllerRule.consumeNext()
    (newModel.currentNotes as LoadingState.Ready)
      .value!!
      .map { it.body to it.state }
      .let {
        assertThat(it)
          .containsExactlyElementsIn(
            listOf(NOTE2_BODY to NoteState.CREATED, NOTE1_BODY to NoteState.DELETING)
          )
      }

    client.completeDeleteNoteCallWith(LoadingState.PermissionDenied("Permission Denied."))

    newModel = controllerRule.consumeNext()
    assertThat(newModel.permission).isEqualTo(Permission.READ_ONLY)
    assertThat(newModel.currentNotes).isEqualTo(LoadingState.Ready(listOf(NOTE2, NOTE1)))
  }

  @Test
  fun `enter offline mode puts AppInsightsState into offline mode`() = runBlocking {
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          listOf(ISSUE1, ISSUE2),
          emptyList(),
          emptyList(),
          emptyList(),
          Permission.READ_ONLY,
        )
      )
    )

    controllerRule.enterOfflineMode()
    val state = controllerRule.consumeNext()

    assertThat(state.mode).isEqualTo(ConnectionMode.OFFLINE)
    assertThat(state.issues).isEqualTo(LoadingState.Loading)
    assertThat(state.currentIssueDetails).isEqualTo(LoadingState.Ready(null))
    assertThat(state.currentNotes).isEqualTo(LoadingState.Ready(null))
  }

  @Test
  fun `network failure in fetching issues propagates to model`() = runBlocking {
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          listOf(ISSUE1, ISSUE2),
          emptyList(),
          emptyList(),
          emptyList(),
          Permission.READ_ONLY,
        )
      )
    )

    controllerRule.refreshAndConsumeLoadingState()
    client.completeIssuesCallWith(LoadingState.NetworkFailure("test"))
    assertThat(controllerRule.consumeNext().issues)
      .isInstanceOf(LoadingState.NetworkFailure::class.java)
  }

  @Test
  fun `refresh performs a hard fetch and puts AppInsightsState in online mode if successful`() =
    runBlocking {
      // discard initial loading state, already tested above
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1, ISSUE2),
            emptyList(),
            emptyList(),
            emptyList(),
            Permission.READ_ONLY,
          )
        )
      )

      controllerRule.enterOfflineMode()
      assertThat(controllerRule.consumeNext().mode).isEqualTo(ConnectionMode.OFFLINE)

      with(
        controllerRule.consumeFetchState(
          LoadingState.Ready(
            IssueResponse(
              listOf(ISSUE2),
              emptyList(),
              emptyList(),
              emptyList(),
              Permission.READ_ONLY,
            )
          )
        )
      ) {
        assertThat(mode).isEqualTo(ConnectionMode.OFFLINE)
        assertThat(issues)
          .isEqualTo(LoadingState.Ready(Timed(Selection(ISSUE2, listOf(ISSUE2)), clock.instant())))
      }

      clock.advanceTimeBy(10)
      controllerRule.refreshAndConsumeLoadingState()
      with(
        controllerRule.consumeFetchState(
          LoadingState.Ready(
            IssueResponse(
              listOf(ISSUE1),
              emptyList(),
              emptyList(),
              emptyList(),
              Permission.READ_ONLY,
            )
          ),
          isTransitionToOnlineMode = true,
        )
      ) {
        assertThat(mode).isEqualTo(ConnectionMode.ONLINE)
        assertThat(issues)
          .isEqualTo(LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())))
      }
    }

  @Test
  fun `add and delete note triggers offline mode on network failure`() =
    runBlocking<Unit> {
      val testIssue = ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(notesCount = 1))
      // discard initial loading state, already tested above
      var state =
        controllerRule.consumeInitialState(
          state =
            LoadingState.Ready(
              IssueResponse(
                listOf(testIssue),
                emptyList(),
                emptyList(),
                emptyList(),
                Permission.FULL,
              )
            ),
          notesState = LoadingState.Ready(listOf(NOTE1)),
        )
      assertThat(state.issues.selected().issueDetails.notesCount).isEqualTo(1)

      // Create note and fail the call
      controllerRule.controller.addNote(ISSUE1, NOTE2_BODY)
      state = controllerRule.consumeNext()
      assertThat((state.currentNotes as LoadingState.Ready).value).hasSize(2)
      client.completeCreateNoteCallWith(LoadingState.NetworkFailure("failed"))
      state = controllerRule.consumeNext()
      assertThat((state.currentNotes as LoadingState.Ready).value).hasSize(1)
      state = controllerRule.consumeNext()
      assertThat(state.mode).isEqualTo(ConnectionMode.OFFLINE)
      controllerRule.consumeFetchState(
        state =
          LoadingState.Ready(
            IssueResponse(listOf(testIssue), emptyList(), emptyList(), emptyList(), Permission.FULL)
          ),
        notesState = LoadingState.Ready(listOf(NOTE1)),
      )

      // Flip back into online mode
      controllerRule.refreshAndConsumeLoadingState()
      state =
        controllerRule.consumeFetchState(
          state =
            LoadingState.Ready(
              IssueResponse(
                listOf(testIssue),
                emptyList(),
                emptyList(),
                emptyList(),
                Permission.FULL,
              )
            ),
          notesState = LoadingState.Ready(listOf(NOTE1)),
          isTransitionToOnlineMode = true,
        )
      assertThat(state.mode).isEqualTo(ConnectionMode.ONLINE)

      // Delete note and fail the call
      controllerRule.controller.deleteNote(NOTE1)
      state = controllerRule.consumeNext()
      assertThat((state.currentNotes as LoadingState.Ready).value)
        .containsExactly(NOTE1.copy(state = NoteState.DELETING))
      client.completeDeleteNoteCallWith(LoadingState.NetworkFailure("failed"))
      state = controllerRule.consumeNext()
      assertThat((state.currentNotes as LoadingState.Ready).value).containsExactly(NOTE1)
      state = controllerRule.consumeNext()
      assertThat(state.mode).isEqualTo(ConnectionMode.OFFLINE)
      controllerRule.consumeFetchState(
        state =
          LoadingState.Ready(
            IssueResponse(listOf(testIssue), emptyList(), emptyList(), emptyList(), Permission.FULL)
          ),
        notesState = LoadingState.Ready(listOf(NOTE1)),
      )
    }

  @Test
  fun `when visibility type gets selected it propagates to the model`() = runBlocking {
    // discard initial loading state, already tested above
    controllerRule.consumeInitialState()

    controllerRule.selectVisibilityType(VisibilityType.USER_PERCEIVED)

    var model = controllerRule.consumeNext()
    assertThat(model.filters.visibilityType.selected).isEqualTo(VisibilityType.USER_PERCEIVED)
    assertThat(model.issues).isEqualTo(LoadingState.Loading)

    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS,
        )
      )
    )

    model = controllerRule.consumeNext()
    assertThat(model.filters.visibilityType.selected).isEqualTo(VisibilityType.USER_PERCEIVED)
    assertThat(model.issues).isInstanceOf(LoadingState.Ready::class.java)
    assertThat(model.issues.map { it.value })
      .isEqualTo(LoadingState.Ready(Selection<AppInsightsIssue>(null, emptyList())))

    verify(client)
      .listTopOpenIssues(
        argThat { it.filters.visibilityType == VisibilityType.USER_PERCEIVED },
        any(),
        any(),
        any(),
      )
    return@runBlocking
  }

  @Test
  fun `next and previous events propagates state changes to model`() = runBlocking {
    controllerRule.consumeInitialState(
      LoadingState.Ready(
        IssueResponse(
          listOf(ISSUE1, ISSUE2),
          emptyList(),
          emptyList(),
          emptyList(),
          Permission.READ_ONLY,
        )
      ),
      eventsState = LoadingState.Ready(EventPage(listOf(Event("1"), Event("2"), Event("3")), "")),
    )

    controllerRule.controller.nextEvent()
    var state = controllerRule.consumeNext()
    assertThat(state.selectedEvent).isEqualTo(Event("2"))

    controllerRule.controller.previousEvent()
    state = controllerRule.consumeNext()
    assertThat(state.selectedEvent).isEqualTo(Event("1"))
  }

  @Test
  fun `next event triggers querying of next page of events when token is available`() =
    runBlocking {
      controllerRule.consumeInitialState(
        LoadingState.Ready(
          IssueResponse(
            listOf(ISSUE1, ISSUE2),
            emptyList(),
            emptyList(),
            emptyList(),
            Permission.READ_ONLY,
          )
        ),
        eventsState = LoadingState.Ready(EventPage(listOf(Event("1")), "abc")),
      )

      controllerRule.controller.nextEvent()
      client.completeListEvents(LoadingState.Ready(EventPage(listOf(Event("2")), "")))
      val state = controllerRule.consumeNext()

      assertThat(state.selectedEvent).isEqualTo(Event("2"))
    }

  @Test
  fun `default to using issue sample event when in offline mode`() = runBlocking {
    controllerRule.consumeInitialState()

    controllerRule.enterOfflineMode()
    controllerRule.consumeNext()

    val state =
      controllerRule.consumeFetchState(
        LoadingState.Ready(
          IssueResponse(listOf(ISSUE1), emptyList(), emptyList(), emptyList(), Permission.READ_ONLY)
        )
      )

    assertThat(state.currentEvents)
      .isEqualTo(LoadingState.Ready(DynamicEventGallery(listOf(ISSUE1.sampleEvent), 0, "")))
    assertThat(state.selectedEvent).isEqualTo(ISSUE1.sampleEvent)
  }
}

private fun LoadingState<Timed<Selection<AppInsightsIssue>>>.selected() =
  (this as LoadingState.Ready).value.value.selected!!
