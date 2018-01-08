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

import com.android.tools.adtui.*;
import com.android.tools.adtui.model.event.SimpleEventType;
import com.android.tools.profilers.ProfilerMonitorTooltip;
import com.android.tools.profilers.ProfilerMonitorView;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilersView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class EventMonitorView extends ProfilerMonitorView<EventMonitor> {

  private static final Map<SimpleEventType, SimpleEventRenderer<SimpleEventType>> RENDERERS;

  static {
    RENDERERS = new HashMap<>();
    RENDERERS.put(SimpleEventType.TOUCH, new TouchEventRenderer<>());
    RENDERERS.put(SimpleEventType.ROTATION, new EventIconRenderer<>("/icons/events/rotate-event.png"));
    RENDERERS.put(SimpleEventType.KEYBOARD, new KeyboardEventRenderer<>());
  }

  private SimpleEventComponent<SimpleEventType> myEventComponent;
  private StackedEventComponent myActivityComponent;

  public EventMonitorView(@NotNull StudioProfilersView profilersView, @NotNull EventMonitor monitor) {
    super(monitor);
    initializeComponents();
  }

  private void initializeComponents() {
    // Initialization order can change depending on how test are setup as such we may initialize components
    // in the super class, or we may initialize them via a call from the stage. Doing a check so we don't
    // create more objects than needed in production code.
    if (myActivityComponent == null) {
      myActivityComponent = new StackedEventComponent(getMonitor().getActivityEvents());
    }
    if (myEventComponent == null) {
      myEventComponent = new SimpleEventComponent<>(getMonitor().getSimpleEvents(), RENDERERS);
    }
  }

  @Override
  public float getVerticalWeight() {
    // This forces the monitor to use its specified minimum size
    return 0;
  }

  @Override
  public void registerTooltip(@NotNull RangeTooltipComponent tooltip, Stage stage) {
    registerComponent(myEventComponent, () -> new EventSimpleEventTooltip(getMonitor()), tooltip, stage);
    registerComponent(myActivityComponent, () -> new EventActivityTooltip(getMonitor()), tooltip, stage);
  }

  private void registerComponent(JComponent component,
                                 Supplier<ProfilerMonitorTooltip<EventMonitor>> tooltip,
                                 RangeTooltipComponent tooltipComponent,
                                 Stage stage) {
    tooltipComponent.registerListenersOn(component);
    component.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        if (getMonitor().isEnabled()) {
          getMonitor().setTooltipBuilder(tooltip);
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
  }

  @Override
  protected void populateUi(JPanel container) {
    initializeComponents();
    container.setLayout(new TabularLayout("*", "*,*"));
    container.add(myEventComponent, new TabularLayout.Constraint(0, 0));
    container.add(myActivityComponent, new TabularLayout.Constraint(1, 0));
  }
}
