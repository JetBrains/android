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

import com.android.tools.adtui.*;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.event.*;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.updater.Updatable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EventVisualTest extends VisualTest {

  private static final String[] ACTIVITY_NAMES = {
    "SignInActivity",
    "GameModeActivity",
    "MainMenuActivity",
    "OptionsActivity",
    "MultiplayerActivity"
  };

  private static final Map<ActionType, SimpleEventRenderer<ActionType>> MOCK_RENDERERS;
  static {
    MOCK_RENDERERS = new HashMap<>();
    MOCK_RENDERERS.put(ActionType.TOUCH, new TouchEventRenderer<>());
    MOCK_RENDERERS.put(ActionType.ROTATE, new EventIconRenderer<>("/icons/events/rotate-event.png"));
    MOCK_RENDERERS.put(ActionType.KEYBOARD, new KeyboardEventRenderer<>());
  }

  private static final int AXIS_SIZE = 100;

  private ArrayList<MockActivity> myOpenActivities;

  private SimpleEventComponent<ActionType> mySimpleEventComponent;

  private StackedEventComponent myStackedEventComponent;

  private AxisComponent myTimeAxis;

  private DefaultDataSeries<EventAction<ActionType>> myData;

  private DefaultDataSeries<EventAction<StackedEventType>> myActivityData;

  private AnimatedTimeRange myAnimatedRange;

  private AnimatedTimeRange myTimelineRange;

  private AxisComponentModel myTimeAxisModel;

  private EventModel<ActionType> myEventModel;
  private EventModel<StackedEventType> myStackedEventModel;


  @Override
  protected List<Updatable> createModelList() {
    long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    Range xRange = new Range(nowUs, nowUs + TimeUnit.SECONDS.toMicros(60));
    Range xTimelineRange = new Range(0, 0);

    myData = new DefaultDataSeries<>();
    myActivityData = new DefaultDataSeries<>();
    myEventModel = new EventModel<>(new RangedSeries<>(xRange, myData));
    mySimpleEventComponent = new SimpleEventComponent<>(myEventModel, MOCK_RENDERERS);
    myStackedEventModel = new EventModel<>(new RangedSeries<>(xRange, myActivityData));
    myStackedEventComponent = new StackedEventComponent(myStackedEventModel);
    myAnimatedRange = new AnimatedTimeRange(xRange, 0);
    myTimelineRange = new AnimatedTimeRange(xTimelineRange, nowUs);
    myOpenActivities = new ArrayList<>();

    // add horizontal time axis
    myTimeAxisModel = new AxisComponentModel(xTimelineRange, TimeAxisFormatter.DEFAULT);
    myTimeAxis = new AxisComponent(myTimeAxisModel, AxisComponent.AxisOrientation.BOTTOM);
    List<Updatable> componentsList = new ArrayList<>();
    // Add the scene components to the list
    componentsList.add(myAnimatedRange);
    componentsList.add(myTimelineRange);
    componentsList.add(myTimeAxisModel);
    componentsList.add(myEventModel);
    componentsList.add(myStackedEventModel);
    return componentsList;
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(mySimpleEventComponent, myStackedEventComponent);
  }

  @Override
  public String getName() {
    return "EventChart";
  }

  private void performTapAction() {
    long now = System.currentTimeMillis();
    EventAction<ActionType> event = new EventAction<>(now, now, ActionType.TOUCH);
    myData.add(now, event);
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
        myData.add(nowUs, event);
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
    timelinePane.add(mySimpleEventComponent);
    timelinePane.add(myStackedEventComponent);
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
      EventAction<StackedEventType> event =
        new ActivityAction(myStartTimeUs, 0, StackedEventType.ACTIVITY_STARTED, myName);
      myActivityData.add(nowUs, event);
    }

    public void tearDown() {
      long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
      EventAction<StackedEventType> event =
        new ActivityAction(myStartTimeUs, nowUs, StackedEventType.ACTIVITY_COMPLETED, myName);
      myActivityData.add(nowUs, event);
    }
  }
}
