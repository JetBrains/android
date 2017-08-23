package com.android.tools.idea.gradle.task;

import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Test;
import org.picocontainer.PicoContainer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author Vladislav.Soroka
 */
public class AndroidGradleTaskManagerTest {

  @Test
  public void executeTasks() {
    ExternalSystemTaskId taskId = mock(ExternalSystemTaskId.class);
    Project project = mock(Project.class);
    PicoContainer picoContainer = mock(PicoContainer.class);
    AndroidProjectInfo androidProjectInfo = mock(AndroidProjectInfo.class);
    GradleBuildInvoker gradleBuildInvoker = mock(GradleBuildInvoker.class);

    when(taskId.findProject()).thenReturn(project);
    when(project.getPicoContainer()).thenReturn(picoContainer);
    when(picoContainer.getComponentInstance(AndroidProjectInfo.class.getName())).thenReturn(androidProjectInfo);
    when(androidProjectInfo.requiresAndroidModel()).thenReturn(true);
    AndroidGradleBuildConfiguration androidGradleBuildConfiguration = new AndroidGradleBuildConfiguration();
    androidGradleBuildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = true;
    when(picoContainer.getComponentInstance(AndroidGradleBuildConfiguration.class.getName())).thenReturn(androidGradleBuildConfiguration);
    when(picoContainer.getComponentInstance(GradleBuildInvoker.class.getName())).thenReturn(gradleBuildInvoker);
    when(gradleBuildInvoker.getProject()).thenReturn(project);

    List<String> taskNames = ContainerUtil.list("fooTask");
    String projectPath = "projectPath";
    ExternalSystemTaskNotificationListenerAdapter listener = new ExternalSystemTaskNotificationListenerAdapter() {};

    new AndroidGradleTaskManager().executeTasks(taskId, taskNames, projectPath, null, null, listener);

    verify(gradleBuildInvoker).executeTasks(new GradleBuildInvoker.Request(project, taskNames, taskId)
                                              .setBuildFilePath(new File(projectPath))
                                              .setJvmArguments(new ArrayList<>())
                                              .setCommandLineArguments(new ArrayList<>())
                                              .setTaskListener(listener)
                                              .setWaitForCompletion(true));
  }
}