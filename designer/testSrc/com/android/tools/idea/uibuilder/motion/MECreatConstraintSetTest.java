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
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateConstraintSet;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import com.android.tools.idea.uibuilder.motion.adapters.BaseMotionEditorTest;

import javax.swing.Action;
import java.awt.Dimension;

public class MECreatConstraintSetTest extends BaseMotionEditorTest {
  static class CreatorAccess extends CreateConstraintSet {
    void access_populateDialog() {
      populateDialog();
    }

    @Override
    public void dismissPopup() {
    }

    void fill() {
      mId.setText("@+id/foo");
    }
  }

  public void testCreateConstraintSetLayout() {
    CreatorAccess panel = new CreatorAccess();
    String layout = "0,CreatorAccess,0,0,177,139\n" +
      "1,JLabel,5,2,167,15\n" +
      "1,JSeparator,5,20,167,2\n" +
      "1,JLabel,5,25,167,15\n" +
      "1,PromptedTextField,5,43,167,19\n" +
      "1,JLabel,5,65,167,15\n" +
      "1,MEComboBox,5,83,167,24\n" +
      "1,JButton,5,113,167,25\n";
    Dimension size = panel.getPreferredSize();
    panel.setBounds(0, 0, size.width, size.height);
    panel.doLayout();
    panel.validate();
    assertEquals(layout, componentTreeToString(panel, 0));
  }

  public void testCreateConstraintSetAction() {
    CreatorAccess panel = new CreatorAccess();
    MotionEditor motionSceneUi = new MotionEditor();
    motionSceneUi.setMTag(getModel());
    Action action = panel.getAction(motionSceneUi, motionSceneUi);
    panel.access_populateDialog();
    String info = "0,CreatorAccess,\n" +
      "1,JLabel,CREATE CONSTRAINTSET\n" +
      "1,JSeparator,\n" +
      "1,JLabel,ID\n" +
      "1,PromptedTextField,Enter id\n" +
      "1,JLabel,Based On\n" +
      "1,MEComboBox,MotionLayout,base_state,dial,people,half_people\n" +
      "1,JButton,Add\n";
    assertEquals(info, componentFieldsString(panel, 0));
    panel.fill();
    MTag tag = panel.create();
    String created = "\n" +
      "<ConstraintSet\n" +
      "   android:id=\"@+id/foo\" />\n";
    assertEquals(created, tag.toFormalXmlString(""));
  }
}
