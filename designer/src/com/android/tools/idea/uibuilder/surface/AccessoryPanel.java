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

import static com.android.SdkConstants.TAG_DATA;
import static com.android.SdkConstants.TAG_LAYOUT;

import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AccessoryPanel extends JPanel implements DesignSurfaceListener, ModelListener {

  private final boolean myHandlesVisibility;
  private final List<AccessoryPanelListener> myListeners = new ArrayList<>();

  public enum Type { SOUTH_PANEL, EAST_PANEL }

  private NlDesignSurface mySurface;
  private NlModel myModel;
  private AccessoryPanelInterface myCachedPanel;
  private HashMap<NlComponent, AccessoryPanelInterface> myPanels =  new HashMap<>();
  private Type myType;
  private boolean myInitialSelectionChangeFired;

  public AccessoryPanel(@NotNull Type type, boolean handlesVisibility) {
    super(new BorderLayout());
    setBorder(JBUI.Borders.empty());
    myType = type;
    myHandlesVisibility = handlesVisibility;
  }

  public void setSurface(@Nullable NlDesignSurface surface) {
    if (mySurface != null) {
      mySurface.removeListener(this);
      setModel(null);
    }
    removeCurrentPanel();
    mySurface = surface;
    myInitialSelectionChangeFired = false;
    if (surface == null) {
      setModel(null);
    } else {
      setModel(mySurface.getModel());
      mySurface.addListener(this);
      componentSelectionChanged(surface, Collections.emptyList());
    }
  }

  public void addAccessoryPanelListener(@NotNull AccessoryPanelListener listener) {
    myListeners.add(listener);
  }

  public void removeAccessoryPanelListener(@NotNull AccessoryPanelListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void modelDerivedDataChanged(@NotNull NlModel model) {
    ApplicationManager.getApplication().invokeLater(() -> {
      updatePanel();
      if (myCachedPanel != null) {
        myCachedPanel.updateAfterModelDerivedDataChanged();
      }
    });
  }

  @Override
  public void modelActivated(@NotNull NlModel model) {
    ApplicationManager.getApplication().invokeLater(() -> updatePanel());
  }

  private void updatePanel() {
    if (mySurface == null || myInitialSelectionChangeFired) {
      return;
    }
    NlComponent root = getSingleRootComponent(mySurface);
    if (root == null) {
      return;
    }
    componentSelectionChanged(mySurface, mySurface.getSelectionModel().getSelection());
    myInitialSelectionChangeFired = true;
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
          return skipDataBindingLayout(selected);
        }
      }
      else if (parent != selected.getParent()) {
        parent = null;
        break;
      }
    }
    return skipDataBindingLayout(parent);
  }

  @Nullable
  private static NlComponent getSingleRootComponent(@NotNull DesignSurface<?> surface) {
    List<NlModel> models = surface.getModels();
    if (models.isEmpty()) {
      return null;
    }
    NlModel mainModel = models.get(0);
    List<NlComponent> roots = mainModel.getComponents();
    if (roots.size() != 1) {
      return null;
    }
    return skipDataBindingLayout(roots.get(0));
  }

  @Nullable
  private static NlComponent skipDataBindingLayout(@Nullable NlComponent root) {
    if (root == null) {
      return null;
    }
    if (!TAG_LAYOUT.equals(root.getTagName())) {
      return root;
    }
    List<NlComponent> roots = root.getChildren().stream().filter(component -> !TAG_DATA.equals(component.getTagName())).toList();
    if (roots.size() != 1) {
      return null;
    }
    return roots.get(0);
  }

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface<?> surface, @NotNull List<NlComponent> newSelection) {
    NlComponent parent = findSharedParent(newSelection);
    if (parent == null && newSelection.isEmpty()) {
      parent = getSingleRootComponent(surface);
      if (parent != null) {
        newSelection = Collections.singletonList(parent);
      }
    }
    if (parent == null) {
      removeCurrentPanel();
      return;
    }
    ViewHandler handler = ViewHandlerManager.get(surface.getProject()).getHandler(parent);
    if (handler instanceof ViewGroupHandler) {
      ViewGroupHandler viewGroupHandler = (ViewGroupHandler) handler;
      if (!viewGroupHandler.needsAccessoryPanel(myType)) {
        removeCurrentPanel();
        notifyListeners();
        return;
      }
      AccessoryPanelInterface panel = myPanels.get(parent);
      if (panel == null) {
        ViewGroupHandler.AccessoryPanelVisibility visibilityCallback = mySurface;
        panel = viewGroupHandler.createAccessoryPanel(surface, myType, parent, visibilityCallback);
        myPanels.put(parent, panel);
        removeCurrentPanel();
      }
      if (panel != null) {
        if (panel != myCachedPanel) {
          removeCurrentPanel();
          myCachedPanel = panel;
          add(myCachedPanel.getPanel());
          notifyListeners();
        }

        panel.updateAccessoryPanelWithSelection(myType, newSelection);
        if (myHandlesVisibility) {
          setVisible(true);
        }
      }
    }
  }

  @Nullable
  public AccessoryPanelInterface getCurrentPanel() {
    return myCachedPanel;
  }

  private void notifyListeners() {
    List<AccessoryPanelListener> copy = new ArrayList<>(myListeners);
    copy.forEach(listener -> listener.panelChange(myCachedPanel));
  }

  private void removeCurrentPanel() {
    if (myCachedPanel != null) {
      remove(myCachedPanel.getPanel());
      myCachedPanel.deactivate();
      myCachedPanel = null;
    }
    if (myHandlesVisibility) {
      setVisible(false);
    }
  }
}
