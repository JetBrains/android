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
package com.android.tools.idea.fd;

import com.android.ddmlib.IDevice;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.android.tools.idea.logcat.AndroidLogcatService;
import com.android.tools.idea.run.AndroidDevice;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlightRecorder {
  private static final String FD_FLR_LOGS = "flr";
  private static final String FN_GRADLE_LOG = "build.log";
  private static final String FN_GRADLE_PROFILE = "profile.log";
  private static final String FN_BUILD_INFO = "build-info.xml";

  // Number of previous build logs to keep around.
  // Ideally, we'd trim based on a more sophisticated logic that knows about APK installs, but for now
  // we take the simple approach of keeping around logs from the last N builds.
  private static final int MAX_LOG_ENTRY_COUNT = 10;

  // A path specific to this project within which all flight recorder logs are saved
  private final Path myBasePath;

  // Log buffer for runtime logs
  private final LogcatRecorder myLogcatRecorder;

  // Time (in default timezone to match idea.log) at which the last build output was saved.
  private LocalDateTime myTimestamp;

  public static FlightRecorder get(@NotNull Project project) {
    return ServiceManager.getService(project, FlightRecorder.class);
  }

  private FlightRecorder(@NotNull Project project) {
    Path logs = Paths.get(PathManager.getLogPath());
    Path flr = Paths.get(FD_FLR_LOGS, project.getLocationHash());
    myBasePath = logs.resolve(flr);
    myLogcatRecorder = new LogcatRecorder(AndroidLogcatService.getInstance());
    myTimestamp = now();
  }

  public void saveBuildOutput(@NotNull String gradleOutput, @NotNull InstantRunBuildProgressListener instantRunProgressListener) {
    myTimestamp = now();
    ApplicationManager.getApplication().executeOnPooledThread(
      new BuildOutputRecorderTask(myBasePath, myTimestamp, gradleOutput, instantRunProgressListener));
  }

  public void saveBuildInfo(@NotNull InstantRunBuildInfo instantRunBuildInfo) {
    ApplicationManager.getApplication().executeOnPooledThread(
      new BuildInfoRecorderTask(myBasePath, myTimestamp, instantRunBuildInfo));
  }

  public void setLaunchTarget(@NotNull AndroidDevice device) {
    try {
      Path deviceLog = myBasePath.resolve(timeStampToFolder(myTimestamp)).resolve(getDeviceLogFileName(device));
      Files.write(deviceLog, new byte[0]);
    }
    catch (IOException e) {
      Logger.getInstance(FlightRecorder.class).info("Unable to record deployment device info", e);
    }

    // start monitoring logcat if device is online
    if (device.getLaunchedDevice().isDone()) {
      try {
        IDevice d = device.getLaunchedDevice().get();
        myLogcatRecorder.startMonitoring(d, myTimestamp);
      }
      catch (InterruptedException | ExecutionException e) {
        Logger.getInstance(FlightRecorder.class).info("Unable to start recording logcat", e);
      }
    }
  }

  // We need very little detail about the device itself, so we can encode it directly in the file name
  private static String getDeviceLogFileName(@NotNull AndroidDevice device) {
    // for avds, everything we need is already included in idea.log
    if (device.isVirtual()) {
      return "TARGET-AVD";
    }

    ListenableFuture<IDevice> launchedDevice = device.getLaunchedDevice();
    if (!launchedDevice.isDone()) {
      return "OFFLINE";
    }

    IDevice d;
    try {
      d = launchedDevice.get();
    }
    catch (InterruptedException | ExecutionException e) {
      return "OFFLINE";
    }

    return DevicePropertyUtil.getManufacturer(d, "unknown").replace(' ', '.') +
           '-' +
           DevicePropertyUtil.getModel(d, "unknown").replace(' ', '.');
  }

  @NotNull
  private static LocalDateTime now() {
    // Use default timezone since the idea.log uses that and we want to correlate between the two
    return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
  }

  @NotNull
  static String timeStampToFolder(@NotNull LocalDateTime dateTime) {
    return dateTime
      .truncatedTo(ChronoUnit.SECONDS)
      .toString()
      .replace(':', '.');
  }

  @NotNull
  static LocalDateTime folderToTimeStamp(@NotNull Path path) {
    String fileName = path.getFileName().toString();
    return LocalDateTime.parse(fileName.replace('.', ':'));
  }

  static void trimOldLogs(@NotNull Path root, int count) {
    // a filter that only return folders with name matching a timestamp
    DirectoryStream.Filter<Path> filter = entry -> {
      if (!Files.isDirectory(entry)) {
        return false;
      }

      try {
        folderToTimeStamp(entry);
        return true;
      } catch (DateTimeParseException e) {
        return false;
      }
    };

    // collect all folders matching the above filter
    List<Path> allLogs;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, filter)) {
      allLogs = Lists.newArrayList(stream.iterator());
    } catch (IOException e) {
      Logger.getInstance(FlightRecorder.class).warn("Unable to cleanup flight recorder logs", e);
      return;
    }

    // sort the folders by timestamp and only keep the most recent count entries
    allLogs.stream()
      .sorted((p1, p2) -> folderToTimeStamp(p2).compareTo(folderToTimeStamp(p1))) // note: reverse sort
      .skip(count)
      .forEach(path -> {
        try {
          FileUtils.deleteDirectoryContents(path.toFile());
        }
        catch (IOException ignored) {
        }

        try {
          Files.delete(path);
        }
        catch (IOException ignored) {
        }
      });
  }

  @NotNull
  public List<Path> getAllLogs() {
    List<Path> logs = new ArrayList<>();

    Path logsHome = Paths.get(PathManager.getLogPath());
    if (logsHome == null) {
      return logs;
    }

    logs.addAll(getIdeaLogs(logsHome));
    logs.addAll(getFlightRecorderLogs());

    Path logcatPath = logsHome.resolve("logcat.txt");
    try {
      Files.write(logcatPath, myLogcatRecorder.getLogs(), Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      logs.add(logcatPath);
    }
    catch (IOException e) {
      Logger.getInstance(FlightRecorder.class).info("Unexpected error saving logcat", e);
    }

    return logs;
  }

  @NotNull
  private static List<Path> getIdeaLogs(@NotNull Path logsHome) {
    try (Stream<Path> stream = Files.list(logsHome)) {
      return stream
        .filter(p -> Files.isReadable(p) && p.getFileName().toString().startsWith("idea.log"))
        .sorted((p1, p2) -> Long.compare(p2.toFile().lastModified(), p1.toFile().lastModified()))
        .limit(3)
        .collect(Collectors.toList());
    }
    catch (IOException e) {
      return ImmutableList.of();
    }
  }

  @NotNull
  private List<Path> getFlightRecorderLogs() {
    try {
      List<Path> list = new ArrayList<>(100);
      Files.walkFileTree(myBasePath, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Path fileName = file.getFileName();
          if (fileName != null && fileName.toString().startsWith(".")) { // skip past .DS_Store etc
            return FileVisitResult.CONTINUE;
          }

          list.add(file);
          // arbitrary limit to avoid uploading tons of unnecessary files
          return list.size() > 100 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
        }
      });
      return list;
    }
    catch (IOException e) {
      return ImmutableList.of();
    }
  }

  @NotNull
  public String getPresentablePath(@NotNull Path file) {
    if (file.startsWith(myBasePath)) {
      return myBasePath.relativize(file).toString();
    }

    return file.getFileName().toString();
  }

  private static class BuildOutputRecorderTask implements Runnable {
    private final Path myLogsRoot;
    private final Path myCurrentLogPath;
    private final String myGradleOutput;
    private final InstantRunBuildProgressListener myBuildProgressListener;

    private BuildOutputRecorderTask(@NotNull Path logsRoot,
                                    @NotNull LocalDateTime dateTime,
                                    @NotNull String gradleOuput,
                                    @NotNull InstantRunBuildProgressListener buildProgressListener) {
      myLogsRoot = logsRoot;
      myCurrentLogPath = logsRoot.resolve(timeStampToFolder(dateTime));
      myGradleOutput = gradleOuput;
      myBuildProgressListener = buildProgressListener;
    }

    @Override
    public void run() {
      saveCurrentLog();
      trimOldLogs(myLogsRoot, MAX_LOG_ENTRY_COUNT);
    }

    private void saveCurrentLog() {
      try {
        Files.createDirectories(myCurrentLogPath);
        Files.write(myCurrentLogPath.resolve(FN_GRADLE_LOG), myGradleOutput.getBytes(Charsets.UTF_8));

        try (BufferedWriter bw = Files.newBufferedWriter(myCurrentLogPath.resolve(FN_GRADLE_PROFILE), Charsets.UTF_8)) {
          myBuildProgressListener.serializeTo(bw);
        }

      } catch (IOException e) {
        Logger.getInstance(FlightRecorder.class).info("Unable to save gradle output logs", e);
      }
    }
  }

  private static class BuildInfoRecorderTask implements Runnable {
    private final Path myBasePath;
    private final InstantRunBuildInfo myBuildInfo;

    public BuildInfoRecorderTask(Path logsRoot,
                                 LocalDateTime dateTime,
                                 InstantRunBuildInfo instantRunBuildInfo) {
      myBasePath = logsRoot.resolve(timeStampToFolder(dateTime));
      myBuildInfo = instantRunBuildInfo;
    }

    @Override
    public void run() {
      try {
        Files.createDirectories(myBasePath);

        try (BufferedWriter bw = Files.newBufferedWriter(myBasePath.resolve(FN_BUILD_INFO), Charsets.UTF_8)) {
          myBuildInfo.serializeTo(bw);
        }
      } catch (IOException e) {
        Logger.getInstance(FlightRecorder.class).info("Unable to build info", e);
      }
    }
  }
}
