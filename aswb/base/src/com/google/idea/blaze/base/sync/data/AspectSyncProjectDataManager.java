/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.data;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.AspectSyncProjectData;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.common.util.ConcurrencyUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/** Stores a cache of blaze project data and issues any side effects when that data is updated. */
public class AspectSyncProjectDataManager implements BlazeProjectDataManager {

  private static final Logger logger =
      Logger.getInstance(AspectSyncProjectDataManager.class.getName());

  private final Project project;
  // a per-project single-threaded executor to write project data to disk
  private final ListeningExecutorService writeDataExecutor;

  @Nullable private volatile AspectSyncProjectData projectData;

  public AspectSyncProjectDataManager(Project project) {
    this.project = project;
    writeDataExecutor =
        MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor(
                ConcurrencyUtil.namedDaemonThreadPoolFactory(AspectSyncProjectDataManager.class)));
    Disposer.register(project, writeDataExecutor::shutdown);
  }

  @Nullable
  @Override
  public synchronized BlazeProjectData loadProject(BlazeImportSettings importSettings) {
    if (projectData != null) {
      return projectData;
    }
    try {
      File file = getCacheFile(project, importSettings);
      projectData = AspectSyncProjectData.loadFromDisk(importSettings.getBuildSystem(), file);
      return projectData;
    } catch (Throwable e) {
      if (!(e instanceof FileNotFoundException)) {
        logger.warn(e);
      }
      return null;
    }
  }

  @Override
  @Nullable
  public BlazeProjectData getBlazeProjectData() {
    return projectData;
  }

  @Override
  public void saveProject(
      final BlazeImportSettings importSettings, final BlazeProjectData projectData) {
    this.projectData = (AspectSyncProjectData) projectData;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    Future<?> unused =
        ProgressiveTaskWithProgressIndicator.builder(project, "Saving sync data...")
            .setExecutor(writeDataExecutor)
            .submitTask(
                (ProgressIndicator indicator) -> {
                  try {
                    File file = getCacheFile(project, importSettings);
                    if (!file.getParentFile().exists()) {
                      file.getParentFile().mkdirs();
                    }
                    synchronized (this) {
                      this.projectData.saveToDisk(file);
                    }
                    logFileSize(projectData, file);

                  } catch (Throwable e) {
                    logger.error(serializationErrorMessage(e), e);
                  }
                });
  }

  private static void logFileSize(BlazeProjectData projectData, File cacheFile) {
    ImmutableMap.Builder<String, String> data = ImmutableMap.builder();
    data.put("size", Long.toString(FileOperationProvider.getInstance().getFileSize(cacheFile)));
    Long clientCl = projectData.getBlazeVersionData().clientCl;
    if (clientCl != null) {
      data.put("cl", Long.toString(clientCl));
    }
    EventLoggingService.getInstance()
        .logEvent(AspectSyncProjectDataManager.class, "ProjectDataSerialized", data.build());
  }

  private static String serializationErrorMessage(Throwable e) {
    String message = "Could not save cache data file to disk.";
    if (!(e instanceof IOException)) {
      return message;
    }
    return message + " Please resync project.";
  }

  private static File getCacheFile(Project project, BlazeImportSettings importSettings) {
    return new File(BlazeDataStorage.getProjectCacheDir(project, importSettings), "cache.dat.gz");
  }
}
