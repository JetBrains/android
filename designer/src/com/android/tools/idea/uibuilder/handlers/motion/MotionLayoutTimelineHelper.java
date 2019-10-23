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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.GanttEventListener;
import com.android.tools.idea.uibuilder.surface.AccessoryPanelListener;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A helper class for attaching a {@link AccessorySelectionListener} to the
 * timeline associated with the current selected {@link NlComponent} in
 * the current {@link NlDesignSurface}.
 */
public class MotionLayoutTimelineHelper {
  private final AccessoryPanelListener myAccessoryPanelListener;
  private final AccessorySelectionListener myTimelineListener;
  private AccessoryPanelInterface myTimeline;
  private NlDesignSurface mySurface;
  private List<NlComponent> mySelection;

  public MotionLayoutTimelineHelper(@NotNull AccessorySelectionListener timelineListener) {
    myAccessoryPanelListener = new MyAccessoryPanelListener();
    myTimelineListener = timelineListener;
    mySelection = Collections.emptyList();
  }

  @NotNull
  public List<NlComponent> getSelection() {
    return mySelection;
  }

  @Nullable
  public NlDesignSurface getSurface() {
    return mySurface;
  }

  public void setSurface(@Nullable DesignSurface designSurface) {
    NlDesignSurface surface = designSurface instanceof NlDesignSurface ? (NlDesignSurface)designSurface : null;
    if (surface == mySurface) {
      return;
    }

    if (mySurface != null) {
      mySurface.getAccessoryPanel().removeAccessoryPanelListener(myAccessoryPanelListener);
      setTimeline(null);
    }
    mySurface = surface;
    if (mySurface != null) {
      mySurface.getAccessoryPanel().addAccessoryPanelListener(myAccessoryPanelListener);
      setTimeline(mySurface.getAccessoryPanel().getCurrentPanel());
    }
  }

  private void setTimeline(@Nullable AccessoryPanelInterface timeline) {
    // Check below for GanttEventListener instead of MotionLayoutTimelinePanel to enable mocking in tests.
    if (!(timeline instanceof GanttEventListener)) {
      // This is NOT a MotionLayoutTimelinePanel !
      timeline = null;
    }
    if (timeline == myTimeline) {
      return;
    }

    if (myTimeline != null) {
      myTimeline.removeListener(myTimelineListener);
    }
    myTimeline = timeline;
    if (myTimeline != null) {
      myTimeline.addListener(myTimelineListener);
    }
  }

  private class MyAccessoryPanelListener implements AccessoryPanelListener {
    @Override
    public void panelChange(@Nullable AccessoryPanelInterface panel) {
      setTimeline(panel);
      myTimeline = panel instanceof MotionLayoutTimelinePanel ? (MotionLayoutTimelinePanel)panel : null;
    }
  }
}
