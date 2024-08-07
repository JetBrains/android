/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ui.problems;

import com.google.idea.blaze.base.io.AbsolutePathPatcher.AbsolutePathPatcherUtil;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.ui.MessageCategory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** A custom error tree view for Blaze invocation errors. */
public class BlazeProblemsView {

  private static final Logger logger = Logger.getInstance(BlazeProblemsView.class);

  public static BlazeProblemsView getInstance(Project project) {
    return project.getService(BlazeProblemsView.class);
  }

  private static final EnumSet<ErrorTreeElementKind> ALL_MESSAGE_KINDS =
      EnumSet.allOf(ErrorTreeElementKind.class);
  private static final int MAX_ISSUES = 2000;

  private final ExecutorService viewUpdater =
      SequentialTaskExecutor.createSequentialApplicationPoolExecutor("BlazeProblemsView pool");
  private final Icon activeIcon = AllIcons.Toolwindows.Problems;
  private final Icon passiveIcon = IconLoader.getDisabledIcon(activeIcon);

  private final Project project;
  private final String toolWindowId;
  private final RunnableFuture<BlazeProblemsViewPanel> uiFuture;

  private final Set<Integer> problemHashes = Collections.synchronizedSet(new HashSet<>());
  private final AtomicInteger problemCount = new AtomicInteger(0);
  private volatile boolean didFocusProblemsView = false;
  private volatile FocusBehavior focusBehavior;
  private volatile UUID currentSessionId = UUID.randomUUID();

  public BlazeProblemsView(Project project) {
    this.project = project;
    this.toolWindowId = Blaze.getBuildSystemName(project).getName() + " Problems";
    uiFuture =
        new FutureTask<>(
            () -> {
              BlazeProblemsViewPanel panel = new BlazeProblemsViewPanel(project);
              Disposer.register(project, () -> Disposer.dispose(panel));
              createToolWindow(project, ToolWindowManager.getInstance(project), panel);
              return panel;
            });
    ApplicationManager.getApplication().invokeLater(uiFuture);
  }

  @Nullable
  private BlazeProblemsViewPanel getPanel() {
    try {
      return uiFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      logger.error("Couldn't create Problems View Panel", e);
      return null;
    }
  }

  private void createToolWindow(
      Project project, ToolWindowManager wm, BlazeProblemsViewPanel panel) {
    if (project.isDisposed()) {
      return;
    }
    ToolWindow toolWindow =
        wm.registerToolWindow(toolWindowId, false, ToolWindowAnchor.BOTTOM, project, true);
    Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
    toolWindow.getContentManager().addContent(content);
    Disposer.register(project, () -> toolWindow.getContentManager().removeAllContents(true));
    updateIcon(panel);
  }

  public void newProblemsContext(FocusBehavior focusBehavior) {
    BlazeProblemsViewPanel panel = getPanel();
    if (panel == null) {
      return;
    }
    viewUpdater.execute(
        () -> {
          currentSessionId = UUID.randomUUID();
          panel.getErrorViewStructure().clear();
          problemCount.set(0);
          didFocusProblemsView = false;
          this.focusBehavior = focusBehavior;
          problemHashes.clear();
          updateIcon(panel);
          panel.reload();
        });
  }

  public void addMessage(IssueOutput issue, @Nullable Navigatable openInConsole) {
    BlazeProblemsViewPanel panel = getPanel();
    if (panel == null) {
      return;
    }
    if (!problemHashes.add(issue.hashCode())) {
      return;
    }
    int count = problemCount.incrementAndGet();
    if (count > MAX_ISSUES) {
      return;
    }
    if (count == MAX_ISSUES) {
      issue =
          IssueOutput.warn("Too many problems found. Only showing the first " + MAX_ISSUES).build();
    }
    VirtualFile file = issue.getFile() != null ? resolveVirtualFile(issue.getFile()) : null;
    Navigatable navigatable = issue.getNavigatable();
    if (navigatable == null && file != null) {
      navigatable =
          new OpenFileDescriptor(project, file, issue.getLine() - 1, issue.getColumn() - 1);
    }
    IssueOutput.Category category = issue.getCategory();
    int type = translateCategory(category);
    String[] text = convertMessage(issue);
    String groupName = file != null ? file.getPresentableUrl() : category.name();
    addMessage(
        type,
        text,
        groupName,
        file,
        navigatable,
        openInConsole,
        getExportTextPrefix(issue),
        getRenderTextPrefix(issue),
        panel);

    if (didFocusProblemsView) {
      return;
    }
    boolean focus =
        focusBehavior == FocusBehavior.ALWAYS
            || (focusBehavior == FocusBehavior.ON_ERROR && category == IssueOutput.Category.ERROR);
    if (focus) {
      didFocusProblemsView = true;
      focusProblemsView();
    }
  }

