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
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateOnSwipe;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import java.awt.Dimension;

public class MECreateOnSwipeTest extends BaseMotionEditorTest {
  static class CreatorAccess extends CreateOnSwipe {
    void access_populateDialog() {
      populateDialog();
    }

    @Override
    public void dismissPopup() { // override dismiss
    }
  }

  public void testCreateOnSwipeLayout() {
    CreatorAccess panel = new CreatorAccess();
    String layout = "0,CreatorAccess      ,0,0,99,99\n" +
                    "1,JLabel             ,6,2,86,5\n" +
                    "1,JSeparator         ,0,10,99,0\n" +
                    "1,JLabel             ,6,14,86,5\n" +
                    "1,MEComboBox         ,7,20,85,8\n" +
                    "1,JLabel             ,6,31,86,5\n" +
                    "1,MEComboBox         ,7,38,85,8\n" +
                    "1,JLabel             ,6,49,86,5\n" +
                    "1,MEComboBox         ,7,55,85,8\n" +
                    "1,JLabel             ,6,66,86,5\n" +
                    "1,MEComboBox         ,7,73,85,8\n" +
                    "1,JButton            ,6,86,86,8\n";
    Dimension size = panel.getPreferredSize();
    panel.setBounds(0, 0, size.width, size.height);
    panel.doLayout();
    panel.validate();
    String actual = componentTreeToString(panel, 0, null);
    if (!similar(layout, actual,2)) {
      assertEquals(layout, actual);
    }
  }

  public void testCreateOnSwipeAction() {
    CreatorAccess panel = new CreatorAccess();
    MotionEditor motionSceneUi = new MotionEditor();
    motionSceneUi.setMTag(getModel());
    panel.getAction(motionSceneUi, motionSceneUi);
    panel.access_populateDialog();
    String info = "0,CreatorAccess,\n" +
                  "1,JLabel,CREATE ONSWIPE\n" +
                  "1,JSeparator,\n" +
                  "1,JLabel,In Transition\n" +
                  "1,MEComboBox,base_state->dial,base_state->half_people,half_people->people\n" +
                  "1,JLabel,Drag Direction\n" +
                  "1,MEComboBox,dragUp,dragDown,dragLeft,dragRight\n" +
                  "1,JLabel,Anchor Side\n" +
                  "1,MEComboBox,Top,Left,Bottom,Right\n" +
                  "1,JLabel,Anchor ID\n" +
                  "1,MEComboBox,(none),number,dial_pad,dialtitle,button1,button2,button3,button4,button5,button6,button7,button8,button9,button10,button11,button12,people_pad,people_title,people1,people2,people3,people4,people5,people6,people7,people8\n" +
                  "1,JButton,Add\n";
    assertEquals(info, componentFieldsString(panel, 0));
    MTag tag = panel.create();
    String created = "\n" +
      "<OnSwipe />\n";
    assertEquals(created, tag.toFormalXmlString(""));
  }
}
