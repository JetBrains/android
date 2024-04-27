/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.AspectObserver;
import org.jetbrains.annotations.NotNull;

import static org.junit.Assert.assertEquals;

public class MemoryAspectObserver extends AspectObserver {
  protected int myAllocationAspectCount;
  protected int myCurrentLoadedCaptureAspectCount;
  protected int myCurrentLoadingCaptureAspectCount;
  protected int myCurrentGroupingAspectCount;
  protected int myCurrentHeapAspectCount;
  protected int myCurrentClassAspectCount;
  protected int myCurrentInstanceAspectCount;
  protected int myCurrentFieldPathAspectCount;

  public MemoryAspectObserver(@NotNull AspectModel<MemoryProfilerAspect> model,
                              @NotNull AspectModel<CaptureSelectionAspect> selectionModel) {
    resetCounts();

    model.addDependency(this)
      .onChange(MemoryProfilerAspect.TRACKING_ENABLED, () -> ++myAllocationAspectCount);
    selectionModel.addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_LOADING_CAPTURE, () -> ++myCurrentLoadingCaptureAspectCount)
      .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, () -> ++myCurrentLoadedCaptureAspectCount)
      .onChange(CaptureSelectionAspect.CLASS_GROUPING, () -> ++myCurrentGroupingAspectCount)
      .onChange(CaptureSelectionAspect.CURRENT_HEAP, () -> ++myCurrentHeapAspectCount)
      .onChange(CaptureSelectionAspect.CURRENT_CLASS, () -> ++myCurrentClassAspectCount)
      .onChange(CaptureSelectionAspect.CURRENT_INSTANCE, () -> ++myCurrentInstanceAspectCount)
      .onChange(CaptureSelectionAspect.CURRENT_FIELD_PATH, () -> ++myCurrentFieldPathAspectCount);
  }

  public void assertAndResetCounts(int legacyAllocationAspect,
                                   int currentLoadingCaptureAspectCount,
                                   int currentLoadedCaptureAspect,
                                   int currentGroupingAspectCount,
                                   int currentHeapAspectCount,
                                   int currentClassAspectCount,
                                   int currentInstanceAspectCount,
                                   int currentFieldPathAspectCount) {
    assertEquals(legacyAllocationAspect, myAllocationAspectCount);
    assertEquals(currentLoadingCaptureAspectCount, myCurrentLoadingCaptureAspectCount);
    assertEquals(currentLoadedCaptureAspect, myCurrentLoadedCaptureAspectCount);
    assertEquals(currentGroupingAspectCount, myCurrentGroupingAspectCount);
    assertEquals(currentHeapAspectCount, myCurrentHeapAspectCount);
    assertEquals(currentClassAspectCount, myCurrentClassAspectCount);
    assertEquals(currentInstanceAspectCount, myCurrentInstanceAspectCount);
    assertEquals(currentFieldPathAspectCount, myCurrentFieldPathAspectCount);
    resetCounts();
  }

  public void resetCounts() {
    myAllocationAspectCount = 0;
    myCurrentLoadingCaptureAspectCount = 0;
    myCurrentLoadedCaptureAspectCount = 0;
    myCurrentGroupingAspectCount = 0;
    myCurrentHeapAspectCount = 0;
    myCurrentClassAspectCount = 0;
    myCurrentInstanceAspectCount = 0;
    myCurrentFieldPathAspectCount = 0;
  }
}
