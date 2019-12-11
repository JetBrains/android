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
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateTransition;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import com.android.tools.idea.uibuilder.motion.adapters.BaseMotionEditorTest;

import java.awt.Dimension;

public class MECreateTransitionTest extends BaseMotionEditorTest {
  static class CreatorAccess extends CreateTransition {
    void access_populateDialog() {
      populateDialog();
    }

    @Override
    public void dismissPopup() {
    }
  }

  public void testCreateTransitionLayout() {
    CreatorAccess panel = new CreatorAccess();
    String layout = "0,CreatorAccess      ,0,0,99,99\n" +
                    "1,JLabel             ,3,0,92,6\n" +
                    "1,JSeparator         ,3,8,92,0\n" +
                    "1,JLabel             ,3,10,92,6\n" +
                    "1,PromptedTextField  ,3,18,92,8\n" +
                    "1,JLabel             ,3,28,92,6\n" +
                    "1,MEComboBox         ,3,36,92,10\n" +
                    "1,JLabel             ,3,47,92,6\n" +
                    "1,MEComboBox         ,3,55,92,10\n" +
                    "1,JLabel             ,3,67,92,6\n" +
                    "1,MEComboBox         ,3,75,92,10\n" +
                    "1,JButton            ,3,88,92,10\n";
    Dimension size = panel.getPreferredSize();
    panel.setBounds(0, 0, size.width, size.height);
    panel.doLayout();
    panel.validate();
    String actual = componentTreeToString(panel, 0, null);
    if (!similar(layout, actual,2)) {
      assertEquals(layout, actual);
    }
   }

  public void testCreateTransitionAction() {
    CreatorAccess panel = new CreatorAccess();
    MotionEditor motionSceneUi = new MotionEditor();
    motionSceneUi.setMTag(getModel());
    panel.getAction(motionSceneUi, motionSceneUi);
    panel.access_populateDialog();
    String info = "0,CreatorAccess,\n" +
                  "1,JLabel,CREATE TRANSITION\n" +
                  "1,JSeparator,\n" +
                  "1,JLabel,ID\n" +
                  "1,PromptedTextField,Enter Transition's id\n" +
                  "1,JLabel,Start\n" +
                  "1,MEComboBox,base_state,dial,people,half_people\n" +
                  "1,JLabel,End\n" +
                  "1,MEComboBox,base_state,dial,people,half_people\n" +
                  "1,JLabel,Automatically\n" +
                  "1,MEComboBox,Do Nothing,Jump to Start,Jump to End,Animate to Start,Animate to End\n" +
                  "1,JButton,Add\n";
    assertEquals(info, componentFieldsString(panel, 0));
    MTag tag = panel.create();
    String created = "\n" +
      "<Transition\n" +
      "   motion:constraintSetEnd=\"@+id/dial\"\n" +
      "   motion:constraintSetStart=\"@+id/base_state\" />\n";
    assertEquals(created, tag.toFormalXmlString(""));
  }
}
