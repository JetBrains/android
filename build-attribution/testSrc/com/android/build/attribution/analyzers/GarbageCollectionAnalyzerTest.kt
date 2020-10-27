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
package com.android.build.attribution.analyzers

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.TaskContainer
import com.android.ide.common.attribution.AndroidGradlePluginAttributionData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GarbageCollectionAnalyzerTest {

  @Test
  fun testAnalyzer() {
    val analyzersProxy = BuildEventsAnalyzersProxy(BuildAttributionWarningsFilter(), TaskContainer(), PluginContainer())
    val analyzersWrapper = BuildEventsAnalyzersWrapper(analyzersProxy.getBuildEventsAnalyzers(),
                                                       analyzersProxy.getBuildAttributionReportAnalyzers())

    analyzersWrapper.onBuildStart()
    analyzersWrapper.onBuildSuccess(AndroidGradlePluginAttributionData(garbageCollectionData = mapOf(("gc1" to 500L), ("gc2" to 200L))))

    assertThat(analyzersProxy.getTotalGarbageCollectionTimeMs()).isEqualTo(700)

    assertThat(analyzersProxy.getGarbageCollectionData()).hasSize(2)
    assertThat(analyzersProxy.getGarbageCollectionData()[0].name).isEqualTo("gc1")
    assertThat(analyzersProxy.getGarbageCollectionData()[1].name).isEqualTo("gc2")
    assertThat(analyzersProxy.getGarbageCollectionData()[0].collectionTimeMs).isEqualTo(500)
    assertThat(analyzersProxy.getGarbageCollectionData()[1].collectionTimeMs).isEqualTo(200)
  }
}