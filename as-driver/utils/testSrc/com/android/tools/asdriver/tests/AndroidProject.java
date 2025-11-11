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
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AndroidProject {
  // The path to the existing project to be opened.
  private final String path;
  private Path distribution;
  private Path targetProject;
  private final Path gradleProperties;

  public AndroidProject(String path) {
    this.path = path;

    // For projects created through the test, targetProject is the same as the original path.
    this.targetProject = Paths.get(path);
    this.gradleProperties = targetProject.resolve("gradle.properties");

    // By default, we set the distribution to the version that most integration tests should be
    // using. This version corresponds to `INTEGRATION_TEST_GRADLE_VERSION` in Bazel.
    setDistribution("tools/external/gradle/gradle-8.6-bin.zip");
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
    injectGradle();
    return targetProject;
  }

  /**
   * Installs the project into the specified temporary directory outside bazel sandbox. The project
   * source code is copied from the bazel bin folder to the specified location. It also sets the
   * project jdk location through gradle.xml file.
   */
  public Path installAtTmpDir(Path tmpDir) throws IOException {
    Path project = TestUtils.getBinPath(this.path);
    targetProject = tmpDir.resolve(Paths.get(path).getFileName().toString());
    Files.createDirectories(targetProject);
    FileUtils.copyDirectory(project.toFile(), targetProject.toFile());
    setJdkDir(targetProject);
    injectGradle();
    return targetProject;
  }

  private void setJdkDir(Path project) throws IOException {
    Path dotIdea = project.resolve(".idea");
    if (!Files.exists(dotIdea)) {
      Files.createDirectories(dotIdea);
    }
    String xmlContent = """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="GradleSettings">
            <option name="linkedExternalProjectsSettings">
              <GradleProjectSettings>
                <option name="externalProjectPath" value="$PROJECT_DIR$" />
                <option name="gradleJvm" value="$PROJECT_DIR$/../jdk" />
                <option name="resolveModulePerSourceSet" value="false" />
              </GradleProjectSettings>
            </option>
          </component>
        </project>
        """;
    Files.writeString(dotIdea.resolve("gradle.xml"), xmlContent);
  }

  protected void injectGradle() throws IOException {
    Path wrapper = targetProject.resolve("gradle/wrapper/gradle-wrapper.properties");
    String content = Files.readString(wrapper);
    String replacedDistributionUrl = distribution.toUri().toString().replace("file:", "file\\:");
    content = content.replaceAll("distributionUrl=.*", "distributionUrl=" + replacedDistributionUrl);
    Files.writeString(wrapper, content);
  }

  /**
   * Sets the SDK directory in local.properties. This prevents a dialog from spawning saying "The
   * "project and Android Studio point to different Android SDKs", and since that dialog would
   * require interaction, it's best to avoid it.
   */
  public void setSdkDir(Path dir) throws IOException {
    Path localProperties = targetProject.resolve("local.properties");
    // Use toUri() so that we don't have to escape backslashes for Windows paths
    String sdkDirLine = String.format("sdk.dir=%s%n", dir.toUri().toString().replace("file:", ""));
    String contentToWrite = sdkDirLine;
    if (Files.exists(localProperties)) {
      String content = Files.readString(localProperties);
      Pattern pattern = Pattern.compile("^sdk.dir=.*$", Pattern.MULTILINE);
      contentToWrite = pattern.matcher(content).replaceAll(sdkDirLine);
    }
    Files.writeString(localProperties, contentToWrite);
  }

  public void inject(Path target, String contents) throws IOException {
    Path targetFile = Files.createFile(targetProject.resolve(target));
    Files.writeString(targetFile, contents);
  }

  /**
   * Appends a property to gradle.properties, creating the file if it doesn't exist,
   * and ensuring the property is always on a newline
   */
  public void addGradleProperty(final String property) throws IOException {
    FileUtils.appendLine(gradleProperties, property);
  }
}