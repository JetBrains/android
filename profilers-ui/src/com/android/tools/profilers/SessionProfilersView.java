/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers;

import static com.android.tools.profilers.sessions.SessionsView.SESSION_EXPANDED_WIDTH;
import static com.android.tools.profilers.sessions.SessionsView.SESSION_IS_COLLAPSED;

import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.adtui.stdui.TooltipLayeredPane;
import com.android.tools.inspectors.common.ui.ContextMenuInstaller;
import com.android.tools.profilers.cpu.CpuCaptureStage;
import com.android.tools.profilers.cpu.CpuCaptureStageView;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.customevent.CustomEventProfilerStage;
import com.android.tools.profilers.customevent.CustomEventProfilerStageView;
import com.android.tools.profilers.memory.AllocationStage;
import com.android.tools.profilers.memory.AllocationStageView;
import com.android.tools.profilers.memory.MainMemoryProfilerStage;
import com.android.tools.profilers.memory.MainMemoryProfilerStageView;
import com.android.tools.profilers.memory.MemoryCaptureStage;
import com.android.tools.profilers.memory.MemoryCaptureStageView;
import com.android.tools.profilers.sessions.SessionsView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiFunction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.LayoutFocusTraversalPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A view containing a sessions panel, a {@link StageWithToolbarView}, and a {@link ViewBinder} that binds {@link Stage}s and
 * {@link StageView}s.
 */
public class SessionProfilersView implements StudioProfilersView {

  @NotNull
  private final StudioProfilers myProfiler;
  @NotNull
  private final IdeProfilerComponents myIdeProfilerComponents;
  private final ViewBinder<SessionProfilersView, Stage, StageView> myBinder;
  @NotNull
  private final TooltipLayeredPane myLayeredPane;
  /**
   * Splitter between the sessions and main profiler stage panel. We use IJ's {@link ThreeComponentsSplitter} as it supports zero-width
   * divider while still handling mouse resize properly.
   */
  @NotNull private final ThreeComponentsSplitter mySplitter;
  private final JPanel myStageComponent;
  private SessionsView mySessionsView;
  @NotNull
  private final StageWithToolbarView myStageWithToolbarView;

  public SessionProfilersView(@NotNull StudioProfilers profiler,
                              @NotNull IdeProfilerComponents ideProfilerComponents,
                              @NotNull Disposable parentDisposable) {
    myProfiler = profiler;
    myIdeProfilerComponents = ideProfilerComponents;

    myStageComponent = new JPanel(new BorderLayout());

    mySplitter = new ThreeComponentsSplitter(this);
    // Override the splitter's custom traversal policy back to the default, because the custom policy prevents the profilers from tabbing
    // across the components (e.g. sessions panel and the main stage UI).
    mySplitter.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    mySplitter.setDividerWidth(0);
    mySplitter.setDividerMouseZoneSize(-1);
    mySplitter.setHonorComponentsMinimumSize(true);

    myLayeredPane = new TooltipLayeredPane(mySplitter);
    initializeSessionUi();

    myBinder = new ViewBinder<>();
    myBinder.bind(StudioMonitorStage.class, StudioMonitorStageView::new);
    myBinder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    myBinder.bind(CpuCaptureStage.class, CpuCaptureStageView::new);
    myBinder.bind(MainMemoryProfilerStage.class, MainMemoryProfilerStageView::new);
    myBinder.bind(MemoryCaptureStage.class, MemoryCaptureStageView::new);
    myBinder.bind(AllocationStage.class, AllocationStageView::new);
    myBinder.bind(NullMonitorStage.class, NullMonitorStageView::new);
    myBinder.bind(CustomEventProfilerStage.class, CustomEventProfilerStageView::new);

    myStageWithToolbarView = new StageWithToolbarView(profiler, myStageComponent, ideProfilerComponents, this::buildStageView, mySplitter);
    mySplitter.setLastComponent(getStageComponent());

    Disposer.register(parentDisposable, this);
  }

  @Override
  public void dispose() {
  }

  @VisibleForTesting
  <S extends Stage, T extends StageView> void bind(@NotNull Class<S> clazz,
                                                   @NotNull BiFunction<SessionProfilersView, S, T> constructor) {
    myBinder.bind(clazz, constructor);
  }

