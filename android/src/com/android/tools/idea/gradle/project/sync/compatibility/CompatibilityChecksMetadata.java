/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.compatibility;

import com.android.annotations.Nullable;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.compatibility.version.BuildFileComponentVersionReader;
import com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader;
import com.android.tools.idea.project.messages.MessageType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader.*;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.google.common.base.Strings.emptyToNull;

class CompatibilityChecksMetadata {
  @NonNls private static final String BUILD_FILE_PREFIX = "buildFile:";
  @NonNls private static final String METADATA_FILE_NAME = "android-component-compatibility.xml";

  private final int myDataVersion;

  @NotNull private final List<CompatibilityCheck> myCompatibilityChecks = new ArrayList<>();
  @NotNull private final Map<String, ComponentVersionReader> myReadersByComponentName = new ConcurrentHashMap<>();

  @NotNull
  static CompatibilityChecksMetadata reload() {
    File metadataFilePath = getSourceFilePath();
    if (metadataFilePath.isFile()) {
      try {
        Element root = JDOMUtil.load(metadataFilePath);
        return load(root);
      }
      catch (Throwable e) {
        String message = "Failed to load/parse file '" + metadataFilePath.getPath() + "'. Loading metadata from local file.";
        getLogger().info(message, e);
        return loadLocal();
      }
    }
    else {
      return loadLocal();
    }
  }

  @NotNull
  private static CompatibilityChecksMetadata loadLocal() {
    try (InputStream inputStream = CompatibilityChecksMetadata.class.getResourceAsStream(METADATA_FILE_NAME)) {
      Element root = JDOMUtil.load(inputStream);
      return load(root);
    }
    catch (RuntimeException e) {
      logFailureToReadLocalFile(e);
      throw e;
    }
    catch (Throwable e) {
      logFailureToReadLocalFile(e);
      throw new RuntimeException(e);
    }
  }

  private static void logFailureToReadLocalFile(@NotNull Throwable e) {
    getLogger().info("Failed to load/parse local metadata file.", e);
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(CompatibilityChecksMetadata.class);
  }

  @VisibleForTesting
  @NotNull
  static CompatibilityChecksMetadata reloadForTesting(@NotNull @Language("XML") String metadata) throws JDOMException, IOException {
    Element root = JDOMUtil.load(new StringReader(metadata));
    return load(root);
  }

  @NotNull
  static File getSourceFilePath() {
    File configPath = toSystemDependentPath(PathManager.getConfigPath());
    return new File(configPath, METADATA_FILE_NAME);
  }

  @NotNull
  static CompatibilityChecksMetadata load(@NotNull Element root) {
    String dataVersionText = root.getAttributeValue("version");
    int dataVersion = 1;
    try {
      dataVersion = Integer.parseInt(dataVersionText);
    }
    catch (NumberFormatException ignored) {
    }

    CompatibilityChecksMetadata metadata = new CompatibilityChecksMetadata(dataVersion);
    for (Element checkElement : root.getChildren("check")) {
      Element componentElement = checkElement.getChild("component");
      Component version = createComponent(componentElement, metadata);
      for (Element requirementElement : componentElement.getChildren("requires")) {
        version.addRequirement(createComponent(requirementElement, metadata));
      }

      String type = checkElement.getAttributeValue("failureType");
      CompatibilityCheck check = new CompatibilityCheck(version, getFailureType(type));
      metadata.myCompatibilityChecks.add(check);
    }
    return metadata;
  }

  @NotNull
  private static MessageType getFailureType(@NotNull String value) {
    MessageType type = MessageType.findByName(value);
    return type != null ? type : ERROR;
  }

  @NotNull
  private static Component createComponent(@NotNull Element xmlElement, @NotNull CompatibilityChecksMetadata metadata) {
    String name = xmlElement.getAttribute("name").getValue();
    if (name.startsWith(BUILD_FILE_PREFIX)) {
      name = name.substring(BUILD_FILE_PREFIX.length());
      metadata.addIfAbsent(name, new BuildFileComponentVersionReader(name));
    }
    String version = xmlElement.getAttributeValue("version");
    String failureMsg = null;
    Element failureMsgElement = xmlElement.getChild("failureMsg");
    if (failureMsgElement != null) {
      failureMsg = emptyToNull(failureMsgElement.getTextNormalize());
    }
    return new Component(name, version, failureMsg);
  }

  CompatibilityChecksMetadata(int dataVersion) {
    this.myDataVersion = dataVersion;
    myReadersByComponentName.put("gradle", GRADLE);
    myReadersByComponentName.put("android-gradle-plugin", ANDROID_GRADLE_PLUGIN);
    myReadersByComponentName.put("android-gradle-experimental-plugin", ANDROID_GRADLE_EXPERIMENTAL_PLUGIN);
    if (IdeInfo.getInstance().isAndroidStudio()) {
      myReadersByComponentName.put("android-studio", ANDROID_STUDIO);
    }
  }

  private void addIfAbsent(@NotNull String name, @NotNull ComponentVersionReader componentVersionReader) {
    myReadersByComponentName.putIfAbsent(name, componentVersionReader);
  }

  int getDataVersion() {
    return myDataVersion;
  }

  @Nullable
  ComponentVersionReader findComponentVersionReader(@NotNull String name) {
    return myReadersByComponentName.get(name);
  }

  @NotNull
  List<CompatibilityCheck> getCompatibilityChecks() {
    return ImmutableList.copyOf(myCompatibilityChecks);
  }

  @TestOnly
  @NotNull
  Map<String, ComponentVersionReader> getReadersByComponentName() {
    return myReadersByComponentName;
  }
}
