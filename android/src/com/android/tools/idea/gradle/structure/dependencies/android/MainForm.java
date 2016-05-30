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
package com.android.tools.idea.gradle.structure.dependencies.android;

import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.android.tools.idea.gradle.structure.model.PsModelNameComparator;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsBuildType;
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor;
import com.android.tools.idea.gradle.structure.model.android.dependency.PsNewDependencyScopes;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import icons.AndroidIcons;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.*;
import static com.android.tools.idea.gradle.structure.dependencies.android.Configuration.*;
import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.ui.SideBorder.LEFT;
import static com.intellij.ui.SideBorder.RIGHT;
import static com.intellij.util.ui.UIUtil.getTextFieldBackground;
import static com.intellij.util.ui.UIUtil.getTextFieldBorder;

class MainForm implements Disposable {
  @NonNls private static final String DEBUG_BUILD_TYPE = "debug";

  @NotNull private final ConfigurationsPanel myConfigurationsPanel;
  @NotNull private final BuildTypesPanel myBuildTypesPanel;
  @NotNull private final ProductFlavorsPanel myProductFlavorsPanel;

  @NotNull private final List<PsBuildType> myAllBuildTypes = Lists.newArrayList();
  @NotNull private final List<PsProductFlavor> myAllProductFlavors = Lists.newArrayList();
  @NotNull private final List<String> mySelectedScopeNames = Lists.newArrayList();

  @NotNull private final List<ToolWindowPanel> myToolWindowPanels = Lists.newArrayList();

  private JPanel myMainPanel;
  private JPanel myContentsPanel;

  private JXLabel myScopesLabel;

  MainForm(@NotNull PsAndroidModule module) {
    myScopesLabel.setBorder(getTextFieldBorder());
    myScopesLabel.setBackground(getTextFieldBackground());

    ScopesPanel scopesPanel = new ScopesPanel();
    myConfigurationsPanel = new ConfigurationsPanel();
    myConfigurationsPanel.add(newSelection -> updateScopes(), this);
    scopesPanel.createAndAddToolWindowPanel("Configurations", myConfigurationsPanel, myConfigurationsPanel.getPreferredFocusedComponent());

    module.forEachBuildType(myAllBuildTypes::add);
    Collections.sort(myAllBuildTypes, new PsModelNameComparator<>());
    myBuildTypesPanel = new BuildTypesPanel(module, myAllBuildTypes);
    myBuildTypesPanel.add(newSelection -> updateScopes(), this);
    scopesPanel.createAndAddToolWindowPanel("Build Types", myBuildTypesPanel, myBuildTypesPanel.getPreferredFocusedComponent());

    module.forEachProductFlavor(myAllProductFlavors::add);
    Collections.sort(myAllProductFlavors, new PsModelNameComparator<>());
    myProductFlavorsPanel = new ProductFlavorsPanel(myAllProductFlavors);
    myProductFlavorsPanel.add(newSelection -> updateScopes(), this);
    scopesPanel.createAndAddToolWindowPanel("Product Flavors", myProductFlavorsPanel, myProductFlavorsPanel.getPreferredFocusedComponent());

    myContentsPanel.add(scopesPanel, BorderLayout.CENTER);
    updateScopes();
  }

  private void updateScopes() {
    List<Configuration> configurations = myConfigurationsPanel.getSelectedConfigurations();
    List<PsBuildType> buildTypes = myBuildTypesPanel.getSelectedBuildTypes();
    List<PsProductFlavor> productFlavors = myProductFlavorsPanel.getSelectedProductFlavors();
    boolean allBuildTypesSelected = buildTypes.size() == myAllBuildTypes.size();
    boolean allProductFlavorsSelected = productFlavors.size() == myAllProductFlavors.size();

    mySelectedScopeNames.clear();
    mySelectedScopeNames.addAll(deduceScopes(configurations, buildTypes, productFlavors, allBuildTypesSelected, allProductFlavorsSelected));

    String text = "";
    int count = mySelectedScopeNames.size();
    if (count == 1) {
      text = mySelectedScopeNames.get(0);
    }
    else if (mySelectedScopeNames.size() > 1) {
      Collections.sort(mySelectedScopeNames);
      text = Joiner.on(", ").join(mySelectedScopeNames);
    }
    if (text.isEmpty()) {
      text = " ";
    }
    myScopesLabel.setText(text);
  }

