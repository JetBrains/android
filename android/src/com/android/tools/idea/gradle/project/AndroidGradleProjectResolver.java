/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.builder.AndroidBuilder;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ProductFlavor;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.GradleImportNotificationListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.KeyValue;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.List;

/**
 * Imports Android-Gradle projects into IDEA.
 */
public class AndroidGradleProjectResolver implements GradleProjectResolverExtension {
  private static final Logger LOG = Logger.getInstance(AndroidGradleProjectResolver.class);

  @NotNull private final GradleExecutionHelper myHelper;
  @NotNull private final ProjectImportErrorHandler myErrorHandler;
  @NotNull private final ProjectResolverFunctionFactory myFunctionFactory;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'projectResolve'.
  @SuppressWarnings("UnusedDeclaration")
  public AndroidGradleProjectResolver() {
    myHelper = new GradleExecutionHelper();
    myErrorHandler = new ProjectImportErrorHandler();
    myFunctionFactory = new ProjectResolverFunctionFactory(new ProjectResolver(myHelper, myErrorHandler));
  }

  @VisibleForTesting
  AndroidGradleProjectResolver(@NotNull GradleExecutionHelper helper,
                               @NotNull ProjectResolverFunctionFactory functionFactory,
                               @NotNull ProjectImportErrorHandler errorHandler) {
    myHelper = helper;
    myFunctionFactory = functionFactory;
    myErrorHandler = errorHandler;
  }

  /**
   * Imports an Android-Gradle project into IDEA.
   *
   * </p>Two types of projects are supported:
   * <ol>
   *   <li>A single {@link AndroidProject}</li>
   *   <li>A multi-project has at least one {@link AndroidProject} child</li>
   * </ol>
   *
   * @param id                id of the current 'resolve project info' task.
   * @param projectPath       absolute path of the parent folder of the build.gradle file.
   * @param downloadLibraries a hint that specifies if third-party libraries that are not available locally should be resolved (downloaded.)
   * @param settings          settings to use for the project resolving; {@code null} as indication that no specific settings are required.
   * @param listener          callback to be notified about the execution
   * @return the imported project, or {@code null} if the project to import is not supported.
   */
  @Nullable
  @Override
  public DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                                  @NotNull String projectPath,
                                                  boolean downloadLibraries,
                                                  @Nullable GradleExecutionSettings settings,
                                                  @NotNull ExternalSystemTaskNotificationListener listener) {
    Function<ProjectConnection, DataNode<ProjectData>> function =
      myFunctionFactory.createFunction(id, projectPath, myErrorHandler, listener, settings);
    return myHelper.execute(projectPath, settings, function);
  }

  /**
   * <ol>
   * <li>Adds the paths of the 'android' module and jar files of the Android-Gradle project to the classpath of the slave process that
   * performs the Gradle project import.</li>
   * <li>Sets the value of the environment variable "ANDROID_HOME" with the path of the first found Android SDK, if the environment
   * variable has not been set.</li>
   * </ol>
   *
   * @param parameters parameters to be applied to the slave process which will be used for external system communication.
   */
  @Override
  public void enhanceParameters(@NotNull SimpleJavaParameters parameters) {
    GradleImportNotificationListener.attachToManager();
    List<String> jarPaths = getJarPathsOf(getClass(), AndroidBuilder.class, AndroidProject.class, BaseTask.class, ProductFlavor.class,
                                          FullRevision.class);
    LOG.info("Added to RMI/Gradle process classpath: " + jarPaths);
    for (String jarPath : jarPaths) {
      parameters.getClassPath().add(jarPath);
    }
    String androidHome = System.getenv(AndroidSdkUtils.ANDROID_HOME_ENV);
    if (Strings.isNullOrEmpty(androidHome)) {
      for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
        AndroidPlatform androidPlatform = AndroidPlatform.parse(sdk);
        String sdkHomePath = sdk.getHomePath();
        if (androidPlatform != null && sdkHomePath != null) {
          parameters.addEnv(AndroidSdkUtils.ANDROID_HOME_ENV, sdkHomePath);
          break;
        }
      }
    }
    List<KeyValue<String,String>> proxyProperties = HttpConfigurable.getJvmPropertiesList(false, null);
    ParametersList vmParameters = parameters.getVMParametersList();
    for (KeyValue<String, String> proxyProperty : proxyProperties) {
      vmParameters.defineProperty(proxyProperty.getKey(), proxyProperty.getValue());
    }
  }

  @NotNull
  private static List<String> getJarPathsOf(@NotNull Class<?>... types) {
    List<String> jarPaths = Lists.newArrayList();
    for (Class<?> type : types) {
      ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(type), jarPaths);
    }
    return jarPaths;
  }

  static class ProjectResolverFunctionFactory {
    @NotNull private final ProjectResolver myResolver;

    ProjectResolverFunctionFactory(@NotNull ProjectResolver resolver) {
      myResolver = resolver;
    }

    @NotNull
    Function<ProjectConnection, DataNode<ProjectData>> createFunction(@NotNull final ExternalSystemTaskId id,
                                                                      @NotNull final String projectPath,
                                                                      @NotNull final ProjectImportErrorHandler errorHandler,
                                                                      @NotNull final ExternalSystemTaskNotificationListener listener,
                                                                      @Nullable final GradleExecutionSettings settings) {
      return new Function<ProjectConnection, DataNode<ProjectData>>() {
        @Nullable
        @Override
        public DataNode<ProjectData> fun(ProjectConnection connection) {
          try {
            return myResolver.resolveProjectInfo(id, projectPath, settings, connection, listener);
          }
          catch (RuntimeException e) {
            throw errorHandler.getUserFriendlyError(e, null);
          }
        }
      };
    }
  }
}
