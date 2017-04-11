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
import com.intellij.util.ui.JBUI;
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
  private static final int CELL_HEIGHT_PADDING = JBUI.scale(6);

  @NotNull
  private final JList<List<HttpData>> myThreadsList;

  ThreadsView(@NotNull NetworkProfilerStageView stageView) {
    myThreadsList = new JBList<>(new ThreadListModel(stageView.getStage()));
    myThreadsList.setCellRenderer(new ThreadCellRenderer(myThreadsList, stageView.getTimeline()));
    myThreadsList.setBackground(ProfilerColors.DEFAULT_BACKGROUND);
    myThreadsList.setFont(AdtUiUtils.DEFAULT_FONT);
    int fontHeight = myThreadsList.getFontMetrics(myThreadsList.getFont()).getHeight();
    myThreadsList.setFixedCellHeight(fontHeight + CELL_HEIGHT_PADDING);
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
    private AxisComponent myAxis;

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
      for (int i = 0; i < myList.getModel().getSize(); ++i) {
        List<HttpData> data = myList.getModel().getElementAt(i);
        assert !data.isEmpty();

        ConnectionsStateChart chart = new ConnectionsStateChart(data, myTimeline.getSelectionRange());
        chart.setHeightGap(0.4f);

        JLabel label = new JLabel(data.get(0).getJavaThread().getName());
        label.setForeground(myList.getForeground());
        label.setFont(myList.getFont());

        JPanel panel = new JPanel(new TabularLayout("*", "*"));
        panel.add(label, new TabularLayout.Constraint(0, 0));
        panel.add(chart.getComponent(), new TabularLayout.Constraint(0, 0));

        myRows.add(panel);
      }
      myAxis = createAxis();
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends List<HttpData>> list,
                                                  List<HttpData> value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      Component comp = myRows.get(index);
      comp.setBackground(index == myHoveredIndex ? ProfilerColors.NETWORK_TABLE_HOVER_COLOR : list.getBackground());

      myAxis.setMarkerLengths(list.getFixedCellHeight(), 0);
      // If it is the first row show labels.
      myAxis.setShowLabels(index == 0);

      JPanel panel = new JPanel(new TabularLayout("*", "*"));
      panel.add(myAxis, new TabularLayout.Constraint(0, 0));
      panel.add(comp, new TabularLayout.Constraint(0, 0));
      return panel;
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
}
