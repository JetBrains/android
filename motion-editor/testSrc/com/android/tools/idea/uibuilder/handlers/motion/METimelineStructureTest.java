/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.uibuilder.handlers.motion.adapters.BaseMotionEditorTest;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.timeline.TimelineStructure;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class METimelineStructureTest extends BaseMotionEditorTest {
  MTag layout = getLayout();
  MTag scene = getScene();


  public void testGraphRender() {
    TimelineStructure timelineStructure = new TimelineStructure();
    assertEquals(Color.PINK, timelineStructure.getColorForPosition(3));
    assertEquals(0,timelineStructure.getCursorPosition());
    assertEquals(0.0f,timelineStructure.getFramePosition());
    assertEquals(0.0f,timelineStructure.getTimeCursorMs());
    PropertyChangeListener listener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {

      }
    };
    timelineStructure.addWidthChangedListener(listener);
    timelineStructure.removeWidthChanged(listener);
  }

}
