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
import com.android.tools.idea.common.property2.api.PropertiesModel;
import com.android.tools.idea.common.property2.api.PropertiesModelListener;
import com.android.tools.idea.common.property2.api.PropertiesTable;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MotionLayoutAttributesModel implements PropertiesModel<MotionPropertyItem>, Disposable {
  private final List<PropertiesModelListener> myListeners;
  private final MotionLayoutPropertyProvider myPropertyProvider;
  private DesignSurface mySurface;
  private DesignSurfaceListener mySurfaceListener;
  private TimelineOwner myTimeline;
  private TimelineListener myTimelineListener;
  private PropertiesTable<MotionPropertyItem> myPropertiesTable;

  public MotionLayoutAttributesModel(@NotNull Disposable parentDisposable) {
    myListeners = new ArrayList<>();
    myPropertyProvider = new MotionLayoutPropertyProvider();
    myPropertiesTable = PropertiesTable.Companion.emptyTable();
    mySurfaceListener = new PropertiesDesignSurfaceListener();
    myTimelineListener = new PropertiesTimelineListener();
    Disposer.register(parentDisposable, this);
  }

  @Override
  public void dispose() {
    setSurface(null);
    setTimeline(null);
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

  @NotNull
  @Override
  public PropertiesTable<MotionPropertyItem> getProperties() {
    return myPropertiesTable;
  }

  @Override
  public void deactivate() {
    myPropertiesTable = PropertiesTable.Companion.emptyTable();
  }

  @Override
  public void addListener(@NotNull PropertiesModelListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull PropertiesModelListener listener) {
    myListeners.remove(listener);
  }

  private void designSelectionUpdate(@NotNull DesignSurface surface, @NotNull List<NlComponent> selection) {
    if (surface != mySurface) {
      return;
    }
    if (selection.isEmpty()) {
      setTimeline(null);
      return;
    }
    NlComponent component = selection.get(0);
    Object property = component.getClientProperty(TimelineOwner.TIMELINE_PROPERTY);
    if (property instanceof TimelineOwner) {
      setTimeline((TimelineOwner)property);
      return;
    }
    NlComponent parent = component.getParent();
    property = parent != null ? parent.getClientProperty(TimelineOwner.TIMELINE_PROPERTY) : null;
    if (property instanceof TimelineOwner) {
      setTimeline((TimelineOwner)property);
    }
    else {
      setTimeline(null);
    }
  }

  private void displayKeyFrame(@Nullable MotionSceneModel.KeyFrame keyFrame) {
    if (keyFrame != null) {
      myPropertiesTable = myPropertyProvider.getProperties(keyFrame);
      myListeners.forEach(listener -> listener.propertiesGenerated(this));
    }
  }

  private class PropertiesDesignSurfaceListener implements DesignSurfaceListener {
    @Override
    public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
      designSelectionUpdate(surface, newSelection);
    }
  }

  private class PropertiesTimelineListener implements TimelineListener {
    @Override
    public void updateSelection(@Nullable MotionSceneModel.KeyFrame keyFrame) {
      displayKeyFrame(keyFrame);
    }
  }
}
