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
package com.android.tools.idea.npw.ui;

import com.android.tools.adtui.ImageUtils;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class TemplateIcon extends JBUI.ScalableJBIcon {
  private Icon myDelegateIcon;
  private float myScale = 1.f;
  private Rectangle myCropRectangle;

  public TemplateIcon(@NotNull Icon icon) {
    myDelegateIcon = icon;
    myCropRectangle = new Rectangle(myDelegateIcon.getIconWidth(), myDelegateIcon.getIconHeight());
  }

  private void setScale(float scale) {
    myScale = scale;
    myCropRectangle = new Rectangle((int)(myCropRectangle.x * myScale), (int)(myCropRectangle.y * myScale),
                                    (int)(myCropRectangle.width * myScale), (int)(myCropRectangle.height * myScale));
  }

  public void setHeight(int height) {
    setScale((float)height / myCropRectangle.height);
  }

  public void cropBlankWidth() {
    BufferedImage image = ImageUtil.toBufferedImage(IconUtil.toImage(myDelegateIcon), true);
    Rectangle cropBounds = ImageUtils.getCropBounds(image, ImageUtils.TRANSPARENCY_FILTER, null);
    if (cropBounds != null) {
      myCropRectangle.x = cropBounds.x;
      myCropRectangle.width = cropBounds.width;
    }
  }

  @NotNull
  @Override
  public Icon scale(float scale) {
    setScale(scale);
    return this;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Icon icon = IconUtil.scale(myDelegateIcon, c, myScale);
    icon.paintIcon(c, g, x - myCropRectangle.x, y);
  }

  @Override
  public int getIconWidth() {
    return myCropRectangle.width;
  }

  @Override
  public int getIconHeight() {
    return myCropRectangle.height;
  }
}
