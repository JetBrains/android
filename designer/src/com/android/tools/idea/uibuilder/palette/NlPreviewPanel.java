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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NlPreviewPanel extends JPanel implements SelectionListener, Disposable {
  private final NlPreviewImagePanel myImage;
  private final JLabel myItemName;

  public NlPreviewPanel(@NotNull NlPreviewImagePanel imagePanel) {
    super(new BorderLayout(0, 0));
    myImage = imagePanel;
    myImage.setPreferredSize(new Dimension(0, 0));
    myItemName = new JBLabel();
    myItemName.setHorizontalAlignment(SwingConstants.CENTER);
    myItemName.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                                                            BorderFactory.createEmptyBorder(4, 0, 0, 0)));
    myItemName.setText(" ");
    add(myImage, BorderLayout.CENTER);
    add(myItemName, BorderLayout.SOUTH);
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    myImage.setDesignSurface(designSurface);
  }

  @Override
  public void selectionChanged(@Nullable Palette.Item item) {
    myImage.setItem(item);
    myItemName.setText(item != null ? item.getTitle() : " ");
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myImage);
  }
}
