package com.android.tools.idea.gradle.task;

import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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
    ModuleManager moduleManager = mock(ModuleManager.class);
    when(project.getComponent(ModuleManager.class)).thenReturn(moduleManager);
    Module module = mock(Module.class);
    when(moduleManager.getModules()).thenReturn(new Module[]{module});
    when(module.getPicoContainer()).thenReturn(picoContainer);
    when(module.getProject()).thenReturn(project);
    FacetManager facetManager = mock(FacetManager.class);
    when(facetManager.getFacetByType(GradleFacet.getFacetTypeId())).thenReturn(mock(GradleFacet.class));
    when(module.getComponent(FacetManager.class)).thenReturn(facetManager);
    ExternalSystemModulePropertyManager modulePropertyManager = new ExternalSystemModulePropertyManager(module);
    when(picoContainer.getComponentInstance(ExternalSystemModulePropertyManager.class.getName())).thenReturn(modulePropertyManager);
    String projectPath = "projectPath";
    modulePropertyManager.setExternalOptions(GradleUtil.GRADLE_SYSTEM_ID, new ModuleData("", GradleUtil.GRADLE_SYSTEM_ID,
                                                                                         "", "", "", projectPath), null);
    when(module.getOptionValue("external.linked.project.path")).thenReturn(projectPath);
    List<String> taskNames = ContainerUtil.list("fooTask");
    ExternalSystemTaskNotificationListenerAdapter listener = new ExternalSystemTaskNotificationListenerAdapter() {};

    new AndroidGradleTaskManager().executeTasks(taskId, taskNames, projectPath, null, null, listener);

    verify(gradleBuildInvoker).executeTasks(new GradleBuildInvoker.Request(project, new File(projectPath), taskNames, taskId)
                                              .setJvmArguments(new ArrayList<>())
                                              .setCommandLineArguments(new ArrayList<>())
                                              .setTaskListener(listener)
                                              .waitForCompletion());
  }
}