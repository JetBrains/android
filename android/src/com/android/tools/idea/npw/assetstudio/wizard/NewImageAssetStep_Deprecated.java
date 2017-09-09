/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.npw.project.AndroidPackageUtils;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * Step for generating Android icons from some image asset source.
 */
public final class NewImageAssetStep_Deprecated extends ModelWizardStep<GenerateIconsModel> {
  @NotNull
  private static final String NEW_IMAGE_ASSET_STEP_DEPRECATED_ENABLED = "android.studio.new-image-asset-step-deprecated.enabled";

  public static boolean isEnabled() {
    return SystemProperties.getBooleanProperty(NEW_IMAGE_ASSET_STEP_DEPRECATED_ENABLED, false);
  }

  @NotNull private final GenerateIconsPanel myGenerateIconsPanel;
  @NotNull private final AndroidFacet myFacet;

  public NewImageAssetStep_Deprecated(@NotNull GenerateIconsModel model, @NotNull AndroidFacet facet) {
    super(model, "Configure Image Asset");
    int minSdkVersion = AndroidModuleInfo.getInstance(facet).getMinSdkVersion().getApiLevel();
    myGenerateIconsPanel = new GenerateIconsPanel(this, model.getPaths(), minSdkVersion);
    myFacet = facet;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Collections.singletonList(new ConfirmGenerateIconsStep(getModel(), AndroidPackageUtils.getModuleTemplates(myFacet, null)));
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myGenerateIconsPanel;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myGenerateIconsPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setIconGenerator(myGenerateIconsPanel.getIconGenerator());
  }
}
