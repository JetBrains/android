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
package com.android.tools.idea.editors.hprof.tables;

import com.android.tools.idea.actions.EditMultipleSourcesAction;
import com.android.tools.idea.actions.PsiFileAndLineNavigation;
import com.android.tools.idea.editors.allocations.ColumnTreeBuilder;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ClassesTreeView implements DataProvider {
  @NotNull private Project myProject;
  @NotNull private Tree myTree;
  @NotNull private JComponent myColumnTree;
  @Nullable private Comparator<DefaultMutableTreeNode> myComparator;
  private int myCurrentHeapId;

  public ClassesTreeView(@NotNull Project project, @NotNull final SelectionModel selectionModel) {
    myProject = project;

    final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode("Root node"));
    myTree = new Tree(model);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myTree.setLargeModel(true);

    myTree.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, this);
    JBList contextActionList = new JBList(new EditMultipleSourcesAction());
    JBPopupFactory.getInstance().createListPopupBuilder(contextActionList);
    final DefaultActionGroup popupGroup = new DefaultActionGroup(new EditMultipleSourcesAction());
    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, popupGroup).getComponent().show(comp, x, y);
      }
    });

    selectionModel.addListener(new SelectionModel.SelectionListener() {
      @Override
      public void onHeapChanged(@NotNull Heap heap) {
        final Heap selectedHeap = selectionModel.getHeap();
        myCurrentHeapId = selectedHeap.getId();

        assert model.getRoot() instanceof DefaultMutableTreeNode;
        DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();
        root.removeAllChildren();

        ArrayList<ClassObj> entries = new ArrayList<ClassObj>(selectedHeap.getClasses().size() + selectedHeap.getInstancesCount());
        // Find the union of the classObjs this heap has instances of, plus the classObjs themselves that are allocated on this heap.
        HashSet<ClassObj> entriesSet = new HashSet<ClassObj>(selectedHeap.getClasses().size() + selectedHeap.getInstancesCount());
        for (ClassObj classObj : selectedHeap.getClasses()) {
          entriesSet.add(classObj);
        }
        for (Instance instance : selectedHeap.getInstances()) {
          entriesSet.add(instance.getClassObj());
        }
        entries.addAll(entriesSet);

        ClassObj classToSelect = selectionModel.getClassObj();
        TreeNode nodeToSelect = null;
        for (ClassObj classObj : entries) {
          root.add(new DefaultMutableTreeNode(new HeapClassObj(classObj, myCurrentHeapId)));
          if (classObj == classToSelect) {
            nodeToSelect = root.getLastChild();
          }
        }

        sortTree(root);
        model.nodeStructureChanged(root);
        final TreeNode targetNode = nodeToSelect;

        // This is kind of clunky, but the viewport doesn't know how big the tree is until it repaints.
        // We need to do this because the contents of this tree has been more or less completely replaced.
        // Unfortunately, calling repaint() only queues it, so we actually need an extra frame to select the node.
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            // If the new heap has the selected class (from a previous heap), then select it and scroll to it.
            if (targetNode != null) {
              myColumnTree.revalidate();
              TreePath pathToSelect = new TreePath(model.getPathToRoot(targetNode));
              myTree.setSelectionPath(pathToSelect);
              myTree.scrollPathToVisible(pathToSelect);
            }
            else {
              selectionModel.setClassObj(null);
              myTree.scrollRowToVisible(0);
            }
          }
        });
      }

      @Override
      public void onClassObjChanged(@Nullable ClassObj classObj) {

      }

      @Override
      public void onInstanceChanged(@Nullable Instance instance) {

      }
    });

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        if (path == null || path.getPathCount() < 2 || !e.isAddedPath()) {
          selectionModel.setClassObj(null);
          return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getPathComponent(1);
        if (node.getUserObject() instanceof HeapClassObj) {
          selectionModel.setClassObj(((HeapClassObj)node.getUserObject()).getClassObj());
        }
      }
    });

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Class Name")
        .setPreferredWidth(800)
        .setHeaderAlignment(SwingConstants.LEFT)
        .setComparator(new Comparator<DefaultMutableTreeNode>() {
          @Override
          public int compare(DefaultMutableTreeNode a, DefaultMutableTreeNode b) {
            int comparisonResult = ((HeapClassObj)a.getUserObject()).getSimpleName()
              .compareToIgnoreCase(((HeapClassObj)b.getUserObject()).getSimpleName());
            if (comparisonResult == 0) {
              return ((HeapClassObj)a.getUserObject()).getClassObj().getClassName()
                .compareToIgnoreCase(((HeapClassObj)b.getUserObject()).getClassObj().getClassName());
            }
            return comparisonResult;
          }
        })
        .setRenderer(new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            if (node.getUserObject() instanceof HeapClassObj) {
              ClassObj clazz = ((HeapClassObj)node.getUserObject()).getClassObj();
              String name = clazz.getClassName();
              String pkg = null;
              int i = name.lastIndexOf(".");
              if (i != -1) {
                pkg = name.substring(0, i);
                name = name.substring(i + 1);
              }
              append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
              if (pkg != null) {
                append(" (" + pkg + ")", new SimpleTextAttributes(Font.PLAIN, JBColor.GRAY));
              }
              setTransparentIconBackground(true);
              setIcon(PlatformIcons.CLASS_ICON);
              // TODO reformat anonymous classes (ANONYMOUS_CLASS_ICON) to match IJ.
            }
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Total Count")
        .setPreferredWidth(100)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator(new Comparator<DefaultMutableTreeNode>() {
          @Override
          public int compare(DefaultMutableTreeNode a, DefaultMutableTreeNode b) {
            return ((HeapClassObj)a.getUserObject()).getClassObj().getInstanceCount() -
                   ((HeapClassObj)b.getUserObject()).getClassObj().getInstanceCount();
          }
        })
        .setRenderer(new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            if (node.getUserObject() instanceof HeapClassObj) {
              append(Integer.toString(((HeapClassObj)node.getUserObject()).getClassObj().getInstanceCount()));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Heap Count")
        .setPreferredWidth(100)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator(new Comparator<DefaultMutableTreeNode>() {
          @Override
          public int compare(DefaultMutableTreeNode a, DefaultMutableTreeNode b) {
            return ((HeapClassObj)a.getUserObject()).getClassObj().getHeapInstancesCount(myCurrentHeapId) -
                   ((HeapClassObj)b.getUserObject()).getClassObj().getHeapInstancesCount(myCurrentHeapId);
          }
        })
        .setRenderer(new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            if (node.getUserObject() instanceof HeapClassObj) {
              append(Integer.toString(((HeapClassObj)node.getUserObject()).getClassObj().getHeapInstancesCount(myCurrentHeapId)));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Sizeof")
        .setPreferredWidth(80)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator(new Comparator<DefaultMutableTreeNode>() {
          @Override
          public int compare(DefaultMutableTreeNode a, DefaultMutableTreeNode b) {
            return ((HeapClassObj)a.getUserObject()).getClassObj().getInstanceSize() -
                   ((HeapClassObj)b.getUserObject()).getClassObj().getInstanceSize();
          }
        })
        .setRenderer(new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            if (node.getUserObject() instanceof HeapClassObj) {
              append(Integer.toString(((HeapClassObj)node.getUserObject()).getClassObj().getInstanceSize()));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Shallow Size")
        .setPreferredWidth(100)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator(new Comparator<DefaultMutableTreeNode>() {
          @Override
          public int compare(DefaultMutableTreeNode a, DefaultMutableTreeNode b) {
            return ((HeapClassObj)a.getUserObject()).getClassObj().getShallowSize(myCurrentHeapId) -
                   ((HeapClassObj)b.getUserObject()).getClassObj().getShallowSize(myCurrentHeapId);
          }
        })
        .setRenderer(new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            if (node.getUserObject() instanceof HeapClassObj) {
              append(Integer.toString(((HeapClassObj)node.getUserObject()).getClassObj().getShallowSize(myCurrentHeapId)));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Retained Size")
        .setPreferredWidth(120)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator(new Comparator<DefaultMutableTreeNode>() {
          @Override
          public int compare(DefaultMutableTreeNode a, DefaultMutableTreeNode b) {
            return (int)(((HeapClassObj)a.getUserObject()).getRetainedSize() - ((HeapClassObj)b.getUserObject()).getRetainedSize());
          }
        })
        .setRenderer(new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            if (node.getUserObject() instanceof HeapClassObj) {
              append(Long.toString(((HeapClassObj)node.getUserObject()).getRetainedSize()));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    );

    //noinspection NullableProblems
    builder.setTreeSorter(new ColumnTreeBuilder.TreeSorter<DefaultMutableTreeNode>() {
      @Override
      public void sort(@NotNull Comparator<DefaultMutableTreeNode> comparator, @NotNull SortOrder sortOrder) {
        if (myComparator != comparator) {
          myComparator = comparator;

          DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();
          DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();

          selectionModel.setSelectionLocked(true);
          TreePath selectionPath = myTree.getSelectionPath();
          sortTree(root);
          model.nodeStructureChanged(root);
          myTree.setSelectionPath(selectionPath);
          myTree.scrollPathToVisible(selectionPath);
          selectionModel.setSelectionLocked(false);
        }
      }
    });

    myColumnTree = builder.build();
  }

  @NotNull
  public JComponent getComponent() {
    return myColumnTree;
  }

  private void sortTree(@NotNull DefaultMutableTreeNode parent) {
    if (parent.getChildCount() == 0 || myComparator == null) {
      return;
    }

    //noinspection unchecked
    List<DefaultMutableTreeNode> children = Collections.list((Enumeration<DefaultMutableTreeNode>)parent.children());
    Collections.sort(children, myComparator);

    parent.removeAllChildren();
    for (DefaultMutableTreeNode child : children) {
      parent.add(child);
      sortTree(child);
    }
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getTargetFiles();
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    return null;
  }

  @Nullable
  private PsiFileAndLineNavigation[] getTargetFiles() {
    TreePath path = myTree.getSelectionPath();
    if (path.getPathCount() < 2) {
      return null;
    }

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    if (node.getUserObject() instanceof HeapClassObj) {
      ClassObj classObj = ((HeapClassObj)node.getUserObject()).getClassObj();
      String className = classObj.getClassName();

      int arrayIndex = className.indexOf("[");
      if (arrayIndex >= 0) {
        className = className.substring(0, arrayIndex);
      }

      return PsiFileAndLineNavigation.wrappersForClassName(myProject, className, 1);
    }

    return null;
  }

  private static class HeapClassObj {
    @NotNull private ClassObj myClassObj;
    private long myRetainedSize;
    private String mySimpleName;

    private HeapClassObj(@NotNull ClassObj classObj, int heapId) {
      myClassObj = classObj;
      for (Instance instance : myClassObj.getHeapInstances(heapId)) {
        myRetainedSize += instance.getTotalRetainedSize();
      }

      mySimpleName = myClassObj.getClassName();
      int index = mySimpleName.lastIndexOf('.');
      if (index >= 0 && index < mySimpleName.length() - 1) {
        mySimpleName = mySimpleName.substring(index + 1, mySimpleName.length());
      }
    }

    @NotNull
    public ClassObj getClassObj() {
      return myClassObj;
    }

    public long getRetainedSize() {
      return myRetainedSize;
    }

    public String getSimpleName() {
      return mySimpleName;
    }
  }
}
