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
package com.android.tools.idea.uibuilder.motion.adapters;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.createDialogs.BaseCreatePanel;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MeModel;
import com.android.tools.idea.uibuilder.motion.adapters.samples.layout_16_xml;
import com.android.tools.idea.uibuilder.motion.adapters.samples.motion_scene_16_xml;
import junit.framework.TestCase;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Base class for MotionEditor Test providing a Mock Layout and MotionScene to test against
 */
public class BaseMotionEditorTest extends TestCase {

  public void testMETestFramework(){
    assertTrue(true);
  }
  protected MTag getLayout() {
    InputStream layout_stream = layout_16_xml.asStream();
    String layoutStr = convert(layout_stream);
    MTag layout = MTagImp.parse(layoutStr);
    return layout;
  }

  protected MTag getScene() {
    InputStream scene_stream = motion_scene_16_xml.asStream();
    String msStr = convert(scene_stream);
    MTag motionScene = MTagImp.parse(msStr);
    return motionScene;
  }
  protected MeModel getModel(){
    return  new MeModel(getScene(), getLayout(), "Layout","Scene");
  }

  protected static String convert(InputStream inputStream) {
    StringBuilder stringBuilder = new StringBuilder();
    String line = null;

    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
      while ((line = bufferedReader.readLine()) != null) {
        stringBuilder.append(line);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return stringBuilder.toString();
  }
  protected boolean check(BaseCreatePanel panel, String layout) {
    Dimension size = panel.getPreferredSize();
    panel.setBounds(0, 0, size.width, size.height);
    panel.doLayout();
    panel.validate();
    String str = componentTreeToString(panel, 0);
    return str.equals(layout);
  }

  protected String componentTreeToString(JComponent c, int offset) {
    String str;
    Rectangle bounds = c.getBounds();
    String name = c.getClass().getSimpleName();
    if (name.length() == 0) {
      return "";
    }
    str = offset + "," + c.getClass().getSimpleName() + "," + bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height + "\n";
    if (c instanceof JPanel) {
      JPanel p = (JPanel) c;
      int n = p.getComponentCount();
      for (int i = 0; i < n; i++) {
        str += componentTreeToString((JComponent) c.getComponent(i), offset + 1);

      }
    }
    return str;
  }

  protected String componentFieldsString(JComponent c, int offset) {
    String str;
    Rectangle bounds = c.getBounds();
    String name = c.getClass().getSimpleName();
    if (name.length() == 0) {
      return "";
    }
    String content = "";
    if (c instanceof BaseCreatePanel.PromptedTextField) {
      BaseCreatePanel.PromptedTextField textComponent = (BaseCreatePanel.PromptedTextField) c;
      content = textComponent.getPromptText();
    } else if (c instanceof JTextComponent) {
      JTextComponent component = (JTextComponent) c;
      content = component.getText();
    } else if (c instanceof JButton) {
      JButton component = (JButton) c;
      content = component.getText();
    } else if (c instanceof JLabel){
      JLabel component = (JLabel) c;
      content = component.getText();
    }else if (c instanceof JComboBox){
      JComboBox component = (JComboBox) c;
      int n = component.getItemCount();
      for (int i = 0; i < n; i++) {
        content += ((i==0)?"":",")+ component.getItemAt(i).toString();
      }

    }
    str = offset + "," + c.getClass().getSimpleName() + "," + content + "\n";
    if (c instanceof JPanel) {
      JPanel p = (JPanel) c;
      int n = p.getComponentCount();
      for (int i = 0; i < n; i++) {
        str += componentFieldsString((JComponent) c.getComponent(i), offset + 1);

      }
    }
    return str;
  }
}
