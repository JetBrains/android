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
package com.android.tools.idea.gradle.project.compatibility;

import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.JdomKt;
import com.intellij.util.SystemProperties;
import org.intellij.lang.annotations.Language;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNHANDLED_SYNC_ISSUE_TYPE;
import static com.android.tools.idea.gradle.project.compatibility.ComponentVersionReader.*;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.io.Closeables.close;
import static com.intellij.openapi.util.JDOMUtil.load;
import static com.intellij.openapi.util.JDOMUtil.writeDocument;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.PlatformUtils.isIntelliJ;

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
public class VersionCompatibilityService {
  private static Logger LOG = Logger.getInstance(VersionCompatibilityService.class);

  @NonNls private static final String BUILD_FILE_PREFIX = "buildFile:";
  @NonNls private static final String METADATA_FILE_NAME = "android-component-compatibility.xml";

  @NotNull private VersionMetadata myMetadata = new VersionMetadata(1);

  @NotNull
  public static VersionCompatibilityService getInstance() {
    return ServiceManager.getService(VersionCompatibilityService.class);
  }

  public VersionCompatibilityService() {
    reloadMetadata();
  }

  @VisibleForTesting
  public void reloadMetadata() {
    File metadataFilePath = getMetadataFilePath();
    if (metadataFilePath.isFile()) {
      try {
        Element root = load(metadataFilePath);
        myMetadata = loadMetadata(root);
      }
      catch (Throwable e) {
        LOG.info("Failed to load/parse file '" + metadataFilePath.getPath() + "'. Loading metadata from local file.", e);
        loadLocalMetadata();
      }
    }
    else {
      loadLocalMetadata();
    }
  }

  @VisibleForTesting
  public void reloadMetadataForTesting(@NotNull @Language("XML") String metadata) throws JDOMException, IOException {
    myMetadata = loadMetadata(JdomKt.loadElement(metadata));
  }

  /**
   * Updates the version metadata with the given XML document.
   *
   * @param metadata the XML document containing the new metadata.
   * @return {@code true} if the metadata was updated, {@code false} otherwise.
   */
  boolean updateMetadata(@NotNull Document metadata) {
    try {
      VersionMetadata updated = loadMetadata(metadata.getRootElement());
      if (updated.dataVersion > myMetadata.dataVersion) {
        myMetadata = updated;

        File metadataFilePath = getMetadataFilePath();
        writeDocument(metadata, metadataFilePath, SystemProperties.getLineSeparator());
        LOG.info("Saved component version metadata to: " + metadataFilePath);
        return true;
      }
    }
    catch (Throwable e) {
      LOG.info("Failed to update component version metadata", e);
    }
    return false;
  }

  @VisibleForTesting
  @NotNull
  public static File getMetadataFilePath() {
    File configPath = new File(toSystemDependentName(PathManager.getConfigPath()));
    return new File(configPath, METADATA_FILE_NAME);
  }

