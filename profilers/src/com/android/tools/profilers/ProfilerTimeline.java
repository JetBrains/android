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
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * A helper object that manages the current view and selection ranges for the Studio Profilers.
 */
public final class ProfilerTimeline {

  private static final long DEFAULT_VIEW_LENGTH_US = TimeUnit.SECONDS.toMicros(30);
  private static final long DEFAULT_BUFFER_US = TimeUnit.SECONDS.toMicros(1);

  @NotNull private final Range myDataRangeUs;
  @NotNull private final Range myViewRangeUs;
  @NotNull private final Range mySelectionRangeUs;
  private long myBufferUs;
  private boolean myStreaming;
  private boolean myCanStream = true;

  public ProfilerTimeline(@NotNull Range dataRangeUs) {
    myBufferUs = DEFAULT_BUFFER_US;
    myDataRangeUs = dataRangeUs;
    myViewRangeUs = new Range(myDataRangeUs.getMin(), myDataRangeUs.getMax());
    myViewRangeUs.shift(-myBufferUs);
    mySelectionRangeUs = new Range(); // Empty range
  }

  public void resetZoom() {
    double currentMax = myViewRangeUs.getMax();
    myViewRangeUs.set(currentMax - DEFAULT_VIEW_LENGTH_US, currentMax);
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
    if (myStreaming) {
      double deltaUs = (myDataRangeUs.getMax() - myBufferUs) - myViewRangeUs.getMax();
      myViewRangeUs.shift(deltaUs);
    }
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

  public long getViewBuffer() {
    return myBufferUs;
  }

  @NotNull
  public Range getDataRange() {
    return myDataRangeUs;
  }

  @NotNull
  public Range getViewRange() {
    return myViewRangeUs;
  }

  /**
   * Clamps and return the input time to be within the data range, accounting for the head buffer and the edge case where the view range
   * is before data range's min at the default zoom level when the timeline first started.
   */
  public double clampToDataRange(double timeUs) {
    double globalMin = Math.min(myViewRangeUs.getMin(), myDataRangeUs.getMin());
    double globalMax = Math.max(globalMin, myDataRangeUs.getMax() - myBufferUs);
    return Math.min(globalMax, Math.max(globalMin, timeUs));
  }

  public Range getSelectionRange() {
    return mySelectionRangeUs;
  }

  public void selectAll() {
    mySelectionRangeUs.set(myViewRangeUs);
  }
}
