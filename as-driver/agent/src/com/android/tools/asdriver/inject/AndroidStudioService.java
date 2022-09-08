/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.inject;

import com.android.tools.asdriver.proto.ASDriver;
import com.android.tools.asdriver.proto.AndroidStudioGrpc;
import com.android.tools.idea.io.grpc.Server;
import com.android.tools.idea.io.grpc.ServerBuilder;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import com.google.common.base.Objects;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.testFramework.MapDataContext;
import java.io.File;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class AndroidStudioService extends AndroidStudioGrpc.AndroidStudioImplBase {

  static public void start() {
    ServerBuilder<?> builder = ServerBuilder.forPort(0);
    builder.addService(new AndroidStudioService());
    Server server = builder.build();

    new Thread(() -> {
      try {
        server.start();
        long pid = ProcessHandle.current().pid();
        System.out.println("as-driver started on pid: " + pid);
        System.out.println("as-driver server listening at: " + server.getPort());
      }
      catch (Throwable t) {
        t.printStackTrace();
      }
    }).start();
  }

  @Override
  public void getVersion(ASDriver.GetVersionRequest request, StreamObserver<ASDriver.GetVersionResponse> responseObserver) {
    ApplicationInfo info = ApplicationInfo.getInstance();
    String version = info.getFullApplicationName() + " @ " + info.getBuild();
    responseObserver.onNext(ASDriver.GetVersionResponse.newBuilder().setVersion(version).build());
    responseObserver.onCompleted();
  }

  @Override
  public void quit(ASDriver.QuitRequest request, StreamObserver<ASDriver.QuitResponse> responseObserver) {
    if (request.getForce()) {
      System.exit(0);
    }
    else {
      ((ApplicationEx)ApplicationManager.getApplication()).exit(true, true);
    }
  }

  @Override
  public void executeAction(ASDriver.ExecuteActionRequest request, StreamObserver<ASDriver.ExecuteActionResponse> responseObserver) {
    ASDriver.ExecuteActionResponse.Builder builder = ASDriver.ExecuteActionResponse.newBuilder();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      AnAction action = ActionManager.getInstance().getAction(request.getActionId());
      if (action == null) {
        builder.setResult(ASDriver.ExecuteActionResponse.Result.ACTION_NOT_FOUND);
        return;
      }

      String projectName = request.hasProjectName() ? request.getProjectName() : null;
      DataContext dataContext = getDataContext(projectName);
      if (dataContext == null) {
        System.err.println("Could not get a DataContext for executeAction.");
        builder.setResult(ASDriver.ExecuteActionResponse.Result.ERROR);
      }
      else {
        AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
        ActionUtil.performActionDumbAwareWithCallbacks(action, event);
        builder.setResult(ASDriver.ExecuteActionResponse.Result.OK);
      }
    });
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  /**
   * Gets a {@code DataContext} while taking into account that a project may be open.
   *
   * This is important for {@link AndroidStudioService#executeAction} in particular; actions that
   * are project-specific like MakeGradleProject will silently fail if the {@code DataContext} has
   * no associated project, which is possible in cases where Android Studio never got the focus.
   *
   * For more information, see b/238922776.
   * @param projectName optional project name.
   */
  private DataContext getDataContext(String projectName) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    int numProjects = projects.length;
    if (numProjects == 0) {
      if (projectName != null) {
        System.err.printf("No projects are open, but a request was received for \"%s\"%n", projectName);
        return null;
      }
      try {
        return DataManager.getInstance().getDataContextFromFocusAsync().blockingGet(30000);
      }
      catch (TimeoutException | ExecutionException e) {
        e.printStackTrace();
        return null;
      }
    }

    Project projectForContext;

    if (numProjects == 1) {
      projectForContext = projects[0];
      // If a projectName was specified, then make sure it matches the project we're about to use.
      if (projectName != null && !Objects.equal(projectForContext.getName(), projectName)) {
        System.err.printf("The only project open is one named \"%s\", but a project name of \"%s\" was requested%n",
                          projectForContext.getName(), projectName);
        return null;
      }
    }
    else {
      // There are two or more projects, so we have to use projectName to find the one to use.
      if (projectName == null) {
        System.err.printf("%d projects are open, but no project name was specified to be able to determine which to use%n", numProjects);
        return null;
      }

      projectForContext = findProjectByName(projectName);
    }

    // Attempting to create a DataContext via DataManager.getInstance.getDataContext(Component c)
    // causes all sorts of strange issues depending on which component is used. If it's a project,
    // then editor-specific actions like ToggleLineBreakpoint won't work. If it's an editor, then
    // the editor has to be showing or else performDumbAwareWithCallbacks will suppress the action.
    //
    // ...so instead, we create our own DataContext rather than getting one from a component.
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, projectForContext);
    dataContext.put(CommonDataKeys.EDITOR, FileEditorManager.getInstance(projectForContext).getSelectedTextEditor());
    return dataContext;
  }

  @Override
  public void showToolWindow(ASDriver.ShowToolWindowRequest request, StreamObserver<ASDriver.ShowToolWindowResponse> responseObserver) {
    ASDriver.ShowToolWindowResponse.Builder builder = ASDriver.ShowToolWindowResponse.newBuilder();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      boolean found = false;
      for (Project p : ProjectManager.getInstance().getOpenProjects()) {
        found = true;
        ToolWindow window = ToolWindowManagerEx.getInstanceEx(p).getToolWindow(request.getToolWindowId());
        if (window != null) {
          window.show();
          builder.setResult(ASDriver.ShowToolWindowResponse.Result.OK);
        } else {
          builder.setResult(ASDriver.ShowToolWindowResponse.Result.TOOL_WINDOW_NOT_FOUND);
        }
        break;
      }
      if (!found) {
        builder.setResult(ASDriver.ShowToolWindowResponse.Result.PROJECT_NOT_FOUND);
      }
    });
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void invokeComponent(ASDriver.InvokeComponentRequest request, StreamObserver<ASDriver.InvokeComponentResponse> responseObserver) {
    ASDriver.InvokeComponentResponse.Builder builder = ASDriver.InvokeComponentResponse.newBuilder();
    try {
      StudioInteractionService studioInteractionService = new StudioInteractionService();
      studioInteractionService.findAndInvokeComponent(request.getMatchersList());
      builder.setResult(ASDriver.InvokeComponentResponse.Result.OK);
    }
    catch (Exception e) {
      e.printStackTrace();
      builder.setResult(ASDriver.InvokeComponentResponse.Result.ERROR);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void waitForIndex(ASDriver.WaitForIndexRequest request, StreamObserver<ASDriver.WaitForIndexResponse> responseObserver) {
    try {
      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      CountDownLatch latch = new CountDownLatch(projects.length);
      for (Project p : projects) {
        DumbService.getInstance(p).smartInvokeLater(latch::countDown);
      }
      latch.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    responseObserver.onNext(ASDriver.WaitForIndexResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  private Project findProjectByName(String projectName) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    Optional<Project> foundProject = Arrays.stream(projects).filter((p) -> Objects.equal(p.getName(), projectName)).findFirst();
    if (foundProject.isEmpty()) {
      throw new NoSuchElementException("No project found by this name: " + projectName);
    }
    return foundProject.get();
  }

  @Override
  public void openFile(ASDriver.OpenFileRequest request, StreamObserver<ASDriver.OpenFileResponse> responseObserver) {
    ASDriver.OpenFileResponse.Builder builder = ASDriver.OpenFileResponse.newBuilder();
    builder.setResult(ASDriver.OpenFileResponse.Result.ERROR);
    String projectName = request.getProject();
    String fileName = request.getFile();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        Project project = findProjectByName(projectName);
        String basePath = project.getBasePath();
        if (basePath == null) {
          System.err.println("Project has a null base path: " + project);
          return;
        }

        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(fileName));
        if (virtualFile == null) {
          System.err.println("File does not exist on filesystem with path: " + fileName);
          return;
        }

        FileEditorManager manager = FileEditorManager.getInstance(project);
        manager.openTextEditor(new OpenFileDescriptor(project, virtualFile), true);
        goToLine(request.hasLine() ? request.getLine() : null, request.hasColumn() ? request.getColumn() : null);

        builder.setResult(ASDriver.OpenFileResponse.Result.OK);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    });

    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void waitForComponent(ASDriver.WaitForComponentRequest request, StreamObserver<ASDriver.WaitForComponentResponse> responseObserver) {
    ASDriver.WaitForComponentResponse.Builder builder = ASDriver.WaitForComponentResponse.newBuilder();
    try {
      StudioInteractionService studioInteractionService = new StudioInteractionService();
      studioInteractionService.waitForComponent(request.getMatchersList());
      builder.setResult(ASDriver.WaitForComponentResponse.Result.OK);
    }
    catch (Exception e) {
      e.printStackTrace();
      builder.setResult(ASDriver.WaitForComponentResponse.Result.ERROR);
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  /**
   * @param line 0-indexed line number. If null, use the current line. This default (and the
   *             column's default) match {@link com.intellij.ide.util.GotoLineNumberDialog}'s
   *             defaults.
   * @param column 0-indexed column number. If null, use column 0.
   */
  private void goToLine(Integer line, Integer column) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length != 1) {
          System.err.format("Expected exactly one open project, but found %d. If you have a valid test case where >1 project is expected " +
                            "to be open, then this framework can be changed to allow for project selection.%n", projects.length);
          return;
        }
        Project firstProject = projects[0];
        Editor editor = FileEditorManager.getInstance(firstProject).getSelectedTextEditor();
        CaretModel caretModel = editor.getCaretModel();
        int lineToUse = line == null ? caretModel.getLogicalPosition().line : line;
        int columnToUse = column == null ? 0 : column;

        LogicalPosition position = new LogicalPosition(lineToUse, columnToUse);
        caretModel.removeSecondaryCarets();
        caretModel.moveToLogicalPosition(position);
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
        editor.getSelectionModel().removeSelection();
        IdeFocusManager.getGlobalInstance().requestFocus(editor.getContentComponent(), true);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }
}
