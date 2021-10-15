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
package com.android.tools.idea.logcat.filters

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.logcat.filters.LogcatFilterComponent.FilterChangeListener
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

/**
 * Tests for [LogcatFilterComponent]
 */
class LogcatFilterComponentTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  @Test
  fun filter_callsListeners() {
    val logcatFilterComponent = LogcatFilterComponent("key", 5)
    val listener = mock<FilterChangeListener>()
    logcatFilterComponent.addFilterChangeListener(listener)

    logcatFilterComponent.filter()

    verify(listener).onFilterChange(logcatFilterComponent)
  }
}
