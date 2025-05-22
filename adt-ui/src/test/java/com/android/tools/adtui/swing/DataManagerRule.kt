/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.adtui.swing

import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TestDataProvider
import org.junit.rules.ExternalResource

/**
 * By default, [HeadlessDataManager] never traverses across Swing component hierarchy.
 * This rule enables a more realistic production data manager in tests.
 */
class DataManagerRule(private val projectRule: ProjectRule) : ExternalResource() {

  private lateinit var disposable: Disposable

  override fun before() {
    disposable = Disposer.newDisposable()
    HeadlessDataManager.fallbackToProductionDataManager(disposable) // Necessary to properly update toolbar button states.
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(TestDataProvider(projectRule.project), disposable)
  }

  override fun after() {
    Disposer.dispose(disposable)
  }
}