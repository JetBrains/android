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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class FlightRecorderTest {
  @Rule
  public TemporaryFolder myFolder = new TemporaryFolder();

  @Test
  public void folderNameConversions() {
    LocalDateTime dateTime = LocalDateTime.parse("2016-09-23T11:13:41");
    assertEquals("2016-09-23T11.13.41", FlightRecorder.timeStampToFolder(dateTime));
    assertEquals(dateTime, FlightRecorder.folderToTimeStamp(Paths.get("/foo/bar/2016-09-23T11.13.41")));
  }

  @Test
  public void trimOldLogs() throws IOException {
    LocalDateTime now = LocalDateTime.now();

    // create 10 instants back in time
    List<LocalDateTime> instants = ThreadLocalRandom.current()
      .ints(1, 500)
      .distinct()
      .limit(10)
      .mapToObj(i -> now.minus(i, ChronoUnit.SECONDS))
      .collect(Collectors.toList());

    for (LocalDateTime instant : instants) {
      // create a log folder and add some logs within it
      File logFolder = myFolder.newFolder(FlightRecorder.timeStampToFolder(instant));
      Files.write("", new File(logFolder, "build.log"), Charsets.UTF_8);
    }

    Collections.sort(instants);

    int count = 3;
    FlightRecorder.trimOldLogs(myFolder.getRoot().toPath(), count);

    for (int i = 0; i < instants.size() - count; i++) {
      File f = new File(myFolder.getRoot(), FlightRecorder.timeStampToFolder(instants.get(i)));

      if (i < instants.size() - count) {
        assertFalse("Stale log folder still exists", f.exists());
      } else {
        assertTrue("New log folder unexpectedly trimmed", f.exists());
      }
    }
  }
}
