/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.invoker.messages;

import com.android.tools.idea.gradle.invoker.console.view.GradleConsoleToolWindowFactory;
import com.google.common.base.Joiner;
import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.MultilineTreeCellRenderer;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.containers.Convertor;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Locale;

import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.util.ui.UIUtil.getParentOfType;
import static com.intellij.util.ui.tree.TreeUtil.selectFirstNode;

/**
 * Tree view displayed in the "Messages" window. The difference between this one and the original one is that this one displays messages as
 * they appear in the console. The original implementation sorts the messages by type.
 */
public class GradleBuildTreeViewPanel extends NewErrorTreeViewPanel {
  private final GradleBuildTreeStructure myTreeStructure;
  private final ErrorViewTreeBuilder myBuilder;
  private final GradleBuildTreeViewConfiguration myConfiguration;

  private volatile boolean myDisposed;

  public GradleBuildTreeViewPanel(@NotNull Project project) {
    super(project, null);

    myConfiguration = GradleBuildTreeViewConfiguration.getInstance(project);
    myTreeStructure = new GradleBuildTreeStructure(myProject, myConfiguration);

    DefaultTreeModel model = (DefaultTreeModel)myTree.getModel();
    myBuilder = new ErrorViewTreeBuilder(myTree, model, myTreeStructure);
    super.dispose();

    // We need to remove the JTree from the JScrollPane to register a new cell renderer. The reason is that the superclass calls
    // MultilineTreeCellRenderer#installRenderer which installs a new cell renderer, puts the JTree in a JScrollPane and sets the
    // cell renderer over and over when resetting the caches. A simple call to JTree#setCellRenderer does not work because of this.
    JScrollPane scrollPane = getParentOfType(JScrollPane.class, myTree);
    assert scrollPane != null;

    myTree.getParent().remove(myTree);
    Container parent = scrollPane.getParent();
    assert parent instanceof JPanel;
    parent.remove(scrollPane);

    scrollPane = MultilineTreeCellRenderer.installRenderer(myTree, new MessageTreeRenderer());
    parent.add(scrollPane, BorderLayout.CENTER);

    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath treePath) {
        Object last = treePath.getLastPathComponent();
        if (last instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)last).getUserObject();
          if (data instanceof ErrorTreeNodeDescriptor) {
            ErrorTreeElement e = ((ErrorTreeNodeDescriptor)data).getElement();
            String[] text = e.getText();
            if (text != null) {
              return Joiner.on(' ').join(text);
            }
          }
        }
        return "";
      }
    });
  }

  @Nullable
  private GradleBuildTreeViewConfiguration getConfiguration() {
    return myConfiguration;
  }

  @Override
  protected void fillRightToolbarGroup(DefaultActionGroup group) {
    super.fillRightToolbarGroup(group);
    group.add(new DumbAwareAction("Show Console Output", null, AndroidIcons.GradleConsole) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(GradleConsoleToolWindowFactory.ID);
        if (window != null) {
          window.activate(null, false);
        }
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
      }
    });

    DefaultActionGroup filterGroup = new DefaultActionGroup("GradleBuildMessagesFilter", true) {
      @Override
      public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setDescription("Filter messages to display");
        presentation.setIcon(AllIcons.General.Filter);
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };

    // We could have iterated through ErrorTreeElementKind.values() and have less code, but we want to keep this order:
    filterGroup.add(new FilterMessagesByKindAction(ErrorTreeElementKind.ERROR));
    filterGroup.add(new FilterMessagesByKindAction(ErrorTreeElementKind.WARNING));
    filterGroup.add(new FilterMessagesByKindAction(ErrorTreeElementKind.INFO));
    filterGroup.add(new FilterMessagesByKindAction(ErrorTreeElementKind.NOTE));
    filterGroup.add(new FilterMessagesByKindAction(ErrorTreeElementKind.GENERIC));
    group.add(filterGroup);
  }

  @Override
  protected boolean canHideWarnings() {
    return false;
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myTreeStructure.clear();
    Disposer.dispose(myBuilder);
  }

  @Override
  public void updateTree() {
    if (!myDisposed) {
      myBuilder.updateTree();
    }
  }

  @Override
  public void reload() {
    myBuilder.updateTree();
  }

  @Override
  public void addMessage(int type,
                         @NotNull String[] text,
                         @Nullable VirtualFile underFileGroup,
                         @Nullable VirtualFile file,
                         int line,
                         int column,
                         @Nullable Object data) {
    if (myDisposed) {
      return;
    }
    ErrorTreeElementKind kind = ErrorTreeElementKind.convertMessageFromCompilerErrorType(type);
    myTreeStructure.addMessage(kind, text, underFileGroup, file, line, column, data);
    myBuilder.updateTree();
  }

  @Override
  public void addMessage(int type,
                         @NotNull String[] text,
                         @Nullable String groupName,
                         @NotNull Navigatable navigatable,
                         @Nullable String exportTextPrefix,
                         @Nullable String rendererTextPrefix,
                         @Nullable Object data) {
    if (myDisposed) {
      return;
    }
    VirtualFile file = data instanceof VirtualFile ? (VirtualFile)data : null;
    if (file == null && navigatable instanceof OpenFileDescriptor) {
      file = ((OpenFileDescriptor)navigatable).getFile();
    }
    ErrorTreeElementKind kind = ErrorTreeElementKind.convertMessageFromCompilerErrorType(type);
    myTreeStructure.addNavigatableMessage(groupName, navigatable, kind, text, data, nullToEmpty(exportTextPrefix),
                                          nullToEmpty(rendererTextPrefix), file);
    myBuilder.updateTree();
  }

  @Override
  public GradleBuildTreeStructure getErrorViewStructure() {
    return myTreeStructure;
  }

  @Override
  public void selectFirstMessage() {
    ErrorTreeElement firstError = myTreeStructure.getFirstMessage(ErrorTreeElementKind.ERROR);
    if (firstError != null) {
      selectElement(firstError, new Runnable() {
        @Override
        public void run() {
          if (shouldShowFirstErrorInEditor()) {
            navigateToSource(false);
          }
        }
      });
    }
    else {
      selectFirstNode(myTree);
    }
  }

  @Override
  protected boolean shouldShowFirstErrorInEditor() {
    return true;
  }

  private void selectElement(@NotNull ErrorTreeElement element, @Nullable Runnable onDone) {
    myBuilder.select(element, onDone);
  }

  private void navigateToSource(boolean focusEditor) {
    NavigatableMessageElement element = getSelectedMessageElement();
    if (element == null) {
      return;
    }
    Navigatable navigatable = element.getNavigatable();
    if (navigatable.canNavigate()) {
      navigatable.navigate(focusEditor);
    }
  }

  @Nullable
  private NavigatableMessageElement getSelectedMessageElement() {
    ErrorTreeElement selectedElement = getSelectedErrorTreeElement();
    return selectedElement instanceof NavigatableMessageElement ? (NavigatableMessageElement)selectedElement : null;
  }

  private class FilterMessagesByKindAction extends ToggleAction {
    @NotNull private final ErrorTreeElementKind myElementKind;

    FilterMessagesByKindAction(@NotNull ErrorTreeElementKind elementKind) {
      super(capitalize(elementKind.toString().toLowerCase(Locale.getDefault())));
      myElementKind = elementKind;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      GradleBuildTreeViewConfiguration configuration = getConfiguration();
      if (configuration == null) {
        return false;
      }
      return configuration.canShow(myElementKind);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myConfiguration.update(myElementKind, state);
      myBuilder.updateTree();
    }
  }
}
