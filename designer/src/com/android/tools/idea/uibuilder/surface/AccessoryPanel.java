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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;

public class AccessoryPanel extends JPanel implements DesignSurfaceListener, Disposable, ModelListener {

  public static final int SOUTH_ACCESSORY_PANEL = 0;
  public static final int EAST_ACCESSORY_PANEL = 1;

  private final NlDesignSurface mySurface;
  private NlModel myModel;
  private JPanel myCachedPanel;
  private HashMap<ViewGroupHandler, JPanel> myPanels =  new HashMap<>();
  private int myType = 0;

  public AccessoryPanel(@NotNull NlDesignSurface surface, int type) {
    super(new BorderLayout());
    mySurface = surface;
    mySurface.addListener(this);
    Disposer.register(surface, this);
    myType = type;
  }

  @Override
  public void dispose() {
    mySurface.removeListener(this);
    if (myModel !=  null) {
      myModel.removeListener(this);
    }
  }

  @Override
  public void modelDerivedDataChanged(@NotNull NlModel model) {
    updatePanel(model);
  }

  @Override
  public void modelActivated(@NotNull NlModel model) {
    updatePanel(model);
  }

  private void updatePanel(@NotNull NlModel model) {
    if (mySurface == null) {
      return;
    }
    if (mySurface.getSelectionModel().isEmpty()) {
      componentSelectionChanged(mySurface, model.getComponents());
    }
  }

  public void setModel(@Nullable NlModel model) {
    if (myModel != null) {
      myModel.removeListener(this);
    }
    if (model != null) {
      model.addListener(this);
    }
    myModel = model;
  }

  @Nullable
  private static NlComponent findSharedParent(@NotNull List<NlComponent> newSelection) {
    NlComponent parent = null;
    for (NlComponent selected : newSelection) {
      if (parent == null) {
        parent = selected.getParent();
        if (newSelection.size() == 1 && selected.isRoot() && (parent == null || parent.isRoot())) {
          // If you select a root layout, offer selection actions on it as well
          return selected;
        }
      }
      else if (parent != selected.getParent()) {
        parent = null;
        break;
      }
    }
    return parent;
  }

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
    if (newSelection.isEmpty()) {
      removeCurrentPanel();
      return;
    }
    NlComponent parent = findSharedParent(newSelection);
    if (parent == null) {
      removeCurrentPanel();
      return;
    }
    ViewHandler handler = ViewHandlerManager.get(surface.getProject()).getHandler(parent);
    if (handler instanceof ViewGroupHandler) {
      ViewGroupHandler viewGroupHandler = (ViewGroupHandler) handler;
      if (!viewGroupHandler.needsAccessoryPanel()) {
        removeCurrentPanel();
        return;
      }
      JPanel panel = myPanels.get(viewGroupHandler);
      if (panel == null) {
        panel = viewGroupHandler.createAccessoryPanel(myType);
        myPanels.put(viewGroupHandler, panel);
      }
      viewGroupHandler.updateAccessoryPanelWithSelection(myType, panel, newSelection);
      if (panel == myCachedPanel) {
        return;
      }
      removeCurrentPanel();
      myCachedPanel = panel;
      add(myCachedPanel);
      setVisible(true);
    }
  }

  private void removeCurrentPanel() {
    if (myCachedPanel != null) {
      remove(myCachedPanel);
    }
    setVisible(false);
  }

}
