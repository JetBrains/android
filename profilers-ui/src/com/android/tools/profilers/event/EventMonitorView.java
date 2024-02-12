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
package com.android.tools.profilers.event;

import static icons.StudioIcons.Profiler.Events.ROTATE_EVENT;

import com.android.tools.adtui.ActivityComponent;
import com.android.tools.adtui.EventComponent;
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.eventrenderer.EventIconRenderer;
import com.android.tools.adtui.eventrenderer.EventRenderer;
import com.android.tools.adtui.eventrenderer.KeyboardEventRenderer;
import com.android.tools.adtui.eventrenderer.TouchEventRenderer;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.ProfilerMonitorView;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class EventMonitorView extends ProfilerMonitorView<EventMonitor> {

  private static final Map<UserEvent, EventRenderer<UserEvent>> RENDERERS;

  static {
    RENDERERS = new HashMap<>();
    RENDERERS.put(UserEvent.TOUCH, new TouchEventRenderer<>());
    RENDERERS.put(UserEvent.ROTATION, new EventIconRenderer<>(ROTATE_EVENT));
    RENDERERS.put(UserEvent.KEYBOARD, new KeyboardEventRenderer<>());
  }

  private EventComponent<UserEvent> myUserEventComponent;
  private ActivityComponent myActivityComponent;

  public EventMonitorView(@NotNull StudioProfilersView profilersView, @NotNull EventMonitor monitor) {
    super(profilersView, monitor);
    initializeComponents();
  }

  private void initializeComponents() {
    // Initialization order can change depending on how test are setup as such we may initialize components
    // in the super class, or we may initialize them via a call from the stage. Doing a check so we don't
    // create more objects than needed in production code.
    if (myActivityComponent == null) {
      myActivityComponent = new ActivityComponent(getMonitor().getLifecycleEvents());
    }
    if (myUserEventComponent == null) {
      myUserEventComponent = new EventComponent<>(getMonitor().getUserEvents(), RENDERERS);
    }
  }

  @Override
  public float getVerticalWeight() {
    // This forces the monitor to use its specified minimum size
    return 0;
  }

  @Override
  public void registerTooltip(@NotNull RangeTooltipComponent tooltip, Stage stage) {
    registerComponent(myUserEventComponent,
                      () -> new UserEventTooltip(getMonitor().getTimeline(), getMonitor().getUserEvents()),
                      tooltip,
                      stage);
    registerComponent(myActivityComponent,
                      () -> new LifecycleTooltip(getMonitor().getTimeline(), getMonitor().getLifecycleEvents()),
                      tooltip,
                      stage);
  }

  private void registerComponent(JComponent component,
                                 Supplier<TooltipModel> tooltipBuilder,
                                 RangeTooltipComponent tooltipComponent,
                                 Stage stage) {
    component.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        if (getMonitor().isEnabled()) {
          getMonitor().setTooltipBuilder(tooltipBuilder);
          stage.setTooltip(getMonitor().buildTooltip());
        }
        else {
          stage.setTooltip(null);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        stage.setTooltip(null);
      }
    });
    tooltipComponent.registerListenersOn(component);
  }

  @Override
  protected void populateUi(JPanel container) {
    initializeComponents();
    container.setLayout(new TabularLayout("*", "*,*"));
    container.add(myUserEventComponent, new TabularLayout.Constraint(0, 0));
    container.add(myActivityComponent, new TabularLayout.Constraint(1, 0));
  }

  @Override
  protected boolean hasCustomLoadingPanel() {
    return Common.AgentData.Status.UNSPECIFIED == getMonitor().getProfilers().getAgentData().getStatus();
  }

  @Override
  protected void populateCustomLoadingPanel(JPanel container) {
    LoadingPanel loadingPanel = myProfilersView.getIdeProfilerComponents().createLoadingPanel(-1);
    TabularLayout layout = new TabularLayout("*,Fit-,*", "*");
    container.setLayout(layout);
    loadingPanel.startLoading();
    container.add(loadingPanel.getComponent(), new TabularLayout.Constraint(0, 0, 3));
  }
}
