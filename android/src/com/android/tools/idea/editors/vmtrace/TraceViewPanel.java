/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.editors.vmtrace;

import com.android.tools.idea.editors.vmtrace.treemodel.VmStatsTreeTableModel;
import com.android.tools.idea.editors.vmtrace.treemodel.VmStatsTreeUtils;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.SearchResult;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.tools.perflib.vmtrace.viz.TraceViewCanvas;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.find.editorHeaderActions.Utils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class TraceViewPanel {
  @NonNls public static DataKey<TraceViewPanel> KEY = DataKey.create("android.traceview.panel");

  // The names for the cards used in the card layout.
  // Note that these are duplicated in the layout form.
  @NonNls private static final String CARD_FIND = "FIND";
  @NonNls private static final String CARD_DEFAULT = "DEFAULT";

  /** Default name for main thread in Android apps. */
  @NonNls private static final String MAIN_THREAD_NAME = "main";

  private final Project myProject;

  private JPanel myContainer;

  private JPanel myHeaderPanel;
  private TraceViewCanvas myTraceViewCanvas;
  private TreeTable myTreeTable;

  @SuppressWarnings("UnusedDeclaration") // custom creation only
  private JPanel myDefaultHeaderPanel;
  private JComboBox myThreadCombo;
  private JComboBox myRenderClockSelectorCombo;

  @SuppressWarnings("UnusedDeclaration") // custom creation only
  private JPanel myFindPanel;
  private JPanel myFindFieldWrapper;
  private SearchTextField mySearchField;
  private JLabel myCloseLabel;
  private JBLabel myResultsLabel;
  private JLabel mySearchLabel;
  private JBSplitter mySplitter;
  private JLabel myZoomFitLabel;
  private JCheckBox myUseInclusiveTimeForColoring;

  private static final String[] ourRenderClockOptions = new String[] {
    "Wall Clock Time",
    "Thread Time",
  };

  private static final ClockType[] ourRenderClockTypes = new ClockType[] {
    ClockType.GLOBAL,
    ClockType.THREAD,
  };
  private VmTraceData myTraceData;
  private VmStatsTreeTableModel myVmStatsTreeTableModel;

  public TraceViewPanel(Project project) {
    myProject = project;

    myRenderClockSelectorCombo.setModel(new DefaultComboBoxModel(ourRenderClockOptions));
    myRenderClockSelectorCombo.setSelectedIndex(0);

    ActionListener l = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == myThreadCombo) {
          final ThreadInfo selectedThread = (ThreadInfo)myThreadCombo.getSelectedItem();
          myTraceViewCanvas.displayThread(selectedThread);
          myVmStatsTreeTableModel.setThread(selectedThread);
        } else if (e.getSource() == myRenderClockSelectorCombo) {
          myTraceViewCanvas.setRenderClock(getCurrentRenderClock());
          myVmStatsTreeTableModel.setClockType(getCurrentRenderClock());
        } else if (e.getSource() == myUseInclusiveTimeForColoring) {
          myTraceViewCanvas.setUseInclusiveTimeForColorAssignment(myUseInclusiveTimeForColoring.isSelected());
        }
      }
    };

    myThreadCombo.addActionListener(l);
    myRenderClockSelectorCombo.addActionListener(l);
    myUseInclusiveTimeForColoring.addActionListener(l);
    myUseInclusiveTimeForColoring.setOpaque(false);
  }

  private SearchTextField createSearchField() {
    SearchTextField stf = new SearchTextField(true);
    stf.setOpaque(false);
    stf.setEnabled(true);
    Utils.setSmallerFont(stf);

    stf.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        searchTextChanged(getText(e));
      }

      private String getText(DocumentEvent e) {
        try {
          return e.getDocument().getText(0, e.getDocument().getLength());
        }
        catch (BadLocationException e1) {
          return "";
        }
      }
    });

    JTextField editorTextField = stf.getTextEditor();
    editorTextField.setMinimumSize(new Dimension(JBUI.scale(200), -1));

    editorTextField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        closeSearchComponent();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

    return stf;
  }

  private void searchTextChanged(@Nullable String pattern) {
    if (StringUtil.isEmpty(pattern)) {
      myTraceViewCanvas.setHighlightMethods(null);
      myResultsLabel.setText("");
      return;
    }

    ThreadInfo thread = (ThreadInfo)myThreadCombo.getSelectedItem();
    SearchResult results = myTraceData.searchFor(pattern, thread);
    myTraceViewCanvas.setHighlightMethods(results.getMethods());

    String result = String.format("%1$d %2$s, %3$d %4$s",
                                  results.getMethods().size(),
                                  StringUtil.pluralize("method", results.getMethods().size()),
                                  results.getInstances().size(),
                                  StringUtil.pluralize("instance", results.getInstances().size()));
    myResultsLabel.setText(result);
  }

  public void setTrace(@NotNull VmTraceData trace) {
    myTraceData = trace;

    List<ThreadInfo> threads = trace.getThreads(true);
    if (threads.isEmpty()) {
      return;
    }

    ThreadInfo defaultThread = Iterables.find(threads, new Predicate<ThreadInfo>() {
      @Override
      public boolean apply(ThreadInfo input) {
        return MAIN_THREAD_NAME.equals(input.getName());
      }
    }, threads.get(0));

    myTraceViewCanvas.setTrace(trace, defaultThread, getCurrentRenderClock());
    myThreadCombo.setModel(new DefaultComboBoxModel(threads.toArray()));
    myThreadCombo.setSelectedItem(defaultThread);
    myThreadCombo.setRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        String name = value instanceof ThreadInfo ? ((ThreadInfo)value).getName() : value.toString();
        append(name);
      }
    });

    myThreadCombo.setEnabled(true);
    myRenderClockSelectorCombo.setEnabled(true);

    myVmStatsTreeTableModel.setTraceData(trace, defaultThread);
    myVmStatsTreeTableModel.setClockType(getCurrentRenderClock());
    myTreeTable.setModel(myVmStatsTreeTableModel);

    VmStatsTreeUtils.adjustTableColumnWidths(myTreeTable);
    VmStatsTreeUtils.setCellRenderers(myTreeTable);
    VmStatsTreeUtils.setSpeedSearch(myTreeTable);
    VmStatsTreeUtils.enableSorting(myTreeTable, myVmStatsTreeTableModel);
  }

  private ClockType getCurrentRenderClock() {
    return ourRenderClockTypes[myRenderClockSelectorCombo.getSelectedIndex()];
  }

  @NotNull
  public JComponent getComponent() {
    return myContainer;
  }

  private void createUIComponents() {
    MouseAdapter l = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (e.getSource() == myCloseLabel) {
          closeSearchComponent();
        } else if (e.getSource() == mySearchLabel) {
          showSearchComponent();
        } else if (e.getSource() == myZoomFitLabel) {
          myTraceViewCanvas.zoomFit();
        }
      }
    };

    myDefaultHeaderPanel = new EditorHeaderComponent();
    mySearchLabel = new JLabel(AllIcons.Actions.Search);
    mySearchLabel.addMouseListener(l);
    mySearchLabel.setToolTipText("Find (Ctrl + F)");
    myZoomFitLabel = new JLabel(AndroidIcons.ZoomFit);
    myZoomFitLabel.setToolTipText("Zoom Fit");
    myZoomFitLabel.addMouseListener(l);

    myFindPanel = new EditorHeaderComponent();
    myFindFieldWrapper = new NonOpaquePanel(new BorderLayout());
    mySearchField = createSearchField();
    myFindFieldWrapper.add(mySearchField);

    myCloseLabel = new JLabel(AllIcons.Actions.Cross);
    myCloseLabel.addMouseListener(l);

    myVmStatsTreeTableModel = new VmStatsTreeTableModel();
    myTreeTable = new TreeTable(myVmStatsTreeTableModel);
    myTraceViewCanvas = new TraceViewCanvasWrapper();
    JBScrollPane scrollPane = new JBScrollPane(myTreeTable);

    mySplitter = new JBSplitter(true, 0.75f);
    mySplitter.setShowDividerControls(true);
    mySplitter.setShowDividerIcon(true);
    mySplitter.setFirstComponent(myTraceViewCanvas);
    mySplitter.setSecondComponent(scrollPane);
  }

  public void showSearchComponent() {
    CardLayout layout = (CardLayout)myHeaderPanel.getLayout();
    layout.show(myHeaderPanel, CARD_FIND);
    IdeFocusManager.getInstance(myProject).requestFocus(mySearchField, true);
  }

  private void closeSearchComponent() {
    CardLayout layout = (CardLayout)myHeaderPanel.getLayout();
    layout.show(myHeaderPanel, CARD_DEFAULT);
    IdeFocusManager.getInstance(myProject).requestFocus(myTraceViewCanvas, true);
  }

  /**
   * {@link TraceViewCanvasWrapper} is a wrapper around {@link TraceViewCanvas} that also implements the {@link DataProvider} interface.
   * This allows {@link VmTraceEditorSearchAction} to identify the editor from the current context of an event.
   */
  private class TraceViewCanvasWrapper extends TraceViewCanvas implements DataProvider {
    public TraceViewCanvasWrapper() {
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          IdeFocusManager.getInstance(myProject).requestFocus(TraceViewCanvasWrapper.this, true);
        }
      });
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      return KEY.is(dataId) ? TraceViewPanel.this : null;
    }
  }
}
