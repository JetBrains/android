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

package com.android.tools.adtui.visual;

import com.android.annotations.NonNull;
import com.android.tools.adtui.*;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedSimpleSeries;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;


public class EventVisualTest extends VisualTest {

  private static final int IMAGE_WIDTH = 16;
  private static final int IMAGE_HEIGHT = 16;
  private static final String[] ACTIVITY_NAMES = {
    "SigninActivity",
    "GamemodeActivity",
    "MainMenuActivity",
    "OptionsActivity",
    "MultiplayerActivity"
  };

  class MockActivity {

    String myName;
    long myStartTime;

    public MockActivity() {
      myName = EventVisualTest.ACTIVITY_NAMES[(int)(Math.random() * ACTIVITY_NAMES.length)];
      myStartTime = System.currentTimeMillis();
      addSelf();
    }

    private void addSelf() {
      EventAction<StackedEventComponent.Action, String> event =
        new EventAction<StackedEventComponent.Action, String>(myStartTime, 0,
                                                              StackedEventComponent.Action.ACTIVITY_STARTED, myName);
      mActivityData.getSeries().add(event);
    }

    public void tearDown() {
      long now = System.currentTimeMillis();
      EventAction<StackedEventComponent.Action, String> event =
        new EventAction<StackedEventComponent.Action, String>(myStartTime, now,
                                                              StackedEventComponent.Action.ACTIVITY_COMPLETED, myName);
      mActivityData.getSeries().add(event);
    }
  }

  private static BufferedImage buildStaticImage(Color color) {
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT,
                                            BufferedImage.TYPE_4BYTE_ABGR);
    for (int y = 0; y < IMAGE_HEIGHT; y++) {
      for (int x = 0; x < IMAGE_WIDTH; x++) {
        image.setRGB(x, y, color.getRGB());
      }
    }
    return image;
  }

  private static final BufferedImage[] MOCK_ICONS = {
    buildStaticImage(Color.red),
    buildStaticImage(Color.green),
    buildStaticImage(Color.blue),
  };

  /**
   * Enum that defines what Icon to draw for an event action.
   */
  public enum ActionType {
    TOUCH,
    HOLD,
    DOUBLE_TAP;
  }

  @NonNull
  private ArrayList<MockActivity> myOpenActivites;

  @NonNull
  private SimpleEventComponent mSimpleEventComponent;

  @NonNull
  private StackedEventComponent myStackedEventComponent;

  @NonNull
  private AxisComponent mTimeAxis;

  @NonNull
  private RangedSimpleSeries<EventAction<SimpleEventComponent.Action, ActionType>>
    mData;

  @NonNull
  private RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>>
    mActivityData;

  private AnimatedTimeRange mAnimatedRange;
  private AnimatedTimeRange mTimelineRange;
  private static final int AXIS_SIZE = 100;
  private static final int ACTIVITY_GRAPH_SIZE = 31;

  public EventVisualTest() {

  }

  @Override
  protected List<Animatable> createComponentsList() {
    long now = System.currentTimeMillis();
    Range xRange = new Range(now, now + 60000);
    Range xTimelineRange = new Range(0, 0);

    mData
      = new RangedSimpleSeries<EventAction<SimpleEventComponent.Action, ActionType>>(
      xRange);
    mActivityData
      = new RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>>(
      xRange);
    mSimpleEventComponent = new SimpleEventComponent(mData, MOCK_ICONS);
    myStackedEventComponent = new StackedEventComponent(ACTIVITY_GRAPH_SIZE, mActivityData);
    mAnimatedRange = new AnimatedTimeRange(xRange, 0);
    mTimelineRange = new AnimatedTimeRange(xTimelineRange, now);
    myOpenActivites = new ArrayList<MockActivity>();
    // add horizontal time axis
    mTimeAxis = new AxisComponent(xTimelineRange, xTimelineRange, "TIME",
                                  AxisComponent.AxisOrientation.BOTTOM,
                                  AXIS_SIZE, AXIS_SIZE, false, TimeAxisFormatter.DEFAULT);
    List<Animatable> componentsList = new ArrayList<>();
    // Add the scene components to the list
    componentsList.add(xRange);
    componentsList.add(xTimelineRange);
    componentsList.add(mAnimatedRange);
    componentsList.add(mTimelineRange);
    componentsList.add(mTimeAxis);
    componentsList.add(mSimpleEventComponent);
    componentsList.add(myStackedEventComponent);
    return componentsList;

  }

  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
    components.add(mTimeAxis);
    components.add(mSimpleEventComponent);
    components.add(myStackedEventComponent);
  }

  @Override
  public String getName() {
    return "EventChart";
  }

  private void performTapAction() {
    long now = System.currentTimeMillis();
    EventAction<SimpleEventComponent.Action, ActionType> event =
      new EventAction<SimpleEventComponent.Action, ActionType>(now,
                                                               0, SimpleEventComponent.Action.DOWN, ActionType.HOLD);
    mData.getSeries().add(event);
    event = new EventAction<SimpleEventComponent.Action, ActionType>(now,
                                                                     now, SimpleEventComponent.Action.UP, ActionType.TOUCH);
    mData.getSeries().add(event);
  }

  private void addActivityCreatedEvent() {
    performTapAction();
    myOpenActivites.add(new MockActivity());
  }

  private void addActivityFinishedEvent() {
    //Find existing open activity.
    if (myOpenActivites.size() > 0) {
      MockActivity activity = myOpenActivites.remove(myOpenActivites.size() - 1);
      activity.tearDown();
      performTapAction();
    }
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    panel.setLayout(new BorderLayout());
    JLayeredPane timelinePane = createMockTimeline();
    panel.add(timelinePane, BorderLayout.CENTER);

    JPanel controls = new JPanel();
    LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
    controls.setLayout(manager);
    panel.add(controls, BorderLayout.WEST);
    controls.add(VisualTest.createButton("Add Activity", new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        addActivityCreatedEvent();
      }
    }));
    controls.add(VisualTest.createButton("Close Top Activity", new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        addActivityFinishedEvent();
      }
    }));
    controls.add(VisualTest.createButton("Close Random Activity", new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int size = myOpenActivites.size();
        MockActivity m = myOpenActivites.remove((int)(Math.random() * size));
        m.tearDown();
        performTapAction();
      }
    }));
    JButton tapButton = VisualTest.createButton("Tap Me");
    tapButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        long now = System.currentTimeMillis();
        EventAction<SimpleEventComponent.Action, ActionType> event =
          new EventAction<SimpleEventComponent.Action, ActionType>(now,
                                                                   0, SimpleEventComponent.Action.DOWN, ActionType.HOLD);
        mData.getSeries().add(event);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        long now = System.currentTimeMillis();
        EventAction<SimpleEventComponent.Action, ActionType> event =
          new EventAction<SimpleEventComponent.Action, ActionType>(now,
                                                                   now, SimpleEventComponent.Action.UP, ActionType.TOUCH);
        mData.getSeries().add(event);
      }
    });
    controls.add(tapButton);
    controls.add(VisualTest.createCheckbox("Shift xRange Min", new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        mAnimatedRange.setShift(itemEvent.getStateChange() == ItemEvent.SELECTED);
        mTimelineRange.setShift(itemEvent.getStateChange() == ItemEvent.SELECTED);
      }
    }));

    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private JLayeredPane createMockTimeline() {
    JLayeredPane timelinePane = new JLayeredPane();
    timelinePane.add(mTimeAxis);
    timelinePane.add(mSimpleEventComponent);
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
}
