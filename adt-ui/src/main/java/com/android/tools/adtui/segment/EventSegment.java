package com.android.tools.adtui.segment;

import com.android.annotations.NonNull;
import com.android.tools.adtui.*;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedSimpleSeries;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

public class EventSegment<E extends Enum<E>> extends SegmentBase {

  private static final String SEGMENT_NAME = "Events";
  private static final int ACTIVITY_GRAPH_SIZE = 25;
  private static final int FRAGMENT_GRAPH_SIZE = 25;

  @NonNull
  private Range mTimeGlobalRange;

  @NonNull
  private SimpleEventComponent mSystemEvents;

  @NonNull
  private StackedEventComponent mFragmentEvents;

  @NonNull
  private StackedEventComponent mActivityEvents;

  @NonNull
  private final RangedSimpleSeries<EventAction<SimpleEventComponent.Action, E>> mSystemEventData;

  @NonNull
  private final RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> mFragmentEventData;

  @NonNull
  private final RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> mActivityEventData;

  @NonNull
  private BufferedImage[] mIcons;

  //TODO Add labels for series data.

  public EventSegment(@NonNull Range scopedRange,
                      @NonNull RangedSimpleSeries<EventAction<SimpleEventComponent.Action, E>> systemData,
                      @NonNull RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> fragmentData,
                      @NonNull RangedSimpleSeries<EventAction<StackedEventComponent.Action, String>> activityData,
                      @NonNull BufferedImage[] icons) {
    super(SEGMENT_NAME, scopedRange);
    mSystemEventData = systemData;
    mFragmentEventData = fragmentData;
    mActivityEventData = activityData;
    mIcons = icons;
  }

  @Override
  public void createComponentsList(@NonNull List<Animatable> animatables) {
    mSystemEvents = new SimpleEventComponent(mSystemEventData, mIcons);
    mFragmentEvents = new StackedEventComponent(FRAGMENT_GRAPH_SIZE, mFragmentEventData);
    mActivityEvents = new StackedEventComponent(ACTIVITY_GRAPH_SIZE, mActivityEventData);

    animatables.add(mSystemEvents);
    animatables.add(mFragmentEvents);
    animatables.add(mActivityEvents);
  }

  @Override
  public void registerComponents(@NonNull List<AnimatedComponent> components) {
    components.add(mSystemEvents);
    components.add(mFragmentEvents);
    components.add(mActivityEvents);
  }

  @Override
  protected void setLeftContent(@NonNull JPanel panel) {
    //The Events Segment, shows no Axis components and the spacing is taken care of by our base.
  }

  @Override
  protected void setRightContent(@NonNull JPanel panel) {
    //The Events Segment, shows no Axis components and the spacing is taken care of by our base.
  }

  @Override
  protected void setCenterContent(@NonNull JPanel panel) {
    JPanel layeredPane = new JPanel();
    layeredPane.setLayout(new GridBagLayout());
    //Divide up the space equally amongst the child components. Each of these may change to a specific height
    //as decided by design.
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.gridx = 0;

    gbc.gridy = 0;
    layeredPane.add(mSystemEvents, gbc);
    gbc.gridy = 1;
    layeredPane.add(mFragmentEvents, gbc);
    gbc.gridy = 2;
    layeredPane.add(mActivityEvents, gbc);
    panel.add(layeredPane, BorderLayout.CENTER);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    assert mIcons.length > 0 && mIcons[0] != null;
    return new Dimension(size.width, (ACTIVITY_GRAPH_SIZE + FRAGMENT_GRAPH_SIZE) * 2 + mIcons[0].getHeight());
  }
}
