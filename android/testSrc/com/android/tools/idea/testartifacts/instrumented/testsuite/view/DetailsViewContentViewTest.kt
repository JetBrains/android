/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.intellij.mock.MockApplication
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [DetailsViewContentView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class DetailsViewContentViewTest {
  @get:Rule
  val edtRule = EdtRule()
  @get:Rule val disposableRule = DisposableRule()

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    MockApplication.setUp(disposableRule.disposable)
  }

  // TODO: Remove this test case when we have a real implementation of DetailsViewContentView.
  @Test
  fun constructView() {
    val view = DetailsViewContentView()
  }
}