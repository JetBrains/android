/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.config;

import com.android.annotations.VisibleForTesting;
import com.android.prefs.AndroidLocation;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.welcome.wizard.SdkComponentsStep;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around data passed from the installer.
 */
public class InstallerData {
  public static final InstallerData EMPTY = new InstallerData(null, null, null, true, null, null);

  private static final String PATH_FIRST_RUN_PROPERTIES = FileUtil.join("studio", "installer", "firstrun.data");
  private static final String PROPERTY_SDK = "androidsdk.dir";
  private static final String PROPERTY_JDK = "jdk.dir";
  private static final String PROPERTY_SDK_REPO = "androidsdk.repo";
  private static final String PROPERTY_TIMESTAMP = "install.timestamp";
  private static final String PROPERTY_AVD = "create.avd";
  private static final String PROPERTY_VERSION = "studio.version";
  private static final Logger LOG = Logger.getInstance(InstallerData.class);

  @Nullable private final File myJavaDir;
  @Nullable private final File myAndroidSrc;
  @Nullable private final File myAndroidDest;
  private final boolean myCreateAvd;
  @Nullable private final String myTimestamp;
  @Nullable private final String myVersion;

  public InstallerData(@Nullable File javaDir, @Nullable File androidSrc,
                       @Nullable File androidDest, boolean createAvd,
                       @Nullable String timestamp, @Nullable String version) {
    myJavaDir = javaDir;
    myAndroidSrc = androidSrc;
    myAndroidDest = androidDest;
    myCreateAvd = createAvd;
    myTimestamp = timestamp;
    myVersion = version;
  }

  @Nullable
  private static InstallerData parse() {
    Map<String, String> properties = readProperties();
    if (properties == null) {
      return null;
    }
    String androidSdkPath = properties.get(PROPERTY_SDK);
    File androidDest = StringUtil.isEmptyOrSpaces(androidSdkPath) ? null : new File(androidSdkPath);
    return new InstallerData(getIfPathExists(properties, PROPERTY_JDK), getIfPathExists(properties, PROPERTY_SDK_REPO), androidDest,
                             Boolean.valueOf(properties.containsKey(PROPERTY_AVD) ? properties.get(PROPERTY_AVD) : "true"),
                             properties.get(PROPERTY_TIMESTAMP), properties.get(PROPERTY_VERSION));
  }

  @Nullable
  private static Map<String, String> readProperties() {
    try {
      // Firstrun properties file contains a series of "key=value" lines.
      File file = new File(AndroidLocation.getFolder(), PATH_FIRST_RUN_PROPERTIES);
      if (file.isFile()) {
        Map<String, String> properties = Maps.newHashMap();
        final List<String> lines = Files.readLines(file, Charsets.UTF_16LE);
        for (String line : lines) {
          int keyValueSeparator = line.indexOf('=');
          if (keyValueSeparator < 0) {
            continue;
          }
          final String key = line.substring(0, keyValueSeparator).trim();
          final String value = line.substring(keyValueSeparator + 1).trim();
          if (key.isEmpty()) {
            continue;
          }
          properties.put(key, value);
        }
        return properties;
      }
    }
    catch (AndroidLocation.AndroidLocationException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
  }

  @Nullable
  private static File getIfPathExists(Map<String, String> properties, String propertyName) {
    String path = properties.get(propertyName);
    if (!StringUtil.isEmptyOrSpaces(path)) {
      File file = new File(path);
      return file.isDirectory() ? file : null;
    }
    return null;
  }

  @VisibleForTesting
  public static synchronized void set(@Nullable InstallerData data) {
    Holder.INSTALLER_DATA = data;
  }

  public static boolean exists() {
    return Holder.INSTALLER_DATA != null;
  }

  @NotNull
  public static synchronized InstallerData get() {
    InstallerData data = Holder.INSTALLER_DATA;
    assert data != null;
    return data;
  }

  @Nullable
  public File getJavaDir() {
    return myJavaDir;
  }

  @Nullable
  public File getAndroidSrc() {
    return myAndroidSrc;
  }

  @Nullable
  public File getAndroidDest() {
    return myAndroidDest;
  }

  public boolean shouldCreateAvd() {
    return myCreateAvd;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add(PROPERTY_JDK, myJavaDir).add(PROPERTY_SDK_REPO, myAndroidSrc).add(PROPERTY_SDK, myAndroidDest)
      .add(PROPERTY_AVD, myCreateAvd).add(PROPERTY_TIMESTAMP, myTimestamp).toString();
  }

  public boolean hasValidSdkLocation() {
    File location = getAndroidDest();
    if (location == null) {
      return false;
    }
    else {
      String path = location.getAbsolutePath();
      WizardUtils.ValidationResult validationResult = WizardUtils.validateLocation(path, SdkComponentsStep.FIELD_SDK_LOCATION, false);
      return !validationResult.isError();
    }
  }

  public boolean hasValidJdkLocation() {
    File javaDir = getJavaDir();
    return javaDir != null && JdkDetection.validateJdkLocation(javaDir) == null;
  }

  @Nullable
  public String getTimestamp() {
    return myTimestamp;
  }

  public boolean isCurrentVersion() {
    String buildStr = Integer.toString(ApplicationInfo.getInstance().getBuild().getBuildNumber());
    return buildStr.equals(myVersion);
  }

  private static class Holder {
    @Nullable private static InstallerData INSTALLER_DATA = parse();
  }
}
