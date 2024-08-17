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
package com.google.idea.common.experiments;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Reads experiments from a property file. */
class FileExperimentLoader implements ExperimentLoader {

  private static final Logger logger = Logger.getInstance(FileExperimentLoader.class);

  private final File file;

  private volatile ImmutableMap<String, String> experiments = ImmutableMap.of();

  FileExperimentLoader(String filename) {
    this.file = new File(filename);
  }

  @Override
  public ImmutableMap<String, String> getExperiments() {
    return experiments;
  }

  @Override
  public String getId() {
    return this.file.toString();
  }

  @SuppressWarnings("unchecked") // Properties is Map<Object, Object>, we cast to strings
  private void reloadExperiments() {
    if (!file.exists()) {
      experiments = ImmutableMap.of();
      return;
    }
    logger.info("loading experiments file " + file);

    try (InputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis)) {
      Properties properties = new Properties();
      properties.load(bis);
      experiments = ImmutableMap.copyOf((Map) properties);
    } catch (IOException e) {
      logger.warn("Could not load experiments from file: " + file, e);
    }
  }

  @Override
  public void initialize() {
    // first read the file synchronously -- experiment checks on startup require this
    reloadExperiments();
    // set up VFS listener asynchronously, to prevent cyclic dependencies if initialization involves
    // experiment checks (b/118813939)
    ApplicationManager.getApplication().executeOnPooledThread(this::doInitialize);
  }

  private void doInitialize() {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    fileSystem.addRootToWatch(file.getPath(), /* watchRecursively= */ false);
    // We need to look up the file in the VFS or else we don't receive events about it. This works
    // even if the returned VirtualFile is null (because the experiments file doesn't exist yet).
    fileSystem.findFileByIoFile(file);
    ApplicationManager.getApplication()
        .getMessageBus()
        .connect()
        .subscribe(VirtualFileManager.VFS_CHANGES, new RefreshExperimentsListener());
  }

  private class RefreshExperimentsListener implements BulkFileListener {

    @Override
    public void after(List<? extends VFileEvent> events) {
      if (events.stream().anyMatch(this::isExperimentsFile)) {
        logger.info("Scheduling experiments file refresh on " + file);
        ApplicationManager.getApplication()
            .executeOnPooledThread(
                () -> {
                  reloadExperiments();
                  ExperimentService.getInstance().notifyExperimentsChanged();
                });
      }
    }

    private boolean isExperimentsFile(VFileEvent event) {
      return event.getFile() != null && event.getFile().getPath().equals(file.getPath());
    }
  }
}
