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

import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.tools.idea.gradle.util.PropertiesFiles.savePropertiesToFile;
import static com.android.tools.idea.gradle.util.ProxySettings.HTTPS_PROXY_TYPE;
import static com.android.tools.idea.gradle.util.ProxySettings.HTTP_PROXY_TYPE;

import com.android.tools.idea.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Properties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleProperties {
  @NonNls private static final String JVM_ARGS_PROPERTY_NAME = "org.gradle.jvmargs";

  @NotNull private final File myPath;
  private final Properties myProperties;

  public GradleProperties(@NotNull Project project) throws IOException {
    this(new File(Projects.getBaseDirPath(project), FN_GRADLE_PROPERTIES));
  }

  public GradleProperties(@NotNull File path) throws IOException {
    myPath = path;
    myProperties = PropertiesFiles.getProperties(myPath);
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
    // Sort properties before saving, this way files are more stable and easy to read
    savePropertiesToFile(getSortedProperties(), myPath, getHeaderComment());
  }

  @VisibleForTesting
  @NotNull
  Properties getSortedProperties() {
    Properties sorted = new Properties() {
      @NotNull
      @Override
      public synchronized Enumeration<Object> keys() {
        // Change enumeration to lexicographical order, this way file content is more stable and easier to read
        ArrayList<Object> list = Collections.list(super.keys());
        list.sort(Comparator.comparing(Object::toString));
        return Collections.enumeration(list);
      }
    };
    myProperties.forEach((key, value) -> sorted.setProperty((String)key, (String)value));
    return sorted;
  }

  @NotNull
  private static String getHeaderComment() {
    String[] lines = {
      "# For more details on how to configure your build environment visit",
      "# http://www.gradle.org/docs/current/userguide/build_environment.html",
      "",
      "# Specifies the JVM arguments used for the daemon process.",
      "# The setting is particularly useful for tweaking memory settings.",
      "# Default value: -Xmx1024m -XX:MaxPermSize=256m",
      "# org.gradle.jvmargs=-Xmx2048m -XX:MaxPermSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8",
      "",
      "# When configured, Gradle will run in incubating parallel mode.",
      "# This option should only be used with decoupled projects. For more details, visit",
      "# https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects",
      "# org.gradle.parallel=true"
    };
    return Joiner.on(System.lineSeparator()).join(lines);
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
