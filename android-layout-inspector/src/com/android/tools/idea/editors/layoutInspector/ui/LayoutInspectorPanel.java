/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.layoutInspector.ui;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext;
import com.android.tools.idea.editors.layoutInspector.actions.LoadOverlayAction;
import com.android.tools.idea.editors.layoutInspector.actions.SetOverlayAlphaAction;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.ImageEditorActions;
import org.intellij.images.editor.actions.ActualSizeAction;
import org.intellij.images.editor.actions.ToggleGridAction;
import org.intellij.images.editor.actions.ZoomInAction;
import org.intellij.images.editor.actions.ZoomOutAction;
import org.intellij.images.options.EditorOptions;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;
import org.intellij.images.options.ZoomOptions;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Objects;

/**
 * The middle section of Layout Inspector that displays active ViewNode selected by the Layout Tree.
 */
public class LayoutInspectorPanel extends JPanel implements DataProvider, ImageComponentDecorator {
  private final Class[] SUPPORTED_IMAGE_ACTIONS = new Class[]{ToggleGridAction.class, ZoomInAction.class, ZoomOutAction.class, ActualSizeAction.class};

  @NotNull private final JScrollPane myScrollPane;

  @NotNull private ViewNodeActiveDisplay myPreview;
  @NotNull private final ImageZoomModel myZoomModel = new ViewNodeZoomModel();
  @NotNull private final ImageWheelAdapter myWheelAdapter = new ImageWheelAdapter();

  public LayoutInspectorPanel(@NotNull LayoutInspectorContext context) {
    super(new BorderLayout());
    setOpaque(true);

    myPreview = new ViewNodeActiveDisplay(context.getRoot(), context.getBufferedImage());
    myPreview.addViewNodeActiveDisplayListener(context);

    add(getActionPanel(), BorderLayout.NORTH);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myPreview, BorderLayout.CENTER);
    myScrollPane = ScrollPaneFactory.createScrollPane(panel);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.addMouseWheelListener(myWheelAdapter);
    add(myScrollPane, BorderLayout.CENTER);

