/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.android.tools.idea.gradle.util.ProxySettings.HTTP_PROXY_TYPE;
import static com.android.tools.idea.gradle.util.ProxySettings.HTTPS_PROXY_TYPE;

public class GradleProperties {
  @NonNls private static final String JVM_ARGS_PROPERTY_NAME = "org.gradle.jvmargs";

  @NotNull private final File myPath;
  private final Properties myProperties;

  public GradleProperties(@NotNull Project project) throws IOException {
    this(new File(Projects.getBaseDirPath(project), "gradle.properties"));
  }

  @VisibleForTesting
  GradleProperties(@NotNull File path) throws IOException {
    myPath = path;
    myProperties = PropertiesUtil.getProperties(myPath);
  }

  @VisibleForTesting
  String getProperty(@NotNull String name) {
    return myProperties.getProperty(name);
  }

  @NotNull
  public ProxySettings getHttpProxySettings() {
    return new ProxySettings(myProperties, HTTP_PROXY_TYPE);
  }

  @NotNull
  public ProxySettings getHttpsProxySettings() {
    return new ProxySettings(myProperties, HTTPS_PROXY_TYPE);
  }

  public void save() throws IOException {
    savePropertiesToFile(myProperties, myPath, getHeaderComment());
  }

  @NotNull
  private static String getHeaderComment() {
    String[] lines = {
      "# Project-wide Gradle settings.",
      "",
      "# For more details on how to configure your build environment visit",
      "# http://www.gradle.org/docs/current/userguide/build_environment.html",
      "",
      "# Specifies the JVM arguments used for the daemon process.",
      "# The setting is particularly useful for tweaking memory settings.",
      "# Default value: -Xmx1024m -XX:MaxPermSize=256m",
      "# org.gradle.jvmargs=-Xmx2048m -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8",
      "",
      "# When configured, Gradle will run in incubating parallel mode.",
      "# This option should only be used with decoupled projects. More details, visit",
      "# http://www.gradle.org/docs/current/userguide/multi_project_builds.html#sec:decoupled_projects",
      "# org.gradle.parallel=true"
    };
    return Joiner.on(SystemProperties.getLineSeparator()).join(lines);
  }

  public void setJvmArgs(@NotNull String jvmArgs) {
    myProperties.setProperty(JVM_ARGS_PROPERTY_NAME, jvmArgs);
  }

  public void clear() {
    myProperties.clear();
  }

  @Nullable
  public String getJvmArgs() {
    return myProperties.getProperty(JVM_ARGS_PROPERTY_NAME);
  }

  public Properties getProperties() {
    return myProperties;
  }

  @NotNull
  public File getPath() {
    return myPath;
  }
}