  private void loadLocalMetadata() {
    InputStream inputStream = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      inputStream = getClass().getResourceAsStream(METADATA_FILE_NAME);
      myMetadata = loadMetadata(JdomKt.loadElement(inputStream));
    }
    catch (Throwable e) {
      // Impossible to happen.
      LOG.info("Failed to load/parse local metadata file.", e);
    }
    finally {
      try {
        close(inputStream, true);
      }
      catch (IOException ignored) {
      }
    }
  }

  @NotNull
  private static VersionMetadata loadMetadata(@NotNull Element root) {
    String dataVersionText = root.getAttributeValue("version");
    int dataVersion = 1;
    try {
      dataVersion = Integer.parseInt(dataVersionText);
    }
    catch (NumberFormatException ignored) {
    }

    VersionMetadata metadata = new VersionMetadata(dataVersion);
    for (Element checkElement : root.getChildren("check")) {
      Element componentElement = checkElement.getChild("component");
      ComponentVersion version = createComponentVersion(componentElement, metadata);
      for (Element requirementElement : componentElement.getChildren("requires")) {
        version.requirements.add(createComponentVersion(requirementElement, metadata));
      }

      String type = checkElement.getAttributeValue("failureType");
      CompatibilityCheck check = new CompatibilityCheck(version, getFailureType(type));
      metadata.compatibilityChecks.add(check);
    }
    return metadata;
  }

  @NotNull
  private static Message.Type getFailureType(@NotNull String value) {
    Message.Type type = Message.Type.find(value);
    return type != null ? type : Message.Type.ERROR;
  }

  @NotNull
  private static ComponentVersion createComponentVersion(@NotNull Element xmlElement, @NotNull VersionMetadata metadata) {
    String name = xmlElement.getAttribute("name").getValue();
    if (name.startsWith(BUILD_FILE_PREFIX)) {
      name = name.substring(BUILD_FILE_PREFIX.length());
      ComponentVersionReader reader = metadata.versionReadersByComponentName.get(name);
      if (reader == null) {
        metadata.versionReadersByComponentName.put(name, new BuildFileComponentVersionReader(name));
      }
    }
    String version = xmlElement.getAttributeValue("version");
    String failureMsg = null;
    Element failureMsgElement = xmlElement.getChild("failureMsg");
    if (failureMsgElement != null) {
      failureMsg = emptyToNull(failureMsgElement.getTextNormalize());
    }
    return new ComponentVersion(name, version, failureMsg);
  }

  @NotNull
  public List<VersionIncompatibilityMessage> checkComponentCompatibility(@NotNull Project project) {
    CompatibilityChecker checker = new CompatibilityChecker(project, myMetadata);
    return checker.execute();
  }

  private static class CompatibilityChecker {
    @NotNull private final Project myProject;
    @NotNull private final VersionMetadata myMetadata;

    // [ Component name -> Version reader, Component version ]
    @NotNull private final Map<String, Pair<ComponentVersionReader, String>> myProjectComponentVersionCache = Maps.newHashMap();

    // [ Component name -> [ Module Name -> Version reader, Component version ] ]
    @NotNull private final Map<String, Map<String, Pair<ComponentVersionReader, String>>> myModuleComponentVersionCache = Maps.newHashMap();

    CompatibilityChecker(@NotNull Project project, @NotNull VersionMetadata metadata) {
      myProject = project;
      myMetadata = metadata;
    }

    @NotNull
    List<VersionIncompatibilityMessage> execute() {
      Map<String, ComponentVersion.Incompatibility> incompatibilitiesByCheck = Maps.newHashMap();

      Module[] modules = ModuleManager.getInstance(myProject).getModules();
      for (Module module : modules) {
        for (CompatibilityCheck check : myMetadata.compatibilityChecks) {
          ComponentVersion componentVersion = check.myComponentVersion;
          Pair<ComponentVersionReader, String> readerAndVersion = getComponentVersion(componentVersion, module);
          if (readerAndVersion == null) {
            continue;
          }
          String version = readerAndVersion.getSecond();
          if (!componentVersion.versionRange.contains(version)) {
            continue;
          }

          for (ComponentVersion requirement : componentVersion.requirements) {
            Pair<ComponentVersionReader, String> readerAndRequirementVersion = getComponentVersion(requirement, module);
            if (readerAndRequirementVersion == null) {
              continue;
            }
            String requirementVersion = readerAndRequirementVersion.getSecond();
            if (!requirement.versionRange.contains(requirementVersion)) {
              String id;
              boolean projectLevelCheck = readerAndVersion.getFirst().isProjectLevel();
              if (projectLevelCheck) {
                id = check.myComponentVersion.componentName;
              }
              else {
                id = module.getName() + "." + check.myComponentVersion.componentName;
              }
              ComponentVersion.Incompatibility incompatibility = incompatibilitiesByCheck.get(id);
              if (incompatibility == null) {
                ComponentVersionReader requirementVersionReader = readerAndRequirementVersion.getFirst();
                incompatibility =
                  new ComponentVersion.Incompatibility(module, check, readerAndVersion, requirement, requirementVersionReader);
                incompatibilitiesByCheck.put(id, incompatibility);
              }

              if (readerAndRequirementVersion.getFirst().isProjectLevel()) {
                // If the requirement is at project level, show only one message, instead of one message per module.
                if (incompatibility.messages.isEmpty()) {
                  String msg = String.format(" but project is using version %1$s.", requirementVersion);
                  incompatibility.messages.add(msg);
                }
              }
              else {
                String msg = String.format("Module '%1$s' is using version %2$s", module.getName(), requirementVersion);
                incompatibility.messages.add(msg);
              }
            }
          }
        }
      }

      if (incompatibilitiesByCheck.isEmpty()) {
        return Collections.emptyList();
      }

      List<VersionIncompatibilityMessage> failureMessages = Lists.newArrayList();
      for (ComponentVersion.Incompatibility incompatibility : incompatibilitiesByCheck.values()) {
        CompatibilityCheck check = incompatibility.compatibilityCheck;

        Pair<ComponentVersionReader, String> readerAndVersion = incompatibility.readerAndVersion;
        ComponentVersionReader reader = readerAndVersion.getFirst();
        String componentName = reader.getComponentName();
        String version = readerAndVersion.getSecond();

        ComponentVersionReader requirementVersionReader = incompatibility.requirementVersionReader;
        String requirementComponentName = requirementVersionReader.getComponentName();

        StringBuilder msg = new StringBuilder();
        msg.append(componentName).append(" ").append(version);

        Module module = incompatibility.module;
        FileLocation location = reader.getVersionSource(module);
        if (!reader.isProjectLevel() && location == null) {
          msg.append(", in module '").append(module.getName()).append(",'");
        }
        msg.append(" requires ").append(requirementComponentName).append(" ");

        ComponentVersion requirement = incompatibility.requirement;
        VersionRange requirementVersionRange = requirement.versionRange;

        msg.append(requirementVersionRange.getDescription());

        List<String> messages = incompatibility.messages;
        if (messages.size() == 1) {
          msg.append(" ").append(messages.get(0));
        }
        else {
          msg.append("<ul>");
          for (String message : messages) {
            msg.append("<li>").append(message).append("</li>");
          }
          msg.append("</ul>");
        }

        Message message;
        String group = UNHANDLED_SYNC_ISSUE_TYPE;
        Message.Type failureType = check.failureType;

        List<String> textLines = Lists.newArrayList();
        textLines.add(msg.toString());
        String failureMsg = requirement.failureMsg;
        if (failureMsg != null) {
          List<String> lines = Splitter.on("\\n").omitEmptyStrings().splitToList(failureMsg);
          textLines.addAll(lines);
        }
        String[] text = toStringArray(textLines);

        if (location != null) {
          message = new Message(myProject, group, failureType, location.file, location.lineNumber, location.column, text);
        }
        else {
          message = new Message(group, failureType, text);
        }

        List<NotificationHyperlink> quickFixes = Lists.newArrayList();
        quickFixes.addAll(reader.getQuickFixes(module, null, null));
        quickFixes.addAll(requirementVersionReader.getQuickFixes(module, requirementVersionRange, location));

        failureMessages.add(new VersionIncompatibilityMessage(message, quickFixes));
      }

      return failureMessages;
    }

    @Nullable
    private Pair<ComponentVersionReader, String> getComponentVersion(@NotNull ComponentVersion componentVersion, @NotNull Module module) {
      String componentName = componentVersion.componentName;
      // First check if the value is already cached for project
      Pair<ComponentVersionReader, String> readerAndVersion = myProjectComponentVersionCache.get(componentName);
      if (readerAndVersion == null) {
        // Value has not been cached for project, check for cached value for module
        Map<String, Pair<ComponentVersionReader, String>> componentVersionsByModule = myModuleComponentVersionCache.get(componentName);
        if (componentVersionsByModule != null) {
          readerAndVersion = componentVersionsByModule.get(module.getName());
        }
      }
      if (readerAndVersion == null) {
        // There is no cached value for this component's version. Go ahead and read it from project.
        ComponentVersionReader reader = myMetadata.versionReadersByComponentName.get(componentName);
        if (reader == null) {
          LOG.info(String.format("Failed to find version reader for component '%1$s'", componentName));
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
            myProjectComponentVersionCache.put(componentName, readerAndVersion);
          }
          else {
            Map<String, Pair<ComponentVersionReader, String>> componentVersionsByModule = myModuleComponentVersionCache.get(componentName);
            if (componentVersionsByModule == null) {
              componentVersionsByModule = Maps.newHashMap();
              myModuleComponentVersionCache.put(componentName, componentVersionsByModule);
            }
            componentVersionsByModule.put(module.getName(), readerAndVersion);
          }
        }
        else {
          Project project = module.getProject();
          String msg = String.format("Failed to read version for component '%1$s'", componentName);
          if (reader.isProjectLevel()) {
            msg += String.format(" for project '%1$s'", project.getName());
          }
          else {
            msg += String.format(" for module '%1$s', in project '%2$s'", module.getName(), project.getName());
          }
          LOG.info(msg);
        }
      }

      return readerAndVersion;
    }
  }

  private static class VersionMetadata {
    final int dataVersion;

    @NotNull private final List<CompatibilityCheck> compatibilityChecks = Lists.newArrayList();
    @NotNull private final Map<String, ComponentVersionReader> versionReadersByComponentName = Maps.newConcurrentMap();

    VersionMetadata(int dataVersion) {
      this.dataVersion = dataVersion;
      versionReadersByComponentName.put("gradle", GRADLE);
      versionReadersByComponentName.put("android-gradle-plugin", ANDROID_GRADLE_PLUGIN);
      if (isAndroidStudio()) {
        versionReadersByComponentName.put("android-studio", IDE);
      }
      else if (isIntelliJ()) {
        versionReadersByComponentName.put("idea", IDE);
      }
    }
  }

  private static class CompatibilityCheck {
    @NotNull final ComponentVersion myComponentVersion;
    @NotNull final Message.Type failureType;

    CompatibilityCheck(@NotNull ComponentVersion componentVersion, @NotNull Message.Type failureType) {
      this.myComponentVersion = componentVersion;
      this.failureType = failureType;
    }
  }

  private static class ComponentVersion {
    @NotNull final String componentName;
    @NotNull final VersionRange versionRange;
    @Nullable final String failureMsg;

    @NotNull final List<ComponentVersion> requirements = Lists.newArrayList();

    ComponentVersion(@NotNull String componentName, @NotNull String version, @Nullable String failureMsg) {
      this.componentName = componentName;
      versionRange = VersionRange.parse(version);
      this.failureMsg = failureMsg;
    }

    private static class Incompatibility {
      @NotNull final Module module;
      @NotNull final CompatibilityCheck compatibilityCheck;
      @NotNull final Pair<ComponentVersionReader, String> readerAndVersion;
      @NotNull final ComponentVersion requirement;
      @NotNull final ComponentVersionReader requirementVersionReader;

      @NotNull final List<String> messages = Lists.newArrayList();

      Incompatibility(@NotNull Module module,
                      @NotNull CompatibilityCheck compatibilityCheck,
                      @NotNull Pair<ComponentVersionReader, String> readerAndVersion,
                      @NotNull ComponentVersion requirement,
                      @NotNull ComponentVersionReader requirementVersionReader) {
        this.module = module;
        this.compatibilityCheck = compatibilityCheck;
        this.readerAndVersion = readerAndVersion;
        this.requirement = requirement;
        this.requirementVersionReader = requirementVersionReader;
      }
    }
  }

  public static class VersionIncompatibilityMessage {
    @NotNull private final Message myMessage;
    @NotNull private final NotificationHyperlink[] myQuickFixes;

    VersionIncompatibilityMessage(@NotNull Message message, @NotNull List<NotificationHyperlink> quickFixes) {
      myMessage = message;
      myQuickFixes = quickFixes.toArray(new NotificationHyperlink[quickFixes.size()]);
    }

    @NotNull
    public Message getMessage() {
      return myMessage;
    }

    @NotNull
    public NotificationHyperlink[] getQuickFixes() {
      return myQuickFixes;
    }
  }
}
