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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A helper class for attaching a {@link TimelineListener} to the
 * timeline associated with the current selected {@link NlComponent} in
 * the current {@link DesignSurface}.
 */
public class TimelineHelper {
  private final DesignSurfaceListener mySurfaceListener;
  private final TimelineListener myTimelineListener;
  private TimelineOwner myTimeline;
  private DesignSurface mySurface;
  private List<NlComponent> mySelection;

  public TimelineHelper(@NotNull TimelineListener timelineListener) {
    mySurfaceListener = new PropertiesDesignSurfaceListener();
    myTimelineListener = timelineListener;
    mySelection = Collections.emptyList();
  }

  @NotNull
  public List<NlComponent> getSelection() {
    return mySelection;
  }

  @Nullable
  public DesignSurface getSurface() {
    return mySurface;
  }

  public void setSurface(@Nullable DesignSurface surface) {
    if (surface == mySurface) {
      return;
    }

    if (mySurface != null) {
      mySurface.removeListener(mySurfaceListener);
    }
    mySurface = surface;
    if (mySurface != null) {
      mySurface.addListener(mySurfaceListener);
    }
  }

  private void setSelection(@NotNull DesignSurface surface, @NotNull List<NlComponent> selection) {
    if (surface != mySurface) {
      return;
    }
    if (selection.isEmpty()) {
      setTimeline(null);
      mySelection = selection;
      return;
    }
    NlComponent component = selection.get(0);
    Object property = component.getClientProperty(TimelineOwner.TIMELINE_PROPERTY);
    if (property instanceof TimelineOwner) {
      setTimeline((TimelineOwner)property);
      mySelection = Collections.singletonList(component);
      return;
    }
    NlComponent parent = component.getParent();
    property = parent != null ? parent.getClientProperty(TimelineOwner.TIMELINE_PROPERTY) : null;
    if (property instanceof TimelineOwner) {
      setTimeline((TimelineOwner)property);
      mySelection = Collections.singletonList(component);
    }
    else {
      setTimeline(null);
      mySelection = Collections.emptyList();
    }
  }

  private void setTimeline(@Nullable TimelineOwner timeline) {
    if (timeline == myTimeline) {
      return;
    }

    if (myTimeline != null) {
      myTimeline.removeTimeLineListener(myTimelineListener);
    }
    myTimeline = timeline;
    if (myTimeline != null) {
      myTimeline.addTimelineListener(myTimelineListener);
    }
  }

  private class PropertiesDesignSurfaceListener implements DesignSurfaceListener {
    @Override
    public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
      setSelection(surface, newSelection);
    }
  }
}
