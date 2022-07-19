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
import com.google.common.base.Objects;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

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
      if (action != null) {
        DataContext dataContext = DataManager.getInstance().getDataContext();
        AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
        ActionUtil.performActionDumbAwareWithCallbacks(action, event);
        builder.setResult(ASDriver.ExecuteActionResponse.Result.OK);
      } else {
        builder.setResult(ASDriver.ExecuteActionResponse.Result.ACTION_NOT_FOUND);
      }
    });
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
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

  @Override
  public void openFile(ASDriver.OpenFileRequest request, StreamObserver<ASDriver.OpenFileResponse> responseObserver) {
    ASDriver.OpenFileResponse.Builder builder = ASDriver.OpenFileResponse.newBuilder();
    builder.setResult(ASDriver.OpenFileResponse.Result.ERROR);
    String projectName = request.getProject();
    String fileName = request.getFile();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        Optional<Project> foundProject = Arrays.stream(projects).filter((p) -> Objects.equal(p.getName(), projectName)).findFirst();
        if (foundProject.isEmpty()) {
          System.err.println("No project found by this name: " + projectName);
          return;
        }

        Project project = foundProject.get();
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
}
