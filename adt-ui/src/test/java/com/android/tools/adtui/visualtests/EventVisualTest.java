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

package com.android.tools.adtui.visualtests;

import static icons.StudioIcons.Profiler.Events.ROTATE_EVENT;

import com.android.tools.adtui.ActivityComponent;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.EventComponent;
import com.android.tools.adtui.eventrenderer.EventIconRenderer;
import com.android.tools.adtui.eventrenderer.EventRenderer;
import com.android.tools.adtui.eventrenderer.KeyboardEventRenderer;
import com.android.tools.adtui.eventrenderer.TouchEventRenderer;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.LifecycleAction;
import com.android.tools.adtui.model.event.LifecycleEvent;
import com.android.tools.adtui.model.event.LifecycleEventModel;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class EventVisualTest extends VisualTest {

  private static final String[] ACTIVITY_NAMES = {
    "SignInActivity",
    "GameModeActivity",
    "MainMenuActivity",
    "OptionsActivity",
    "MultiplayerActivity"
  };

  private static final Map<ActionType, EventRenderer<ActionType>> MOCK_RENDERERS;
  static {
    MOCK_RENDERERS = new HashMap<>();
    MOCK_RENDERERS.put(ActionType.TOUCH, new TouchEventRenderer<>());
    MOCK_RENDERERS.put(ActionType.ROTATE, new EventIconRenderer<>(ROTATE_EVENT));
    MOCK_RENDERERS.put(ActionType.KEYBOARD, new KeyboardEventRenderer<>());
  }

  private static final int AXIS_SIZE = 100;

  private ArrayList<MockActivity> myOpenActivities;

  private EventComponent<ActionType> myEventComponent;

  private ActivityComponent myActivityComponent;

  private AxisComponent myTimeAxis;

  private DefaultDataSeries<EventAction<ActionType>> myUserEventData;

  private DefaultDataSeries<EventAction<LifecycleEvent>> myActivityLifecycleData;

  private DefaultDataSeries<EventAction<LifecycleEvent>> myFragmentLifecycleData;

  private AnimatedTimeRange myAnimatedRange;

  private AnimatedTimeRange myTimelineRange;

  private ResizingAxisComponentModel myTimeAxisModel;

  private EventModel<ActionType> myUserEventModel;


  @Override
  protected List<Updatable> createModelList() {
    long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    Range xRange = new Range(nowUs, nowUs + TimeUnit.SECONDS.toMicros(60));
    Range xTimelineRange = new Range(0, 0);

    myUserEventData = new DefaultDataSeries<>();
    myActivityLifecycleData = new DefaultDataSeries<>();
    myFragmentLifecycleData = new DefaultDataSeries<>();
    myUserEventModel = new EventModel<>(new RangedSeries<>(xRange, myUserEventData));
    myEventComponent = new EventComponent<>(myUserEventModel, MOCK_RENDERERS);
    myActivityComponent = new ActivityComponent(new LifecycleEventModel(new RangedSeries<>(xRange, myActivityLifecycleData),
                                                                        new RangedSeries<>(xRange, myFragmentLifecycleData)));
    myAnimatedRange = new AnimatedTimeRange(xRange, 0);
    myTimelineRange = new AnimatedTimeRange(xTimelineRange, nowUs);
    myOpenActivities = new ArrayList<>();

    // add horizontal time axis
    myTimeAxisModel = new ResizingAxisComponentModel.Builder(xTimelineRange, TimeAxisFormatter.DEFAULT).build();
    myTimeAxis = new AxisComponent(myTimeAxisModel, AxisComponent.AxisOrientation.BOTTOM);
    List<Updatable> componentsList = new ArrayList<>();
    // Add the scene components to the list
    componentsList.add(myAnimatedRange);
    componentsList.add(myTimelineRange);
    return componentsList;
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(myEventComponent, myActivityComponent);
  }

  @Override
  public String getName() {
    return "EventChart";
  }

  private void performTapAction() {
    long now = System.currentTimeMillis();
    EventAction<ActionType> event = new EventAction<>(now, now, ActionType.TOUCH);
    myUserEventData.add(now, event);
  }

  private void addActivityCreatedEvent() {
    performTapAction();
    myOpenActivities.add(new MockActivity());
  }

  private void addActivityFinishedEvent() {
    //Find existing open activity.
    if (myOpenActivities.size() > 0) {
      MockActivity activity = myOpenActivities.remove(myOpenActivities.size() - 1);
      activity.tearDown();
      performTapAction();
    }
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    panel.setLayout(new BorderLayout());
    JLayeredPane timelinePane = createMockTimeline();
    panel.add(timelinePane, BorderLayout.CENTER);

    JPanel controls = new JPanel();
    LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
    controls.setLayout(manager);
    panel.add(controls, BorderLayout.WEST);
    controls.add(VisualTest.createButton("Add Activity", e -> addActivityCreatedEvent()));
    controls.add(VisualTest.createButton("Close Top Activity", e -> addActivityFinishedEvent()));
    controls.add(VisualTest.createButton("Close Random Activity", e -> {
      int size = myOpenActivities.size();
      if (size != 0) {
        MockActivity m = myOpenActivities.remove((int)(Math.random() * size));
        m.tearDown();
        performTapAction();
      }
    }));
    JButton tapButton = VisualTest.createButton("Tap Me");
    tapButton.addMouseListener(new MouseAdapter() {
      private long mDownTime = 0;
      @Override
      public void mousePressed(MouseEvent e) {
        long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        mDownTime = nowUs;
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        EventAction<ActionType> event =
          new EventAction<>(mDownTime, nowUs, ActionType.TOUCH);
        myUserEventData.add(nowUs, event);
      }
    });
    controls.add(tapButton);
    controls.add(VisualTest.createCheckbox("Shift xRange Min", itemEvent -> {
      myAnimatedRange.setShift(itemEvent.getStateChange() == ItemEvent.SELECTED);
      myTimelineRange.setShift(itemEvent.getStateChange() == ItemEvent.SELECTED);
    }));

    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private JLayeredPane createMockTimeline() {
    JLayeredPane timelinePane = new JLayeredPane();
    timelinePane.add(myTimeAxis);
    timelinePane.add(myEventComponent);
    timelinePane.add(myActivityComponent);
    timelinePane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          int numChart = 0;
          for (Component c : host.getComponents()) {
            if (c instanceof AxisComponent) {
              AxisComponent axis = (AxisComponent)c;
              switch (axis.getOrientation()) {
                case LEFT:
                  axis.setBounds(0, 0, AXIS_SIZE, dim.height);
                  break;
                case BOTTOM:
                  axis.setBounds(0, dim.height - AXIS_SIZE, dim.width, AXIS_SIZE);
                  break;
                case RIGHT:
                  axis.setBounds(dim.width - AXIS_SIZE, 0, AXIS_SIZE, dim.height);
                  break;
                case TOP:
                  axis.setBounds(0, 0, dim.width, AXIS_SIZE);
                  break;
              }
            }
            else {
              c.setBounds(AXIS_SIZE, 40 * numChart, dim.width - AXIS_SIZE * 2,
                          AXIS_SIZE);
              numChart++;
            }
          }
        }
      }
    });

    return timelinePane;
  }

  /**
   * Enum that defines what Icon to draw for an event action.
   */
  public enum ActionType {
    TOUCH,
    ROTATE,
    KEYBOARD
  }

  class MockActivity {

    String myName;
    long myStartTimeUs;

    public MockActivity() {
      myName = EventVisualTest.ACTIVITY_NAMES[(int)(Math.random() * ACTIVITY_NAMES.length)];
      myStartTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
      addSelf();
    }

    private void addSelf() {
      long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
      EventAction<LifecycleEvent> event =
        new LifecycleAction(myStartTimeUs, 0, LifecycleEvent.STARTED, myName);
      myActivityLifecycleData.add(nowUs, event);
    }

    public void tearDown() {
      long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
      EventAction<LifecycleEvent> event =
        new LifecycleAction(myStartTimeUs, nowUs, LifecycleEvent.COMPLETED, myName);
      myActivityLifecycleData.add(nowUs, event);
    }
  }
}
