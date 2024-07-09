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
package com.android.tools.idea.common.surface;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.model.DefaultSelectionModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionListener;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport;
import com.android.tools.idea.common.layout.manager.MatchParentLayoutManager;
import com.android.tools.idea.common.surface.layout.NonScrollableDesignSurfaceViewport;
import com.android.tools.idea.common.surface.layout.ScrollableDesignSurfaceViewport;
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JScrollPane;
import javax.swing.OverlayLayout;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DesignSurface<T extends SceneManager> extends PreviewSurface<T> {

  @Nullable protected final JScrollPane myScrollPane;

  @Nullable
  @Override
  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  @NotNull private final JComponent myContentContainerPane;
  protected @NotNull JComponent getContentContainerPane() { return myContentContainerPane; }

  @NotNull protected final DesignSurfaceViewport myViewport;
  @NotNull protected final SceneViewPanel mySceneViewPanel;

  @Override
  protected @NotNull SceneViewPanel getSceneViewPanel() { return mySceneViewPanel; }

  @VisibleForTesting
  private final GuiInputHandler myGuiInputHandler;

  private final ActionManager<? extends DesignSurface<T>> myActionManager;

  @NotNull
  private final DesignerAnalyticsManager myAnalyticsManager;

  @NotNull
  private final AWTEventListener myOnHoverListener;

  @NotNull
  @Override
  public AWTEventListener getOnHoverListener() {
    return myOnHoverListener;
  }

  public DesignSurface(
    @NotNull Project project,
    @NotNull Disposable parentDisposable,
    @NotNull Function1<DesignSurface<T>, ActionManager<? extends DesignSurface<T>>> actionManagerProvider,
    @NotNull Function1<DesignSurface<T>, InteractionHandler> interactionProviderCreator,
    @NotNull Function1<DesignSurface<T>, PositionableContentLayoutManager> positionableLayoutManagerProvider,
    @NotNull Function1<DesignSurface<T>, DesignSurfaceActionHandler> designSurfaceActionHandlerProvider,
    @NotNull ZoomControlsPolicy zoomControlsPolicy) {
    this(project, parentDisposable, actionManagerProvider, SurfaceInteractable::new, interactionProviderCreator,
         positionableLayoutManagerProvider, designSurfaceActionHandlerProvider, new DefaultSelectionModel(), zoomControlsPolicy);
  }

  public DesignSurface(
    @NotNull Project project,
    @NotNull Disposable parentDisposable,
    @NotNull Function1<DesignSurface<T>, ActionManager<? extends DesignSurface<T>>> actionManagerProvider,
    @NotNull Function1<DesignSurface<T>, Interactable> interactableProvider,
    @NotNull Function1<DesignSurface<T>, InteractionHandler> interactionProviderCreator,
    @NotNull Function1<DesignSurface<T>, PositionableContentLayoutManager> positionableLayoutManagerProvider,
    @NotNull Function1<DesignSurface<T>, DesignSurfaceActionHandler> actionHandlerProvider,
    @NotNull SelectionModel selectionModel,
    @NotNull ZoomControlsPolicy zoomControlsPolicy) {
    super(project, parentDisposable, actionManagerProvider, interactableProvider, interactionProviderCreator,
          positionableLayoutManagerProvider, actionHandlerProvider,  selectionModel, zoomControlsPolicy);

    myAnalyticsManager = new DesignerAnalyticsManager(this);

    // TODO: handle the case when selection are from different NlModels.
    // Manager can be null if the selected component is not part of NlModel. For example, a temporarily NlMode.
    // In that case we don't change focused SceneView.
    SelectionListener selectionListener = (model, selection) -> {
      if (getFocusedSceneView() != null) {
        notifySelectionListeners(selection);
      }
      else {
        notifySelectionListeners(Collections.emptyList());
      }
    };
    getSelectionModel().addListener(selectionListener);

    mySceneViewPanel = new SceneViewPanel(
      this::getSceneViews,
      () -> getGuiInputHandler().getLayers(),
      this::getActionManager,
      this,
      this::shouldRenderErrorsPanel,
      getPositionableLayoutManagerProvider().invoke(this));
    mySceneViewPanel.setBackground(getBackground());

    if (getHasZoomControls()) {
      myScrollPane = DesignSurfaceScrollPane.createDefaultScrollPane(mySceneViewPanel, getBackground(), this::notifyPanningChanged);
    }
    else {
      myScrollPane = null;
    }

    if (myScrollPane != null) {
      getLayeredPane().setLayout(new MatchParentLayoutManager());
      getLayeredPane().add(myScrollPane, JLayeredPane.POPUP_LAYER);
      myContentContainerPane = myScrollPane;
      myViewport = new ScrollableDesignSurfaceViewport(myScrollPane.getViewport());
      myScrollPane.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          // Relayout the PositionableContents when visible size (e.g. window size) of ScrollPane is changed.
          revalidateScrollArea();
        }
      });
    }
    else {
      getLayeredPane().setLayout(new OverlayLayout(getLayeredPane()));
      mySceneViewPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
      getLayeredPane().add(mySceneViewPanel, JLayeredPane.POPUP_LAYER);
      myContentContainerPane = mySceneViewPanel;
      myViewport = new NonScrollableDesignSurfaceViewport(this);
    }

    add(getLayeredPane());

    Interactable interactable = interactableProvider.invoke(this);
    myGuiInputHandler = new GuiInputHandler(this, interactable, interactionProviderCreator.invoke(this));
    myGuiInputHandler.startListening();
    //noinspection AbstractMethodCallInConstructor
    myActionManager = actionManagerProvider.invoke(this);
    myActionManager.registerActionsShortcuts(getLayeredPane());

    if (getZoomControlsLayerPane() != null) {
      getLayeredPane().add(getZoomControlsLayerPane(), JLayeredPane.DRAG_LAYER);
      getZoomControlsLayerPane().add(myActionManager.getDesignSurfaceToolbar(), BorderLayout.EAST);
      if (getZoomControlsPolicy() == ZoomControlsPolicy.AUTO_HIDE) {
        myOnHoverListener = DesignSurfaceHelper.createZoomControlAutoHiddenListener(this, getZoomControlsLayerPane());
        Toolkit.getDefaultToolkit().addAWTEventListener(myOnHoverListener, AWTEvent.MOUSE_EVENT_MASK);
      }
      else {
        myOnHoverListener = event -> {};
      }
    }
    else {
      myOnHoverListener = event -> {};
    }
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  @NotNull
  @Override
  public DesignSurfaceViewport getViewport() {
    return myViewport;
  }

  @NotNull
  @Override
  public ActionManager getActionManager() {
    return myActionManager;
  }

  @Override
  protected void notifyModelChanged(@NotNull NlModel model) {
    // TODO: The listeners have the expectation of the call happening in the EDT. We need
    //       to address that.
    for (DesignSurfaceListener listener : getSurfaceListeners()) {
      listener.modelChanged(this, model);
    }
  }

  @NotNull
  @Override
  public DesignerAnalyticsManager getAnalyticsManager() {
    return myAnalyticsManager;
  }

  protected void notifySelectionListeners(@NotNull List<NlComponent> newSelection) {
    List<DesignSurfaceListener> listeners = getSurfaceListeners();
    for (DesignSurfaceListener listener : listeners) {
      listener.componentSelectionChanged(this, newSelection);
    }
  }

  @NotNull
  @Override
  public GuiInputHandler getGuiInputHandler() {
    return myGuiInputHandler;
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return getActionHandlerProvider().invoke(this);
    }
    return super.getData(dataId);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    //noinspection FieldAccessNotGuarded We are only accessing the reference so we do not need to guard the access
    if (getModelToSceneManagers() != null) {
      // updateUI() is called in the parent constructor, at that time all class member in this class has not initialized.
      for (SceneManager manager : getSceneManagers()) {
        manager.getSceneViews().forEach(SceneView::updateUI);
      }
    }
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);

    // setBackground is called before the class initialization is complete so we do the null checking to prevent calling mySceneViewPanel
    // before the constructor has completed. At that point mySceneViewPanel might still be null.
    //noinspection ConstantConditions
    if (mySceneViewPanel != null) {
      mySceneViewPanel.setBackground(bg);
    }
  }
}
