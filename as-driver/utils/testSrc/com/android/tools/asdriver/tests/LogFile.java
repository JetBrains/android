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

import com.google.common.collect.ImmutableList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogFile {
  // Minimum amount of time to wait before (re)examining log files.
  private static final int MIN_DELAY_MS = 50;
  // Maximum amount of time to wait before (re)examining log files.
  private static final int MAX_DELAY_MS = 1000;
  private final Path path;

  /** The size of the log file's header, which we do not want to search when looking for matching lines. */
  private final long headerSize;

  /** The current position on the log file, see {@code waitForMatchingLine} */
  private long position;

  private static final String CHECK_LOGS_INSTRUCTIONS = "For more information, check the logs: go/e2e-find-log-files";

  public LogFile(Path filePath) {
    this(filePath, 0);
  }

  public LogFile(Path filePath, int headerSize) {
    path = filePath;
    this.headerSize = headerSize;
    position = headerSize;
  }

  public Matcher waitForMatchingLine(String regex, String failureRegex, long timeout, TimeUnit unit) throws IOException, InterruptedException {
    return waitForMatchingLine(regex, failureRegex, false, timeout, unit);
  }

  public Matcher waitForMatchingLine(String regex, long timeout, TimeUnit unit) throws IOException, InterruptedException {
    return waitForMatchingLine(regex, null, false, timeout, unit);
  }

  /**
   * Find all the lines in studio logs that match the regex and return all the matching regex groups.
   *
   * @param regex Regular expression to match
   * @return All matching regex groups for every single matching line.
   */
  public List<List<String>> findMatchingLines(String regex) throws IOException {
    Pattern pattern = Pattern.compile(regex);
    // Initialize the matcher that we will be reusing.
    Matcher matcher = pattern.matcher("");
    return Files.readAllLines(path)
      .stream()
      .map(matcher::reset) // reuse matcher
      .filter(Matcher::matches)
      .map(LogFile::getGroups)
      .collect(Collectors.toList());
  }

  private static List<String> getGroups(Matcher matcher) {
    var immutableListBuilder = new ImmutableList.Builder<String>();
    for (int i = 0; i <= matcher.groupCount(); i++) {
      immutableListBuilder.add(matcher.group(i));
    }
    return immutableListBuilder.build();
  }

  /**
   * Returns true if {@code regex} matches a line in the log.
   */
  public boolean hasMatchingLine(String regex) throws IOException {
    Pattern pattern = Pattern.compile(regex);
    return Files.readAllLines(path)
      .stream()
      .map(pattern::matcher)
      .anyMatch(Matcher::matches);
  }

  /**
   *  Waits until the given regex matches a line in the log. The log is scanned from the last read position.
   *  If {@code lookAhead} is true, the current position of the log remains unchanged.
   */
  public Matcher waitForMatchingLine(String regex, String failureRegex, boolean lookAhead, long timeout, TimeUnit unit) throws IOException, InterruptedException {
    try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {

      FileChannel channel = raf.getChannel();
      channel.position(position);

      long timeoutMillis = unit.toMillis(timeout);
      Pattern pattern = Pattern.compile(regex);
      Pattern failurePattern = failureRegex == null ? null : Pattern.compile(failureRegex);
      Matcher matcher = null;
      long elapsed = 0;
      long start = System.currentTimeMillis();
      int delayMs = MIN_DELAY_MS;
      List<String> verifyLines = new ArrayList<>();
      while (elapsed < timeoutMillis) {
        String line = raf.readLine();
        if (failurePattern != null && line != null && failurePattern.matcher(line).matches()) {
          throw new IllegalStateException(String.format("Found line matching failureRegex: %s%n%n%s", line, CHECK_LOGS_INSTRUCTIONS));
        }

        matcher = line == null ? null : pattern.matcher(line);
        if (matcher != null && matcher.matches()) {
          break;
        }
        matcher = null;
        if (line == null) {
          Thread.sleep(Math.min(delayMs, timeoutMillis - elapsed));
          delayMs = Math.min(MAX_DELAY_MS, 2 * delayMs);  // Exponential backoff up to 1 second max
        }
        else {
          verifyLines.add(line);
        }
        elapsed = System.currentTimeMillis() - start;
      }
      if (matcher == null) {
        // This may happen more than once per instance of this class, so we must generate a unique unused path.
        Path verification = Files.createTempFile(path.getParent(), "verification-", "-" + path.getFileName().toString());
        try (PrintWriter out = new PrintWriter(verification.toFile())) {
          out.println("Failed to find '" + regex + "' in:\n");
          for (String line: verifyLines) {
            out.println(line);
          }
        }
        throw new InterruptedException(
          String.format("Time out after %d %s while waiting for line matching '%s'%n%n%s", timeout, unit, regex, CHECK_LOGS_INSTRUCTIONS));
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
    position = headerSize;
  }
}
