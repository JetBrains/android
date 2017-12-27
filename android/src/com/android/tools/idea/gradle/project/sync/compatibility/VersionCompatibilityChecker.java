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

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.SystemProperties;
import org.intellij.lang.annotations.Language;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.intellij.openapi.util.JDOMUtil.writeDocument;

/**
 * Compatibility checks between different components of a Gradle-based Android project (e.g. Gradle version vs. Android Gradle plugin
 * version.)
 * <p>
 * The checks are performed based on metadata specified in android-component-compatibility.xml, where you can specify that when a component
 * has certain version range, requires another component(s) to be in a certain version range as well.
 * </p>
 * <p>
 * For example, Gradle 2.4 (or newer) requites Android Gradle plugin 1.2 or (or newer.) This relationship can be expressed in XML as:
 * <pre>
 * &lt;check failureType=&quot;error&quot;&gt;
 *   &lt;!-- 2.4+ can also be written as [2.4, +) --&gt;
 *   &lt;component name=&quot;gradle&quot; version=&quot;2.4+&quot;&gt;
 *     &lt;requires name=&quot;gradle-plugin&quot; version=&quot;[1.2.0, +)&quot;&gt;
 *       &lt;failureMsg&gt;
 * &lt;![CDATA[
 * Please use Android Gradle plugin 1.2.0 or newer.
 * ]]&gt;
 *       &lt;/failureMsg&gt;
 *     &lt;/requires&gt;
 *   &lt;/component&gt;
 * &lt;/check&gt;
 * </pre>
 * </p>
 */
public class VersionCompatibilityChecker {
  public static final String VERSION_COMPATIBILITY_ISSUE_GROUP = "Version Compatibility Issues";

  @NotNull private CompatibilityChecksMetadata myMetadata = new CompatibilityChecksMetadata(1);

  @NotNull
  public static VersionCompatibilityChecker getInstance() {
    return ServiceManager.getService(VersionCompatibilityChecker.class);
  }

  public VersionCompatibilityChecker() {
    reloadMetadata();
  }

  public void reloadMetadata() {
    myMetadata = CompatibilityChecksMetadata.reload();
  }

  @VisibleForTesting
  void reloadMetadataForTesting(@NotNull @Language("XML") String metadata) throws JDOMException, IOException {
    myMetadata = CompatibilityChecksMetadata.reloadForTesting(metadata);
  }

  /**
   * Updates the version metadata with the given XML document.
   *
   * @param metadata the XML document containing the new metadata.
   * @return {@code true} if the metadata was updated, {@code false} otherwise.
   */
  boolean updateMetadata(@NotNull Document metadata) {
    try {
      CompatibilityChecksMetadata updated = CompatibilityChecksMetadata.load(metadata.getRootElement());
      if (updated.getDataVersion() > myMetadata.getDataVersion()) {
        myMetadata = updated;

        File metadataFilePath = CompatibilityChecksMetadata.getSourceFilePath();
        writeDocument(metadata, metadataFilePath, SystemProperties.getLineSeparator());
        getLogger().info("Saved component version metadata to: " + metadataFilePath);
        return true;
      }
    }
    catch (Throwable e) {
      getLogger().info("Failed to update component version metadata", e);
    }
    return false;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(VersionCompatibilityChecker.class);
  }

  public void checkAndReportComponentIncompatibilities(@NotNull Project project) {
    GradleSyncMessages.getInstance(project).removeMessages(VERSION_COMPATIBILITY_ISSUE_GROUP);

    ComponentVersionAndReaderCache cache = new ComponentVersionAndReaderCache();
    Map<String, VersionIncompatibility> incompatibilitiesByCheck = Maps.newHashMap();

    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      collectComponentIncompatibilities(module, incompatibilitiesByCheck, cache);
    }

    if (incompatibilitiesByCheck.isEmpty()) {
      return;
    }

    boolean hasErrors = false;

    for (VersionIncompatibility versionIncompatibility : incompatibilitiesByCheck.values()) {
      if (versionIncompatibility.getType() == ERROR) {
        hasErrors = true;
      }
      versionIncompatibility.reportMessages(project);
    }

