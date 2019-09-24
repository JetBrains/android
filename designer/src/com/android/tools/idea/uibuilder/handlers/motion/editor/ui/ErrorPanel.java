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
package com.android.tools.idea.uibuilder.handlers.motion.editor.ui;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class ErrorPanel extends JPanel {
  JLabel myBanner = new JLabel();

  JLabel myLabel = new JLabel();
  JLabel myErrorLabel = new JLabel();

  String error;
  ErrorPanel() {
    super(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.fill=  GridBagConstraints.BOTH;
    myBanner.setText("Motion Editor cannot be used");
    myBanner.setOpaque(true);
    myBanner.setMinimumSize(new Dimension(1,MEUI.scale(16)));
    Debug.log("ourBannerColor " + Integer.toHexString(MEUI.ourBannerColor.getRGB()));
    myBanner.setBackground(MEUI.ourBannerColor);
    add(myBanner, gbc);
    gbc.gridy++;

    gbc.weighty = 1;
    gbc.fill=  GridBagConstraints.BOTH;
    myLabel.setText("Motion Editor");
    add(myLabel, gbc);
    gbc.gridy++;
    gbc.weightx = 1;
    gbc.insets = MEUI.insets(10,10,10,10);
    myLabel.setVerticalAlignment(JLabel.BOTTOM);
    myLabel.setHorizontalAlignment(JLabel.CENTER);
    Font font = myLabel.getFont();
    myLabel.setFont(font.deriveFont(font.getSize()*2f));
    myErrorLabel.setVerticalAlignment(JLabel.TOP);
    myErrorLabel.setText(null);
    add(myErrorLabel, gbc);

    myErrorLabel.setForeground(MEUI.ourErrorColor);

  }
  HashSet<String> validTop = new HashSet<String>(Arrays.asList(MotionSceneAttrs.Tags.TRANSITION, MotionSceneAttrs.Tags.CONSTRAINTSET));
  HashMap<String, String> containedIn = new HashMap<String, String>();
  {
    containedIn.put(MotionSceneAttrs.Tags.CONSTRAINT, MotionSceneAttrs.Tags.CONSTRAINTSET);
    containedIn.put(MotionSceneAttrs.Tags.KEY_FRAME_SET, MotionSceneAttrs.Tags.TRANSITION);
    containedIn.put(MotionSceneAttrs.Tags.KEY_ATTRIBUTE, MotionSceneAttrs.Tags.KEY_FRAME_SET);
    containedIn.put(MotionSceneAttrs.Tags.KEY_CYCLE, MotionSceneAttrs.Tags.KEY_FRAME_SET);
    containedIn.put(MotionSceneAttrs.Tags.KEY_POSITION, MotionSceneAttrs.Tags.KEY_FRAME_SET);
    containedIn.put(MotionSceneAttrs.Tags.KEY_TRIGGER, MotionSceneAttrs.Tags.KEY_FRAME_SET);
    containedIn.put(MotionSceneAttrs.Tags.KEY_TIME_CYCLE, MotionSceneAttrs.Tags.KEY_FRAME_SET);
    containedIn.put(MotionSceneAttrs.Tags.ON_CLICK, MotionSceneAttrs.Tags.TRANSITION);
    containedIn.put(MotionSceneAttrs.Tags.ON_SWIPE, MotionSceneAttrs.Tags.TRANSITION);
    containedIn.put(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, MotionSceneAttrs.Tags.CONSTRAINT);
    containedIn.put(MotionSceneAttrs.Tags.MOTION, MotionSceneAttrs.Tags.CONSTRAINT);
    containedIn.put(MotionSceneAttrs.Tags.LAYOUT, MotionSceneAttrs.Tags.CONSTRAINT);
    containedIn.put(MotionSceneAttrs.Tags.PROPERTY_SET, MotionSceneAttrs.Tags.CONSTRAINT);
    containedIn.put(MotionSceneAttrs.Tags.TRANSFORM, MotionSceneAttrs.Tags.CONSTRAINT);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (myBanner != null) {
      myBanner.setBackground(MEUI.ourBannerColor);
    }
  }

  public boolean validateMotionScene(MTag scene) {
    String str = null;
    MTag[] layer0 = scene.getChildTags();
    for (int i = 0; i < layer0.length; i++) {
      String tagName = layer0[i].getTagName();
      if (!validTop.contains(tagName)) {
        str = ((str != null)?str+"\n":"" ) + "<li> &lt;" + tagName +"&gt; cannot be a top level component </li>";
      }
     str = validateChildren(layer0[i], str);
    }

    if (str != null) {
      str = "<HTML>MotionScene Syntax error:<ul>"+str+"</ul></HTML>";
    }
    error = str;
    myErrorLabel.setText(error);
    System.out.println("ERROR      " + error);
    return error == null;
  }

  public String validateChildren(MTag tag, String ret) {
    MTag[] child = tag.getChildTags();
    String parent = tag.getTagName();
    for (int i = 0; i < child.length; i++) {
      String childName = child[i].getTagName();
      String shouldBeIn = containedIn.get(childName);
      if (shouldBeIn == null) {
        ret = ((ret != null)?ret+"\n":"" ) + "<li> &lt;" + childName +"&gt; of parent &lt;"+parent+"&gt; unknown</li>";
      }
      if (!parent.equals(shouldBeIn)) {
        ret = ((ret != null)?ret+"\n":"" ) + "<li> &lt;" + childName +"&gt; of parent &lt;"+parent+"&gt; should be in &lt;"+shouldBeIn +"&gt;</li>";
      }
      ret = validateChildren(child[i], ret);
    }
    return ret;
  }

  public static void main(String [] arg) {
    JFrame frame = new JFrame("  error panel ");
    frame.setContentPane(new ErrorPanel());
    frame.setBounds(100,100,500,800);
    frame.setTitle("JFrame");

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setVisible(true);
  }
}
