/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.idea;

import com.android.tools.asdriver.tests.AndroidProject;
import com.android.tools.asdriver.tests.AndroidStudio;
import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.MavenRepo;
import com.android.utils.FileUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Creates Ide system dir and synced project files used for pre indexing and pre sync optimization.
 * It takes project path as input and runs the Ide with that project.
 * Once the Ide completes gradle sync and indexing, its system dir and the synced project is captured as output.
 */
public class ProjectIndexAndGradleSyncGenerator {

  public static void main(String[] args) throws Exception {
    AndroidSystem system = AndroidSystem.standardWithTmpDir();
    System.out.println(args[1]);
    Path outputDir = Path.of(args[0]);
    String projectPath = args[1];
    String manifestPath = args[2];
    String projectName = Paths.get(projectPath).getFileName().toString();
    AndroidProject project = new AndroidProject(projectPath);
    system.installRepo(new MavenRepo(manifestPath));
    try (AndroidStudio studio = system.runStudio(project)) {
      studio.waitForSync();
      studio.waitForIndex();
    }

    Path systemDir = system.getInstallation().getSystemDir();
    Path projectDir = system.getInstallation().getTmpDir().resolve(projectName);
    Files.createDirectories(outputDir);
    Path outputSystemDir = outputDir.resolve("system");
    Path outputProjectDir = outputDir.resolve(projectName);
    FileUtils.copyDirectory(systemDir.toFile(), outputSystemDir.toFile());
    FileUtils.copyDirectory(projectDir.toFile(), outputProjectDir.toFile());
  }
}