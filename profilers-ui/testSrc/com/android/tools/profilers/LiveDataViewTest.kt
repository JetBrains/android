/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers

import com.android.tools.adtui.RangeTooltipComponent
import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.model.TooltipModel
import com.android.tools.adtui.model.ViewBinder
import com.android.tools.profilers.LiveDataModel
import com.android.tools.profilers.LiveDataView
import com.android.tools.profilers.Stage
import com.android.tools.profilers.StageView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class LiveDataViewTest {
  private lateinit var myModel: LiveDataModel

  @Before
  fun setUp() {
    myModel = Mockito.mock(LiveDataModel::class.java, Mockito.RETURNS_DEEP_STUBS)
  }

  @Test
  fun testLiveDataViewInstanceCreation() {
    val liveDataView: LiveDataView<LiveDataModel>
    liveDataView = FakeAllocationView(myModel)
    assertThat(liveDataView).isNotNull()
  }

  @Test
  fun testGetComponentReturnsAComponent() {
    val liveDataView: LiveDataView<LiveDataModel>
    liveDataView = FakeAllocationView(myModel)
    assertThat(liveDataView).isNotNull()
    assertThat(liveDataView.component).isNotNull()
  }
}

class FakeAllocationView(model: LiveDataModel) : LiveDataView<LiveDataModel>(model) {
  /**
   * Helper function to register LiveData components on tooltips. This function is responsible for setting the
   * active tooltip on the stage when a mouse enters the desired component.
   */
  override fun registerTooltip(binder: ViewBinder<StageView<Stage<*>>, TooltipModel, TooltipView>?,
                               tooltip: RangeTooltipComponent,
                               stage: Stage<*>?) {
    throw NotImplementedError("UnitTestFake")
  }

  override fun populateUi(tooltipComponent: RangeTooltipComponent) {
    throw NotImplementedError("UnitTestFake")
  }
}