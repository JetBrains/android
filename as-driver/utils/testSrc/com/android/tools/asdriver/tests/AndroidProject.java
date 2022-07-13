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

import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.intellij.openapi.util.SystemInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AndroidProject {
  // The path to the existing project to be opened.
  private final String path;
  private Path distribution;
  private Path targetProject;

  public AndroidProject(String path) {
    this.path = path;
  }

  public void setDistribution(String path) {
    this.distribution = TestUtils.resolveWorkspacePathUnchecked(path);
  }

  public Path getTargetProject() {
    return targetProject;
  }

  public Path install(Path tempDir) throws IOException {
    Path project = TestUtils.resolveWorkspacePath(this.path);
    targetProject = Files.createTempDirectory(tempDir, "project");
    FileUtils.copyDirectory(project.toFile(), targetProject.toFile());
    Path wrapper = targetProject.resolve("gradle/wrapper/gradle-wrapper.properties");
    String content = Files.readString(wrapper);
    String replacedDistributionUrl = distribution.toUri().toString().replace("file:", "file\\:");
    content = content.replaceAll("distributionUrl=.*", "distributionUrl=" + replacedDistributionUrl);
    Files.writeString(wrapper, content);
    return targetProject;
  }
}
