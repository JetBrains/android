/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.network;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTimeline;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * Displays network connection information of all threads.
 */
final class ThreadsView {
  private static final int CELL_HEIGHT = 15;

  @NotNull
  private final JList<List<HttpData>> myThreadsList;

  ThreadsView(@NotNull NetworkProfilerStageView stageView) {
    myThreadsList = new JBList<>(new ThreadListModel(stageView.getStage()));
    myThreadsList.setCellRenderer(new ThreadCellRenderer(myThreadsList, stageView.getTimeline()));
    myThreadsList.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myThreadsList.setFont(AdtUiUtils.FONT_DEFAULT);
    myThreadsList.setFixedCellHeight(CELL_HEIGHT);
  }

  @NotNull
  JComponent getComponent() {
    return myThreadsList;
  }

  private static final class ThreadListModel extends DefaultListModel<List<HttpData>> {
    @NotNull private final AspectObserver myAspectObserver;
    @NotNull private final NetworkProfilerStage myStage;

    private ThreadListModel(@NotNull NetworkProfilerStage stage) {
      myStage = stage;
      myAspectObserver = new AspectObserver();
      Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
      selection.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::rangeChanged);
      rangeChanged();
    }

    public void rangeChanged() {
      Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
      removeAllElements();
      if (selection.isEmpty()) {
        return;
      }
      List<HttpData> dataList = myStage.getConnectionsModel().getData(selection);
      Map<Long, List<HttpData>> threads = new HashMap<>();
      for (HttpData data : dataList) {
        if (!threads.containsKey(data.getJavaThread().getId())) {
          threads.put(data.getJavaThread().getId(), new ArrayList<>());
        }
        threads.get(data.getJavaThread().getId()).add(data);
      }

      // Sort by thread name, so that they're consistently displayed in alphabetical order.
      // TODO: Implement sorting mechanism in JList and move this responsibility to the JList.
      threads.values().stream().sorted((o1, o2) -> {
        HttpData.JavaThread thread1 = o1.get(0).getJavaThread();
        HttpData.JavaThread thread2 = o2.get(0).getJavaThread();
        int nameCompare = thread1.getName().compareTo(thread2.getName());
        return (nameCompare != 0) ? nameCompare : Long.compare(thread1.getId(), thread2.getId());
      }).forEach(this::addElement);
    }
  }

  private static final class ThreadCellRenderer implements ListCellRenderer<List<HttpData>> {
    @NotNull private final JList<List<HttpData>> myList;
    @NotNull private final List<JComponent> myRows;
    @NotNull private final ProfilerTimeline myTimeline;

    private int myHoveredIndex = -1;

    ThreadCellRenderer(@NotNull JList<List<HttpData>> list, @NotNull ProfilerTimeline timeline) {
      myList = list;
      myTimeline = timeline;
      myRows = new ArrayList<>();

      list.getModel().addListDataListener(new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) {
          modelChanged();
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
          modelChanged();
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
          modelChanged();
        }
      });

      MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          hoveredIndexChanged(myList.locationToIndex(e.getPoint()));
        }

        @Override
        public void mouseExited(MouseEvent e) {
          hoveredIndexChanged(-1);
        }

        private void hoveredIndexChanged(int index) {
          if (index == myHoveredIndex) {
            return;
          }
          myHoveredIndex = index;
          myList.repaint();
        }
      };
      myList.addMouseMotionListener(mouseAdapter);
      myList.addMouseListener(mouseAdapter);

      modelChanged();
    }

    private void modelChanged() {
      myRows.clear();
      for (int index = 0; index < myList.getModel().getSize(); ++index) {
        List<HttpData> data = myList.getModel().getElementAt(index);
        assert !data.isEmpty();

        ConnectionsStateChart chart = new ConnectionsStateChart(data, myTimeline.getSelectionRange());
        chart.setHeightGap(0.2f);

        JLabel label = new JLabel(data.get(0).getJavaThread().getName());
        label.setForeground(myList.getForeground());
        label.setFont(myList.getFont());
        AxisComponent axis = createAxis();
        axis.setMarkerLengths(myList.getFixedCellHeight(), 0);
        // If it is the first row show labels.
        axis.setShowLabels(index == 0);

        JPanel panel = new JPanel(new TabularLayout("*", "*"));
        panel.setPreferredSize(new Dimension((int)panel.getPreferredSize().getWidth(), CELL_HEIGHT));
        panel.add(label, new TabularLayout.Constraint(0, 0));
        panel.add(axis, new TabularLayout.Constraint(0, 0));
        panel.add(new ConnectionNamesComponent(data, myTimeline.getSelectionRange()), new TabularLayout.Constraint(0, 0));
        panel.add(chart.getComponent(), new TabularLayout.Constraint(0, 0));
        myRows.add(panel);
      }
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends List<HttpData>> list,
                                                  List<HttpData> value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      Component comp = myRows.get(index);
      comp.setBackground(index == myHoveredIndex ? ProfilerColors.NETWORK_TABLE_HOVER_COLOR : list.getBackground());
      return comp;
    }

    @NotNull
    private AxisComponent createAxis() {
      AxisComponentModel model = new AxisComponentModel(myTimeline.getSelectionRange(), new TimeAxisFormatter(1, 5, 1));
      model.setClampToMajorTicks(false);
      model.setGlobalRange(myTimeline.getDataRange());

      AxisComponent axis = new AxisComponent(model, AxisComponent.AxisOrientation.BOTTOM);
      axis.setShowAxisLine(false);
      axis.setMarkerColor(ProfilerColors.NETWORK_TABLE_AXIS);
      axis.setForeground(ProfilerColors.NETWORK_TABLE_AXIS);
      model.update(1);
      return axis;
    }
  }

  /**
   * A component that responsible for rendering the given connections name.
   */
  private static final class ConnectionNamesComponent extends JComponent {
    private static final int PADDING = 6;

    @NotNull private final List<HttpData> myDataList;
    @NotNull private final Range myRange;

    private ConnectionNamesComponent(@NotNull List<HttpData> data, @NotNull Range range) {
      myDataList = data;
      myRange = range;
      setFont(AdtUiUtils.FONT_DEFAULT_TITLE);
      setForeground(ProfilerColors.NETWORK_TABLE_CONNECTIONS_NAME);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D)g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setFont(getFont());
      g2d.setColor(getForeground());

      for (HttpData data : myDataList) {
        float start = (float)((data.getStartTimeUs() - myRange.getMin()) / myRange.getLength() * getWidth());
        float end = getWidth();
        if (data.getEndTimeUs() != 0) {
          end = (float)((data.getEndTimeUs() - myRange.getMin()) / myRange.getLength() * getWidth());
        }

        FontMetrics metrics = getFontMetrics(getFont());
        String text = AdtUiUtils.getFittedString(metrics, HttpData.getUrlName(data.getUrl()), end - start - 2 * PADDING, 1);
        float availableSpace = (end - start - metrics.stringWidth(text));
        g2d.drawString(text, start + availableSpace / 2, (getHeight() - metrics.getHeight()) * 0.5f + metrics.getAscent());
      }

      g2d.dispose();
    }
  }
}
