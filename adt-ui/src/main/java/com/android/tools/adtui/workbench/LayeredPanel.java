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
package com.android.tools.adtui.workbench;

import static com.android.tools.adtui.workbench.AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX;

import com.android.tools.adtui.common.AdtUiUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.JBUI;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.LayoutFocusTraversalPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link LayeredPanel} implements {@link AutoHide} tool windows.
 * The main part of a {@link WorkBench} is placed lowest in the z-order of this {@link JBLayeredPane}.
 * Any {@link AutoHide} tool windows is placed on top whenever they are visible.
 *
 * @param <T> Specifies the type of data controlled by the {@link WorkBench}.
 */
class LayeredPanel<T> extends JBLayeredPane implements SideModel.Listener<T>, Disposable {
  private final String myBenchName;
  private final PropertiesComponent myPropertiesComponent;
  private final ThreeComponentsSplitter mySplitter;
  private final JPanel myContainer;
  private String myToolName;
  private Side mySide;

  LayeredPanel(@NotNull String benchName, @NotNull JComponent defaultLayer, @NotNull SideModel<T> model) {
    this(benchName, defaultLayer, model, PropertiesComponent.getInstance());
  }

  LayeredPanel(@NotNull String benchName,
               @NotNull JComponent defaultLayer,
               @NotNull SideModel<T> model,
               @NotNull PropertiesComponent propertiesComponent) {
    myBenchName = benchName;
    myPropertiesComponent = propertiesComponent;
    myContainer = new JPanel();
    myContainer.setOpaque(false);
    myContainer.addComponentListener(createWidthUpdater());
    mySplitter = new ThreeComponentsSplitter();
    mySplitter.setOpaque(false);
    mySplitter.setInnerComponent(myContainer);
    mySplitter.setDividerWidth(JBUI.scale(0));
    mySplitter.setFocusCycleRoot(false);
    mySplitter.setFocusTraversalPolicyProvider(true);
    mySplitter.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    mySide = Side.LEFT;

    add(defaultLayer, DEFAULT_LAYER);
    add(mySplitter, PALETTE_LAYER);
    model.addListener(this);

    setFullOverlayLayout(true);
  }

  @VisibleForTesting
  ThreeComponentsSplitter getSplitter() {
    return mySplitter;
  }

  @Override
  public void modelChanged(@NotNull SideModel<T> model, @NotNull SideModel.EventType unused) {
    model.getHiddenSliders().forEach(this::addHiddenTool);
    addVisibleTool(model.getVisibleAutoHideTool());
    revalidate();
    repaint();
  }

  private void addVisibleTool(@Nullable AttachedToolWindow<T> tool) {
    if (tool == null) {
      mySplitter.setVisible(false);
      mySplitter.setFirstComponent(null);
      mySplitter.setLastComponent(null);
      myToolName = null;
    }
    else {
      JComponent component = tool.getComponent();
      component.setVisible(true);
      mySplitter.setVisible(true);
      myToolName = tool.getToolName();
      if (tool.isLeft()) {
        mySide = Side.LEFT;
        mySplitter.setFirstComponent(component);
        mySplitter.setFirstSize(getToolWidth(tool));
        mySplitter.setLastComponent(null);
        myContainer.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));
      }
      else {
        mySide = Side.RIGHT;
        mySplitter.setFirstComponent(null);
        mySplitter.setLastComponent(component);
        mySplitter.setLastSize(getToolWidth(tool));
        myContainer.setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));
      }
    }
  }

  private void addHiddenTool(@NotNull AttachedToolWindow<T> tool) {
    JComponent component = tool.getComponent();
    component.setVisible(false);
    myContainer.add(component, PALETTE_LAYER);
  }

  @Override
  public void dispose() {
  }

  @NotNull
  private ComponentListener createWidthUpdater() {
    return new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        int width = mySide.isLeft() ? mySplitter.getFirstSize() : mySplitter.getLastSize();
        if (myToolName != null && width > 0) {
          setToolWidth(width);
        }
      }
    };
  }

  @NotNull
  private String getUnscaledWidthPropertyName() {
    return TOOL_WINDOW_PROPERTY_PREFIX + myBenchName + "." + myToolName + ".UNSCALED.WIDTH";
  }

  @NotNull
  private String getScaledWidthPropertyName() {
    return TOOL_WINDOW_PROPERTY_PREFIX + myBenchName + "." + myToolName + ".WIDTH";
  }

  private int getToolWidth(@NotNull AttachedToolWindow<T> tool) {
    int width = myPropertiesComponent.getInt(getUnscaledWidthPropertyName(), -1);
    if (width != -1) {
      return JBUI.scale(width);
    }
    int scaledWidth = myPropertiesComponent.getInt(getScaledWidthPropertyName(), -1);
    if (scaledWidth == -1) {
      return tool.getDefinition().getInitialMinimumWidth();
    }
    myPropertiesComponent.unsetValue(getScaledWidthPropertyName());
    setToolWidth(scaledWidth);
    return scaledWidth;
  }

  private void setToolWidth(int width) {
    myPropertiesComponent.setValue(getUnscaledWidthPropertyName(), AdtUiUtils.unscale(width), -1);
  }
}
