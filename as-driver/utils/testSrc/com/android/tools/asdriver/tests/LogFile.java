/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.tests;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogFile {
  private final Path path;

  /** The current position on the log file, see {@code waitForMatchingLine} */
  private long position;

  public LogFile(Path filePath) {
    path = filePath;
  }

  public Matcher waitForMatchingLine(String regex, long timeout, TimeUnit unit) throws IOException, InterruptedException {
    return waitForMatchingLine(regex, false, timeout, unit);
  }

  /**
   *  Waits until the given regex matches a line in the log. The log is scanned from the last read position.
   *  If {@code lookAhead} is true, the current position of the log remains unchanged.
   */
  public Matcher waitForMatchingLine(String regex, boolean lookAhead, long timeout, TimeUnit unit) throws IOException, InterruptedException {
    try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {

      FileChannel channel = raf.getChannel();
      channel.position(position);

      long timeoutMillis = unit.toMillis(timeout);
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = null;
      long elapsed = 0;
      long start = System.currentTimeMillis();
      while (elapsed < timeoutMillis) {
        String line = raf.readLine();
        matcher = line == null ? null : pattern.matcher(line);
        if (matcher != null && matcher.matches()) {
          break;
        }
        matcher = null;
        if (line == null) {
          Thread.sleep(Math.min(1000, timeoutMillis - elapsed));
        }
        elapsed = System.currentTimeMillis() - start;
      }
      if (matcher == null) {
        throw new InterruptedException("Time out while waiting for line matching '" + regex + "'");
      }
      if (!lookAhead) {
        position = channel.position();
      }
      return matcher;
    }
  }

  public void printContents() throws IOException {
    System.out.printf("===%s===%n", path);
    printFile();
    System.out.println("===END===");
  }

  private void printFile() throws IOException {
    try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        System.out.println(line);
      }
    }
  }

  public Path getPath() {
    return path;
  }

  public void reset() {
    position = 0;
  }
}
