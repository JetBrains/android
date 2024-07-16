/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.idea.testing

import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.ProjectRule
import org.junit.rules.ExternalResource

/**
 * A `TestRule` that waits for indexing to be completed.
 *
 * Useful for tests that depend on indices but also prevents flakiness in leak detection and
 * eliminates warning logs.
 */
class WaitForIndexRule(private val projectRule: ProjectRule) : ExternalResource() {
  override fun before() {
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)
  }
}
