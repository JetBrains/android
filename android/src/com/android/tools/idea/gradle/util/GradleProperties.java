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
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class GradleProperties {
  @NonNls private static final String PROXY_HOST_PROPERTY_NAME = "systemProp.http.proxyHost";
  @NonNls private static final String PROXY_PORT_PROPERTY_NAME = "systemProp.http.proxyPort";
  @NonNls private static final String PROXY_USER_PROPERTY_NAME = "systemProp.http.proxyUser";
  @NonNls private static final String PROXY_PASSWORD_PROPERTY_NAME = "systemProp.http.proxyPassword";
  @NonNls private static final String PROXY_EXCEPTIONS_PROPERTY_NAME = "systemProp.http.nonProxyHosts";
  @NonNls private static final String JVM_ARGS_PROPERTY_NAME = "org.gradle.jvmargs";

  @NotNull private final File myPath;
  private final Properties myProperties;

  public GradleProperties(@NotNull Project project) throws IOException {
    this(new File(Projects.getBaseDirPath(project), "gradle.properties"));
  }

  @VisibleForTesting
  GradleProperties(@NotNull File path) throws IOException {
    myPath = path;
    myProperties = getProperties(myPath);
  }

  @VisibleForTesting
  String getProperty(@NotNull String name) {
    return myProperties.getProperty(name);
  }

  @NotNull
  public ProxySettings getProxySettings() {
    return new ProxySettings(myProperties);
  }

  public void setProxySettings(@NotNull ProxySettings settings) {
    myProperties.setProperty(PROXY_HOST_PROPERTY_NAME, settings.getHost());
    myProperties.setProperty(PROXY_PORT_PROPERTY_NAME, String.valueOf(settings.myPort));
    if (isEmpty(settings.myUser)) {
      myProperties.remove(PROXY_USER_PROPERTY_NAME);
      myProperties.remove(PROXY_PASSWORD_PROPERTY_NAME);
    }
    else {
      myProperties.setProperty(PROXY_USER_PROPERTY_NAME, settings.myUser);
      // password can be blank.
      myProperties.setProperty(PROXY_PASSWORD_PROPERTY_NAME, nullToEmpty(settings.myPassword));
    }
    if (isEmpty(settings.myExceptions)) {
      myProperties.remove(PROXY_EXCEPTIONS_PROPERTY_NAME);
    }
    else {
      myProperties.setProperty(PROXY_EXCEPTIONS_PROPERTY_NAME, settings.myExceptions);
    }
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
      "# Default value: -Xmx10248m -XX:MaxPermSize=256m",
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

  @Nullable
  public String getJvmArgs() {
    return myProperties.getProperty(JVM_ARGS_PROPERTY_NAME);
  }

  public static class ProxySettings {
    @Nullable private String myHost;
    @Nullable private String myExceptions;
    @Nullable private String myUser;
    @Nullable private String myPassword;

    private int myPort = 80;

    public ProxySettings(@NotNull Properties properties) {
      myHost = properties.getProperty(PROXY_HOST_PROPERTY_NAME);
      String portValue = properties.getProperty(PROXY_PORT_PROPERTY_NAME);
      if (isNotEmpty(portValue)) {
        try {
          myPort = Integer.parseInt(portValue);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }

    @Nullable
    public String getHost() {
      return myHost;
    }

    public void copyFrom(@NotNull HttpConfigurable ideProxySettings) {
      myHost = ideProxySettings.PROXY_HOST;
      myPort = ideProxySettings.PROXY_PORT;
      if (ideProxySettings.PROXY_AUTHENTICATION) {
        myUser = ideProxySettings.PROXY_LOGIN;
        myPassword = ideProxySettings.getPlainProxyPassword();
      }
      myExceptions = ideProxySettings.PROXY_EXCEPTIONS;
    }

    public int getPort() {
      return myPort;
    }
  }
}
