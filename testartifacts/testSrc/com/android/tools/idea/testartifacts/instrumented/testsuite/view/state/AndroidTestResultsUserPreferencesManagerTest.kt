/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view.state

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.test.assertEquals

/**
 * Unit tests for [AndroidTestResultsUserPreferencesManager].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class AndroidTestResultsUserPreferencesManagerTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)

  @get:Rule
  val mockitoJunitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Mock
  lateinit var mockAndroidTestRunConfiguration: AndroidTestRunConfiguration

  @Test
  fun getUserPreferredColumnWidthWithNoSavedPreferenceReturnsDefaultWidth() {
    whenever(mockAndroidTestRunConfiguration.project).thenReturn(projectRule.project)
    val androidTestResultsUserPreferencesManager = AndroidTestResultsUserPreferencesManager(mockAndroidTestRunConfiguration, hashSetOf("device1Id"))
    val deviceIds = HashSet<String>()
    deviceIds.add("device1")

    val preferredWidth = androidTestResultsUserPreferencesManager.getUserPreferredColumnWidth("column1", 90)
    assertEquals(90, preferredWidth)
  }

  @Test
  fun setUserPreferredColumnWidthAndThenGetUserPreferredColumnWidth() {
    whenever(mockAndroidTestRunConfiguration.project).thenReturn(projectRule.project)
    val androidTestResultsUserPreferencesManager = AndroidTestResultsUserPreferencesManager(mockAndroidTestRunConfiguration, hashSetOf("device1Id"))
    val deviceIds = HashSet<String>()
    deviceIds.add("device1")

    androidTestResultsUserPreferencesManager.setUserPreferredColumnWidth("column1", 120)
    val preferredWidth = androidTestResultsUserPreferencesManager.getUserPreferredColumnWidth("column1", 90)
    assertEquals(120, preferredWidth)
  }
}