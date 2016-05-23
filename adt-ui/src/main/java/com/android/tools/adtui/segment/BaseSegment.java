package com.android.tools.adtui.segment;

import com.android.tools.adtui.AccordionLayout;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.common.AdtUIUtils;
import com.android.tools.adtui.common.RotatedLabel;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
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
    TIME,
    EVENT,
    NETWORK,
    MEMORY,
    CPU,
    GPU
  }

  private static final int SPACER_WIDTH = 100;

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

  /**
   * Top/bottom border between segments.
   */
  private static final Border SEGMENT_BORDER = new CompoundBorder(new MatteBorder(0, 0, 1, 0, AdtUIUtils.DEFAULT_BORDER_COLOR),
                                                                   new EmptyBorder(0, 0, 0, 0));

  private static final int LABEL_BORDER_WIDTH = 2;

  /**
   * Border around the segment label.
   */
  private static final Border LABEL_BORDER = new MatteBorder(0, 0, 0, LABEL_BORDER_WIDTH, AdtUIUtils.DEFAULT_BORDER_COLOR);

  private JPanel mRightPanel;

  @NotNull
  private RotatedLabel mLabel;

  @NotNull
  protected final String myName;

  @NotNull
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

  public BaseSegment(@NotNull String name, @NotNull Range scopedRange) {
    myName = name;
    mScopedRange = scopedRange;
    mDelayedEvents = new ArrayDeque<>();

    initializeListeners();
  }

  public void initializeComponents() {
    setLayout(new BorderLayout());

    FontMetrics metrics = getFontMetrics(AdtUIUtils.DEFAULT_FONT);
    JPanel labelPanel = createSpacerPanel(metrics.getHeight() + LABEL_BORDER_WIDTH);
    labelPanel.setBorder(LABEL_BORDER);
    mLabel = new RotatedLabel();
    mLabel.setFont(AdtUIUtils.DEFAULT_FONT);
    mLabel.setText(myName);
    mLabel.setBorder(SEGMENT_BORDER);
    labelPanel.add(mLabel);
    this.add(labelPanel, BorderLayout.WEST);

    JBPanel panels = new JBPanel();
    panels.setBorder(SEGMENT_BORDER);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    panels.setLayout(new GridBagLayout());

    gbc.weightx = 0;
    gbc.weighty = 0;

    //Setup the left panel, mostly filled with spacer, or AxisComponent
    JPanel leftPanel = createSpacerPanel(getSpacerWidth());
    gbc.gridx = 0;
    gbc.gridy = 1;
    panels.add(leftPanel, gbc);
    setLeftContent(leftPanel);

    //Setup the top center panel.
    JBPanel topPanel = new JBPanel();
    topPanel.setLayout(new BorderLayout());
    gbc.gridx = 1;
    gbc.gridy = 0;
    panels.add(topPanel, gbc);
    setTopCenterContent(topPanel);

    //Setup the right panel, like the left mostly filled with an AxisComponent
    mRightPanel = createSpacerPanel(getSpacerWidth());
    gbc.gridx = 2;
    gbc.gridy = 1;
    panels.add(mRightPanel, gbc);
    setRightContent(mRightPanel);

    //Setup the center panel, the primary component.
    //This component should consume all available space.
    JBPanel centerPanel = new JBPanel();
    centerPanel.setLayout(new BorderLayout());
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.gridx = 1;
    gbc.gridy = 1;
    panels.add(centerPanel, gbc);
    setCenterContent(centerPanel);

    add(panels, BorderLayout.CENTER);
  }

  public int getLabelColumnWidth() {
    return mLabel == null ? LABEL_BORDER_WIDTH : mLabel.getPreferredSize().width + LABEL_BORDER_WIDTH;
  }

  private JPanel createSpacerPanel(int spacerWidth) {
    JBPanel panel = new JBPanel();
    panel.setLayout(new BorderLayout());
    Dimension spacerDimension = new Dimension(spacerWidth, 0);
    panel.setPreferredSize(spacerDimension);
    panel.setMinimumSize(spacerDimension);
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

  public abstract void createComponentsList(@NotNull List<Animatable> animatables);

  protected void setLeftContent(@NotNull JPanel panel) {

  }

  protected abstract void setCenterContent(@NotNull JPanel panel);

  protected void setRightContent(@NotNull JPanel panel) {

  }

  protected void setTopCenterContent(@NotNull JPanel panel) {

  }

  //TODO Refactor out of BaseSegment as this is a VisualTest specific function.
  protected abstract void registerComponents(@NotNull List<AnimatedComponent> components);

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

          Timer dispatchTimer = new Timer(MULTI_CLICK_INTERVAL_MS, e1 -> dispatchOrAbsorbEvents());
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
