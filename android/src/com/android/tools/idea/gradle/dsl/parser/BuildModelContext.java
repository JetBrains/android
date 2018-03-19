/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFileCache;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A context object used to hold information relevant to each unique instance of the project/build model.
 * This means there is one {@link BuildModelContext} for each call to the following methods,
 * {@link GradleBuildModel#parseBuildFile(VirtualFile, Project)}, {@link GradleBuildModel#get(Module)}
 * and {@link ProjectBuildModel#get(Project)}. This can be accessed from each of the {@link GradleDslFile}s.
 */
public final class BuildModelContext {
  @NotNull
  private final Project myProject;
  @NotNull
  private final GradleDslFileCache myFileCache;
  @NotNull
  private final ClassToInstanceMap<BuildModelNotification> myNotifications = MutableClassToInstanceMap.create();
  @NotNull
  private final DependencyManager myDependencyManager;

  @NotNull
  public static BuildModelContext create(@NotNull Project project) {
    return new BuildModelContext(project);
  }

  private BuildModelContext(@NotNull Project project) {
    myProject = project;
    myFileCache = new GradleDslFileCache(project);
    myDependencyManager = DependencyManager.create();
  }

  @NotNull
  public DependencyManager getDependencyManager() {
    return myDependencyManager;
  }

  @NotNull
  public List<BuildModelNotification> getPublicNotifications() {
    return new ArrayList<>(myNotifications.values());
  }

  @NotNull
  public <T extends BuildModelNotification> T getNotificationForType(@NotNull NotificationTypeReference<T> type) {
    if (myNotifications.containsKey(type.getClazz())) {
      return myNotifications.getInstance(type.getClazz());
    } else {
      T notification = type.getConstructor().produce();
      myNotifications.putInstance(type.getClazz(), notification);
      return notification;
    }
  }

  /**
   * Resets the state of the build context.
   */
  public void reset() {
    myFileCache.clearAllFiles();
  }

  /* The following methods are just wrappers around the same methods in GradleDslFileCache but pass this build
   * context along as well. */
  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file, @NotNull String name) {
    return myFileCache.getOrCreateBuildFile(file, name, this);
  }

  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file) {
    return getOrCreateBuildFile(file, file.getName());
  }

  @Nullable
  public GradleSettingsFile getSettingsFile(@NotNull Project project) {
    return myFileCache.getSettingsFile(project);
  }

  @Nullable
  public GradleSettingsFile getOrCreateSettingsFile(@NotNull Project project) {
    return myFileCache.getOrCreateSettingsFile(project, this);
  }
}
