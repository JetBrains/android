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
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.compiler.BuildProcessJvmArgs.*;

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
      //noinspection ResultOfMethodCallIgnored
      dir.delete();
    }
  }

  public void testConstructorWithValidJvmArgs() {
    String gradleHomeDirPath = myGradleHomeDir.getPath();
    System.setProperty(GRADLE_HOME_DIR_PATH, gradleHomeDirPath);

    String gradleHomeServicePath = myGradleServiceDir.getPath();
    System.setProperty(GRADLE_SERVICE_DIR_PATH, gradleHomeServicePath);

    String javaHomePath = myJavaHomeDir.getPath();
    System.setProperty(GRADLE_JAVA_HOME_DIR_PATH, javaHomePath);

    String projectDirPath = myProjectDir.getPath();
    System.setProperty(PROJECT_DIR_PATH, projectDirPath);

    System.setProperty(USE_EMBEDDED_GRADLE_DAEMON, "true");
    System.setProperty(USE_GRADLE_VERBOSE_LOGGING, "true");
    System.setProperty(GRADLE_CONFIGURATION_ON_DEMAND, "true");

    System.setProperty(GRADLE_DAEMON_COMMAND_LINE_OPTION_PREFIX + 0, "--stacktrace");
    System.setProperty(GRADLE_DAEMON_COMMAND_LINE_OPTION_PREFIX + 1, "--offline");

    String xmx = "-Xmx2048m";
    System.setProperty(GRADLE_DAEMON_JVM_OPTION_PREFIX + 0, xmx);

    String maxPermSize = "-XX:MaxPermSize=512m";
    System.setProperty(GRADLE_DAEMON_JVM_OPTION_PREFIX + 1, maxPermSize);

    String httpProxyHost = "proxy.android.com";
    System.setProperty(HTTP_PROXY_PROPERTY_PREFIX + 0, "http.proxyHost:" + httpProxyHost);

    String httpProxyPort = "8080";
    System.setProperty(HTTP_PROXY_PROPERTY_PREFIX + 1, "http.proxyPort:" + httpProxyPort);

    // Add some garbage to test that parsing HTTP proxy properties is correct.
    System.setProperty(HTTP_PROXY_PROPERTY_PREFIX + 2, "randomText");
    System.setProperty(HTTP_PROXY_PROPERTY_PREFIX + 3, "randomText:");

    System.setProperty(GRADLE_TASKS_TO_INVOKE_PROPERTY_PREFIX + 0, ":main:assemble");
    System.setProperty(GRADLE_TASKS_TO_INVOKE_PROPERTY_PREFIX + 1, ":lib:assemble");

    BuilderExecutionSettings settings = new BuilderExecutionSettings();
    assertEquals(gradleHomeDirPath, pathOf(settings.getGradleHomeDir()));
    assertEquals(gradleHomeServicePath, pathOf(settings.getGradleServiceDir()));
    assertEquals(javaHomePath, pathOf(settings.getJavaHomeDir()));
    assertEquals(projectDirPath, settings.getProjectDir().getPath());
    assertTrue(settings.isEmbeddedModeEnabled());
    assertTrue(settings.isVerboseLoggingEnabled());
    assertTrue(settings.isConfigureOnDemand());

    List<String> vmOptions = settings.getJvmOptions();
    assertEquals(4, vmOptions.size());
    assertTrue(vmOptions.contains(xmx));
    assertTrue(vmOptions.contains(maxPermSize));
    assertTrue(vmOptions.contains("-Dhttp.proxyHost=" + httpProxyHost));
    assertTrue(vmOptions.contains("-Dhttp.proxyPort=" + httpProxyPort));

    List<String> modulesToBuildNames = settings.getGradleTasksToInvoke();
    assertEquals(2, modulesToBuildNames.size());
    assertTrue(modulesToBuildNames.contains(":main:assemble"));
    assertTrue(modulesToBuildNames.contains(":lib:assemble"));

    List<String> commandLineOptions = settings.getCommandLineOptions();
    assertEquals(2, commandLineOptions.size());
    assertTrue(commandLineOptions.contains("--stacktrace"));
    assertTrue(commandLineOptions.contains("--offline"));
  }

  private static String pathOf(@Nullable File dir) {
    assertNotNull(dir);
    return dir.getPath();
  }
}
