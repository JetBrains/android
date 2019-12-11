/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.motion;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateOnClick;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import com.android.tools.idea.uibuilder.motion.adapters.BaseMotionEditorTest;

import java.awt.Dimension;

public class MECreateOnClickTest extends BaseMotionEditorTest {
  static class CreatorAccess extends CreateOnClick {
    void access_populateDialog() {
      populateDialog();
    }

    @Override
    public void dismissPopup() { // override dismiss
    }
  }

  public void testCreateOnClickLayout() {
    CreatorAccess panel = new CreatorAccess();
    String layout = "0,CreatorAccess      ,0,0,99,99\n" +
                    "1,JLabel             ,3,1,92,9\n" +
                    "1,JSeparator         ,3,12,92,1\n" +
                    "1,JLabel             ,3,15,92,9\n" +
                    "1,MEComboBox         ,3,26,92,14\n" +
                    "1,JLabel             ,3,42,92,9\n" +
                    "1,MEComboBox         ,3,53,92,14\n" +
                    "1,JLabel             ,3,70,92,9\n" +
                    "1,JButton            ,3,83,92,15\n";
    Dimension size = panel.getPreferredSize();
    panel.setBounds(0, 0, size.width, size.height);
    panel.doLayout();
    panel.validate();
    String actual = componentTreeToString(panel, 0, null);
    if (!similar(layout, actual,2)) {
      assertEquals(layout, actual);
    }
  }

  public void testCreateOnClickAction() {
    CreatorAccess panel = new CreatorAccess();
    MotionEditor motionSceneUi = new MotionEditor();
    motionSceneUi.setMTag(getModel());
    panel.getAction(motionSceneUi, motionSceneUi);
    panel.access_populateDialog();
    String info = "0,CreatorAccess,\n" +
                  "1,JLabel,CREATE ONCLICK\n" +
                  "1,JSeparator,\n" +
                  "1,JLabel,In Transition\n" +
                  "1,MEComboBox,base_state->dial,base_state->half_people,half_people->people\n" +
                  "1,JLabel,View To Click\n" +
                  "1,MEComboBox,(Base Layout),number,dial_pad,dialtitle,button1,button2,button3,button4,button5,button6,button7,button8,button9,button10,button11,button12,people_pad,people_title,people1,people2,people3,people4,people5,people6,people7,people8\n" +
                  "1,JLabel,Action\n" +
                  "1,JButton,Add\n";
    assertEquals(info, componentFieldsString(panel, 0));
    MTag tag = panel.create();
    String created = "\n" +
      "<OnClick />\n";
    assertEquals(created, tag.toFormalXmlString(""));
  }
}