  @VisibleForTesting
  @NotNull
  SessionsView getSessionsView() {
    return mySessionsView;
  }

  private StageView buildStageView(Stage stage) {
    return myBinder.build(this, stage);
  }

  private void initializeSessionUi() {
    mySessionsView = new SessionsView(myProfiler, myIdeProfilerComponents);
    JComponent sessionsComponent = mySessionsView.getComponent();
    mySplitter.setFirstComponent(sessionsComponent);
    mySessionsView.addExpandListener(e -> {
      toggleSessionsPanel(false);
      myProfiler.getIdeServices().getFeatureTracker().trackSessionsPanelStateChanged(true);
    });
    mySessionsView.addCollapseListener(e -> {
      toggleSessionsPanel(true);
      myProfiler.getIdeServices().getFeatureTracker().trackSessionsPanelStateChanged(false);
    });
    boolean initiallyCollapsed =
      myProfiler.getIdeServices().getPersistentProfilerPreferences().getBoolean(SESSION_IS_COLLAPSED, false);
    toggleSessionsPanel(initiallyCollapsed);

    // Track Sessions UI resize event.
    // The divider mechanism within ThreeComponentsSplitter consumes the mouse event so we cannot use regular mouse listeners on the
    // splitter itself. Instead, we mirror the logic that the divider uses to capture mouse event and check whether the width of the
    // sessions UI has changed between mouse press and release. Using Once here to mimic ThreeComponentsSplitter's implementation, as
    // we only need to add the MousePreprocessor to the glassPane once when the UI shows up.
    UiNotifyConnector.Once.installOn(mySplitter, new Activatable() {
      @Override
      public void showNotify() {
        IdeGlassPane glassPane = IdeGlassPaneUtil.find(mySplitter);
        glassPane.addMousePreprocessor(new MouseAdapter() {
          private int mySessionsUiWidth;

          @Override
          public void mousePressed(MouseEvent e) {
            mySessionsUiWidth = sessionsComponent.getWidth();
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            int width = sessionsComponent.getWidth();
            if (mySessionsUiWidth != width) {
              myProfiler.getIdeServices().getPersistentProfilerPreferences().setInt(SESSION_EXPANDED_WIDTH, width);
              myProfiler.getIdeServices().getFeatureTracker().trackSessionsPanelResized();
            }
          }
        }, SessionProfilersView.this);
      }
    });
  }

  private void toggleSessionsPanel(boolean isCollapsed) {
    if (isCollapsed) {
      mySplitter.setDividerMouseZoneSize(-1);
      mySessionsView.getComponent().setMinimumSize(SessionsView.getComponentMinimizeSize(false));
      // Let the Sessions panel min size govern how much space to reserve on the left.
      mySplitter.setFirstSize(0);
    }
    else {
      mySplitter.setDividerMouseZoneSize(JBUI.scale(10));
      mySessionsView.getComponent().setMinimumSize(SessionsView.getComponentMinimizeSize(true));
      mySplitter
        .setFirstSize(myProfiler.getIdeServices().getPersistentProfilerPreferences().getInt(SESSION_EXPANDED_WIDTH, 0));
    }

    mySplitter.revalidate();
    mySplitter.repaint();
  }

  @NotNull
  @Override
  public StudioProfilers getStudioProfilers() {
    return myProfiler;
  }

  @NotNull
  @Override
  public IdeProfilerComponents getIdeProfilerComponents() {
    return myIdeProfilerComponents;
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myLayeredPane;
  }

  @Override
  @NotNull
  public StageWithToolbarView getStageWithToolbarView() {
    return myStageWithToolbarView;
  }

  @Override
  @NotNull
  public final JPanel getStageComponent() {
    return myStageComponent;
  }

  @Override
  @Nullable
  public StageView getStageView() {
    return myStageWithToolbarView.getStageView();
  }

  /**
   * Installs the {@link ContextMenuItem} common to all profilers.
   */
  @Override
  public void installCommonMenuItems(@NotNull JComponent component) {
    ContextMenuInstaller contextMenuInstaller = getIdeProfilerComponents().createContextMenuInstaller();
    ProfilerContextMenu.createIfAbsent(myStageComponent).getContextMenuItems()
      .forEach(item -> contextMenuInstaller.installGenericContextMenu(component, item));
  }
}
