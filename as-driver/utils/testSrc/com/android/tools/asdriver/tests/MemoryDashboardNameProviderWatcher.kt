/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Watcher that provides generates a Perfgate memory usage dashboard name.
 * To use it please include the following lines to your test:
 * <code>
 *
 * @JvmField
 * @Rule
 * var watcher = MemoryDashboardNameProviderWatcher()
 *
 * </code>
 */
class MemoryDashboardNameProviderWatcher : TestWatcher() {
  var dashboardName: String? = null

  override fun starting(description: Description) {
    // remove package from the class name and concatenate with the test name
    dashboardName = "${description.className.substringAfterLast('.')}_${description.methodName}".replace(' ', '_')
  }
}