  /**
   * Finds the virtual file associated with the given file path, resolving symlinks where relevant.
   */
  @Nullable
  private static VirtualFile resolveVirtualFile(File file) {
    VirtualFile vf = VfsUtils.resolveVirtualFile(file, /* refreshIfNeeded= */ true);
    return vf != null ? resolveSymlinks(vf) : null;
  }

  /**
   * Attempts to resolve symlinks in the virtual file path, falling back to returning the original
   * virtual file if unsuccessful.
   */
  private static VirtualFile resolveSymlinks(VirtualFile file) {
    VirtualFile resolved =
        AbsolutePathPatcherUtil.fixPath(file.getCanonicalFile(), /* refreshIfNeeded= */ false);
    return resolved != null ? resolved : file;
  }

  private static int translateCategory(IssueOutput.Category category) {
    switch (category) {
      case ERROR:
        return MessageCategory.ERROR;
      case WARNING:
        return MessageCategory.WARNING;
      case NOTE:
        return MessageCategory.NOTE;
      case STATISTICS:
        return MessageCategory.STATISTICS;
      case INFORMATION:
        return MessageCategory.INFORMATION;
      default:
        logger.error("Unknown message category: " + category);
        return 0;
    }
  }

  private static String[] convertMessage(final IssueOutput issue) {
    String text = issue.getMessage();
    if (!text.contains("\n")) {
      return new String[] {text};
    }
    final List<String> lines = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(text, "\n", false);
    while (tokenizer.hasMoreTokens()) {
      lines.add(tokenizer.nextToken());
    }
    return ArrayUtil.toStringArray(lines);
  }

  private static String getExportTextPrefix(IssueOutput issue) {
    int line = issue.getLine();
    if (line >= 0) {
      return String.format("line: %d", line);
    }
    return "";
  }

  private static String getRenderTextPrefix(IssueOutput issue) {
    int line = issue.getLine();
    if (line >= 0) {
      return String.format("(%d, %d)", line, issue.getColumn());
    }
    return "";
  }

  private void addMessage(
      int type,
      String[] text,
      String groupName,
      @Nullable VirtualFile file,
      @Nullable Navigatable navigatable,
      @Nullable Navigatable openInConsole,
      String exportTextPrefix,
      String rendererTextPrefix,
      BlazeProblemsViewPanel panel) {
    UUID sessionId = currentSessionId;
    viewUpdater.execute(
        () -> {
          final ErrorViewStructure structure = panel.getErrorViewStructure();
          final GroupingElement group = structure.lookupGroupingElement(groupName);
          if (group != null && !sessionId.equals(group.getData())) {
            structure.removeElement(group);
          }
          if (openInConsole != null) {
            panel.addNavigableMessageElement(
                groupName,
                new ProblemsViewMessageElement(
                    ErrorTreeElementKind.convertMessageFromCompilerErrorType(type),
                    structure.getGroupingElement(groupName, sessionId, file),
                    text,
                    navigatable != null ? navigatable : openInConsole,
                    openInConsole,
                    exportTextPrefix,
                    rendererTextPrefix));
          } else if (navigatable != null) {
            panel.addMessage(
                type,
                text,
                groupName,
                navigatable,
                exportTextPrefix,
                rendererTextPrefix,
                sessionId);
          } else {
            panel.addMessage(type, text, null, -1, -1, sessionId);
          }
          updateIcon(panel);
        });
  }

  private void updateIcon(BlazeProblemsViewPanel panel) {
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              if (project.isDisposed()) {
                return;
              }
              ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
              if (tw == null) {
                return;
              }
              boolean active = panel.getErrorViewStructure().hasMessages(ALL_MESSAGE_KINDS);
              tw.setIcon(active ? activeIcon : passiveIcon);
            });
  }

  private void focusProblemsView() {
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
              if (tw != null) {
                tw.activate(null, false, false);
              }
            });
  }
}
