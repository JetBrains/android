/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.structure.services.view;

import com.intellij.ui.JBColor;
import com.intellij.ui.UI;
import com.intellij.util.ui.UIUtil;

import java.awt.*;


/**
 * Centralized values and common, granular, UI properties. Scope of class
 * should be constrained to matters of color, font, padding. More complex
 * UI related functionality should live in more specific code.
 *
 * TODO: Add font defaults.
 */
public class UIUtils {

  /**
   * Foreground color for positive indication of results.
   * TODO: Currently mirrors the quoted string editor colors. Directly leveraging something else that shares semantics would be ideal.
   * NOTE: The colors may be found at Settings -> Editor -> Colors & Fonts -> Custom [String].
   */
  private static final Color SUCCESS_COLOR = new JBColor(0x008000, 0x6A8759);

  /**
   * Foreground color for negative indication of results.
   * TODO: Currently mirrors the keyword (4th variant) editor colors. Directly leveraging something else that shares semantics would be
   * ideal.
   * NOTE: The colors may be found at Settings -> Editor -> Colors & Fonts -> Custom [Keyword4].
   */
  private static final Color FAILURE_COLOR = new JBColor(0x660000, 0xC93B48);

  // TODO: Find or create a better background reference, we're not a tree but
  // this does currently match our desired color treatment. This is pulled
  // from the Maven Projects side panel.
  public static Color getBackgroundColor() {
    return UIUtil.getTreeTextBackground();
  }

  public static Color getSeparatorColor() {
    return UI.getColor("panel.separator.color");
  }

  // TODO: This blue seems a bit washed out, confirm what color we should
  // leverage.
  public static Color getLinkColor() {
    return UI.getColor("link.foreground");
  }


  public static Color getSuccessColor() {
    return SUCCESS_COLOR;
  }

  public static Color getFailureColor() {
    return FAILURE_COLOR;
  }

  public static GridBagConstraints getVerticalGlueConstraints(int gridy) {
    return getVerticalGlueConstraints(gridy, 1);
  }

  /**
   * Gets a common set of constraints for vertical glue to fill remaining space. New instances created as caller may need to override some
   * values.
   */
  public static GridBagConstraints getVerticalGlueConstraints(int gridy, int gridwidth) {
    GridBagConstraints glueConstraints = new GridBagConstraints();
    glueConstraints.gridx = 0;
    glueConstraints.gridy = gridy;
    glueConstraints.gridwidth = gridwidth;
    glueConstraints.gridheight = 1;
    glueConstraints.weightx = 0;
    glueConstraints.weighty = 1;
    glueConstraints.anchor = GridBagConstraints.NORTH;
    glueConstraints.fill = GridBagConstraints.BOTH;
    glueConstraints.insets = new Insets(0, 0, 0, 0);
    glueConstraints.ipadx = 0;
    glueConstraints.ipady = 0;
    return glueConstraints;
  }

}
