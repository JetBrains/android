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
package org.jetbrains.android.facet

import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class GradleSourceProviderManagerTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun selfDisposesOnFacetConfigurationChange() {
    projectRule.load(TestProjectPaths.SIMPLE_APPLICATION)
    val facet = AndroidFacet.getInstance(projectRule.project.findAppModule() )!!
    val sourceProviderManagerBeforeNotification = facet.sourceProviders
    projectRule.requestSyncAndWait()
    val sourceProviderManagerAfterNotification = facet.sourceProviders
    assertThat(sourceProviderManagerAfterNotification).isNotSameAs(sourceProviderManagerBeforeNotification)
  }
}