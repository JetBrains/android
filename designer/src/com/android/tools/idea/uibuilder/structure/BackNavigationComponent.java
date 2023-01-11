/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.editor.LayoutNavigationManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Panel to show on top of the component tree allowing to navigate back to
 * the parent of an included layout
 */
public class BackNavigationComponent extends JPanel {

  public static final String BACK_NAVIGATION_COMPONENT_NAME = "BackNavigationComponent";
  private static final Color BACKGROUND = UIUtil.getPanelBackground();
  private static final Color HOVER_BACKGROUND = UIUtil.getPanelBackground().darker();

  private final DesignSurfaceListener mySurfaceListener;
  private final JLabel myBackLabel;
  @Nullable private DesignSurface<?> myDesignSurface;
  private final BackNavigationListener myMouseAdapter;

  public BackNavigationComponent() {
    super(new FlowLayout(FlowLayout.LEFT));
    setName(BACK_NAVIGATION_COMPONENT_NAME);
    mySurfaceListener = createDesignSurfaceListener();
    myBackLabel = new JLabel(AllIcons.Actions.Back);
    myBackLabel.setIconTextGap(8);
    myMouseAdapter = new BackNavigationListener();
    addMouseListener(myMouseAdapter);
    addMouseMotionListener(myMouseAdapter);
    setVisible(false);
    add(myBackLabel);
    setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    setBackground(BACKGROUND);
  }

  private void updateBackNavigation() {
    if (myDesignSurface == null) {
      return;
    }
    NlModel model = myDesignSurface.getModel();
    if (model != null) {
      LayoutNavigationManager layoutNavigationManager = LayoutNavigationManager.getInstance(myDesignSurface.getProject());
      VirtualFile modelFile = model.getVirtualFile();
      VirtualFile parentFile = layoutNavigationManager.get(modelFile);
      if (parentFile != null) {
        setVisible(true);
        myMouseAdapter.setNavigation(modelFile, parentFile, layoutNavigationManager);
        myBackLabel.setText(parentFile.getPresentableName());
      }
      else {
        setVisible(false);
      }
    }
  }

  @NotNull
  private DesignSurfaceListener createDesignSurfaceListener() {
    return new DesignSurfaceListener() {
      @Override
      public void modelChanged(@NotNull DesignSurface<?> surface, @Nullable NlModel model) {
        updateBackNavigation();
      }

      @Override
      public boolean activatePreferredEditor(@NotNull DesignSurface<?> surface, @NotNull NlComponent component) {
        return DesignSurfaceListener.super.activatePreferredEditor(surface, component);
      }
    };
  }

  public void setDesignSurface(@Nullable DesignSurface<?> designSurface) {
    if (myDesignSurface != null) {
      myDesignSurface.removeListener(mySurfaceListener);
    }
    myDesignSurface = designSurface;
    if (myDesignSurface != null) {
      myDesignSurface.addListener(mySurfaceListener);
      updateBackNavigation();
    }
  }

  @Nullable
  public DesignSurface<?> getDesignSurface() {
    return myDesignSurface;
  }

  private class BackNavigationListener extends MouseAdapter {

    private VirtualFile myFrom;
    private VirtualFile myTo;
    private LayoutNavigationManager myManager;

    private void setNavigation(@NotNull VirtualFile from,
                               @NotNull VirtualFile to,
                               @NotNull LayoutNavigationManager layoutNavigationManager) {
      myFrom = from;
      myTo = to;
      myManager = layoutNavigationManager;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      myManager.popFile(myFrom, myTo);
      setBackground(BACKGROUND);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      setBackground(HOVER_BACKGROUND);
      repaint();
    }

    @Override
    public void mouseExited(MouseEvent e) {
      setBackground(BACKGROUND);
      repaint();
    }
  }
}
