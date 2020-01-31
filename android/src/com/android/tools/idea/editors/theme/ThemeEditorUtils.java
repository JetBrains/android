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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.tools.idea.AndroidTextUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.Component;
import java.awt.Font;
import java.util.Collection;
import java.util.List;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for static methods which are used in different classes of theme editor
 */
public class ThemeEditorUtils {
  private ThemeEditorUtils() {
  }

  static int getMinApiLevel(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return 1;
    }
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(facet);
    return moduleInfo.getMinSdkVersion().getApiLevel();
  }

  /**
   * Returns the smallest api level of the folders in folderNames.
   * Returns Integer.MAX_VALUE if folderNames is empty.
   */
  public static int getMinFolderApi(@NotNull List<String> folderNames, @NotNull Module module) {
    int minFolderApi = Integer.MAX_VALUE;
    int minModuleApi = getMinApiLevel(module);
    for (String folderName : folderNames) {
      FolderConfiguration folderConfig = FolderConfiguration.getConfigForFolder(folderName);
      if (folderConfig != null) {
        VersionQualifier version = folderConfig.getVersionQualifier();
        int folderApi = version != null ? version.getVersion() : minModuleApi;
        minFolderApi = Math.min(minFolderApi, folderApi);
      }
    }
    return minFolderApi;
  }

  @NotNull
  public static Configuration getConfigurationForModule(@NotNull Module module) {
    Project project = module.getProject();
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : "moduleComboModel must contain only Android modules";

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(module);

    // Using the project virtual file to set up configuration for the theme editor
    // That fact is hard-coded in computeBestDevice() method in Configuration.java
    // BEWARE if attempting to modify to use a different virtual file
    final VirtualFile projectFile = project.getProjectFile();
    assert projectFile != null;

    return configurationManager.getConfiguration(projectFile);
  }

  @NotNull
  public static RenderTask configureRenderTask(@NotNull final Module module, @NotNull final Configuration configuration) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    final RenderService service = RenderService.getInstance(module.getProject());
    RenderLogger logger = new RenderLogger("ThemeEditorLogger", null);
    RenderTask task = service.taskBuilder(facet, configuration)
      .withLogger(logger)
      .buildSynchronously();
    assert task != null;
    task.getLayoutlibCallback().setLogger(logger);
    return task;
  }

  /**
   * Returns a string with the words concatenated into an enumeration w1, w2, ..., w(n-1) and wn
   */
  @NotNull
  public static String generateWordEnumeration(@NotNull Collection<String> words) {
    return AndroidTextUtils.generateCommaSeparatedList(words, "and");
  }

  @NotNull
  public static Font scaleFontForAttribute(@NotNull Font font) {
    // Use Math.ceil to ensure that the result is a font with an integer point size
    return font.deriveFont((float)Math.ceil(font.getSize() * ThemeEditorConstants.ATTRIBUTES_FONT_SCALE));
  }

  public static void setInheritsPopupMenuRecursive(JComponent comp) {
    comp.setInheritsPopupMenu(true);
    for (Component child : comp.getComponents()) {
      if (child instanceof JComponent) {
        setInheritsPopupMenuRecursive((JComponent)child);
      }
    }
  }
}