  @VisibleForTesting
  static List<String> deduceScopes(@NotNull List<Configuration> configurations,
                                   @NotNull List<PsBuildType> buildTypes,
                                   @NotNull List<PsProductFlavor> productFlavors,
                                   boolean allBuildTypesSelected,
                                   boolean allProductFlavorsSelected) {
    if (configurations.contains(MAIN)) {
      if (allBuildTypesSelected && allProductFlavorsSelected) {
        return Collections.singletonList(COMPILE);
      }
      List<String> scopes = Lists.newArrayList();
      if (!allBuildTypesSelected) {
        scopes.addAll(deduceCompileScopes(buildTypes));
      }
      if (!allProductFlavorsSelected) {
        scopes.addAll(deduceCompileScopes(productFlavors));
      }
      return scopes;
    }

    List<String> scopes = Lists.newArrayList();

    if (configurations.contains(ANDROID_TEST)) {
      boolean debugBuildTypeChecked = false;
      for (PsBuildType buildType : buildTypes) {
        if (buildType.getName().equals(DEBUG_BUILD_TYPE)) {
          debugBuildTypeChecked = true;
          break;
        }
      }
      if (debugBuildTypeChecked) {
        if (allProductFlavorsSelected) {
          scopes.add(ANDROID_TEST_COMPILE);
        }
        else {
          scopes.addAll(deduceScopes(productFlavors, "androidTest", "Compile"));
        }
      }
    }

    if (configurations.contains(UNIT_TEST)) {
      if (allBuildTypesSelected && allProductFlavorsSelected) {
        scopes.add(TEST_COMPILE);
      }
      else {
        if (!allBuildTypesSelected) {
          scopes.addAll(deduceUnitTestScopes(buildTypes));
        }
        if (!allProductFlavorsSelected) {
          scopes.addAll(deduceUnitTestScopes(productFlavors));
        }
      }
    }

    return scopes;
  }

  @NotNull
  private static List<String> deduceCompileScopes(@NotNull List<? extends PsModel> models) {
    return deduceScopes(models, "", "Compile");
  }

  @NotNull
  private static List<String> deduceUnitTestScopes(@NotNull List<? extends PsModel> models) {
    return deduceScopes(models, "test", "Compile");
  }

  @NotNull
  private static List<String> deduceScopes(@NotNull List<? extends PsModel> models, @NotNull String prefix, @NotNull String suffix) {
    List<String> configurationNames = Lists.newArrayList();
    for (PsModel model : models) {
      StringBuilder buffer = new StringBuilder();
      if (prefix.isEmpty()) {
        buffer.append(model.getName());
      }
      else {
        buffer.append(prefix).append(capitalize(model.getName()));
      }
      buffer.append(suffix);
      configurationNames.add(buffer.toString());
    }
    return configurationNames;
  }

  @NotNull
  JPanel getPanel() {
    return myMainPanel;
  }

  @Nullable
  ValidationInfo validateInput() {
    List<Configuration> configurations = myConfigurationsPanel.getSelectedConfigurations();
    if (configurations.isEmpty()) {
      return new ValidationInfo("Please select at least one configuration", myConfigurationsPanel);
    }
    if (mySelectedScopeNames.isEmpty()) {
      if (configurations.size() == 1 && configurations.contains(ANDROID_TEST)) {
        List<PsBuildType> buildTypes = myBuildTypesPanel.getSelectedBuildTypes();
        boolean hasDebugBuildType = false;
        for (PsBuildType buildType : buildTypes) {
          if (buildType.getName().equals(DEBUG_BUILD_TYPE)) {
            hasDebugBuildType = true;
            break;
          }
        }
        if (!hasDebugBuildType) {
          return new ValidationInfo("For 'Android Tests', the 'debug' build type must be selected", myBuildTypesPanel);
        }
      }
    }
    return null;
  }

  @Nullable
  public PsNewDependencyScopes getNewScopes() {
    if (!mySelectedScopeNames.isEmpty()) {
      List<String> artifactNames = Lists.newArrayList();
      myConfigurationsPanel.getSelectedConfigurations().forEach(configuration -> artifactNames.add(configuration.getArtifactName()));

      return new PsNewDependencyScopes(myBuildTypesPanel.getSelectedBuildTypes(), myProductFlavorsPanel.getSelectedProductFlavors(),
                                       artifactNames);
    }
    return null;
  }

  @NotNull
  List<String> getSelectedScopeNames() {
    return mySelectedScopeNames;
  }

  @Override
  public void dispose() {
    myToolWindowPanels.forEach(Disposer::dispose);
  }

  private class ScopesPanel extends JPanel {
    ScopesPanel() {
      super(new GridLayout(1, 0));
    }

    private void createAndAddToolWindowPanel(@NotNull String title,
                                             @NotNull JPanel contents,
                                             @Nullable JComponent preferredFocusedComponent) {
      ToolWindowPanel panel = new ToolWindowPanel(title, AndroidIcons.Android, null) {
      };

      ToolWindowHeader header = panel.getHeader();

      int borders = RIGHT;
      if (myToolWindowPanels.isEmpty()) {
        // The first panel should have left border.
        borders = LEFT | borders;
      }

      header.setBorder(IdeBorderFactory.createBorder(borders));
      header.setPreferredFocusedComponent(preferredFocusedComponent);

      panel.add(contents, BorderLayout.CENTER);
      add(panel);
      myToolWindowPanels.add(panel);
    }
  }
}
