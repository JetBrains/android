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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Small static functions used across the system
 */
public class Utils {

  public static String stripID(String id) {
    if (id == null) {
      return "";
    }
    int index = id.indexOf('/');
    if (index < 0) {
      return id;
    }
    return id.substring(index + 1).trim();
  }

  public static String formatTransition(MTag tag) {
    String id = Utils.stripID(tag.getAttributeValue(MotionSceneAttrs.Transition.ATTR_ID));
    String start = Utils.stripID(tag.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_START));
    String end = Utils.stripID(tag.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_END));
    return formatTransition(id, start, end);
  }

  public static String formatTransition(String id, String start, String end) {
    if (id == null || id.length() == 0) {
      return start + "->" + end;
    }
    return id;
  }

  /**
   * Produce an icon of reduced opacity but turning down the alpha by 1/3
   *
   * @param icon input icon
   * @return created icon
   */
  public static Icon computeLiteIcon(Icon icon) {
    final int w = icon.getIconWidth();
    final int h = icon.getIconHeight();
    BufferedImage image = MEUI.createImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();
    icon.paintIcon(null, g2d, 0, 0);
    int[] data = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
    for (int i = 0; i < data.length; i++) {
      int v = data[i] & 0xFF;
      int a = (data[i] >> 24) & 0xFF;
      a /= 3;
      data[i] = (a << 24) | (v * 0x10101);
    }
    return new ImageIcon(image);
  }
}
