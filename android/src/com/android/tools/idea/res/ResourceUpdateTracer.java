/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.res;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.Math.max;

import com.android.ide.common.util.PathString;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.util.FileExtensions;
import com.android.utils.FlightRecorder;
import com.android.utils.TraceUtils;
import com.google.common.base.Joiner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to investigate b/167583128.
 */
public final class ResourceUpdateTracer {
  private static boolean enabled;
  private static final Logger LOG = Logger.getInstance(ResourceUpdateTracer.class);

  static {
    if (ResourceUpdateTraceSettings.getInstance().getEnabled()) {
      startTracing();
    }
  }

  static void startTracing() {
    FlightRecorder.initialize(StudioFlags.RESOURCE_REPOSITORY_TRACE_SIZE.get());
    enabled = true;
  }

  static void stopTracing() {
    enabled = false;
  }

  public static boolean isTracingActive() {
    return enabled;
  }

  public static void dumpTrace(@Nullable String message) {
    List<Object> trace = FlightRecorder.getAndClear();
    if (trace.isEmpty()) {
      if (message == null) {
        LOG.info("No resource updates recorded");
      }
      else {
        LOG.info(message + " - no resource updates recorded");
      }
    }
    else {
      String intro = isNullOrEmpty(message) ? "" : message + '\n';
      LOG.info(intro + "--- Resource update trace: ---\n" + Joiner.on('\n').join(trace) + "\n------------------------------");
    }
  }

  public static void log(@NotNull Supplier<?> lazyRecord) {
    if (enabled) {
      FlightRecorder.log(() -> TraceUtils.currentTime() + ' ' + lazyRecord.get());
    }
  }

  public static void logDirect(@NotNull Supplier<?> lazyRecord) {
    if (enabled) {
      LOG.info(lazyRecord.get().toString());
    }
  }

  public static @Nullable String pathForLogging(@Nullable VirtualFile file) {
    if (file == null) {
      return null;
    }
    PathString path = FileExtensions.toPathString(file);
    return path.subpath(max(path.getNameCount() - 6, 0), path.getNameCount()).getNativePath();
  }

  public static @Nullable String pathForLogging(@Nullable PsiFile file) {
    return file == null ? null : pathForLogging(file.getVirtualFile());
  }

  public static @Nullable String pathForLogging(@Nullable VirtualFile file, @NotNull Project project) {
    if (file == null) {
      return null;
    }
    return pathForLogging(FileExtensions.toPathString(file), project);
  }

  public static @NotNull String pathForLogging(@NotNull PathString file, @NotNull Project project) {
    VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    if (projectDir == null) {
      return file.subpath(max(file.getNameCount() - 4, 0), file.getNameCount()).getNativePath();
    }
    return FileExtensions.toPathString(projectDir).relativize(file).getNativePath();
  }

  public static @NotNull String pathsForLogging(@NotNull Collection<? extends VirtualFile> files, @NotNull Project project) {
    return files.stream().map(file -> pathForLogging(file, project)).collect(Collectors.joining(", "));
  }
}
