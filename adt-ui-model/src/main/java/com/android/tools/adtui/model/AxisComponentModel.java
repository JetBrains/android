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
package com.android.tools.adtui.model;

import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AxisComponentModel extends AspectModel<AxisComponentModel.Aspect> implements Updatable {

  public enum Aspect {
    AXIS
  }

  @NotNull private final Range myRange;
  @NotNull private final BaseAxisFormatter myFormatter;
  @Nullable private Range myGlobalRange;

  private boolean myClampToMajorTicks;

  @NotNull private String myLabel;

  /**
   * During the first update, skip the y range interpolation and snap to the initial max value.
   */
  private boolean myFirstUpdate = true;

  private AxisComponentModel(@NotNull Builder builder) {
    myRange = builder.myRange;
    myFormatter = builder.myFormatter;
    myClampToMajorTicks = builder.myClampToMajorTicks;
    myGlobalRange = builder.myGlobalRange;
    myLabel = builder.myLabel;
  }

  @Override
  public void update(long elapsedNs) {
    boolean needsUpdate = false;

    // During the animate/updateData phase, the axis updates the range's max to a new target based on whether myClampToMajorTicks is enabled
    //    - This would increase the max to an integral multiplier of the major interval.
    if (myClampToMajorTicks) {
      double maxTarget = myRange.getMax() - getZero();
      double rangeTarget = myRange.getLength();
      double clampedMaxTarget;

      // TODO Handle non-zero min offsets. Currently these features are used only for y axes and a non-zero use case does not exist yet.
      long majorInterval = myFormatter.getMajorInterval(rangeTarget);

      float majorNumTicksTarget = (float)Math.ceil(maxTarget / majorInterval);
      clampedMaxTarget = majorNumTicksTarget * majorInterval;

      clampedMaxTarget += getZero();
      double max = myFirstUpdate
                   ? clampedMaxTarget
                   : Updater.lerp(myRange.getMax(), clampedMaxTarget, Updater.DEFAULT_LERP_FRACTION, elapsedNs,
                                  (float)(clampedMaxTarget * Updater.DEFAULT_LERP_THRESHOLD_PERCENTAGE));
      if (Double.compare(max, myRange.getMax()) != 0) {  // Precise comparison, since the lerp snaps to the target value.
        myRange.setMax(max);
        needsUpdate = true;
      }
    }
    else {
      // TODO(b/80085190): Move {@link AxisComponent#calculateMarkers} implementation here.
      needsUpdate = true;
    }
    myFirstUpdate = false;

    if (needsUpdate) {
      //TODO also change when data changes
      changed(Aspect.AXIS);
    }
  }

  @Override
  public void reset() {
    myFirstUpdate = true;
    update(0);
  }

  @NotNull
  public String getLabel() {
    return myLabel;
  }

  @NotNull
  public Range getRange() {
    return myRange;
  }

  @Nullable
  public Range getGlobalRange() {
    return myGlobalRange;
  }

  @NotNull
  public BaseAxisFormatter getFormatter() {
    return myFormatter;
  }

  public double getZero() {
    return myGlobalRange != null ? myGlobalRange.getMin() : myRange.getMin();
  }

  public static class Builder {
    @NotNull private final Range myRange;
    @NotNull private final BaseAxisFormatter myFormatter;
    private final boolean myClampToMajorTicks;

    @Nullable private Range myGlobalRange;
    @NotNull private String myLabel = "";

    /**
     * @param range             A Range object this AxisComponent listens to for the min/max values.
     * @param formatter         Formatter used for determining the tick marker and labels that need to be rendered.
     * @param clampToMajorTicks If true, the AxisComponent will extend itself to the next major tick based on the current max value.
     */
    public Builder(@NotNull Range range, @NotNull BaseAxisFormatter formatter, boolean clampToMajorTicks) {
      myRange = range;
      myFormatter = formatter;
      myClampToMajorTicks = clampToMajorTicks;
    }

    /**
     * @param globalRange sets the global range on the AxisComponent. The global range also sets the relative zero point.
     */
    @NotNull
    public Builder setGlobalRange(@NotNull Range globalRange) {
      myGlobalRange = globalRange;
      return this;
    }

    /**
     * Sets the content of the axis's label.
     */
    @NotNull
    public Builder setLabel(@NotNull String label) {
      myLabel = label;
      return this;
    }

    @NotNull
    public AxisComponentModel build() {
      return new AxisComponentModel(this);
    }
  }
}
