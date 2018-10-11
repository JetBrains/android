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
import com.google.common.primitives.Doubles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResizingAxisComponentModel extends AxisComponentModel {
  @Nullable private Range myGlobalRange;
  double myGlobalRangeMin;

  private ResizingAxisComponentModel(@NotNull BaseBuilder<ResizingAxisComponentModel> builder) {
    super(builder);

    myGlobalRange = builder.myGlobalRange;
    if (myGlobalRange != null) {
      myGlobalRange.addDependency(this).onChange(Range.Aspect.RANGE, this::onGlobalRangeUpdated);
      myGlobalRangeMin = myGlobalRange.getMin();
    }
    updateImmediately();
  }

  private void onGlobalRangeUpdated() {
    // We only care about the global range min, so we fire the aspect only when that changes.
    assert myGlobalRange != null;
    if (Doubles.compare(myGlobalRangeMin, myGlobalRange.getMin()) != 0) {
      myGlobalRangeMin = myGlobalRange.getMin();
      updateImmediately();
    }
  }

  @Override
  public void updateImmediately() {
    myFirstUpdate = false;
    changed(Aspect.AXIS);
  }

  @Override
  public double getZero() {
    return myGlobalRange != null ? myGlobalRange.getMin() : myRange.getMin();
  }

  @Override
  public double getDataRange() {
    return myGlobalRange == null ? super.getDataRange() : myGlobalRange.getLength();
  }

  public static class Builder extends BaseBuilder<ResizingAxisComponentModel> {
    /**
     * @param range     A Range object this AxisComponent listens to for the min/max values.
     * @param formatter Formatter used for determining the tick marker and labels that need to be rendered.
     */
    public Builder(@NotNull Range range, @NotNull BaseAxisFormatter formatter) {
      super(range, formatter);
    }

    /**
     * @param globalRange sets the global range on the AxisComponent. The global range also sets the relative zero point.
     */
    @NotNull
    public Builder setGlobalRange(@NotNull Range globalRange) {
      myGlobalRange = globalRange;
      return this;
    }

    @NotNull
    @Override
    public ResizingAxisComponentModel build() {
      return new ResizingAxisComponentModel(this);
    }
  }
}
