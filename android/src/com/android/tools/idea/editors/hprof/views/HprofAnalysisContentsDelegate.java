/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof.views;

import com.android.tools.idea.editors.hprof.HprofEditor;
import com.android.tools.idea.editors.hprof.HprofView;
import com.android.tools.idea.profiling.view.AnalysisContentsDelegate;
import com.android.tools.perflib.analyzer.AnalysisResultEntry;
import com.android.tools.perflib.analyzer.Offender;
import com.android.tools.perflib.heap.*;
import com.android.tools.perflib.heap.memoryanalyzer.MemoryAnalysisResultEntry;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import static com.android.tools.perflib.heap.memoryanalyzer.DuplicatedStringsAnalyzerTask.DuplicatedStringsEntry;
import static com.android.tools.perflib.heap.memoryanalyzer.LeakedActivityAnalyzerTask.LeakedActivityEntry;

public class HprofAnalysisContentsDelegate extends AnalysisContentsDelegate {
  @NotNull private static final Logger LOG = Logger.getInstance(HprofAnalysisContentsDelegate.class);

  public HprofAnalysisContentsDelegate(@NotNull HprofEditor editor) {
    super(editor.getCapturePanel());

    HprofView hprofView = editor.getView();
    assert hprofView != null;

    final SelectionModel selectionModel = hprofView.getSelectionModel();
    myResultsTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
        TreePath path = treeSelectionEvent.getPath();
        if (path.getPath().length <= 1) {
          return; // Early out if the user selects a category node.
        }

        Object component = path.getLastPathComponent();
        if (component instanceof DefaultMutableTreeNode) {
          component = ((DefaultMutableTreeNode)component).getUserObject();
        }

        if (component instanceof InstanceListItem) {
          Instance instance = ((InstanceListItem)component).myInstance;
          Heap heap = instance.getHeap();
          ClassObj classObj = instance instanceof ClassObj ? (ClassObj)instance : instance.getClassObj();
          selectionModel.setHeap(heap);
          selectionModel.setClassObj(classObj);
          if (instance instanceof ClassInstance || instance instanceof ArrayInstance) {
            selectionModel.setInstance(instance);
          }
        }
      }
    });
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (value instanceof DefaultMutableTreeNode) {
      Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof EntryListItem) {
        int index = ((EntryListItem)userObject).myIndex;
        AnalysisResultEntry resultEntry = ((EntryListItem)userObject).myEntry;
        if (resultEntry instanceof DuplicatedStringsEntry) {
          DuplicatedStringsEntry entry = (DuplicatedStringsEntry)resultEntry;
          append(Integer.toString(index), XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
          append(" = ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          append(String.format("\"%s\" (%d instances)", entry.getOffender().getOffendingDescription(),
                               entry.getOffender().getOffenders().size()),
                 SimpleTextAttributes.fromTextAttributes(DebuggerUIUtil.getColorScheme(null).getAttributes(JavaHighlightingColors.STRING)));
        }
      }
      else if (userObject instanceof InstanceListItem) {
        int index = ((InstanceListItem)userObject).myIndex;
        Instance instance = ((InstanceListItem)userObject).myInstance;

        append(Integer.toString(index), XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
        append(" = ", SimpleTextAttributes.REGULAR_ATTRIBUTES);

        String className = null;
        if (instance instanceof ClassInstance) {
          setIcon(AllIcons.Debugger.Value);
          className = instance.getClassObj().getClassName();
        }
        else if (instance instanceof ClassObj) {
          setIcon(PlatformIcons.CLASS_ICON);
          className = ((ClassObj)instance).getClassName();
        }
        else if (instance instanceof ArrayInstance) {
          setIcon(AllIcons.Debugger.Db_array);
          className = instance.getClassObj().getClassName();
        }

        if (className != null) {
          int i = className.lastIndexOf('.');
          if (i != -1) {
            className = className.substring(i + 1);
          }
          long id = instance.getUniqueId();
          append(String.format("{%s@%d (0x%x)}", className, id, id), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
      else if (userObject instanceof String) {
        append((String)userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      else if (userObject != null) {
        LOG.warn("Unhandled user object type: " + userObject.getClass().getSimpleName());
      }
    }
    else if (value != null) {
      LOG.warn("Invalid tree node type: " + value.getClass().getSimpleName());
    }
  }

  @NotNull
  @Override
  public Icon getToolIcon() {
    return AndroidIcons.ToolWindows.HeapAnalysis;
  }

  @Override
  @Nullable
  public DefaultMutableTreeNode getNodeForEntry(int index, @NotNull AnalysisResultEntry entry) {
    if (!(entry instanceof MemoryAnalysisResultEntry)) {
      return null;
    }

    DefaultMutableTreeNode subtreeRoot = new DefaultMutableTreeNode();
    if (entry instanceof DuplicatedStringsEntry) {
      DuplicatedStringsEntry duplicatedStringsEntry = (DuplicatedStringsEntry)entry;
      subtreeRoot.setUserObject(new EntryListItem(index, duplicatedStringsEntry));
      for (Instance instance : duplicatedStringsEntry.getOffender().getOffenders()) {
        subtreeRoot.add(new DefaultMutableTreeNode(new InstanceListItem(subtreeRoot.getChildCount(), instance)));
      }
    }
    else if (entry instanceof LeakedActivityEntry) {
      LeakedActivityEntry leakedActivityEntry = (LeakedActivityEntry)entry;
      subtreeRoot.setUserObject(new InstanceListItem(index, leakedActivityEntry.getOffender().getOffenders().get(0)));
    }
    else {
      throw new RuntimeException("Failed to handle a subtype of MemoryAnalysisResultEntry \"" +
                                 entry.getClass().getSimpleName() +
                                 "\". Perhaps this method needs to be updated?");
    }

    return subtreeRoot;
  }

  private static class InstanceListItem {
    public int myIndex;
    public Instance myInstance;

    public InstanceListItem(int index, @NotNull Instance instance) {
      myIndex = index;
      myInstance = instance;
    }

    @Override
    public String toString() {
      return myInstance.toString();
    }
  }

  private static class EntryListItem {
    public int myIndex;
    public AnalysisResultEntry myEntry;

    public EntryListItem(int index, @NotNull AnalysisResultEntry entry) {
      myIndex = index;
      myEntry = entry;
    }

    @Override
    public String toString() {
      return myEntry.getOffender().getOffendingDescription();
    }
  }
}
