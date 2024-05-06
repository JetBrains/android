/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

public class DetailedMemoryUsage extends MemoryUsage {
  private static final String JAVA_MEM = "Java";
  private static final String NATIVE_MEM = "Native";
  private static final String GRAPHICS_MEM = "Graphics";
  private static final String STACK_MEM = "Stack";
  private static final String CODE_MEM = "Code";
  private static final String OTHERS_MEM = "Others";
  private static final String ALLOCATED = "Allocated";

  @NotNull private final StudioProfilers myProfilers;
  @NotNull private final Range myObjectsRange;
  @NotNull private final RangedContinuousSeries myJavaSeries;
  @NotNull private final RangedContinuousSeries myNativeSeries;
  @NotNull private final RangedContinuousSeries myGraphicsSeries;
  @NotNull private final RangedContinuousSeries myStackSeries;
  @NotNull private final RangedContinuousSeries myCodeSeries;
  @NotNull private final RangedContinuousSeries myOtherSeries;
  @NotNull private final RangedContinuousSeries myObjectsSeries;
  @NotNull private final DurationDataModel<GcDurationData> myGcDurations;
  @NotNull private final DurationDataModel<AllocationSamplingRateDurationData> myAllocationSamplingRateDurations;

  /**
   * DetailedMemoryUsage overloaded constructor without stage passed in.
   * So that this constructor can be called in the places which doesn't have access to BaseStreamingMemoryProfilerStage
   *
   * @param profilers
   * @param gcStatsModel
   * @param allocationSamplingRateDuration
   */
  public DetailedMemoryUsage(@NotNull StudioProfilers profilers,
                             @NotNull DurationDataModel<GcDurationData> gcStatsModel,
                             @NotNull DurationDataModel<AllocationSamplingRateDurationData> allocationSamplingRateDuration) {
    super(profilers);

    myProfilers = profilers;
    myObjectsRange = new Range(0, 0);

    myJavaSeries = createRangedSeries(profilers, JAVA_MEM, getMemoryRange(),
                                      UnifiedEventDataSeries.DEFAULT_GROUP_ID,
                                      UnifiedEventDataSeries
                                        .fromFieldToDataExtractor(e -> (long)e.getMemoryUsage().getJavaMem() * KB_TO_B));
    myNativeSeries = createRangedSeries(profilers, NATIVE_MEM, getMemoryRange(),
                                        UnifiedEventDataSeries.DEFAULT_GROUP_ID,
                                        UnifiedEventDataSeries
                                          .fromFieldToDataExtractor(e -> (long)e.getMemoryUsage().getNativeMem() * KB_TO_B));
    myGraphicsSeries = createRangedSeries(profilers, GRAPHICS_MEM, getMemoryRange(),
                                          UnifiedEventDataSeries.DEFAULT_GROUP_ID,
                                          UnifiedEventDataSeries
                                            .fromFieldToDataExtractor(e -> (long)e.getMemoryUsage().getGraphicsMem() * KB_TO_B));
    myStackSeries = createRangedSeries(profilers, STACK_MEM, getMemoryRange(),
                                       UnifiedEventDataSeries.DEFAULT_GROUP_ID,
                                       UnifiedEventDataSeries
                                         .fromFieldToDataExtractor(e -> (long)e.getMemoryUsage().getStackMem() * KB_TO_B));
    myCodeSeries = createRangedSeries(profilers, CODE_MEM, getMemoryRange(),
                                      UnifiedEventDataSeries.DEFAULT_GROUP_ID,
                                      UnifiedEventDataSeries
                                        .fromFieldToDataExtractor(e -> (long)e.getMemoryUsage().getCodeMem() * KB_TO_B));
    myOtherSeries = createRangedSeries(profilers, OTHERS_MEM, getMemoryRange(),
                                       UnifiedEventDataSeries.DEFAULT_GROUP_ID,
                                       UnifiedEventDataSeries
                                         .fromFieldToDataExtractor(e -> (long)e.getMemoryUsage().getOthersMem() * KB_TO_B));

    AllocStatsDataSeries series = new AllocStatsDataSeries(myProfilers,
                                                           sample -> (long)(sample.getJavaAllocationCount() - sample.getJavaFreeCount()));
    myObjectsSeries = new RangedContinuousSeries(ALLOCATED, profilers.getTimeline().getViewRange(), getObjectsRange(), series);

    myGcDurations = gcStatsModel;
    myAllocationSamplingRateDurations = allocationSamplingRateDuration;

    add(myJavaSeries);
    add(myNativeSeries);
    add(myGraphicsSeries);
    add(myStackSeries);
    add(myCodeSeries);
    add(myOtherSeries);
    add(myObjectsSeries);

    // Listen to range changes, because others (e.g. AxisComponentModel) may adjust these
    getMemoryRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> changed(Aspect.LINE_CHART));
    getObjectsRange().addDependency(this).onChange(Range.Aspect.RANGE, () -> changed(Aspect.LINE_CHART));
  }

  @VisibleForTesting
  public DetailedMemoryUsage(@NotNull StudioProfilers profilers, @NotNull BaseStreamingMemoryProfilerStage memoryProfilerStage) {
    this(profilers, memoryProfilerStage.getGcStatsModel(), memoryProfilerStage.getAllocationSamplingRateDurations());
  }

  @NotNull
  public Range getObjectsRange() {
    return myObjectsRange;
  }

  @NotNull
  public RangedContinuousSeries getJavaSeries() {
    return myJavaSeries;
  }

  @NotNull
  public RangedContinuousSeries getNativeSeries() {
    return myNativeSeries;
  }

  @NotNull
  public RangedContinuousSeries getGraphicsSeries() {
    return myGraphicsSeries;
  }

  @NotNull
  public RangedContinuousSeries getStackSeries() {
    return myStackSeries;
  }

  @NotNull
  public RangedContinuousSeries getCodeSeries() {
    return myCodeSeries;
  }

  @NotNull
  public RangedContinuousSeries getOtherSeries() {
    return myOtherSeries;
  }

  @NotNull
  public RangedContinuousSeries getObjectsSeries() {
    return myObjectsSeries;
  }

  @NotNull
  public DurationDataModel<GcDurationData> getGcDurations() {
    return myGcDurations;
  }

  @NotNull
  public DurationDataModel<AllocationSamplingRateDurationData> getAllocationSamplingRateDurations() {
    return myAllocationSamplingRateDurations;
  }

  @Override
  protected String getTotalSeriesLabel() {
    return "Total";
  }
}
