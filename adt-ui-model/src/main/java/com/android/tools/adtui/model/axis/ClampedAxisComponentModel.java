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
package com.android.tools.adtui.model.axis;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import org.jetbrains.annotations.NotNull;

/**
 * An axis component model to be used with an {@link Updater}, which supports animation. Make sure to call
 * {@link Updater#register(Updatable)} on this.
 */
public final class ClampedAxisComponentModel extends AxisComponentModel implements Updatable {
  // This needs to be removed once AxisComponentModel separates the target lerp Range from the current lerp state Range.
  private boolean myIsUpdating = false;

  private ClampedAxisComponentModel(@NotNull BaseBuilder<ClampedAxisComponentModel> builder) {
    super(builder);
  }

  @Override
  public void update(long elapsedNs) {
    if (myIsUpdating) {
      return;
    }

    myIsUpdating = true; // Prevent feedback loops.

    boolean needsUpdate = false;

    double clampedMaxTarget = calculateClampedMaxTarget();
    double max = myFirstUpdate
                 ? clampedMaxTarget
                 : Updater.lerp(myRange.getMax(), clampedMaxTarget, Updater.DEFAULT_LERP_FRACTION, elapsedNs,
                                (float)(clampedMaxTarget * Updater.DEFAULT_LERP_THRESHOLD_RATIO));
    if (Double.compare(max, myRange.getMax()) != 0 || myFirstUpdate) {  // Precise comparison, since the lerp snaps to the target value.
      myRange.setMax(max);
      needsUpdate = true;
    }

    myFirstUpdate = false;

    if (needsUpdate) {
      //TODO also change when data changes
      changed(Aspect.AXIS);
    }

    myIsUpdating = false;
  }

  @Override
  public void updateImmediately() {
    update(0);
  }

  private double calculateClampedMaxTarget() {
    // During the update phase, the axis updates the range's max to a new target based on whether myClampToMajorTicks is enabled
    //    - This would increase the max to an integral multiplier of the major interval.
    double maxTarget = myRange.getMax() - getZero();
    double rangeTarget = myRange.getLength();

    // TODO Handle non-zero min offsets. Currently these features are used only for y axes and a non-zero use case does not exist yet.
    long majorInterval = myFormatter.getMajorInterval(rangeTarget);
    float majorNumTicksTarget = (float)Math.ceil(maxTarget / majorInterval);
    return majorNumTicksTarget * majorInterval + getZero();
  }

  public static class Builder extends AxisComponentModel.BaseBuilder<ClampedAxisComponentModel> {
    /**
     * @param range     A Range object this AxisComponent listens to for the min/max values.
     * @param formatter Formatter used for determining the tick marker and labels that need to be rendered.
     */
    public Builder(@NotNull Range range, @NotNull BaseAxisFormatter formatter) {
      super(range, formatter);
    }

    @NotNull
    @Override
    public ClampedAxisComponentModel build() {
      return new ClampedAxisComponentModel(this);
    }
  }
}
