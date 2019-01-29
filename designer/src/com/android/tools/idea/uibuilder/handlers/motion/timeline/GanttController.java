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
package com.android.tools.idea.uibuilder.handlers.motion.timeline;

import java.awt.event.ActionEvent;

/**
 * Provide the interface to system that use the Gantt panel
 */
public class GanttController {

  GanttEventListener myListener;

  int mSelectionType = 0;
  int[] mElements;
  int mNumberOfElements;

  public void setListener(GanttEventListener listener) {
    myListener = listener;
  }

  public void framePosition(float percent) {
    if (myListener != null) {
      myListener.setProgress(percent);
    }
  }

  public void setSelection(int type, int[] elements, int numberOfElements) {
    mSelectionType = type;
    mNumberOfElements = numberOfElements;

    if (numberOfElements > elements.length) {
      throw new IllegalArgumentException("number of element is less than the array length");
    }
    if (elements == null || numberOfElements == 0) {
      mNumberOfElements = 0;
      return;
    }
    if (mElements == null || mElements.length > elements.length) {
      mElements = new int[elements.length];
    }
    System.arraycopy(elements, 0, mElements, 0, numberOfElements);
  }

  public int getSelectionType() {
    return mSelectionType;
  }

  public void buttonPressed(ActionEvent e, GanttEventListener.Actions action) {
    if (myListener != null) {
      myListener.buttonPressed(e, action);
    }
  }
}
