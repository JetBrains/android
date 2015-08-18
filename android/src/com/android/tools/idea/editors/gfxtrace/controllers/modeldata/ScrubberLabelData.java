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

import com.android.tools.idea.editors.gfxtrace.rpc.AtomGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ScrubberLabelData {
  private long myAtomId;
  @NotNull private String myLabel;
  @NotNull private AtomGroup myHierarchyReference;

  @NotNull private ImageIcon myImageIcon;
  private long myLoadIconStartTime;
  private boolean myIsCurrentlyLoadingIcon;
  private boolean myIsSelected;

  public ScrubberLabelData(long atomId, @NotNull AtomGroup hierarchyReference, @NotNull String label, @NotNull ImageIcon icon) {
    myAtomId = atomId;
    myHierarchyReference = hierarchyReference;
    myLabel = label;
    myImageIcon = icon;
  }

  @NotNull
  public String getLabel() {
    return myLabel;
  }

  public long getAtomId() {
    return myAtomId;
  }

  @NotNull
  public AtomGroup getHierarchyReference() {
    return myHierarchyReference;
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

  public void setLoading(boolean isLoading) {
    if (isLoading && !myIsCurrentlyLoadingIcon) {
      myLoadIconStartTime = System.currentTimeMillis();
    }
    myIsCurrentlyLoadingIcon = isLoading;
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
}
