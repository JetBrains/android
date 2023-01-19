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
package com.android.tools.idea.diagnostics.jfr;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Slow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import jdk.jfr.Recording;

public class RecordingBuffer {
  private static final Logger LOG = Logger.getInstance(RecordingBuffer.class);
  private Recording[] recordings = new Recording[2];
  private int latest = 0;

  // caller is responsible for calling close() on the returned Recording
  public Recording swapBuffers() {
    if (recordings[latest] != null) {
      recordings[latest].stop();
    }
    latest = 1 - latest;
    Recording oldRecording = recordings[latest];
    createAndStartRecording();
    return oldRecording;
  }

  private void createAndStartRecording() {
    recordings[latest] = new Recording();
    recordings[latest].enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20));
    recordings[latest].enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(20));
    recordings[latest].enable("jdk.GCPhasePause");
    recordings[latest].enable("jdk.GCHeapSummary");
    recordings[latest].setToDisk(true);
    recordings[latest].start();
  }

  @Slow
  @Nullable
  public Path dumpJfrTo(Path directory) {
    if (recordings[latest] != null) {
      File dumpDir = null;
      try {
        dumpDir = FileUtil.createTempDirectory("studio_jfr_recordings", null);
        ArrayList<File> recordingFiles = new ArrayList<>();
        if (recordings[1 - latest] != null) {
          recordingFiles.addAll(dumpRecording(1 - latest, dumpDir, "0.jfr"));
        }
        recordingFiles.addAll(dumpRecording(latest, dumpDir, recordingFiles.size() + ".jfr"));
        if (recordingFiles.size() == 1) {
          Files.move(recordingFiles.get(0).toPath(), new File(directory.toFile(), "recording.jfr").toPath(), REPLACE_EXISTING);
        } else if (recordingFiles.size() > 1) {
          File concatenatedRecordings = new File(directory.toFile(), "recording.jfr");
          try (FileOutputStream fos = new FileOutputStream(concatenatedRecordings)) {
            // concatenate the individual recordings. A JFR file is a list of self-contained "chunks", so
            // concatenating JFR files produces a valid JFR file encompassing the entire duration of the components.
            for (File jfrFile : recordingFiles) {
              Files.copy(jfrFile.toPath(), fos);
            }
          }
          catch (IOException e) {
            FileUtil.delete(concatenatedRecordings);
            LOG.warn(e);
            return null;
          }
          return concatenatedRecordings.toPath();
        }
      } catch (IOException e) {
        LOG.warn(e);
      } finally {
        if (dumpDir != null) {
          FileUtil.delete(dumpDir);
        }
      }
    }
    return null;
  }

  private List<File> dumpRecording(int index, File dumpDir, String name) {
    List<File> files = new ArrayList<>();
    try {
      Path jfrPath = Paths.get(new File(dumpDir, name).toURI());
      recordings[index].dump(jfrPath);
      files.add(jfrPath.toFile());
    } catch (IOException e) {
      LOG.warn(e);
    }
    return files;
  }
}