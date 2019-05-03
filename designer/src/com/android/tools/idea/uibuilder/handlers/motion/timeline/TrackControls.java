// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.uibuilder.handlers.motion.timeline;

import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.stdui.CommonButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.android.tools.idea.uibuilder.handlers.motion.timeline.Chart.ourViewListWidth;

/**
 * The icons at the top left of the panel
 */
public class TrackControls extends JPanel {

  private final Chart mChart;

  JButton myCircle  = new CommonButton(TimeLineIcons.SLOW_MOTION);
  JButton myPrevKeyFrame  = new CommonButton(TimeLineIcons.BACKWARD);
  JButton myPlayButton = new CommonButton(TimeLineIcons.PLAY);
  JButton myNextKeyFrame = new CommonButton(TimeLineIcons.FORWARD);
  JButton myLoop = new CommonButton(TimeLineIcons.LOOP);
  public static final int NO_OF_BUTTONS = 5;

  JButton[] myButtons = {myCircle, myPrevKeyFrame, myPlayButton, myNextKeyFrame, myLoop};

  static GanttEventListener.Actions[] actions = {
    GanttEventListener.Actions.SLOW_MOTION,
    GanttEventListener.Actions.START_ACTION,
    GanttEventListener.Actions.PLAY_ACTION,
    GanttEventListener.Actions.END_ACTION,
    GanttEventListener.Actions.LOOP_ACTION
  };

  private GanttController myGanttInterface;

  ButtonUI mButtonUI = new BasicButtonUI();

  TrackControls(Chart chart) {
    super(new GridLayout(1, NO_OF_BUTTONS, 0, 0));
    mChart = chart;
    setPreferredSize(new Dimension(ourViewListWidth, Gantt.HEADER_HEIGHT));
    mChart.add(new Gantt.ChartElement() {
      @Override
      public void update(Reason reason) {
        switch (reason) {
          case MODE_CHANGE:
            switch (mChart.getMode()) {
              case PLAY:
                myPlayButton.setIcon(TimeLineIcons.PAUSE);
                break;
              case PAUSE:
              case TRANSITION:
                myPlayButton.setIcon(TimeLineIcons.PLAY);
                break;
              default:
            }
            break;
          default:
        }
      }

      @Override
      public void repaint() {

      }

      @Override
      public void invalidate() {

      }
    });
    Font f = myButtons[0].getFont().deriveFont(10f);
    for (int i = 0; i < myButtons.length; i++) {
      final GanttEventListener.Actions action = actions[i];
      JButton button = myButtons[i];
      button.setFont(f);
      add(button);

      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          buttonPressed(e, action);
        }
      });
    }
    setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 0, 0, 1, 1));
  }

  private void buttonPressed(ActionEvent e, GanttEventListener.Actions action) {
    if (action == GanttEventListener.Actions.SLOW_MOTION) {
      popupSlowSpee(e, action);
    }
    if (myGanttInterface != null) {
      myGanttInterface.buttonPressed(e, action);
    }
  }

  private void popupSlowSpee(ActionEvent e, GanttEventListener.Actions action) {
    String[] list = {"0.25 x", "0.5 x", "1 x", "2 x", "4 x"};
    final float[] speed = {0.25f, 0.5f, 1, 2, 4};
    final JList<String> displayedList = new JBList<>(list);
    JBPopupListener listener = new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        JBPopup popup = event.asPopup();
        mChart.myPlayBackSpeed = speed[displayedList.getLeadSelectionIndex()];
        myGanttInterface.buttonPressed(e, action);
      }
    };
    JBPopup popup = JBPopupFactory.getInstance()
                                  .createListPopupBuilder(displayedList)
                                  .setTitle("Speed")
                                  .addListener(listener)
                                  .createPopup();

    JComponent component = ((JComponent)e.getSource());

    popup.show(new RelativePoint(component, new Point(0, 0)));
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);
    if (myButtons == null) {
      return;
    }
    for (int i = 0; i < myButtons.length; i++) {
      JButton button = myButtons[i];
      button.setBackground(bg);
    }
  }

  void setGantt(@NotNull GanttController gantt) {
    myGanttInterface = gantt;
  }
}
