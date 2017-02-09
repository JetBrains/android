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
package com.android.tools.profilers;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.Updatable;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * A helper object that manages the current view and selection ranges for the Studio Profilers.
 */
public final class ProfilerTimeline implements Updatable {

  @VisibleForTesting
  public static final long DEFAULT_VIEW_LENGTH_US = TimeUnit.SECONDS.toMicros(30);

  @NotNull private final Range myDataRangeUs;
  @NotNull private final Range myViewRangeUs;
  @NotNull private final Range mySelectionRangeUs;
  @NotNull private final Range myTooltipRangeUs;
  @NotNull private RelativeTimeConverter myRelativeTimeConverter;
  private boolean myStreaming;
  private boolean myCanStream = true;
  private long myLengthNs;

  public ProfilerTimeline(@NotNull RelativeTimeConverter converter) {
    myDataRangeUs = new Range(0, 0);
    myViewRangeUs = new Range(0, 0);
    mySelectionRangeUs = new Range(); // Empty range
    myTooltipRangeUs = new Range(); // Empty range
    myRelativeTimeConverter = converter;
  }

  /**
   * Change the streaming mode of this timeline. If canStream is currently set to false (e.g. while user is scrolling or zooming), then
   * nothing happens if the caller tries to enable streaming.
   */
  public void setStreaming(boolean isStreaming) {
    if (!myCanStream && isStreaming) {
      isStreaming = false;
    }

    if (myStreaming == isStreaming) {
      return;
    }
    assert myCanStream || !isStreaming;

    myStreaming = isStreaming;
    // Update the ranges as if no time has passed.
    update(0);
  }

  public boolean isStreaming() {
    return myStreaming;
  }

  /**
   * Sets whether the timeline can turn on streaming. If this is set to false and the timeline is currently streaming, streaming mode
   * will be toggled off.
   */
  public void setCanStream(boolean canStream) {
    myCanStream = canStream;
    if (!myCanStream && myStreaming) {
      setStreaming(false);
    }
  }

  public boolean canStream() {
    return myCanStream;
  }

  @NotNull
  public Range getDataRange() {
    return myDataRangeUs;
  }

  @NotNull
  public Range getViewRange() {
    return myViewRangeUs;
  }

  public Range getSelectionRange() {
    return mySelectionRangeUs;
  }

  public Range getTooltipRange() {
    return myTooltipRangeUs;
  }

  @Override
  public void update(long elapsedNs) {
    myLengthNs += elapsedNs;
    long deviceNowNs = myRelativeTimeConverter.convertToAbsoluteTime(myLengthNs);
    long deviceNowUs = TimeUnit.NANOSECONDS.toMicros(deviceNowNs);
    myDataRangeUs.setMax(deviceNowUs);
    if (myStreaming) {
      double viewUs = myViewRangeUs.getLength();
      myViewRangeUs.set(deviceNowUs - viewUs, deviceNowUs);
    }
  }

  public void zoom(double deltaUs, double percent) {
    setStreaming(false);
    double minUs = myViewRangeUs.getMin() - deltaUs * percent;
    double maxUs = myViewRangeUs.getMax() + deltaUs * (1 - percent);
    if (minUs < myDataRangeUs.getMin()) {
      maxUs += myDataRangeUs.getMin() - minUs;
      minUs = myDataRangeUs.getMin();
    }
    if (maxUs > myDataRangeUs.getMax()) {
      minUs -= maxUs - myDataRangeUs.getMax();
      maxUs = myDataRangeUs.getMax();
    }
    // minUs could have gone past again.
    minUs = Math.max(minUs, myDataRangeUs.getMin());
    myViewRangeUs.set(minUs, maxUs);
  }

  public void pan(double deltaUs) {
    if (deltaUs < 0) {
      setStreaming(false);
    }
    if (myViewRangeUs.getMin() + deltaUs < myDataRangeUs.getMin()) {
      deltaUs = myDataRangeUs.getMin() - myViewRangeUs.getMin();
    } else if (myViewRangeUs.getMax() + deltaUs > myDataRangeUs.getMax()) {
      deltaUs = myDataRangeUs.getMax() - myViewRangeUs.getMax();
    }
    myViewRangeUs.shift(deltaUs);
  }

  public void reset(@NotNull RelativeTimeConverter converter) {
    myRelativeTimeConverter = converter;
    myLengthNs = 0;
    double us = TimeUnit.NANOSECONDS.toMicros(converter.getDeviceStartTimeNs());
    myDataRangeUs.set(us, us);
    myViewRangeUs.set(us - DEFAULT_VIEW_LENGTH_US, us);
  }
}
