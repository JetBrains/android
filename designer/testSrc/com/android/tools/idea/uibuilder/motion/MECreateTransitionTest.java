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
    String layout = "0,CreatorAccess,0,0,155,269\n" +
      "1,JLabel,5,2,145,15\n" +
      "1,JSeparator,5,20,145,2\n" +
      "1,JLabel,5,25,145,15\n" +
      "1,PromptedTextField,5,43,145,19\n" +
      "1,JLabel,5,65,145,15\n" +
      "1,MEComboBox,5,83,145,24\n" +
      "1,JLabel,5,110,145,15\n" +
      "1,MEComboBox,5,128,145,24\n" +
      "1,JLabel,5,155,145,15\n" +
      "1,PromptedTextField,5,173,145,19\n" +
      "1,JLabel,5,195,145,15\n" +
      "1,MEComboBox,5,213,145,24\n" +
      "1,JButton,5,243,145,25\n";
    Dimension size = panel.getPreferredSize();
    panel.setBounds(0, 0, size.width, size.height);
    panel.doLayout();
    panel.validate();
    assertEquals(layout, componentTreeToString(panel, 0));
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
      "1,JLabel,Duration\n" +
      "1,PromptedTextField,Duration in ms\n" +
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
