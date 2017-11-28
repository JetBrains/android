package com.android.tools.idea.gradle.project.build;

import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.task.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA;
import static com.android.tools.idea.gradle.util.BuildMode.REBUILD;

/**
 * @author Vladislav.Soroka
 */
public class AndroidProjectTaskRunner extends ProjectTaskRunner {
  @Override
  public void run(@NotNull Project project,
                  @NotNull ProjectTaskContext context,
                  @Nullable ProjectTaskNotification callback,
                  @NotNull Collection<? extends ProjectTask> tasks) {
    Map<Boolean, List<ModuleBuildTask>> moduleBuildTasksMap = StreamEx.of(tasks)
      .select(ModuleBuildTask.class)
      .partitioningBy(task -> task.isIncrementalBuild());

    ProjectTaskNotification aggregatedCallback = callback == null ? null : new MergedProjectTaskNotification(callback, 2);

    executeTasks(project, REBUILD, moduleBuildTasksMap.get(Boolean.FALSE), aggregatedCallback);
    executeTasks(project, COMPILE_JAVA, moduleBuildTasksMap.get(Boolean.TRUE), aggregatedCallback);
  }

  @Override
  public boolean canRun(@NotNull ProjectTask projectTask) {
    if (projectTask instanceof ModuleBuildTask) {
      Module module = ((ModuleBuildTask)projectTask).getModule();
      if (GradleFacet.getInstance(module) != null || JavaFacet.getInstance(module) != null/* || AndroidModuleModel.get(module) != null*/) {
        return true;
      }
    }
    return false;
  }

  private void executeTasks(@NotNull Project project,
                            @NotNull BuildMode buildMode,
                            List<ModuleBuildTask> moduleBuildTasks,
                            @Nullable ProjectTaskNotification callback) {
    Module[] modules = moduleBuildTasks.stream().map(task -> task.getModule()).toArray(Module[]::new);
    if (modules == null || modules.length == 0) {
      // nothing to build
      if (callback != null) {
        callback.finished(new ProjectTaskResult(false, 0, 0));
      }
      return;
    }

    String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(modules[0]);
    if (rootProjectPath == null) {
      if (callback != null) {
        callback.finished(new ProjectTaskResult(false, 1, 0));
      }
      return;
    }
    File projectFile = new File(rootProjectPath);
    final String projectName;
    if (projectFile.isFile()) {
      projectName = projectFile.getParentFile().getName();
    }
    else {
      projectName = projectFile.getName();
    }
    String executionName = "Build " + projectName;
    ListMultimap<Path, String> tasks = GradleBuildInvoker.findTasksToExecute(modules, buildMode, GradleBuildInvoker.TestCompileType.NONE);

    GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);

    Set<Path> rootPaths = tasks.keys().elementSet();
    ProjectTaskNotification aggregatedCallback = callback == null ? null : new MergedProjectTaskNotification(callback, rootPaths.size());

    for (Path projectRootPath : rootPaths) {
      GradleBuildInvoker.Request request = new GradleBuildInvoker.Request(project, projectRootPath.toFile(), tasks.get(projectRootPath));

      BuildSettings.getInstance(project).setBuildMode(buildMode);
      // the blocking mode required because of static behaviour of the BuildSettings.setBuildMode() method
      request.setWaitForCompletion(true);

      ExternalSystemTaskNotificationListener buildTaskListener = gradleBuildInvoker.createBuildTaskListener(request, executionName);
      ExternalSystemTaskNotificationListener listenerDelegate =
        aggregatedCallback == null ? buildTaskListener : new ExternalSystemTaskNotificationListenerAdapter(buildTaskListener) {
          @Override
          public void onSuccess(@NotNull ExternalSystemTaskId id) {
            super.onSuccess(id);
            aggregatedCallback.finished(new ProjectTaskResult(false, 0, 0));
          }

          @Override
          public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
            super.onFailure(id, e);
            aggregatedCallback.finished(new ProjectTaskResult(false, 1, 0));
          }
        };

      request.setTaskListener(listenerDelegate);
      gradleBuildInvoker.executeTasks(request);
    }
  }

  private static class MergedProjectTaskNotification implements ProjectTaskNotification {
    private final ProjectTaskNotification myCallback;
    private final AtomicInteger myResultsCounter = new AtomicInteger(0);
    private final int myExpectedResults;
    private boolean myAborted;
    private int myErrors;
    private int myWarnings;

    public MergedProjectTaskNotification(ProjectTaskNotification callback, int expectedResults) {
      myCallback = callback;
      myExpectedResults = expectedResults;
    }

    @Override
    public void finished(@NotNull ProjectTaskResult executionResult) {
      int finished = myResultsCounter.incrementAndGet();
      if (executionResult.isAborted()) {
        myAborted = true;
      }
      myErrors += executionResult.getErrors();
      myWarnings += executionResult.getWarnings();

      if (finished == myExpectedResults) {
        myCallback.finished(new ProjectTaskResult(myAborted, myErrors, myWarnings));
      }
    }
  }
}
