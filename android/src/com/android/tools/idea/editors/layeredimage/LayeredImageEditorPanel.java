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

package com.android.tools.idea.editors.layeredimage;

import com.android.tools.pixelprobe.Image;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightFillLayout;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import org.intellij.images.editor.ImageEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class LayeredImageEditorPanel extends JPanel implements DesignerEditorPanelFacade {
  private final ThreeComponentsSplitter myContentSplitter;
  private final JComponent myContentComponent;
  private final Image myImage;

  LayeredImageEditorPanel(@NotNull ImageEditor editor, @Nullable Image image) {
    super(new BorderLayout());
    setOpaque(true);

    myImage = image;

    // Steal the editor's components
    JComponent editorComponent = editor.getComponent();
    myContentComponent = editor.getContentComponent();

    // Steal the toolbar
    Component toolbar = editorComponent.getComponent(0);
    editorComponent.remove(0);

    JPanel contentPanel = new JPanel(new LightFillLayout());
    contentPanel.add(toolbar);
    contentPanel.add(editorComponent);

    myContentSplitter = new ThreeComponentsSplitter();
    myContentSplitter.setDividerWidth(0);
    myContentSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    myContentSplitter.setInnerComponent(contentPanel);

    add(myContentSplitter, BorderLayout.CENTER);
  }

  @NotNull
  Image getImage() {
    return myImage;
  }

  @Override
  public ThreeComponentsSplitter getContentSplitter() {
    return myContentSplitter;
  }

  void dispose() {
    Disposer.dispose(myContentSplitter);
  }

  JComponent getPreferredFocusedComponent() {
    return myContentComponent;
  }
}
