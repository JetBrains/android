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
package com.android.tools.idea.common.editor;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.adtui.common.AdtPrimaryPanel;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.util.ActionToolbarUtil;
import com.android.tools.editor.PanZoomListener;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.google.common.collect.Iterables;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.event.AdjustmentEvent;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The actions toolbar updates dynamically based on the component selection, their
 * parents (and if no selection, the root layout)
 */
public final class ActionsToolbar implements DesignSurfaceListener, Disposable, PanZoomListener, ConfigurationListener,
                                             ModelListener {

  private static final int CONFIGURATION_UPDATE_FLAGS = ConfigurationListener.CFG_TARGET |
                                                        ConfigurationListener.CFG_DEVICE;

  private final DesignSurface<?> mySurface;
  private final JComponent myToolbarComponent;
  private ActionToolbar myNorthToolbar;
  private ActionToolbar myNorthEastToolbar;
  private ActionToolbarImpl myCenterToolbar;
  private ActionToolbar myEastToolbar;
  private final DefaultActionGroup myDynamicGroup = new DefaultActionGroup();
  private Configuration myConfiguration;
  private DesignerEditorFileType myLayoutType;
  private ToolbarActionGroups myToolbarActionGroups;
  private NlModel myModel = null;

  public ActionsToolbar(@NotNull Disposable parent, @NotNull DesignSurface<?> surface) {
    Disposer.register(parent, this);
    mySurface = surface;
    mySurface.addListener(this);
    mySurface.addPanZoomListener(this);
    if (myConfiguration == null) {
      // TODO: Update to support multiple configurations
      myConfiguration = Iterables.getFirst(mySurface.getConfigurations(), null);
      if (myConfiguration != null) {
        myConfiguration.addListener(this);
      }
    }
    myToolbarComponent = createToolbarComponent();
    updateActionGroups(surface.getLayoutType());
    updateActions();
  }

  @Override
  public void dispose() {
    mySurface.removePanZoomListener(this);
    mySurface.removeListener(this);
    if (myConfiguration != null) {
      myConfiguration.removeListener(this);
    }
    if (myModel != null) {
      myModel.removeListener(this);
      myModel = null;
    }
  }

  @NotNull
  public JComponent getToolbarComponent() {
    return myToolbarComponent;
  }

  @TestOnly
  ActionToolbarImpl getCenterToolbar() {
    return myCenterToolbar;
  }

  @NotNull
  private static JComponent createToolbarComponent() {
    JComponent panel = new AdtPrimaryPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, StudioColorsKt.getBorder()));
    return panel;
  }

  private void updateActionGroups(@NotNull DesignerEditorFileType layoutType) {
   myToolbarComponent.removeAll();
    if (myToolbarActionGroups != null) {
      Disposer.dispose(myToolbarActionGroups);
    }
    myToolbarActionGroups = layoutType.getToolbarActionGroups(mySurface);
    Disposer.register(this, myToolbarActionGroups);

    myNorthToolbar = createActionToolbar("NlConfigToolbar", myToolbarActionGroups.getNorthGroup());
    myNorthToolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);
    myNorthToolbar.setTargetComponent(mySurface);

    JComponent northToolbarComponent = myNorthToolbar.getComponent();
    northToolbarComponent.setName("NlConfigToolbar");

    myNorthEastToolbar = createActionToolbar("NlRhsConfigToolbar", myToolbarActionGroups.getNorthEastGroup());
    myNorthEastToolbar.setTargetComponent(mySurface);

    JComponent northEastToolbarComponent = myNorthEastToolbar.getComponent();
    myNorthEastToolbar.setReservePlaceAutoPopupIcon(false);
    northEastToolbarComponent.setName("NlRhsConfigToolbar");
    myNorthEastToolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);

    myCenterToolbar = createActionToolbar("NlLayoutToolbar", myDynamicGroup);
    myCenterToolbar.setTargetComponent(mySurface);

    JComponent centerToolbarComponent = myCenterToolbar.getComponent();
    centerToolbarComponent.setName("NlLayoutToolbar");
    // Wrap the component inside a fixed height component so it doesn't disappear
    JPanel centerToolbarComponentWrapper = new AdtPrimaryPanel(new BorderLayout());
    centerToolbarComponentWrapper.add(centerToolbarComponent);

    myEastToolbar = createActionToolbar("NlRhsToolbar", myToolbarActionGroups.getEastGroup());
    myEastToolbar.setTargetComponent(mySurface);

    JComponent eastToolbarComponent = myEastToolbar.getComponent();
    eastToolbarComponent.setName("NlRhsToolbar");

    if (northToolbarComponent.isVisible() || northEastToolbarComponent.isVisible()) {
      JComponent northPanel = new AdtPrimaryPanel(new BorderLayout());
      northPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, StudioColorsKt.getBorder()));
      northPanel.add(northToolbarComponent, BorderLayout.CENTER);
      northPanel.add(northEastToolbarComponent, BorderLayout.EAST);
      myToolbarComponent.add(northPanel, BorderLayout.NORTH);
    }

    NlModel model = mySurface.getModels().stream().findFirst().orElse(null);
    // Only add center toolbar for XML files.
    //noinspection UnstableApiUsage
    if (model != null && BackedVirtualFile.getOriginFileIfBacked(model.getVirtualFile()).getFileType() instanceof XmlFileType) {
      myToolbarComponent.add(centerToolbarComponentWrapper, BorderLayout.CENTER);
    }
    myToolbarComponent.add(eastToolbarComponent, BorderLayout.EAST);
  }

  @NotNull
  private static ActionToolbarImpl createActionToolbar(@NotNull String place, @NotNull ActionGroup group) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(place, group, true);
    toolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    if (group == ActionGroup.EMPTY_GROUP) {
      toolbar.getComponent().setVisible(false);
    }
    ActionToolbarUtil.makeToolbarNavigable(toolbar);
    return (ActionToolbarImpl)toolbar;
  }

  /**
   * Call to update the state of all the toolbar icons. This can be called when we do not want to wait the default 500ms automatic
   * delay where toolbars are updated automatically.
   */
  private void refreshToolbarState() {
    myNorthToolbar.updateActionsImmediately();
    myNorthEastToolbar.updateActionsImmediately();
    myEastToolbar.updateActionsImmediately();
    myCenterToolbar.updateActionsImmediately();
  }

  public void updateActions() {
    SceneView view = mySurface.getFocusedSceneView();
    if (view != null) {
      SelectionModel selectionModel = view.getSelectionModel();
      List<NlComponent> selection = selectionModel.getSelection();
      if (selection.isEmpty()) {
        List<NlComponent> roots = view.getSceneManager().getModel().getComponents();
        if (roots.size() == 1) {
          selection = Collections.singletonList(roots.get(0));
        }
      }
      updateActions(selection);
    }
    else {
      refreshToolbarState();
    }
  }

  private void updateActions(@NotNull List<NlComponent> newSelection) {
    SceneView screenView = mySurface.getFocusedSceneView();
    if (screenView != null) {
      // TODO: Perform caching
      DesignerEditorFileType surfaceLayoutType = mySurface.getLayoutType();
      DefaultActionGroup selectionToolbar = surfaceLayoutType.getSelectionContextToolbar(mySurface, newSelection);
      if (selectionToolbar.getChildrenCount() > 0) {
        myDynamicGroup.copyFromGroup(selectionToolbar);
      }
      updateBottomActionBarBorder();
      myCenterToolbar.clearPresentationCache();
    }

    refreshToolbarState();
  }

  // ---- Implements DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface<?> surface, @NotNull List<NlComponent> newSelection) {
    assert surface == mySurface;
    if (!newSelection.isEmpty()) {
      updateActions(newSelection);
    }
    else {
      updateActions();
    }
  }

  @Override
  @UiThread
  public void modelChanged(@NotNull DesignSurface<?> surface, @Nullable NlModel model) {
    if (myModel != null) {
      myModel.removeListener(this);
    }
    if (model != null) {
      model.addListener(this);
    }
    myModel = model;
    myNorthToolbar.updateActionsImmediately();
    myNorthEastToolbar.updateActionsImmediately();
    DesignerEditorFileType surfaceLayoutType = surface.getLayoutType();
    if (surfaceLayoutType != myLayoutType) {
      myLayoutType = surfaceLayoutType;
      updateActionGroups(myLayoutType);
    }
    updateActions();
  }

  // Hide the bottom border on the main toolbar when the toolbar is empty.
  // This eliminates the double border from the toolbar when the north toolbar is visible.
  private void updateBottomActionBarBorder() {
    boolean hasBottomActionBar = myEastToolbar.getComponent().isVisible() || myDynamicGroup.getChildrenCount() > 0;
    int bottom = hasBottomActionBar ? 1 : 0;
    myToolbarComponent.setBorder(BorderFactory.createMatteBorder(0, 0, bottom, 0, StudioColorsKt.getBorder()));
  }

  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface<?> surface, @NotNull NlComponent component) {
    return false;
  }

  // ---- Implements ModelListener ----

  @Override
  public void modelDerivedDataChanged(@NotNull NlModel model) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (mySurface.getProject().isDisposed()) {
        return;
      }
      if (model.getComponents().size() == 1) {
        updateActions();
      }
    });
  }

  @Override
  public void zoomChanged(double previousScale, double newScale) {
    UIUtil.invokeLaterIfNeeded(() -> myNorthEastToolbar.updateActionsImmediately());
  }

  @Override
  public void panningChanged(@NotNull AdjustmentEvent event) {
    UIUtil.invokeLaterIfNeeded(() -> myNorthEastToolbar.updateActionsImmediately());
  }

  @Override
  public boolean changed(int flags) {
    if ((flags & CONFIGURATION_UPDATE_FLAGS) > 0) {
      if (myNorthToolbar != null) {
        // the North toolbar is the one holding the Configuration Actions
        UIUtil.invokeLaterIfNeeded(() -> myNorthEastToolbar.updateActionsImmediately());
      }
    }
    return true;
  }
}
