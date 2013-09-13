/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.jps.builder;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.compiler.BuildProcessJvmArgs;
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * Tests for {@link BuilderExecutionSettings}.
 */
public class BuilderExecutionSettingsTest extends TestCase {
  private File myGradleHomeDir;
  private File myGradleServiceDir;
  private File myJavaHomeDir;
  private File myProjectDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File tempDir = Files.createTempDir();
    myGradleHomeDir = createDirectory(tempDir, "gradle-1.6");
    myGradleServiceDir = createDirectory(tempDir, "gradle");
    myJavaHomeDir = createDirectory(tempDir, "java");
    myProjectDir = createDirectory(tempDir, "project1");
  }

  @NotNull
  private static File createDirectory(@NotNull File parent, @NotNull String name) {
    File dir = new File(parent, name);
    FileUtil.createDirectory(dir);
    return dir;
  }

  @Override
  protected void tearDown() throws Exception {
    delete(myGradleHomeDir);
    delete(myGradleServiceDir);
    delete(myJavaHomeDir);
    delete(myProjectDir);
    super.tearDown();
  }

  private static void delete(@Nullable File dir) {
    if (dir != null) {
      dir.delete();
    }
  }

  public void testConstructorWithValidVmArgs() {
    System.setProperty(BuildProcessJvmArgs.GRADLE_DAEMON_MAX_IDLE_TIME_IN_MS, "55");

    String gradleHomeDirPath = myGradleHomeDir.getPath();
    System.setProperty(BuildProcessJvmArgs.GRADLE_HOME_DIR_PATH, gradleHomeDirPath);

    String gradleHomeServicePath = myGradleServiceDir.getPath();
    System.setProperty(BuildProcessJvmArgs.GRADLE_SERVICE_DIR_PATH, gradleHomeServicePath);

    String javaHomePath = myJavaHomeDir.getPath();
    System.setProperty(BuildProcessJvmArgs.GRADLE_JAVA_HOME_DIR_PATH, javaHomePath);

    String projectDirPath = myProjectDir.getPath();
    System.setProperty(BuildProcessJvmArgs.PROJECT_DIR_PATH, projectDirPath);

    System.setProperty(BuildProcessJvmArgs.USE_EMBEDDED_GRADLE_DAEMON, "true");
    System.setProperty(BuildProcessJvmArgs.USE_GRADLE_VERBOSE_LOGGING, "true");

    System.setProperty(BuildProcessJvmArgs.GRADLE_DAEMON_VM_OPTION_COUNT, "2");

    String xmx = "-Xmx2048m";
    System.setProperty(BuildProcessJvmArgs.GRADLE_DAEMON_VM_OPTION_DOT + 0, xmx);

    String maxPermSize = "-XX:MaxPermSize=512m";
    System.setProperty(BuildProcessJvmArgs.GRADLE_DAEMON_VM_OPTION_DOT + 1, maxPermSize);


    BuilderExecutionSettings settings = new BuilderExecutionSettings();
    assertEquals(55, settings.getGradleDaemonMaxIdleTimeInMs());
    assertEquals(gradleHomeDirPath, pathOf(settings.getGradleHomeDir()));
    assertEquals(gradleHomeServicePath, pathOf(settings.getGradleServiceDir()));
    assertEquals(javaHomePath, pathOf(settings.getJavaHomeDir()));
    assertEquals(projectDirPath, settings.getProjectDir().getPath());
    assertTrue(settings.isEmbeddedGradleDaemonEnabled());
    assertTrue(settings.isVerboseLoggingEnabled());

    List<String> vmOptions = settings.getGradleDaemonVmOptions();
    assertEquals(2, vmOptions.size());
    assertEquals(xmx, vmOptions.get(0));
    assertEquals(maxPermSize, vmOptions.get(1));
  }

  private static String pathOf(@Nullable File dir) {
    assertNotNull(dir);
    return dir.getPath();
  }
}
