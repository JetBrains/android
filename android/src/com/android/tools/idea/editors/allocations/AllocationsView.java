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
package com.android.tools.idea.editors.allocations;

import com.android.ddmlib.AllocationInfo;
import com.android.tools.idea.editors.allocations.nodes.*;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AllocationsView {

  @NotNull
  private final Project myProject;

  @NotNull
  private final AllocationInfo[] myAllocations;

  @NotNull
  private final JComponent myColumnTree;

  @NotNull
  private final StackTraceNode myTreeNode;

  @NotNull
  private final JTree myTree;

  @NotNull
  private final DefaultTreeModel myTreeModel;

  public AllocationsView(@NotNull Project project, @NotNull final AllocationInfo[] allocations) {
    myProject = project;
    myAllocations = allocations;
    myTreeNode = groupByStack(allocations);
    myTreeModel = new DefaultTreeModel(myTreeNode);

    myTree = new Tree(myTreeModel);
    myTree.setRootVisible(false);

    final DefaultActionGroup popupGroup = new DefaultActionGroup();
    popupGroup.add(new EditMultipleSourcesAction());

    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UPDATE_POPUP, popupGroup);
        popupMenu.getComponent().show(comp, x, y);
      }
    });
    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
        .addColumn(new ColumnTreeBuilder.ColumnBuilder()
            .setName("Method")
            .setPreferredWidth(600)
            .setComparator(new Comparator<AbstractTreeNode>() {
              @Override
              public int compare(AbstractTreeNode a, AbstractTreeNode b) {
                if (a instanceof ThreadNode && b instanceof ThreadNode) {
                  return ((ThreadNode)a).getThreadId() - ((ThreadNode)b).getThreadId();
                }
                else if (a instanceof StackNode && b instanceof StackNode) {
                  StackTraceElement ea = ((StackNode)a).getStackTraceElement();
                  StackTraceElement eb = ((StackNode)b).getStackTraceElement();
                  int value = ea.getMethodName().compareTo(eb.getMethodName());
                  if (value == 0) value = ea.getLineNumber() - eb.getLineNumber();
                  return value;
                }
                else {
                  return a.getClass().toString().compareTo(b.getClass().toString());
                }
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
                if (value instanceof ThreadNode) {
                  setIcon(AllIcons.Debugger.ThreadSuspended);
                  append("< Thread " + ((ThreadNode)value).getThreadId() + " >");
                }
                else if (value instanceof StackNode) {
                  StackTraceElement element = ((StackNode)value).getStackTraceElement();
                  String name = element.getClassName();
                  String pkg = null;
                  int ix = name.lastIndexOf(".");
                  if (ix != -1) {
                    pkg = name.substring(0, ix);
                    name = name.substring(ix + 1);
                  }

                  setIcon(PlatformIcons.METHOD_ICON);
                  append(element.getMethodName() + "()");
                  append(":" + element.getLineNumber() + ", ");
                  append(name);
                  if (pkg != null) {
                    append(" (" + pkg + ")", new SimpleTextAttributes(Font.PLAIN, JBColor.GRAY));
                  }
                }
                else if (value instanceof AllocNode) {
                  AllocationInfo allocation = ((AllocNode)value).getAllocation();
                  setIcon(AllIcons.FileTypes.JavaClass);
                  append(allocation.getAllocatedClass());
                }
                else {
                  append(value.toString());
                }
              }

              @Override
              protected boolean shouldDrawBackground() {
                return false;
              }
            }))
        .addColumn(new ColumnTreeBuilder.ColumnBuilder()
            .setName("Count")
            .setPreferredWidth(150)
            .setComparator(new Comparator<AbstractTreeNode>() {
                @Override
                public int compare(AbstractTreeNode a, AbstractTreeNode b) {
                  return a.getCount() - b.getCount();
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
                if (value instanceof ValuedTreeNode) {
                  int v = ((ValuedTreeNode)value).getCount();
                  int total = myTreeNode.getCount();
                  setTextAlign(SwingConstants.RIGHT);
                  append(String.valueOf(v));
                  append(String.format(" (%.2f%%)", 100.0f * v / total), new SimpleTextAttributes(Font.PLAIN, JBColor.GRAY));
                }
              }
            }))
        .addColumn(new ColumnTreeBuilder.ColumnBuilder()
            .setName("Size")
            .setPreferredWidth(150)
            .setComparator(new Comparator<AbstractTreeNode>() {
                @Override
                public int compare(AbstractTreeNode a, AbstractTreeNode b) {
                  return a.getValue() - b.getValue();
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
                if (value instanceof ValuedTreeNode) {
                  int v = ((ValuedTreeNode)value).getValue();
                  int total = myTreeNode.getValue();
                  setTextAlign(SwingConstants.RIGHT);
                  append(String.valueOf(v));
                  append(String.format(" (%.2f%%)", 100.0f * v / total), new SimpleTextAttributes(Font.PLAIN, JBColor.GRAY));
                }
              }
            }));
    builder.setTreeSorter(new ColumnTreeBuilder.TreeSorter<AbstractTreeNode>() {
      @Override
      public void sort(Comparator<AbstractTreeNode> comparator) {
        myTreeNode.sort(comparator);
        myTreeModel.nodeStructureChanged(myTreeNode);
      }
    });

    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath e) {
        Object o = e.getLastPathComponent();
        if (o instanceof StackNode) {
          StackTraceElement ee = ((StackNode)o).getStackTraceElement();
          return ee.toString();
        }
        return o.toString();
      }
    }, true);

    myColumnTree = builder.build();
  }

  public Component getComponent() {
    return myColumnTree;
  }

  private static StackTraceNode groupByStack(@NotNull AllocationInfo[] allocations) {
    StackTraceNode tree = new StackTraceNode();
    for (AllocationInfo alloc : allocations) {
      tree.insert(alloc);
    }
    return tree;
  }

  private class EditMultipleSourcesAction extends AnAction {
    public EditMultipleSourcesAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setText(ActionsBundle.actionText("EditSource"));
      presentation.setIcon(AllIcons.Actions.EditSource);
      presentation.setDescription(ActionsBundle.actionDescription("EditSource"));
      // TODO shortcuts
      // setShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet());
    }

    StackTraceElement getStackTraceElement(AnActionEvent e) {
      Object node = myTree.getLastSelectedPathComponent();
      if (node instanceof StackNode) {
        return ((StackNode)node).getStackTraceElement();
      }
      return null;
    }

    List<PsiElement> getTargetFiles(AnActionEvent e) {
      List<PsiElement> files = new ArrayList<PsiElement>();
      StackTraceElement element = getStackTraceElement(e);
      if (element != null) {
        String className = element.getClassName();
        int ix = className.indexOf("$");
        if (ix >= 0) {
          className = className.substring(0, ix);
        }
        PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(className, GlobalSearchScope.allScope(myProject));
        for (PsiClass c : classes) {
          files.add(c.getContainingFile().getNavigationElement());
        }
      }
      return files;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(!getTargetFiles(e).isEmpty());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      List<PsiElement> files = getTargetFiles(e);
      assert !files.isEmpty();
      final StackTraceElement element = getStackTraceElement(e);
      if (files.size() > 1) {
        final JBList list = new JBList(files);
        int width = WindowManager.getInstance().getFrame(myProject).getSize().width;
        list.setCellRenderer(new GotoFileCellRenderer(width));
        JBPopupFactory.getInstance().createListPopupBuilder(list).setTitle("Choose Target File").setItemChoosenCallback(new Runnable() {
          @Override
          public void run() {
            VirtualFile file = ((PsiFile)list.getSelectedValue()).getVirtualFile();
            new OpenFileHyperlinkInfo(myProject, file, element.getLineNumber()).navigate(myProject);
          }
        }).createPopup().showInFocusCenter();
      }
      else {
        VirtualFile file = ((PsiFile)files.get(0)).getVirtualFile();
        new OpenFileHyperlinkInfo(myProject, file, element.getLineNumber()).navigate(myProject);
      }
    }
  }
}