    context.setPreview(myPreview);
  }

  /**
   * Returns the top panel which holds ActionGroups
   */
  @NotNull
  private JPanel getActionPanel() {
    ActionManager actionManager = ActionManager.getInstance();

    DefaultActionGroup liActionGroup = getInspectorActionGroup();
    ActionToolbar liActionToolbar = actionManager.createActionToolbar(
      "LiActionsToolbar", liActionGroup, true
    );
    liActionToolbar.updateActionsImmediately();
    liActionToolbar.setTargetComponent(this);
    JComponent actionToolbarPanel = liActionToolbar.getComponent();
    actionToolbarPanel.setName("LiActionsToolbar");

    ActionGroup actionGroup = getZoomActionGroup(actionManager);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(
      ImageEditorActions.ACTION_PLACE, actionGroup, true
    );
    // Make sure toolbar is 'ready' before it's added to component hierarchy
    // to prevent ActionToolbarImpl.updateActionsImpl(boolean, boolean) from increasing popup size unnecessarily
    actionToolbar.updateActionsImmediately();
    actionToolbar.setTargetComponent(this);
    JComponent toolbarPanel = actionToolbar.getComponent();

    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    if (StudioFlags.LAYOUT_INSPECTOR_LOAD_OVERLAY_ENABLED.get()) {
      topPanel.add(actionToolbarPanel, BorderLayout.WEST);
    }
    topPanel.add(toolbarPanel, BorderLayout.EAST);
    return topPanel;
  }

  private DefaultActionGroup getInspectorActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new LoadOverlayAction(myPreview), Constraints.FIRST);
    actionGroup.add(new SetOverlayAlphaAction(myPreview), new Constraints(Anchor.AFTER, LoadOverlayAction.ACTION_ID));
    return actionGroup;
  }

  private ActionGroup getZoomActionGroup(ActionManager actionManager) {
    DefaultActionGroup intellijActionGroup = (DefaultActionGroup)actionManager.getAction(ImageEditorActions.GROUP_TOOLBAR);
    DefaultActionGroup zoomActionGroup = new DefaultActionGroup();

    for (AnAction child : intellijActionGroup.getChildActionsOrStubs()) {
      // Actions are instances of ActionStub before render
      if (child instanceof ActionStub) {
        for (Class cls : SUPPORTED_IMAGE_ACTIONS) {
          if (Objects.equals(((ActionStub)child).getClassName(), cls.getName())) {
            zoomActionGroup.add(child);
          }
        }
      }
      // After render they are actual Actions
      else {
        for (Class cls : SUPPORTED_IMAGE_ACTIONS) {
          if (child.getClass().equals(cls)) {
            zoomActionGroup.add(child);
          }
        }
      }
    }

    return zoomActionGroup;
  }

  @Override
  public void setTransparencyChessboardVisible(boolean visible) {

  }

  @Override
  public boolean isTransparencyChessboardVisible() {
    return false;
  }

  @Override
  public boolean isEnabledForActionPlace(String place) {
    return false;
  }

  @Override
  public void setGridVisible(boolean visible) {
    myPreview.setGridVisible(visible);
  }

  @Override
  public boolean isGridVisible() {
    return myPreview.isGridVisible();
  }

  @Override
  public boolean isFileSizeVisible() {
    return false;
  }

  @Override
  public boolean isFileNameVisible() {
    return false;
  }

  @Override
  @NotNull
  public ImageZoomModel getZoomModel() {
    return myZoomModel;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (ImageComponentDecorator.DATA_KEY.is(dataId)) {
      return this;
    }
    return null;
  }

  @VisibleForTesting
  @NotNull
  ViewNodeActiveDisplay getPreview() {
    return myPreview;
  }

  // A simplified implementation of ZoomModel based on ImageEditorUI's private ImageZoomModelImpl
  private class ViewNodeZoomModel implements ImageZoomModel {
    // change zoom by 30% each time gives a good balance between finesse and too little change on each zoom
    private static final double DELTA = 1.3;
    private boolean myZoomLevelChanged = false;

    @Override
    public double getZoomFactor() {
      return myPreview.getZoomFactor();
    }

    @Override
    public void setZoomFactor(double zoomFactor) {
      myPreview.setZoomFactor((float)zoomFactor);
      myZoomLevelChanged = false;
    }

    @Override
    public void zoomOut() {
      setZoomFactor(getZoomFactor() / DELTA);
      myZoomLevelChanged = true;
    }

    @Override
    public void zoomIn() {
      setZoomFactor(getZoomFactor() * DELTA);
      myZoomLevelChanged = true;
    }

    @Override
    public boolean canZoomOut() {
      return myScrollPane.getHorizontalScrollBar().isVisible() || myScrollPane.getVerticalScrollBar().isVisible();
    }

    @Override
    public boolean canZoomIn() {
      return getZoomFactor() < ImageZoomModel.MACRO_ZOOM_LIMIT;
    }

    @Override
    public boolean isZoomLevelChanged() {
      return myZoomLevelChanged;
    }
  }

  private final class ImageWheelAdapter implements MouseWheelListener {
    @Override
    public void mouseWheelMoved(@NotNull MouseWheelEvent e) {
      Options options = OptionsManager.getInstance().getOptions();
      EditorOptions editorOptions = options.getEditorOptions();
      ZoomOptions zoomOptions = editorOptions.getZoomOptions();
      if (zoomOptions.isWheelZooming() && e.isControlDown()) {
        int rotation = e.getWheelRotation();
        // scroll down = 1, scroll up = -1
        if (rotation < 0 && myZoomModel.canZoomIn()) {
          myZoomModel.zoomIn();
        }
        else if (rotation > 0 && myZoomModel.canZoomOut()) {
          myZoomModel.zoomOut();
        }

        e.consume();
      }
    }
  }
}
