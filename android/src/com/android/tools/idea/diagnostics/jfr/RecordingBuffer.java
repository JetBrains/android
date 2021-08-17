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

import com.android.annotations.concurrency.Slow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jdk.jfr.Recording;

public class RecordingBuffer {
  private static final Logger LOG = Logger.getInstance(RecordingBuffer.class);

  private Recording[] recordings = new Recording[2];
  private int latest = 0;
  private FreezeEvent currentFreezeEvent;

  public boolean isRecordingAndFrozen() {
    return currentFreezeEvent != null;
  }

  public void swapBuffers() {
    if (recordings[latest] != null) {
      recordings[latest].stop();
    }
    latest = 1 - latest;
    createAndStartRecording();
  }

  public void startFreeze() {
    currentFreezeEvent = new FreezeEvent();
    currentFreezeEvent.begin();
  }

  public void truncateLongFreeze() {
    currentFreezeEvent.truncated = true;
    currentFreezeEvent.commit();
    currentFreezeEvent = null;
    recordings[latest].stop();
  }

  private void closeRecording(int index) {
    if (recordings[index] != null) {
      recordings[index].close();
      recordings[index] = null;
    }
  }

  private void createAndStartRecording() {
    closeRecording(latest);
    recordings[latest] = new Recording();
    recordings[latest].enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20));
    recordings[latest].enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(20));
    recordings[latest].enable("jdk.ThreadDump").withPeriod(Duration.ofMillis(5000));
    recordings[latest].setToDisk(true);
    recordings[latest].start();
  }

  @Slow
  public Path dumpZipTo(Path directory) {
    if (currentFreezeEvent != null) {
      recordings[latest].stop();
      currentFreezeEvent.commit();
      currentFreezeEvent = null;
    }
    if (recordings[latest] != null) {
      File dumpDir = null;
      try {
        dumpDir = FileUtil.createTempDirectory("studio_jfr_recordings", null);
        ArrayList<File> recordingFiles = new ArrayList<>();
        if (recordings[1 - latest] != null) {
          recordingFiles.addAll(dumpAndCloseRecording(1 - latest, dumpDir, "0.jfr"));
        }
        recordingFiles.addAll(dumpAndCloseRecording(latest, dumpDir, recordingFiles.size() + ".jfr"));
        if (!recordingFiles.isEmpty()) {
          File zipFile = new File(directory.toFile(), "jfr.zip");
          try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("recording.jfr"));
            // concatenate the individual recordings. A JFR file is a list of self-contained "chunks", so
            // concatenating JFR files produces a valid JFR file encompassing the entire duration of the components.
            for (File jfrFile : recordingFiles) {
              Files.copy(jfrFile.toPath(), zos);
            }
          } catch (IOException e) {
            FileUtil.delete(zipFile);
            LOG.warn(e);
            return null;
          }
          return zipFile.toPath();
        }
      } catch (IOException e) {
        LOG.warn(e);
      } finally {
        if (dumpDir != null) {
          FileUtil.delete(dumpDir);
        }
        createAndStartRecording();
      }
    }
    return null;
  }

  private List<File> dumpAndCloseRecording(int index, File dumpDir, String name) {
    List<File> files = new ArrayList<>();
    try {
      Path jfrPath = Paths.get(new File(dumpDir, name).toURI());
      recordings[index].dump(jfrPath);
      files.add(jfrPath.toFile());
    } catch (IOException e) {
      LOG.warn(e);
    } finally {
      closeRecording(index);
    }
    return files;
  }

}