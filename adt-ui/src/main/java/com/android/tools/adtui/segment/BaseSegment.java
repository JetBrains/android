package com.android.tools.adtui.segment;

import com.android.annotations.NonNull;
import com.android.tools.adtui.AccordionLayout;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.common.RotatedLabel;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayDeque;
import java.util.List;

public abstract class BaseSegment extends JComponent {

  public enum SegmentType {
    EVENT,
    NETWORK,
    MEMORY,
    CPU,
    GPU
  }

  private static final int SPACER_WIDTH = 100;
  //TODO Adjust this when the vertical label gets integrated.
  private static final int TEXT_FIELD_WIDTH = 50;
  private static final Color BACKGROUND_COLOR = Color.white;

  /**
   * TODO consider getting OS/system specific double-click intervals.
   * If this is too large, however, the delay in dispatching the queued events would be significantly noticeable. The lag is undesirable
   * if the user is trying to perform other operations such as selection.
   */
  private static final int MULTI_CLICK_INTERVAL_MS = 300;

  /**
   * A mouse drag threshold to short circuit the double-click detection logic. Once the user starts dragging the mouse beyond this distance
   * value, all queued up events will be dispatched immediately.
   */
  private static final int MOUSE_DRAG_DISTANCE_THRESHOLD = 5;

  private static final int MULTI_CLICK_THRESHOLD = 2;

  private final CompoundBorder mCompoundBorder;

  private JPanel mRightPanel;

  @NonNull
  protected final String myName;

  @NonNull
  protected Range mScopedRange;

  /**
   * Mouse events that are queued up as the segment waits for the double-click event. See {@link #initializeListeners()}.
   */
  private final ArrayDeque<MouseEvent> mDelayedEvents;

  private boolean mMultiClicked;

  private Point mMousePressedPosition;

  public static int getSpacerWidth() {
    return SPACER_WIDTH;
  }

  public static int getTextFieldWidth() {
    return TEXT_FIELD_WIDTH;
  }

  public BaseSegment(@NonNull String name, @NonNull Range scopedRange) {
    myName = name;
    mScopedRange = scopedRange;
    mDelayedEvents = new ArrayDeque<>();

    //TODO Adjust borders according to neighbors
    mCompoundBorder = new CompoundBorder(new MatteBorder(1, 1, 1, 1, Color.lightGray),
                                         new EmptyBorder(0, 0, 0, 0));

    initializeListeners();
  }

  public void initializeComponents() {
    setLayout(new BorderLayout());
    RotatedLabel name = new RotatedLabel();
    name.setText(myName);
    name.setBorder(mCompoundBorder);
    this.add(name, BorderLayout.WEST);
    JPanel panels = new JPanel();
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 1;
    panels.setLayout(new GridBagLayout());

    //Setup the left panel, mostly filled with spacer, or AxisComponent
    JPanel leftPanel = createSpacerPanel();
    gbc.weightx = 0;
    gbc.gridx = 0;
    panels.add(leftPanel, gbc);
    setLeftContent(leftPanel);

    //Setup the center panel, the primary component.
    //This component should consume all available space.
    JPanel centerPanel = new JPanel();
    centerPanel.setLayout(new BorderLayout());
    centerPanel.setBorder(mCompoundBorder);
    gbc.weightx = 1;
    gbc.gridx = 1;
    panels.add(centerPanel, gbc);
    setCenterContent(centerPanel);

    //Setup the right panel, like the left mostly filled with an AxisComponent
    JPanel rightPanel = createSpacerPanel();
    gbc.weightx = 0;
    gbc.gridx = 2;
    panels.add(rightPanel, gbc);
    setRightContent(rightPanel);

    add(panels, BorderLayout.CENTER);
  }

  private JPanel createSpacerPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.setBackground(BACKGROUND_COLOR);
    panel.setBorder(mCompoundBorder);
    panel.setPreferredSize(new Dimension(getSpacerWidth(), 0));
    return panel;
  }

  /**
   * This enables segments to toggle the visibilty of the right panel.
   *
   * @param isVisible True indicates the panel is visible, false hides it.
   */
  public void setRightSpacerVisible(boolean isVisible) {
    mRightPanel.setVisible(isVisible);
  }

  public abstract void createComponentsList(@NonNull List<Animatable> animatables);

  protected abstract void setLeftContent(@NonNull JPanel panel);

  protected abstract void setCenterContent(@NonNull JPanel panel);

  protected abstract void setRightContent(@NonNull JPanel panel);


  //TODO Refactor out of BaseSegment as this is a VisualTest specific function.
  protected abstract void registerComponents(@NonNull List<AnimatedComponent> components);

  private void initializeListeners() {
    // Add mouse listener to support expand/collapse when user double-clicks on the Segment.
    // Note that other mouse events have to be queued up for a certain delay to allow the listener to detect the second click.
    // If the second click event has not arrived within the time limit, the queued events are dispatched up the tree to allow other
    // components to perform operations such as selection.
    addMouseListener(new MouseListener() {
      @Override
      public void mousePressed(MouseEvent e) {
        // Cache the mouse pressed position to detect dragging threshold.
        mMousePressedPosition = e.getPoint();
        if (e.getClickCount() >= MULTI_CLICK_THRESHOLD && !mDelayedEvents.isEmpty()) {
          // If a multi-click event has arrived and the dispatch timer below has not run to dispatch the queue events,
          // then process the multi-click.
          mMultiClicked = true;
          Container parent = getParent();
          if (parent != null) {
            LayoutManager layout = parent.getLayout();
            if (layout instanceof AccordionLayout) {
              AccordionLayout accordion = (AccordionLayout)layout;
              accordion.toggleMaximize(BaseSegment.this);
            }
          }
        } else {
          mMultiClicked = false;
          mDelayedEvents.add(e);

          Timer dispatchTimer = new Timer(MULTI_CLICK_INTERVAL_MS, e1 -> {
            dispatchOrAbsorbEvents();
          });
          dispatchTimer.setRepeats(false);
          dispatchTimer.start();
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }
    });

    // MouseMotionListener to detect the distance the user has dragged since the last mouse press.
    // Dispatch the queued events immediately if the threshold is passed.
    addMouseMotionListener(new MouseMotionListener() {
      @Override
      public void mouseDragged(MouseEvent e) {
        dispatchOrDelayEvent(e);
        if (!mDelayedEvents.isEmpty()) {
          double distance = Point.distance(mMousePressedPosition.getX(), mMousePressedPosition.getY(),
                                           e.getPoint().getX(), e.getPoint().getY());
          if (distance > MOUSE_DRAG_DISTANCE_THRESHOLD) {
            dispatchOrAbsorbEvents();
          }
        }
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        dispatchOrDelayEvent(e);
      }
    });
  }

  /**
   * Queue the MouseEvent if the dispatch timer has started and there are already events in the queue.
   * Dispatch the event immediately to the parent otherwise.
   */
  private void dispatchOrDelayEvent(MouseEvent e) {
    if (mDelayedEvents.isEmpty()) {
      getParent().dispatchEvent(e);
    } else {
      mDelayedEvents.addLast(e);
    }
  }

  /**
   * If a multi-click event has not occurred, dispatch all the queued events to the parent in order.
   * Swallows all the queued events otherwise.
   */
  private void dispatchOrAbsorbEvents() {
    if (mMultiClicked) {
      mDelayedEvents.clear();
    } else {
      while (!mDelayedEvents.isEmpty()) {
        getParent().dispatchEvent(mDelayedEvents.remove());
      }
    }
  }

}
