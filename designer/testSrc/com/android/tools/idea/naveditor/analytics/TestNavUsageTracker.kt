/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.analytics

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettingsData
import com.android.tools.idea.common.model.NlModel
import org.mockito.Mockito
import java.io.Closeable

// Open for testing
open class TestNavUsageTracker private constructor(override val model: NlModel) : NavNopTracker(), Closeable {
  override fun close() {
    NavUsageTracker.MANAGER.cleanAfterTesting(model)
  }

  companion object {
    fun create(model: NlModel): TestNavUsageTracker {
      val settings = AnalyticsSettingsData()
      settings.optedIn = true
      AnalyticsSettings.setInstanceForTest(settings)
      val realTracker = TestNavUsageTracker(model)
      val tracker = Mockito.spy(realTracker)
      NavUsageTracker.MANAGER.setInstanceForTest(model, tracker)
      return tracker
    }

  }
}