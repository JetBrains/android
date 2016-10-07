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

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.idea.actions.EditMultipleSourcesAction;
import com.android.tools.idea.actions.PsiFileAndLineNavigation;
import com.android.tools.idea.editors.hprof.views.nodedata.HeapClassObjNode;
import com.android.tools.idea.editors.hprof.views.nodedata.HeapNode;
import com.android.tools.idea.editors.hprof.views.nodedata.HeapPackageNode;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ClassesTreeView implements DataProvider, Disposable {
  public static final String TREE_NAME = "HprofClassesTree";

  @NotNull private Project myProject;
  @NotNull private Tree myTree;
  @NotNull private DefaultTreeModel myTreeModel;
  @NotNull private HeapPackageNode myRoot;
  @NotNull private JComponent myColumnTree;
  @Nullable private Comparator<HeapNode> myComparator;

  private int mySelectedHeapId;

  @NotNull private ListIndex myListIndex;
  @NotNull private TreeIndex myTreeIndex;
  @NotNull private DisplayMode myDisplayMode;

  public ClassesTreeView(@NotNull Project project,
                         @NotNull DefaultActionGroup editorActionGroup,
                         @NotNull final SelectionModel selectionModel) {
    myProject = project;

    myRoot = new HeapPackageNode(null, "");
    myTreeModel = new DefaultTreeModel(myRoot);
    myTree = new Tree(myTreeModel);
    myTree.setName(TREE_NAME);
    myDisplayMode = DisplayMode.LIST;
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

    editorActionGroup.addAction(new ComboBoxAction() {
      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        for (final DisplayMode mode : DisplayMode.values()) {
          group.add(new AnAction(mode.toString()) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              myDisplayMode = mode;
              boolean isTreeMode = myDisplayMode == DisplayMode.TREE;
              myTree.setShowsRootHandles(isTreeMode);
              if (isTreeMode) {
                myTreeIndex.buildTree(mySelectedHeapId);
              }
              else {
                myListIndex.buildList(myRoot);
              }

              restoreViewState(selectionModel);
            }
          });
        }
        return group;
      }

      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        getTemplatePresentation().setText(myDisplayMode.toString());
        e.getPresentation().setText(myDisplayMode.toString());
      }
    });

    myListIndex = new ListIndex();
    myTreeIndex = new TreeIndex();
    selectionModel.addListener(myListIndex); // Add list index first, since that always updates; and tree index depends on it.
    selectionModel.addListener(myTreeIndex);

    selectionModel.addListener(new SelectionModel.SelectionListener() {
      @Override
      public void onHeapChanged(@NotNull Heap heap) {
        mySelectedHeapId = heap.getId();

        assert myListIndex.myHeapId == mySelectedHeapId;
        if (myDisplayMode == DisplayMode.LIST) {
          myListIndex.buildList(myRoot);
        }
        else if (myDisplayMode == DisplayMode.TREE) {
          myTreeIndex.buildTree(mySelectedHeapId);
        }

        restoreViewState(selectionModel);
      }

      @Override
      public void onClassObjChanged(@Nullable ClassObj classObj) {
        TreeNode nodeToSelect = null;
        if (classObj != null) {
          nodeToSelect = findClassObjNode(classObj);
        }

        if (nodeToSelect != null) {
          TreePath pathToSelect = new TreePath(myTreeModel.getPathToRoot(nodeToSelect));
          myTree.setSelectionPath(pathToSelect);
          myTree.scrollPathToVisible(pathToSelect);
        }
      }

      @Override
      public void onInstanceChanged(@Nullable Instance instance) {

      }
    });

    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        if (!e.isAddedPath()) {
          return;
        }

        if (path == null || path.getPathCount() < 2) {
          selectionModel.setClassObj(null);
          return;
        }

        assert path.getLastPathComponent() instanceof HeapNode;
        HeapNode heapNode = (HeapNode)path.getLastPathComponent();
        if (heapNode instanceof HeapClassObjNode) {
          selectionModel.setClassObj(((HeapClassObjNode)heapNode).getClassObj());
        }
      }
    });

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Class Name")
        .setPreferredWidth(800)
        .setHeaderAlignment(SwingConstants.LEFT)
        .setComparator(new Comparator<HeapNode>() {
          @Override
          public int compare(HeapNode a, HeapNode b) {
            int valueA = a instanceof HeapPackageNode ? 0 : 1;
            int valueB = b instanceof HeapPackageNode ? 0 : 1;
            if (valueA != valueB) {
              return valueA - valueB;
            }
            return compareNames(a, b);
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
            if (value instanceof HeapClassObjNode) {
              ClassObj clazz = ((HeapClassObjNode)value).getClassObj();
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
            else if (value instanceof HeapNode) {
              append(((HeapNode)value).getSimpleName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              setTransparentIconBackground(true);
              setIcon(PlatformIcons.PACKAGE_ICON);
            }
            else {
              append("This should not be rendered");
            }
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Total Count")
        .setPreferredWidth(100)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator(new Comparator<HeapNode>() {
          @Override
          public int compare(HeapNode a, HeapNode b) {
            int result = a.getTotalCount() - b.getTotalCount();
            return result == 0 ? compareNames(a, b) : result;
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
            if (value instanceof HeapNode) {
              append(Integer.toString(((HeapNode)value).getTotalCount()));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Heap Count")
        .setPreferredWidth(100)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator(new Comparator<HeapNode>() {
          @Override
          public int compare(HeapNode a, HeapNode b) {
            int result = a.getHeapInstancesCount(mySelectedHeapId) - b.getHeapInstancesCount(mySelectedHeapId);
            return result == 0 ? compareNames(a, b) : result;
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
            if (value instanceof HeapNode) {
              append(Integer.toString(((HeapNode)value).getHeapInstancesCount(mySelectedHeapId)));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Sizeof")
        .setPreferredWidth(80)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator(new Comparator<HeapNode>() {
          @Override
          public int compare(HeapNode a, HeapNode b) {
            int sizeA = a.getInstanceSize();
            int sizeB = b.getInstanceSize();
            if (sizeA < 0 && sizeB < 0) {
              return compareNames(a, b);
            }
            int result = sizeA - sizeB;
            return result == 0 ? compareNames(a, b) : result;
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
            if (value instanceof HeapClassObjNode) {
              append(Integer.toString(((HeapClassObjNode)value).getInstanceSize()));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Shallow Size")
        .setPreferredWidth(100)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setComparator(new Comparator<HeapNode>() {
          @Override
          public int compare(HeapNode a, HeapNode b) {
            int result = a.getShallowSize(mySelectedHeapId) - b.getShallowSize(mySelectedHeapId);
            return result == 0 ? compareNames(a, b) : result;
          }
        }).setRenderer(new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            if (value instanceof HeapNode) {
              append(Integer.toString(((HeapNode)value).getShallowSize(mySelectedHeapId)));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    ).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Retained Size")
        .setPreferredWidth(120)
        .setHeaderAlignment(SwingConstants.RIGHT)
        .setInitialOrder(SortOrder.DESCENDING)
        .setComparator(new Comparator<HeapNode>() {
          @Override
          public int compare(HeapNode a, HeapNode b) {
            long result = a.getRetainedSize() - b.getRetainedSize();
            return result == 0 ? compareNames(a, b) : (result > 0 ? 1 : -1);
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
            if (value instanceof HeapNode) {
              append(Long.toString(((HeapNode)value).getRetainedSize()));
            }
            setTextAlign(SwingConstants.RIGHT);
          }
        })
    );

    //noinspection NullableProblems
    builder.setTreeSorter(new ColumnTreeBuilder.TreeSorter<HeapNode>() {
      @Override
      public void sort(@NotNull Comparator<HeapNode> comparator, @NotNull SortOrder sortOrder) {
        if (myComparator != comparator) {
          myComparator = comparator;

          selectionModel.setSelectionLocked(true);
          TreePath selectionPath = myTree.getSelectionPath();
          sortTree(myRoot);
          myTreeModel.nodeStructureChanged(myRoot);
          myTree.setSelectionPath(selectionPath);
          myTree.scrollPathToVisible(selectionPath);
          selectionModel.setSelectionLocked(false);
        }
      }
    });

    myColumnTree = builder.build();
    installTreeSpeedSearch();
  }

  @NotNull
  public JComponent getComponent() {
    return myColumnTree;
  }

  public void requestFocus() {
    myTree.requestFocus();
  }

  private void installTreeSpeedSearch() {
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath e) {
        Object o = e.getLastPathComponent();
        if (o instanceof HeapNode) {
          if (o instanceof HeapClassObjNode) {
            return ((HeapClassObjNode)o).getSimpleName();
          }
          else if (o instanceof HeapPackageNode) {
            return ((HeapPackageNode)o).getFullName();
          }
        }
        return o.toString();
      }
    }, true);
  }

  private void sortTree(@NotNull HeapPackageNode parent) {
    if (parent.isLeaf() || myComparator == null) {
      return;
    }

    List<HeapNode> children = parent.getChildren();
    Collections.sort(children, myComparator);

    for (HeapNode child : children) {
      if (child instanceof HeapPackageNode) {
        sortTree((HeapPackageNode)child);
      }
    }
  }

  private static int compareNames(@NotNull HeapNode a, @NotNull HeapNode b) {
    int comparisonResult = a.getSimpleName()
      .compareToIgnoreCase(b.getSimpleName());
    if (comparisonResult == 0) {
      return a.getFullName().compareToIgnoreCase(b.getFullName());
    }
    return comparisonResult;
  }

  private void restoreViewState(@NotNull final SelectionModel selectionModel) {
    ClassObj classToSelect = selectionModel.getClassObj();
    TreeNode nodeToSelect = null;
    if (classToSelect != null) {
      nodeToSelect = findClassObjNode(classToSelect);
    }

    sortTree(myRoot);
    myTreeModel.nodeStructureChanged(myRoot);
    final TreeNode targetNode = nodeToSelect;

    if (targetNode != null) {
      // If the new heap has the selected class (from a previous heap), then select it and scroll to it.
      myColumnTree.revalidate();
      final TreePath pathToSelect = new TreePath(myTreeModel.getPathToRoot(targetNode));
      myTree.setSelectionPath(pathToSelect);

      // This is kind of clunky, but the viewport doesn't know how big the tree is until it repaints.
      // We need to do this because the contents of this tree has been more or less completely replaced.
      // Unfortunately, calling repaint() only queues it, so we actually need an extra frame to select the node.
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myTree.scrollPathToVisible(pathToSelect);
        }
      });
    }
    else {
      selectionModel.setClassObj(null);
      if (myTree.getRowCount() > 0) {
        myTree.scrollRowToVisible(0);
      }
    }
  }

  @Nullable
  private HeapClassObjNode findClassObjNode(@NotNull ClassObj targetClass) {
    if (myDisplayMode == DisplayMode.LIST) {
      for (int i = 0; i < myRoot.getChildCount(); ++i) {
        TreeNode child = myRoot.getChildAt(i);
        assert child instanceof HeapClassObjNode;
        if (((HeapClassObjNode)child).getClassObj() == targetClass) {
          return (HeapClassObjNode)child;
        }
      }
    }
    else if (myDisplayMode == DisplayMode.TREE) {
      HeapPackageNode currentNode = myRoot;

      String[] packages = targetClass.getClassName().split("\\.");
      assert packages.length > 0;
      int currentPackageIndex = 0;

      while (currentPackageIndex < packages.length - 1) {
        if (currentNode.getSubPackages().containsKey(packages[currentPackageIndex])) {
          currentNode = currentNode.getSubPackages().get(packages[currentPackageIndex]);
          ++currentPackageIndex;
        }
        else {
          return null;
        }
      }

      for (int i = 0; i < currentNode.getChildCount(); ++i) {
        TreeNode childTreeNode = currentNode.getChildAt(i);
        assert childTreeNode instanceof HeapNode;
        HeapNode child = (HeapNode)childTreeNode;
        if (child instanceof HeapClassObjNode && ((HeapClassObjNode)child).getClassObj() == targetClass) {
          return (HeapClassObjNode)child;
        }
      }
    }

    return null;
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

    assert path.getLastPathComponent() instanceof HeapNode;
    HeapNode node = (HeapNode)path.getLastPathComponent();
    if (node instanceof HeapClassObjNode) {
      ClassObj classObj = ((HeapClassObjNode)node).getClassObj();
      String className = classObj.getClassName();

      int arrayIndex = className.indexOf("[");
      if (arrayIndex >= 0) {
        className = className.substring(0, arrayIndex);
      }

      return PsiFileAndLineNavigation.wrappersForClassName(myProject, className, 1);
    }

    return null;
  }

  @Override
  public void dispose() {
    myListIndex.clear();
    myTreeIndex.clear();
  }

  private static class ListIndex implements SelectionModel.SelectionListener {
    ArrayList<HeapClassObjNode> myClasses = new ArrayList<HeapClassObjNode>();
    private int myHeapId = -1;

    @Override
    public void onHeapChanged(@NotNull Heap heap) {
      if (myHeapId != heap.getId()) {
        myHeapId = heap.getId();
        myClasses.clear();

        // Find the union of the classObjs this heap has instances of, plus the classObjs themselves that are allocated on this heap.
        final HashSet<ClassObj> entriesSet = new HashSet<ClassObj>(heap.getClasses().size() + heap.getInstancesCount());
        for (ClassObj classObj : heap.getClasses()) {
          entriesSet.add(classObj);
        }
        heap.forEachInstance(new TObjectProcedure<Instance>() {
          @Override
          public boolean execute(Instance instance) {
            entriesSet.add(instance.getClassObj());
            return true;
          }
        });

        for (ClassObj classObj : entriesSet) {
          myClasses.add(new HeapClassObjNode(classObj, myHeapId));
        }
      }
    }

    @Override
    public void onClassObjChanged(@Nullable ClassObj classObj) {

    }

    @Override
    public void onInstanceChanged(@Nullable Instance instance) {

    }

    private void buildList(@NotNull HeapNode root) {
      root.removeAllChildren();
      for (HeapClassObjNode heapClassObjNode : myClasses) {
        heapClassObjNode.removeFromParent();
        root.add(heapClassObjNode);
      }
    }

    private void clear() {
      myClasses.clear();
    }
  }

  private class TreeIndex implements SelectionModel.SelectionListener {
    private int myHeapId = -1;

    @Override
    public void onHeapChanged(@NotNull Heap heap) {
      // TODO save the expansion state
      if (myDisplayMode == DisplayMode.TREE) {
        assert myListIndex.myHeapId == heap.getId();
        buildTree(heap.getId());
      }
    }

    @Override
    public void onClassObjChanged(@Nullable ClassObj classObj) {

    }

    @Override
    public void onInstanceChanged(@Nullable Instance instance) {

    }

    private void buildTree(int heapId) {
      if (myHeapId != heapId) {
        myHeapId = heapId;
        myRoot.clear();

        for (HeapClassObjNode heapClassObjNode : myListIndex.myClasses) {
          myRoot.classifyClassObj(heapClassObjNode);
        }

        myRoot.update(mySelectedHeapId);
      }

      myRoot.buildTree();
    }

    private void clear() {
      myRoot.clear();
    }
  }

  private enum DisplayMode {
    LIST("Class List View"),
    TREE("Package Tree View");

    @NotNull
    private String myName;

    DisplayMode(@NotNull String name) {
      myName = name;
    }

    @Override
    public String toString() {
      return myName;
    }
  }
}
