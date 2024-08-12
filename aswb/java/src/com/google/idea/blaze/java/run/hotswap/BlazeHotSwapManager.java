/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.hotswap;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.HotSwapFile;
import com.intellij.debugger.impl.HotSwapManager;
import com.intellij.debugger.impl.HotSwapProgress;
import com.intellij.debugger.ui.HotSwapProgressImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.MessageCategory;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import javax.annotation.Nullable;

/** Manages hotswapping for blaze java_binary run configurations. */
public final class BlazeHotSwapManager {

  private static final Logger logger = Logger.getInstance(BlazeHotSwapManager.class);

  static void reloadChangedClasses(Project project) {
    HotSwappableDebugSession session = findHotSwappableBlazeDebuggerSession(project);
    if (session == null) {
      return;
    }
    HotSwapProgressImpl progress = new HotSwapProgressImpl(project);
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              progress.setSessionForActions(session.session);
              ProgressManager.getInstance()
                  .runProcess(
                      () -> doReloadClasses(session, progress), progress.getProgressIndicator());
            });
  }

  private static void doReloadClasses(HotSwappableDebugSession session, HotSwapProgress progress) {
    try {
      progress.setDebuggerSession(session.session);
      progress.setText("Building .class file manifest");
      ClassFileManifest.Diff manifestDiff =
          ClassFileManifestBuilder.buildManifest(session.env, progress);
      if (manifestDiff == null) {
        progress.addMessage(
            session.session, MessageCategory.ERROR, "Modified classes could not be determined.");
        return;
      }
      Map<String, File> localFiles = copyClassFilesLocally(manifestDiff);
      Map<String, HotSwapFile> files =
          localFiles
              .entrySet()
              .stream()
              .collect(toImmutableMap(Entry::getKey, e -> new HotSwapFile(e.getValue())));
      if (!files.isEmpty()) {
        progress.setText(String.format("HotSwapping %s .class file(s)", files.size()));
      }
      try {
        HotSwapManager.reloadModifiedClasses(ImmutableMap.of(session.session, files), progress);
      } finally {
        localFiles.values().forEach(File::delete);
      }

    } catch (Throwable e) {
      processException(e, session.session, progress);
      if (e.getMessage() != null) {
        progress.addMessage(session.session, MessageCategory.ERROR, e.getMessage());
      }
    } finally {
      progress.finished();
    }
  }

  /**
   * Given a per-jar map of .class files, extracts them locally and returns a map from qualified
   * class name to File, suitable for {@link HotSwapManager}.
   *
   * @throws ExecutionException if operation failed
   */
  private static Map<String, File> copyClassFilesLocally(ClassFileManifest.Diff manifestDiff)
      throws ExecutionException {
    File tempDir = new File(System.getProperty("java.io.tmpdir"));
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    File localDir = new File(tempDir, "class_files_" + suffix);
    if (!localDir.mkdir()) {
      throw new ExecutionException(
          String.format("Cannot create temp output directory '%s'", localDir.getPath()));
    }
    localDir.deleteOnExit();

    Map<String, File> map = new HashMap<>();
    for (File jar : manifestDiff.perJarModifiedClasses.keySet()) {
      Collection<String> classes = manifestDiff.perJarModifiedClasses.get(jar);
      map.putAll(copyClassFilesLocally(localDir, jar, classes));
    }
    return ImmutableMap.copyOf(map);
  }

  private static Map<String, File> copyClassFilesLocally(
      File destination, File jar, Collection<String> classes) throws ExecutionException {
    ImmutableMap.Builder<String, File> map = ImmutableMap.builder();
    try {
      JarFile jarFile = new JarFile(jar);
      for (String path : classes) {
        ZipEntry entry = jarFile.getJarEntry(path);
        if (entry == null) {
          throw new ExecutionException(
              String.format("Couldn't find class file %s inside jar %s.", path, jar));
        }
        File f = new File(destination, path.replace('/', '-'));
        f.deleteOnExit();
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
          Files.copy(inputStream, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        map.put(deriveQualifiedClassName(path), f);
      }
    } catch (IOException | IllegalStateException e) {
      throw new ExecutionException("Error reading runtime jars", e);
    }
    return map.build();
  }

  /** Derive the fully-qualified class name from the path inside the jar. */
  private static String deriveQualifiedClassName(String path) {
    return path.substring(0, path.length() - ".class".length()).replace('/', '.');
  }

  @Nullable
  static HotSwappableDebugSession findHotSwappableBlazeDebuggerSession(Project project) {
    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    DebuggerSession session = debuggerManager.getContext().getDebuggerSession();
    if (session == null || !session.isAttached()) {
      return null;
    }
    JavaDebugProcess process = session.getProcess().getXdebugProcess();
    if (process == null) {
      return null;
    }
    ExecutionEnvironment env = ((XDebugSessionImpl) process.getSession()).getExecutionEnvironment();
    if (env == null || ClassFileManifestBuilder.getManifest(env) == null) {
      return null;
    }
    RunProfile runProfile = env.getRunProfile();
    if (!(runProfile instanceof BlazeCommandRunConfiguration)) {
      return null;
    }
    return new HotSwappableDebugSession(session, env, (BlazeCommandRunConfiguration) runProfile);
  }

  private static class HotSwappableDebugSession {
    final DebuggerSession session;
    final ExecutionEnvironment env;
    final BlazeCommandRunConfiguration blazeRunConfig;

    HotSwappableDebugSession(
        DebuggerSession session,
        ExecutionEnvironment env,
        BlazeCommandRunConfiguration blazeRunConfig) {
      this.session = session;
      this.env = env;
      this.blazeRunConfig = blazeRunConfig;
    }
  }

  private static void processException(
      Throwable e, DebuggerSession session, HotSwapProgress progress) {
    if (e.getMessage() != null) {
      progress.addMessage(session, MessageCategory.ERROR, e.getMessage());
    }
    if (e instanceof ProcessCanceledException) {
      progress.addMessage(
          session, MessageCategory.INFORMATION, DebuggerBundle.message("error.operation.canceled"));
    } else {
      logger.warn(e);
      progress.addMessage(session, MessageCategory.ERROR, "Error reloading classes");
    }
  }
}