    if (hasErrors) {
      GradleSyncState.getInstance(project).getSummary().setSyncErrorsFound(true);
    }
  }

  private void collectComponentIncompatibilities(@NotNull Module module,
                                                 @NotNull Map<String, VersionIncompatibility> incompatibilitiesByCheck,
                                                 @NotNull ComponentVersionAndReaderCache cache) {
    for (CompatibilityCheck check : myMetadata.getCompatibilityChecks()) {
      Component component = check.getComponent();
      Pair<ComponentVersionReader, String> readerAndVersion = getComponentVersion(component, module, cache);
      if (readerAndVersion == null) {
        continue;
      }
      String version = readerAndVersion.getSecond();
      if (!component.getVersionRange().contains(version)) {
        continue;
      }

      for (Component requirement : component.getRequirements()) {
        Pair<ComponentVersionReader, String> readerAndRequirementVersion = getComponentVersion(requirement, module, cache);
        if (readerAndRequirementVersion == null) {
          continue;
        }
        String requirementVersion = readerAndRequirementVersion.getSecond();
        if (requirement.getVersionRange().contains(requirementVersion)) {
          continue;
        }
        String id;
        boolean projectLevelCheck = readerAndVersion.getFirst().isProjectLevel();
        String componentName = check.getComponent().getName();
        if (projectLevelCheck) {
          id = componentName;
        }
        else {
          id = module.getName() + "." + componentName;
        }
        VersionIncompatibility versionIncompatibility = incompatibilitiesByCheck.get(id);
        if (versionIncompatibility == null) {
          ComponentVersionReader reader = readerAndRequirementVersion.getFirst();
          versionIncompatibility = new VersionIncompatibility(module, check, readerAndVersion, requirement, reader);
          incompatibilitiesByCheck.put(id, versionIncompatibility);
        }

        if (readerAndRequirementVersion.getFirst().isProjectLevel()) {
          // If the requirement is at project level, show only one message, instead of one message per module.
          if (!versionIncompatibility.hasMessages()) {
            String msg = String.format("but project is using version %1$s.", requirementVersion);
            versionIncompatibility.addMessage(msg);
          }
          continue;
        }

        String msg = String.format("Module '%1$s' is using version %2$s", module.getName(), requirementVersion);
        versionIncompatibility.addMessage(msg);
      }
    }
  }

  @Nullable
  private Pair<ComponentVersionReader, String> getComponentVersion(@NotNull Component component,
                                                                   @NotNull Module module,
                                                                   @NotNull ComponentVersionAndReaderCache cache) {
    String componentName = component.getName();
    // First check if the value is already cached for project
    Pair<ComponentVersionReader, String> readerAndVersion = cache.projectComponents.get(componentName);
    if (readerAndVersion == null) {
      // Value has not been cached for project, check for cached value for module
      Map<String, Pair<ComponentVersionReader, String>> componentVersionsByModule = cache.moduleComponents.get(componentName);
      if (componentVersionsByModule != null) {
        readerAndVersion = componentVersionsByModule.get(module.getName());
      }
    }
    if (readerAndVersion != null) {
      return readerAndVersion;
    }
    // There is no cached value for this component's version. Go ahead and read it from project.
    ComponentVersionReader reader = myMetadata.findComponentVersionReader(componentName);
    if (reader == null) {
      getLogger().info(String.format("Failed to find version reader for component '%1$s'", componentName));
      return null;
    }
    if (!reader.appliesTo(module)) {
      // Silently quit (e.g. getting Android model version from a Java library module)
      return null;
    }
    String version = reader.getComponentVersion(module);
    if (version != null) {
      // Cache the value for potential future use
      readerAndVersion = Pair.create(reader, version);

      if (reader.isProjectLevel()) {
        cache.projectComponents.put(componentName, readerAndVersion);
      }
      else {
        Map<String, Pair<ComponentVersionReader, String>> componentVersionsByModule =
          cache.moduleComponents.computeIfAbsent(componentName, k -> Maps.newHashMap());
        componentVersionsByModule.put(module.getName(), readerAndVersion);
      }

      return readerAndVersion;
    }

    Project project = module.getProject();
    String msg = String.format("Failed to read version for component '%1$s'", componentName);
    if (reader.isProjectLevel()) {
      msg += String.format(" for project '%1$s'", project.getName());
    }
    else {
      msg += String.format(" for module '%1$s', in project '%2$s'", module.getName(), project.getName());
    }
    getLogger().info(msg);

    return null;
  }

  private static class ComponentVersionAndReaderCache {
    // [ Component name -> Version reader, Component version ]
    @NotNull final Map<String, Pair<ComponentVersionReader, String>> projectComponents = new HashMap<>();

    // [ Component name -> [ Module Name -> Version reader, Component version ] ]
    @NotNull final Map<String, Map<String, Pair<ComponentVersionReader, String>>> moduleComponents = new HashMap<>();
  }
}
