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
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateConstraintSet;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import java.awt.Dimension;
import javax.swing.Action;

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
    String layout = "0,CreatorAccess      ,0,0,99,99\n" +
      "1,JLabel             ,5,4,88,8\n" +
      "1,JSeparator         ,0,16,99,1\n" +
      "1,JLabel             ,5,22,88,8\n" +
      "1,PromptedTextField  ,6,32,87,10\n" +
      "1,JLabel             ,5,47,88,8\n" +
      "1,MEComboBox         ,6,57,87,13\n" +
      "1,JButton            ,5,79,88,13\n";
    Dimension size = panel.getPreferredSize();
    panel.setBounds(0, 0, size.width, size.height);
    panel.doLayout();
    panel.validate();
    String actual = componentTreeToString(panel, 0, null);
    if (!similar(layout, actual,4)) {
      assertEquals(layout, actual);
    }
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
