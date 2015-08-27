/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.controllers.modeldata;

import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.service.atom.Range;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ScrubberLabelData implements LoadingCallback.LoadingDone {
  @NotNull public final AtomPath atomPath;
  @NotNull public final Range range;
  @NotNull public final String label;

  @NotNull private ImageIcon myImageIcon;
  private long myLoadIconStartTime;
  private boolean myIsCurrentlyLoadingIcon;
  private boolean myIsSelected;

  public ScrubberLabelData(AtomPath atomPath, @NotNull Range range, @NotNull String label, @NotNull ImageIcon icon) {
    this.atomPath = atomPath;
    this.range = range;
    this.label = label;
    myImageIcon = icon;
    myLoadIconStartTime = 0;
  }

  @NotNull
  public ImageIcon getIcon() {
    return myImageIcon;
  }

  public void setIcon(@NotNull ImageIcon icon) {
    myImageIcon = icon;
  }

  public boolean isLoading() {
    return myIsCurrentlyLoadingIcon;
  }

  public void startLoading() {
    if (!myIsCurrentlyLoadingIcon) {
      myLoadIconStartTime = System.currentTimeMillis();
    }
    myIsCurrentlyLoadingIcon = true;
  }

  @Override
  public void stopLoading() {
    myIsCurrentlyLoadingIcon = false;
  }

  public long getLoadIconStartTime() {
    return myLoadIconStartTime;
  }

  public boolean isSelected() {
    return myIsSelected;
  }

  public void setSelected(boolean isSelected) {
    myIsSelected = isSelected;
  }

  public boolean isLoaded() {
    return !myIsCurrentlyLoadingIcon && (myLoadIconStartTime != 0);
  }

}
