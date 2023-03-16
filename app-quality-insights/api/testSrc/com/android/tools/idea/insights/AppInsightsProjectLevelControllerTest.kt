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

import com.android.flags.junit.FlagRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.time.FakeClock
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.android.tools.idea.insights.client.IssueResponse
import com.android.tools.idea.testing.AndroidExecutorsRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class AppInsightsProjectLevelControllerTest {

  private val projectRule = ProjectRule()
  private val executorsRule = AndroidExecutorsRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(executorsRule).around(controllerRule)!!

  @get:Rule val flagRule = FlagRule(StudioFlags.OFFLINE_MODE_SUPPORT_ENABLED, true)

  private val client: TestCrashlyticsClient
    get() = controllerRule.client

  private val clock: FakeClock
    get() = controllerRule.clock

  @Test
  fun `when controller is initialized it emits a loading state and starts an issue fetch`() =
    runBlocking {
      val model = controllerRule.consumeNext()
      assertThat(model)
        .isEqualTo(
          AppInsightsState(
            Selection(VARIANT1, listOf(VARIANT1, VARIANT2, PLACEHOLDER_CONNECTION)),
            TEST_FILTERS,
            LoadingState.Loading
          )
        )

      client.completeIssuesCallWith(
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS
          )
        )
      )

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          AppInsightsState(
            connections = Selection(VARIANT1, listOf(VARIANT1, VARIANT2, PLACEHOLDER_CONNECTION)),
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
            permission = Permission.FULL
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
              message = "Currently selected app is not linked to a Firebase project.",
              cause = UnconfiguredAppException
            ),
          filters = TEST_FILTERS
        )
      )
  }

  @Test
  fun `when connection gets selected it propagates to the model`() = runBlocking {
    // discard initial loading state, already tested above
    val model = controllerRule.consumeInitialState()

    controllerRule.selectFirebaseConnection(VARIANT2)

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          connections = model.connections.select(VARIANT2),
          issues = LoadingState.Loading,
          filters = model.filters.copy(versions = MultiSelection.emptySelection())
        )
      )
    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS
        )
      )
    )

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          connections = model.connections.select(VARIANT2),
          filters =
            TEST_FILTERS.copy(
              versions =
                MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), listOf(DEFAULT_FETCHED_VERSIONS)),
              devices =
                MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), listOf(DEFAULT_FETCHED_DEVICES)),
              operatingSystems =
                MultiSelection(setOf(DEFAULT_FETCHED_OSES), listOf(DEFAULT_FETCHED_OSES)),
              signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
            ),
          issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant()))
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
        any()
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
        .isEqualTo(Selection(VARIANT1, listOf(VARIANT1, VARIANT2, PLACEHOLDER_CONNECTION)))

      // Available connections get changed but active connection remains, thus no new fetch.
      controllerRule.updateConnections(listOf((VARIANT1)))

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          model.copy(
            connections = Selection(VARIANT1, listOf(VARIANT1)),
            filters =
              TEST_FILTERS.copy(
                versions =
                  MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), listOf(DEFAULT_FETCHED_VERSIONS)),
                devices =
                  MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), listOf(DEFAULT_FETCHED_DEVICES)),
                operatingSystems =
                  MultiSelection(setOf(DEFAULT_FETCHED_OSES), listOf(DEFAULT_FETCHED_OSES)),
                signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
              ),
            issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant()))
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
        .isEqualTo(Selection(VARIANT1, listOf(VARIANT1, VARIANT2, PLACEHOLDER_CONNECTION)))

      // Available connections get changed. Since "VARIANT1" has been removed from the available
      // connections list, active connection falls to "VARIANT2". Thus, new fetch.
      controllerRule.updateConnections(listOf(VARIANT2))

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          model.copy(
            connections = Selection(VARIANT2, listOf(VARIANT2)),
            issues = LoadingState.Loading,
            filters = TEST_FILTERS
          )
        )
      client.completeIssuesCallWith(
        LoadingState.Ready(
          IssueResponse(
            emptyList(),
            listOf(DEFAULT_FETCHED_VERSIONS),
            listOf(DEFAULT_FETCHED_DEVICES),
            listOf(DEFAULT_FETCHED_OSES),
            DEFAULT_FETCHED_PERMISSIONS
          )
        )
      )

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          model.copy(
            connections = Selection(VARIANT2, listOf(VARIANT2)),
            filters =
              TEST_FILTERS.copy(
                versions =
                  MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), listOf(DEFAULT_FETCHED_VERSIONS)),
                devices =
                  MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), listOf(DEFAULT_FETCHED_DEVICES)),
                operatingSystems =
                  MultiSelection(setOf(DEFAULT_FETCHED_OSES), listOf(DEFAULT_FETCHED_OSES)),
                signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
              ),
            issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant()))
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
          any()
        )
      return@runBlocking
    }

  @Test
  fun `correct selection is inferred when connections are updated`() = runBlocking {
    // discard initial loading state, already tested above
    val model = controllerRule.consumeInitialState()

    // mock the inferrer so it prefers VARIANT2
    `when`(controllerRule.connectionInferrer.canBecomeActiveConnection(PLACEHOLDER_CONNECTION))
      .thenReturn(false)
    `when`(controllerRule.connectionInferrer.canBecomeActiveConnection(VARIANT2)).thenReturn(true)

    // Available connections get changed. Since "VARIANT1" has been removed from the available
    // connections list, active connection should fall to VARIANT2 given the preference of the
    // inferrer.
    controllerRule.updateConnections(listOf(PLACEHOLDER_CONNECTION, VARIANT2))

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          connections = Selection(VARIANT2, listOf(PLACEHOLDER_CONNECTION, VARIANT2)),
          issues = LoadingState.Loading,
          filters = TEST_FILTERS
        )
      )
    client.completeIssuesCallWith(
      LoadingState.Ready(
        IssueResponse(
          emptyList(),
          listOf(DEFAULT_FETCHED_VERSIONS),
          listOf(DEFAULT_FETCHED_DEVICES),
          listOf(DEFAULT_FETCHED_OSES),
          DEFAULT_FETCHED_PERMISSIONS
        )
      )
    )

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          connections = Selection(VARIANT2, listOf(PLACEHOLDER_CONNECTION, VARIANT2)),
          filters =
            TEST_FILTERS.copy(
              versions =
                MultiSelection(setOf(DEFAULT_FETCHED_VERSIONS), listOf(DEFAULT_FETCHED_VERSIONS)),
              devices =
                MultiSelection(setOf(DEFAULT_FETCHED_DEVICES), listOf(DEFAULT_FETCHED_DEVICES)),
              operatingSystems =
                MultiSelection(setOf(DEFAULT_FETCHED_OSES), listOf(DEFAULT_FETCHED_OSES)),
              signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
            ),
          issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant()))
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
        any()
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
        .isEqualTo(Selection(VARIANT1, listOf(VARIANT1, VARIANT2, PLACEHOLDER_CONNECTION)))

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
                message = "Currently selected app is not linked to a Firebase project.",
                cause = UnconfiguredAppException
              ),
            filters = TEST_FILTERS
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
            DEFAULT_FETCHED_PERMISSIONS
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
          DEFAULT_FETCHED_PERMISSIONS
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
        any()
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
          DEFAULT_FETCHED_PERMISSIONS
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
          DEFAULT_FETCHED_PERMISSIONS
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
          DEFAULT_FETCHED_PERMISSIONS
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
                    listOf(DEFAULT_FETCHED_VERSIONS, anotherFetchedVersion)
                  )
              ),
          issues = LoadingState.UnknownFailure(null, NoVersionsSelectedException)
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
        any()
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
          DEFAULT_FETCHED_PERMISSIONS
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
              signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
            ),
          issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant())),
          permission = DEFAULT_FETCHED_PERMISSIONS
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
          DEFAULT_FETCHED_PERMISSIONS
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
          Permission.FULL
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
          DEFAULT_FETCHED_PERMISSIONS
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
              signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
            ),
          issues = LoadingState.Ready(Timed(Selection.emptySelection(), clock.instant()))
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
            DEFAULT_FETCHED_PERMISSIONS
          )
        )
      )

    controllerRule.selectIssue(ISSUE1, IssueSelectionSource.LIST)

    controllerRule.consumeNext()

    client.completeDetailsCallWith(LoadingState.Ready(null))
    controllerRule.consumeNext()

    client.completeListNotesCallWith(LoadingState.Ready(emptyList()))
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
                DEFAULT_FETCHED_PERMISSIONS
              )
            ),
          detailsState = LoadingState.Ready(ISSUE1_DETAILS),
          notesState = LoadingState.Ready(emptyList())
        )
      )
      .isEqualTo(
        model.copy(
          issues =
            LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE2, ISSUE1)), clock.instant())),
          currentIssueDetails = LoadingState.Ready(ISSUE1_DETAILS),
          currentNotes = LoadingState.Ready(emptyList()),
          permission = Permission.FULL
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
          DEFAULT_FETCHED_PERMISSIONS
        )
      )
    )

    controllerRule.toggleFatality(FailureType.FATAL)
    controllerRule.consumeNext()
    controllerRule.toggleFatality(FailureType.NON_FATAL)
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
          DEFAULT_FETCHED_PERMISSIONS
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
          DEFAULT_FETCHED_PERMISSIONS
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
            DEFAULT_FETCHED_PERMISSIONS
          )
        )
      )

    assertThat(model.issues).isInstanceOf(LoadingState.Ready::class.java)
    assertThat((model.issues as LoadingState.Ready).value.value)
      .isEqualTo(Selection(ISSUE1, listOf(ISSUE1, ISSUE2)))
    assertThat(model.currentIssueDetails).isEqualTo(LoadingState.Ready(null))
    assertThat(model.currentNotes).isEqualTo(LoadingState.Ready(emptyList<Note>()))

    controllerRule.selectIssue(ISSUE2, IssueSelectionSource.LIST)

    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          issues = model.issues.map { Timed(it.value.select(ISSUE2), clock.instant()) },
          currentIssueDetails = LoadingState.Loading,
          currentNotes = LoadingState.Loading
        )
      )

    client.completeDetailsCallWith(LoadingState.Ready(null))
    controllerRule.consumeNext()

    client.completeListNotesCallWith(LoadingState.Ready(emptyList()))
    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        model.copy(
          issues = model.issues.map { Timed(it.value.select(ISSUE2), clock.instant()) },
          currentIssueDetails = LoadingState.Ready(null),
          currentNotes = LoadingState.Ready(emptyList())
        )
      )
    return@runBlocking
  }

  // TODO(b/228076042): Add test scenario for ANRs when it's added back
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
            DEFAULT_FETCHED_PERMISSIONS
          )
        )
      controllerRule.consumeInitialState(issuesResponse)

      controllerRule.toggleFatality(FailureType.FATAL)
      assertThat(controllerRule.consumeNext().filters.failureTypeToggles.selected)
        .containsExactly(FailureType.NON_FATAL)
      client.completeIssuesCallWith(issuesResponse)
      assertThat(controllerRule.consumeNext().filters.failureTypeToggles.selected)
        .containsExactly(FailureType.NON_FATAL)
      verify(client)
        .listTopOpenIssues(
          argThat {
            it.filters.eventTypes.size == 1 && it.filters.eventTypes.contains(FailureType.NON_FATAL)
          },
          any(),
          any(),
          any()
        )

      controllerRule.toggleFatality(FailureType.NON_FATAL)
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
          any()
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
            DEFAULT_FETCHED_PERMISSIONS
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
            DEFAULT_FETCHED_PERMISSIONS
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
                signal = selectionOf(SignalType.SIGNAL_UNSPECIFIED)
              ),
            issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())),
            currentIssueDetails = LoadingState.Loading,
            currentNotes = LoadingState.Loading
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
            currentNotes = LoadingState.Ready(emptyList())
          )
        )

      controllerRule.refreshAndConsumeLoadingState()
      client.completeIssuesCallWith(LoadingState.UnknownFailure(null))

      assertThat(controllerRule.consumeNext())
        .isEqualTo(
          newModel.copy(
            issues = LoadingState.UnknownFailure(null),
            currentIssueDetails = LoadingState.Ready(null),
            currentNotes = LoadingState.Ready(null)
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
            DEFAULT_FETCHED_PERMISSIONS
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
              DEFAULT_FETCHED_PERMISSIONS
            )
          ),
        detailsState = LoadingState.Ready(ISSUE1_DETAILS)
      )
    assertThat(newModel)
      .isEqualTo(
        startingState.copy(
          issues = LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())),
          currentIssueDetails = LoadingState.Ready(ISSUE1_DETAILS),
          currentNotes = LoadingState.Ready(emptyList())
        )
      )

    controllerRule.refreshAndConsumeLoadingState()
    client.completeIssuesCallWith(LoadingState.UnknownFailure(null))
    assertThat(controllerRule.consumeNext())
      .isEqualTo(
        newModel.copy(
          issues = LoadingState.UnknownFailure(null),
          currentIssueDetails = LoadingState.Ready(null),
          currentNotes = LoadingState.Ready(null)
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
        cause = RevertibleException(snapshot = null, CancellableTimeoutException)
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
          Permission.READ_ONLY
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
            DEFAULT_FETCHED_PERMISSIONS
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
          DEFAULT_FETCHED_PERMISSIONS
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
            currentIssueDetails = LoadingState.Loading,
            currentNotes = LoadingState.Loading
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
          Permission.FULL
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
            Permission.FULL
          )
        ),
      // Ensure we have notes fetched before the following "add" or "delete" actions.
      notesState = LoadingState.Ready(listOf(NOTE1))
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
    assertThat(newModel.issues.selected().pendingRequests).isEqualTo(1)

    client.completeCreateNoteCallWith(LoadingState.PermissionDenied("Permission Denied."))

    newModel = controllerRule.consumeNext()
    assertThat(newModel.permission).isEqualTo(Permission.READ_ONLY)
    assertThat(newModel.currentNotes).isEqualTo(LoadingState.Ready(listOf(NOTE1)))
    assertThat(newModel.issues.selected().pendingRequests).isEqualTo(0)
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
            Permission.FULL
          )
        ),
      notesState = LoadingState.Ready(listOf(NOTE2, NOTE1))
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
    assertThat(newModel.issues.selected().pendingRequests).isEqualTo(1)

    client.completeDeleteNoteCallWith(LoadingState.PermissionDenied("Permission Denied."))

    newModel = controllerRule.consumeNext()
    assertThat(newModel.permission).isEqualTo(Permission.READ_ONLY)
    assertThat(newModel.currentNotes).isEqualTo(LoadingState.Ready(listOf(NOTE2, NOTE1)))
    assertThat(newModel.issues.selected().pendingRequests).isEqualTo(0)
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
          Permission.READ_ONLY
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
          Permission.READ_ONLY
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
            Permission.READ_ONLY
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
              Permission.READ_ONLY
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
              Permission.READ_ONLY
            )
          ),
          isTransitionToOnlineMode = true
        )
      ) {
        assertThat(mode).isEqualTo(ConnectionMode.ONLINE)
        assertThat(issues)
          .isEqualTo(LoadingState.Ready(Timed(Selection(ISSUE1, listOf(ISSUE1)), clock.instant())))
      }
    }

  @Test
  fun `fetch notes in offline mode includes pending note additions and deletions`() = runBlocking {
    val testIssue = ISSUE1.copy(issueDetails = ISSUE1.issueDetails.copy(notesCount = 1))
    // discard initial loading state, already tested above
    var state =
      controllerRule.consumeInitialState(
        state =
          LoadingState.Ready(
            IssueResponse(listOf(testIssue), emptyList(), emptyList(), emptyList(), Permission.FULL)
          ),
        notesState = LoadingState.Ready(listOf(NOTE1))
      )
    assertThat(state.issues.selected().pendingRequests).isEqualTo(0)
    assertThat(state.issues.selected().issueDetails.notesCount).isEqualTo(1)

    // Switch into offline mode to queue some pending actions
    controllerRule.enterOfflineMode()
    controllerRule.consumeNext()
    state =
      controllerRule.consumeFetchState(
        state =
          LoadingState.Ready(
            IssueResponse(listOf(testIssue), emptyList(), emptyList(), emptyList(), Permission.FULL)
          ),
        notesState = LoadingState.Ready(listOf(NOTE1))
      )
    assertThat((state.currentNotes as LoadingState.Ready).value).containsExactly(NOTE1)

    controllerRule.controller.addNote(ISSUE1, NOTE2_BODY)
    controllerRule.consumeNext()
    controllerRule.controller.deleteNote(NOTE1)
    state = controllerRule.consumeNext()
    assertThat(state.issues.selected().pendingRequests).isEqualTo(2)
    assertThat(state.issues.selected().issueDetails.notesCount).isEqualTo(1)

    // Switch into online mode and verify pending actions are retried
    controllerRule.refreshAndConsumeLoadingState()
    controllerRule.consumeFetchState(
      state =
        LoadingState.Ready(
          IssueResponse(
            listOf(testIssue.copy(pendingRequests = 2)),
            emptyList(),
            emptyList(),
            emptyList(),
            Permission.FULL
          )
        ),
      notesState = LoadingState.Ready(listOf(NOTE1)),
      isTransitionToOnlineMode = true
    )

    client.completeCreateNoteCallWith(LoadingState.Ready(NOTE2))
    state = controllerRule.consumeNext()
    assertThat(state.issues.selected().issueDetails.notesCount).isEqualTo(2)
    assertThat(state.issues.selected().pendingRequests).isEqualTo(1)
    client.completeDeleteNoteCallWith(LoadingState.Ready(Unit))
    state = controllerRule.consumeNext()
    assertThat((state.currentNotes as LoadingState.Ready).value).containsExactly(NOTE2)
    assertThat(state.issues.selected().pendingRequests).isEqualTo(0)
    assertThat(state.issues.selected().issueDetails.notesCount).isEqualTo(1)
  }

  @Test
  @Ignore("b/260206459: disable queueing of actions for now")
  fun `add and delete notes during network failure, causes offline mode, results in requests being queued and retried later`() =
    runBlocking {
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
                Permission.FULL
              )
            ),
          notesState = LoadingState.Ready(listOf(NOTE1))
        )
      assertThat(state.issues.selected().pendingRequests).isEqualTo(0)
      assertThat(state.issues.selected().issueDetails.notesCount).isEqualTo(1)

      // Fail add and delete actions.
      controllerRule.controller.addNote(testIssue, NOTE2_BODY)
      state = controllerRule.consumeNext()
      assertThat(state.issues.selected().pendingRequests).isEqualTo(1)
      client.completeCreateNoteCallWith(LoadingState.NetworkFailure(null))

      // The failure above should cause AQI to automatically jump into offline mode and perform
      // fetch.
      controllerRule.consumeNext()
      state =
        controllerRule.consumeFetchState(
          state =
            LoadingState.Ready(
              IssueResponse(
                listOf(testIssue),
                emptyList(),
                emptyList(),
                emptyList(),
                Permission.FULL
              )
            ),
          notesState = LoadingState.Ready(listOf(NOTE1))
        )
      assertThat(state.issues.selected().pendingRequests).isEqualTo(1)
      assertThat(state.issues.selected().issueDetails.notesCount).isEqualTo(1)

      // Delete note in offline mode should go straight to the cache, without calling the client.
      controllerRule.controller.deleteNote(NOTE1)
      controllerRule.consumeNext()

      // Perform refresh to go back online.
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
                Permission.FULL
              )
            ),
          detailsState = LoadingState.Ready(null),
          notesState = LoadingState.Ready(listOf(NOTE1)),
          isTransitionToOnlineMode = true
        )

      val offlineNotes = (state.currentNotes as LoadingState.Ready).value!!
      assertThat(offlineNotes).hasSize(2)
      assertThat(offlineNotes[0].id.noteId).isEmpty()
      assertThat(offlineNotes[0].id.sessionId).isNotEmpty()
      assertThat(offlineNotes[0].state).isEqualTo(NoteState.CREATING)
      assertThat(offlineNotes[1]).isEqualTo(NOTE1.copy(state = NoteState.DELETING))

      // Complete the note retries and check they are propagated to the state.
      client.completeCreateNoteCallWith(LoadingState.Ready(NOTE2))
      state = controllerRule.consumeNext()
      assertThat(state.issues.selected().issueDetails.notesCount).isEqualTo(2)
      client.completeDeleteNoteCallWith(LoadingState.Ready(Unit))
      state = controllerRule.consumeNext()
      assertThat((state.currentNotes as LoadingState.Ready).value).containsExactly(NOTE2)
      assertThat(state.issues.selected().pendingRequests).isEqualTo(0)
      assertThat(state.issues.selected().issueDetails.notesCount).isEqualTo(1)
    }
}

private fun LoadingState<Timed<Selection<AppInsightsIssue>>>.selected() =
  (this as LoadingState.Ready).value.value.selected!!
