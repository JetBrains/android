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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AxisComponentModel extends AspectModel<AxisComponentModel.Aspect> implements Updatable  {

  public Range getGlobalRange() {
    return myGlobalRange;
  }

  public void setClampToMajorTicks(boolean clampToMajorTicks) {
    myClampToMajorTicks = clampToMajorTicks;
  }

  public enum AxisOrientation {
    LEFT,
    BOTTOM,
    RIGHT,
    TOP
  }

  public enum Aspect {
    AXIS
  }


  @NotNull private final Range myRange;
  @NotNull private final BaseAxisFormatter myFormatter;
  @NotNull private final AxisOrientation myOrientation;
  @Nullable private Range myGlobalRange;

  private boolean myClampToMajorTicks = false;

  @NotNull private String myLabel = "";

  /**
   * During the first update, skip the y range interpolation and snap to the initial max value.
   */
  private boolean myFirstUpdate = true;


  /**
   * @param range       a Range object this AxisComponent listens to for the min/max values.
   * @param formatter   formatter used for determining the tick marker and labels that need to be rendered.
   * @param orientation the orientation of the axis.
   */
  // TODO: MOVE ORIENTATION TO THE UI
  public AxisComponentModel(@NotNull Range range, @NotNull BaseAxisFormatter formatter, @NotNull AxisOrientation orientation) {
    myRange = range;
    myFormatter = formatter;
    myOrientation = orientation;
  }

  @NotNull
  public String getLabel() {
    return myLabel;
  }

  @NotNull
  public AxisOrientation getOrientation() {
    return myOrientation;
  }

  @NotNull
  public Range getRange() {
    return myRange;
  }

  @NotNull
  public BaseAxisFormatter getFormatter() {
    return myFormatter;
  }

  public double getZero() {
    return myGlobalRange != null ? myGlobalRange.getMin() : myRange.getMin();
  }

  @Override
  public void update(float elapsed) {
    double maxTarget = myRange.getMax() - getZero();
    double rangeTarget = myRange.getLength();
    double clampedMaxTarget;

    // During the animate/updateData phase, the axis updates the range's max to a new target based on whether myClampToMajorTicks is enabled
    //    - This would increase the max to an integral multiplier of the major interval.
    // TODO Handle non-zero min offsets. Currently these features are used only for y axes and a non-zero use case does not exist yet.
    long majorInterval = myFormatter.getMajorInterval(rangeTarget);

    float majorNumTicksTarget = myClampToMajorTicks ? (float)Math.ceil(maxTarget / majorInterval) : (float)(maxTarget / majorInterval);
    clampedMaxTarget = majorNumTicksTarget * majorInterval;

    clampedMaxTarget += getZero();
    float fraction = myFirstUpdate ? 1f : Updater.DEFAULT_LERP_FRACTION;
    myRange.setMax(Updater.lerp(myRange.getMax(), clampedMaxTarget, fraction, elapsed,
                                (float)(clampedMaxTarget * Updater.DEFAULT_LERP_THRESHOLD_PERCENTAGE)));
    myFirstUpdate = false;

    //TODO also change when data changes
    changed(Aspect.AXIS);
  }

  /**
   * @param globalRange sets the global range on the AxisComponent. The global range also sets the relative zero point.
   */
  public AxisComponentModel setGlobalRange(@NotNull Range globalRange) {
    myGlobalRange = globalRange;
    return this;
  }

  /**
   * Sets the content of the axis' label.
   */
  public AxisComponentModel setLabel(@NotNull String label) {
    myLabel = label;
    return this;
  }

  /**
   * @param clampToMajorTicks if true, the AxisComponent will extend itself to the next major tick based on the current max value.
   */
  public AxisComponentModel clampToMajorTicks(boolean clampToMajorTicks) {
    myClampToMajorTicks = clampToMajorTicks;
    return this;
  }
}
