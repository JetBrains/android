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
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.CreateKeyTimeCycle;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditor;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.motion.adapters.BaseMotionEditorTest;

import java.awt.Dimension;

public class MECreateKeyTimeCycleTest extends BaseMotionEditorTest {
  static class CreatorAccess extends CreateKeyTimeCycle {
    void access_populateDialog() {
      populateDialog();
    }

    @Override
    public void dismissPopup() { // override dismiss
    }

    void fillAttributes() {
      mMatchTag.setText("test32");
    }
  }

  public void testCreateKeyTimeCycleLayout() {
    CreatorAccess panel = new CreatorAccess();
    String layout = "0,CreatorAccess,0,0,189,317\n" +
                    "1,JLabel,5,2,179,15\n" +
                    "1,JSeparator,5,20,179,2\n" +
                    "1,JRadioButton,5,25,52,23\n" +
                    "1,JRadioButton,67,25,117,23\n" +
                    "1,JPanel,5,51,179,24\n" +
                    "2,PromptedTextField,0,0,0,0\n" +
                    "2,MEComboBox,0,0,0,0\n" +
                    "1,JSeparator,5,62,179,2\n" +
                    "1,JLabel,5,78,179,15\n" +
                    "1,PromptedTextField,5,96,179,19\n" +
                    "1,JLabel,5,118,179,15\n" +
                    "1,MEComboBox,5,136,179,24\n" +
                    "1,JLabel,5,163,179,15\n" +
                    "1,PromptedTextField,5,181,179,19\n" +
                    "1,JLabel,5,203,179,15\n" +
                    "1,PromptedTextField,5,221,179,19\n" +
                    "1,JLabel,5,243,179,15\n" +
                    "1,MEComboBox,5,261,179,24\n" +
                    "1,JButton,5,291,179,25\n";
    Dimension size = panel.getPreferredSize();
    panel.setBounds(0, 0, size.width, size.height);
    panel.doLayout();
    panel.validate();
    assertEquals(layout, componentTreeToString(panel, 0));
  }

  public void testCreateKeyTimeCycleAction() {
    CreatorAccess panel = new CreatorAccess();
    MotionEditor motionSceneUi = new MotionEditor();
    MeModel model = getModel();
    MTag[] trans = model.motionScene.getChildTags("Transition");
    model.setSelected(MotionEditorSelector.Type.TRANSITION, trans);
    motionSceneUi.setMTag(model);
    panel.getAction(motionSceneUi, motionSceneUi);
    panel.access_populateDialog();

    String info = "0,CreatorAccess,\n" +
                  "1,JLabel,CREATE KEY TIME CYCLE\n" +
                  "1,JSeparator,\n" +
                  "1,JRadioButton,\n" +
                  "1,JRadioButton,\n" +
                  "1,JPanel,\n" +
                  "2,PromptedTextField,tag or regex\n" +
                  "2,MEComboBox,number,dial_pad,dialtitle,button1,button2,button3,button4,button5,button6,button7,button8,button9,button10,button11,button12,people_pad,people_title,people1,people2,people3,people4,people5,people6,people7,people8\n" +
                  "1,JSeparator,\n" +
                  "1,JLabel,Position\n" +
                  "1,PromptedTextField,0-100\n" +
                  "1,JLabel,Wave Shape\n" +
                  "1,MEComboBox,sin,square,triangle,sawtooth,reverseSawtooth,cos,bounce\n" +
                  "1,JLabel,Wave Period\n" +
                  "1,PromptedTextField,1\n" +
                  "1,JLabel,Wave Offset\n" +
                  "1,PromptedTextField,float\n" +
                  "1,JLabel,Attribute to cycle\n" +
                  "1,MEComboBox,alpha,elevation,rotation,rotationX,rotationY,scaleX,scaleY,translationX,translationY,translationZ,transitionPathRotate\n" +
                  "1,JButton,Add\n";
    assertEquals(info, componentFieldsString(panel, 0));
    panel.fillAttributes();
    MTag tag = panel.create();
    String created = "\n" +
      "<KeyTimeCycle\n" +
      "   android:alpha=\"0\"\n" +
      "   motion:framePosition=\"0\"\n" +
      "   motion:motionTarget=\"test32\"\n" +
      "   motion:wavePeriod=\"1\" />\n";
    assertEquals(created, tag.toFormalXmlString(""));
  }
}
