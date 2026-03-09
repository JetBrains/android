/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.project

import com.android.tools.idea.model.queryIsMainManifestIndexReady
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import io.ktor.util.reflect.instanceOf
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class AndroidRunConfigurationsManifestIndexNotReadyTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `create run configurations creates a single android run configuration when the manifest index is not ready`() =
    runBlocking<Unit> {
      val isManifestIndexReady = projectRule.module.androidFacet?.queryIsMainManifestIndexReady() ?: false
      assertFalse(isManifestIndexReady)

      AndroidRunConfigurations.instance.createRunConfigurations(projectRule.project)

      val runConfigurations = RunManager.getInstance(projectRule.project).allConfigurationsList
      assertThat(runConfigurations).hasSize(1)
      assertThat(runConfigurations.single().type).instanceOf(AndroidRunConfigurationType::class)

      // Wear configurations are not created when the index is not ready as they need the index to be ready to
      // check if the watch face feature is set.
    }
}
