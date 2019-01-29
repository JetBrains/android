/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.android.tools.idea.tests.gui.framework.GuiTests.getFailedTestScreenshotDirPath;
import static com.android.tools.idea.tests.gui.framework.GuiTests.getProjectCreationDirPath;
import static com.android.utils.FileUtils.copyFile;
import static java.util.stream.Collectors.toList;

/** Rule to save a gradle performance file when the test fails. Files are copied to the "Test Screenshot Dir" **/
class SavePerformanceOnFailure extends TestWatcher {
  private static String PERFORMANCE_FILE_NAME = "gradle_performance.txt";

  @Override
  protected void failed(Throwable throwable, Description description) {
    try {
      File projectDir = getProjectCreationDirPath(null);

      List<File> files;
      try (Stream<Path> stream = Files.walk(projectDir.toPath())) {
        files = stream.map(Path::toFile)
            .filter(file -> file.getName().endsWith(PERFORMANCE_FILE_NAME))
            .collect(toList());
      }

      File screenshotDir = getFailedTestScreenshotDirPath();
      String baseName = projectDir.getPath() + '/';
      for (File file : files) {
        String fileName = file.getPath().replace(baseName, "").replaceAll("[/\\\\]","_");
        copyFile(file, new File(screenshotDir, fileName));
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}