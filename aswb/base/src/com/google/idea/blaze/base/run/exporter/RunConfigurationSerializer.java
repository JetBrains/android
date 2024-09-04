/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.exporter;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import org.jdom.Element;
import org.jdom.JDOMException;

/** Utility methods for converting run configuration to/from XML. */
public class RunConfigurationSerializer {

  private static final Logger logger = Logger.getInstance(RunConfigurationSerializer.class);

  @VisibleForTesting static final String TEMPLATE_RUN_CONFIG_NAME_PREFIX = "Imported Template for ";
  @VisibleForTesting static final String WORKSPACE_ROOT_VARIABLE_NAME = "WORKSPACE_ROOT";

  private static void setWorkspacePathVariable(Project project) {
    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
    if (root != null) {
      PathMacros.getInstance().setMacro(WORKSPACE_ROOT_VARIABLE_NAME, root.toString());
    }
  }

  private static void clearWorkspacePathVariable() {
    PathMacros.getInstance().setMacro(WORKSPACE_ROOT_VARIABLE_NAME, null);
  }

  private static synchronized void runWithPathVariableSet(Project project, Runnable runnable) {
    try {
      setWorkspacePathVariable(project);
      runnable.run();
    } finally {
      clearWorkspacePathVariable();
    }
  }

  public static Element writeToXml(RunConfiguration configuration) {
    RunnerAndConfigurationSettings settings =
        RunManagerImpl.getInstanceImpl(configuration.getProject()).getSettings(configuration);
    Element element = new Element("configuration");
    try {
      runWithPathVariableSet(
          configuration.getProject(),
          () -> ((RunnerAndConfigurationSettingsImpl) settings).writeExternal(element));
    } catch (WriteExternalException e) {
      logger.warn("Error serializing run configuration to XML", e);
    }
    return element;
  }

  /**
   * Parses a RunConfiguration from the given XML file, and adds it to the project, if there's not
   * already a run configuration with the same name and type,
   */
  public static void loadFromXmlIgnoreExisting(Project project, File xmlFile) {
    try {
      Element runConfig = JDOMUtil.load(xmlFile);
      // We don't support importing/exporting templates. Turn it into a normal run config.
      normalizeTemplateRunConfig(runConfig);
      loadFromXmlElementIgnoreExisting(project, runConfig);
    } catch (InvalidDataException | JDOMException | IOException e) {
      logger.warn("Error parsing run configuration from XML", e);
    }
  }

  /**
   * Turn a template run config into a normal run config by doing the following:
   *
   * <ul>
   *   <li>Sets the "default" attribute to "false" on a configuration element if the "default"
   *       attribute is present. This effectively turns a template configuration into a normal
   *       configuration because "default=true" is what indicates a template configuration.
   *   <li>Sets the "name" attribute to #TEMPLATE_RUN_CONFIG_NAME_PREFIX + factoryName attribute.
   *       This makes it clear to the user what the run-config is, because or else it will just show
   *       as "Untitled".
   * </ul>
   *
   * @see com.intellij.execution.impl.RunnerAndConfigurationSettingsImplKt#TEMPLATE_FLAG_ATTRIBUTE
   * @param element The element to be modified.
   */
  @VisibleForTesting
  static void normalizeTemplateRunConfig(Element element) {
    if (element.getAttribute("default") != null
        && element.getAttributeValue("default").equals("true")) {
      element.setAttribute("default", "false");
      element.setAttribute(
          "name", TEMPLATE_RUN_CONFIG_NAME_PREFIX + element.getAttributeValue("factoryName"));
    }
  }

  /**
   * Parses a RunConfiguration from the given XML element, and adds it to the project, if there's
   * not already a run configuration with the same name and type,
   */
  @VisibleForTesting
  static void loadFromXmlElementIgnoreExisting(Project project, Element element)
      throws InvalidDataException {
    if (!shouldLoadConfiguration(project, element)) {
      return;
    }
    runWithPathVariableSet(
        project,
        () -> {
          RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
          RunnerAndConfigurationSettings settings = runManager.loadConfiguration(element, false);
          RunConfiguration config = settings != null ? settings.getConfiguration() : null;
          if (config instanceof BlazeRunConfiguration) {
            ((BlazeRunConfiguration) config).setKeepInSync(true);
          }
          if (runManager.getSelectedConfiguration() == null) {
            runManager.setSelectedConfiguration(settings);
          }
        });
  }

  /**
   * Deserializes the configuration represented by the given XML element, then searches for an
   * existing run configuration in the project with the same name and type.
   */
  @Nullable
  @VisibleForTesting
  static RunnerAndConfigurationSettings findExisting(Project project, Element element)
      throws InvalidDataException {
    RunManagerImpl manager = RunManagerImpl.getInstanceImpl(project);
    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(manager);
    settings.readExternal(element, /* isStoredInDotIdeaFolder= */ false);
    RunConfiguration config = settings.getConfiguration();
    if (config == null) {
      return null;
    }
    return manager.findConfigurationByTypeAndName(config.getType().getId(), config.getName());
  }

  /**
   * Returns true if there's either no matching configuration already in the project, or the
   * matching configuration is marked as 'keep in sync'.
   */
  @VisibleForTesting
  static boolean shouldLoadConfiguration(Project project, Element element)
      throws InvalidDataException {
    RunnerAndConfigurationSettings existing = findExisting(project, element);
    if (existing == null) {
      return true;
    }
    RunConfiguration config = existing.getConfiguration();
    if (!(config instanceof BlazeRunConfiguration)) {
      // always overwrite non-blaze run configurations
      return true;
    }
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    Boolean keepInSync = blazeConfig.getKeepInSync();
    if (keepInSync == null) {
      // if the matching configuration was never previously imported, don't overwrite, but activate
      // the UI option to keep it in sync.
      blazeConfig.setKeepInSync(false);
      return false;
    }
    return keepInSync;
  }
}
