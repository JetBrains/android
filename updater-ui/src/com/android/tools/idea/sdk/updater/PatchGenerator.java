/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.sdk.updater;

import com.android.repository.api.ProgressIndicator;
import com.google.common.collect.ImmutableMap;
import com.intellij.updater.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utilities for generating "patches" that install or uninstall complete packages (that is, not diffs).
 *
 * Currently this is a naive implementation that just diffs two packages and generates the diff package, with all files marked
 * as critical (so no binary diffs are done). A more sophisticated implementation could modify the complete package zip in-place, to be
 * updater-compatible, but this would require significant refactoring of the updater framework, or significant code duplication.
 */
@SuppressWarnings("unused")  // Invoked by reflection
public class PatchGenerator {

  /**
   * Read a zip containing a complete sdk package and generate an equivalent patch that includes the complete content of the package.
   */
  public static boolean generateFullPackage(@NotNull File srcRoot,
                                            @Nullable final File existingRoot,
                                            @NotNull File outputJar,
                                            @NotNull String oldDescription,
                                            @NotNull String description,
                                            @NotNull ProgressIndicator progress) {
    Digester digester = new Digester("md5");

    Runner.initLogger();
    progress.logInfo("Generating patch...");
    final Set<String> srcFiles = new HashSet<>();
    try {
      Files.walkFileTree(srcRoot.toPath(), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          String relativePath = srcRoot.toPath().relativize(file).toString();
          srcFiles.add(relativePath.replace(srcRoot.separatorChar, '/'));
          return FileVisitResult.CONTINUE;
        }
      });
    }
    catch (IOException e) {
      progress.logWarning("Failed to read unzipped files!", e);
      return false;
    }
    final List<String> deleteFiles = new ArrayList<>();
    if (existingRoot != null) {
      try {
        Files.walkFileTree(existingRoot.toPath(), new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String relativePath = existingRoot.toPath().relativize(file).toString();
            String path = relativePath.replace(srcRoot.separatorChar, '/');
            if (!srcFiles.contains(path)) {
              deleteFiles.add(path);
            }
            return FileVisitResult.CONTINUE;
          }
        });
      }
      catch (IOException e) {
        progress.logWarning("Failed to read existing files!", e);
        return false;
      }
    }

    PatchSpec spec = new PatchSpec()
      .setOldVersionDescription(oldDescription)
      .setNewVersionDescription(description)
      .setRoot("")
      .setBinary(true)
      .setOldFolder(existingRoot == null ? "" : existingRoot.getAbsolutePath())
      .setNewFolder(srcRoot.getAbsolutePath())
      .setStrict(true)
      .setCriticalFiles(new ArrayList<>(srcFiles))
      .setDeleteFiles(deleteFiles)
      .setHashAlgorithm("md5");
    ProgressUI ui = new ProgressUI(progress);
    File patchZip = new File(outputJar.getParent(), "patch-file.zip");
    try {
      Patch patchInfo = new Patch(spec, ui);
      if (!patchZip.getParentFile().exists()) {
        patchZip.getParentFile().mkdirs();
      }
      patchZip.createNewFile();
      PatchFileCreator.create(spec, patchZip, ui);

      // The expected format is for the patch to be inside the package zip.
      try (FileSystem destFs =
             FileSystems.newFileSystem(URI.create("jar:" + outputJar.toURI()), ImmutableMap.of("create", "true"));
           InputStream is = new BufferedInputStream(new FileInputStream(patchZip))) {
        Files.copy(is, destFs.getPath("patch-file.zip"));
      }
    }
    catch (IOException|OperationCancelledException e) {
      progress.logWarning("Failed to create patch", e);
      return false;
    }

    return true;
  }

  private static class ZipBasedUpdateAction extends UpdateAction {

    private final ZipEntry myEntry;
    private final ZipFile mySrc;

    public ZipBasedUpdateAction(@NotNull ZipFile src,
                                @NotNull ZipEntry entry,
                                @NotNull Patch patch,
                                long existingChecksum) {
      super(patch, entry.getName(), existingChecksum);
      myEntry = entry;
      mySrc = src;
    }

    @Override
    protected void writeDiff(File ignored, File newerFile, OutputStream patchOutput) throws IOException {
      patchOutput.write(DiffAlgorithm.determineDiffAlgorithm(null, true, 0).getId());
      Utils.copyStream(mySrc.getInputStream(myEntry), patchOutput);
    }
  }

  /**
   * {@link UpdaterUI} implementation that just wraps a {@link ProgressIndicator}.
   */
  private static class ProgressUI implements UpdaterUI {
    private final ProgressIndicator myProgressIndicator;

    private ProgressUI(@NotNull ProgressIndicator progressIndicator) {
      myProgressIndicator = progressIndicator;
    }

    @Override
    public void startProcess(String title) {
      // Nothing
    }

    @Override
    public void setProgress(int percentage) {
      myProgressIndicator.setIndeterminate(false);
      myProgressIndicator.setFraction((double)percentage/100);
    }

    @Override
    public void setProgressIndeterminate() {
      myProgressIndicator.setIndeterminate(true);
    }

    @Override
    public void setStatus(@Nullable String status) {
      myProgressIndicator.setSecondaryText(status);
    }

    @Override
    public void showError(@Nullable Throwable e) {
      myProgressIndicator.logWarning("Error", e);
    }

    @Override
    public void checkCancelled() throws OperationCancelledException {
      if (myProgressIndicator.isCanceled()) {
        throw new OperationCancelledException();
      }
    }

    @Override
    public void setDescription(String oldBuildDesc, String newBuildDesc) {
      // unused
    }

    @Override
    public boolean showWarning(String message) {
      // unused
      return false;
    }

    @Override
    public Map<String, ValidationResult.Option> askUser(List<ValidationResult> validationResults) throws OperationCancelledException {
      // unused
      return null;
    }
  }
}
