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
package com.android.tools.idea.npw;

import com.android.tools.idea.wizard.*;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.options.ConfigurationException;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Integrates paths from old module import wizard into the new ("dynamic") module wizard
 */
public class LegacyPathWrapper implements NewModuleDynamicPath {
  private final NewModuleWizardState myWizardState;
  private final WizardPath myWizardPath;
  private final List<ModuleWizardStep> mySteps;
  private final Iterable<ModuleTemplate> myTemplates;
  private DynamicWizard myWizard;
  private int myCurrentStep;

  public LegacyPathWrapper(NewModuleWizardState wizardState, WizardPath wizardPath) {
    myTemplates = getModuleTemplates(wizardPath);
    myWizardState = wizardState;
    myWizardPath = wizardPath;
    mySteps = ImmutableList.copyOf(myWizardPath.getSteps());
  }

  private static boolean isStepValid(ModuleWizardStep currentStep) {
    if (currentStep == null) {
      return true;
    }
    try {
      return currentStep.validate();
    }
    catch (ConfigurationException e) {
      return false;
    }
  }

  static Iterable<ModuleTemplate> getModuleTemplates(@NotNull WizardPath wizardPath) {
    Collection<ChooseTemplateStep.MetadataListItem> templates = wizardPath.getBuiltInTemplates();
    if (wizardPath instanceof ImportSourceModulePath) {
      ChooseTemplateStep.MetadataListItem template = Iterables.getFirst(templates, null);
      assert template != null;
      LegacyModuleTemplate importEclipse =
        new LegacyModuleTemplate(template, "Import Eclipse ADT Project", "Import an existing Eclipse ADT project as a module",
                                 AndroidIcons.ModuleTemplates.EclipseModule);
      LegacyModuleTemplate importGradle =
        new LegacyModuleTemplate(template, "Import Gradle Project", "Import an existing Gradle project as a module",
                                 AndroidIcons.ModuleTemplates.GradleModule);
      return ImmutableList.of(importGradle, importEclipse);
    }
    else if (wizardPath instanceof WrapArchiveWizardPath) {
      ChooseTemplateStep.MetadataListItem template = Iterables.getFirst(templates, null);
      assert template != null;
      return Collections.singleton(
        new LegacyModuleTemplate(template, "Import .JAR/.AAR Package", "Import an existing JAR or AAR package as a new project module",
                                 AndroidIcons.ModuleTemplates.Android));
    }
    else {
      ImmutableList.Builder<ModuleTemplate> templatesBuilder = ImmutableList.builder();
      for (ChooseTemplateStep.MetadataListItem template : templates) {
        templatesBuilder.add(new LegacyModuleTemplate(template, null));
      }
      return templatesBuilder.build();
    }
  }

  @Override
  public void onPathStarted(boolean fromBeginning) {
    if (fromBeginning) {
      ModuleTemplate moduleTemplate = myWizard.getState().get(WizardConstants.SELECTED_MODULE_TYPE_KEY);
      if (moduleTemplate instanceof LegacyModuleTemplate) {
        myWizardState.setTemplateLocation(((LegacyModuleTemplate)moduleTemplate).getLocation());
      }
      myCurrentStep = findNext(-1, 1);
    }
    else {
      myCurrentStep = findNext(mySteps.size(), -1);
    }
    updateWizard();
  }

  private int findNext(int currentStep, int direction) {
    final int stepCount = mySteps.size();
    boolean isWithinBounds;
    do {
      currentStep += direction;
      isWithinBounds = currentStep >= 0 && currentStep < stepCount;
    }
    while (isWithinBounds && !myWizardPath.isStepVisible(mySteps.get(currentStep)));
    return !isWithinBounds ? -1 : currentStep;
  }

  @Override
  public ModuleWizardStep getCurrentStep() {
    return myCurrentStep < 0 ? null : mySteps.get(myCurrentStep);
  }

  @Override
  public List<ModuleWizardStep> getAllSteps() {
    return mySteps;
  }

  @Override
  public boolean hasNext() {
    return findNext(myCurrentStep, 1) >= 0;
  }

  @Override
  public boolean hasPrevious() {
    return findNext(myCurrentStep, -1) >= 0;
  }

  @Override
  public boolean canGoPrevious() {
    return true;
  }

  @Override
  public boolean canGoNext() {
    return isStepValid(getCurrentStep()) && hasNext();
  }

  @Override
  public boolean containsStep(@NotNull String stepName, boolean visibleOnly) {
    for (ModuleWizardStep step : mySteps) {
      if (stepName.equals(step.getName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void navigateToNamedStep(@NotNull String stepName, boolean requireVisible) {
    for (ModuleWizardStep step : mySteps) {
      if (stepName.equals(step.getName())) {
        myCurrentStep = mySteps.indexOf(step);
        updateWizard();
        return;
      }
    }
  }

  @Override
  public boolean isPathVisible() {
    ModuleTemplate moduleTemplate = myWizard.getState().get(WizardConstants.SELECTED_MODULE_TYPE_KEY);
    return moduleTemplate != null && Iterables.contains(myTemplates, moduleTemplate);
  }

  @Override
  public int getVisibleStepCount() {
    int count = 0;
    for (ModuleWizardStep step : mySteps) {
      if (myWizardPath.isStepVisible(step)) {
        count += 1;
      }
    }
    return count;
  }

  @Override
  public void attachToWizard(DynamicWizard dynamicWizard) {
    myWizard = dynamicWizard;
  }

  @Nullable
  @Override
  public DynamicWizard getWizard() {
    return myWizard;
  }

  @Override
  public boolean isPathRequired() {
    return true;
  }

  @Nullable
  @Override
  public Step next() {
    return navigate(1);
  }

  private Step navigate(int direction) {
    myCurrentStep = findNext(myCurrentStep, direction);
    if (myCurrentStep < 0) {
      return null;
    }
    assert myCurrentStep >= 0;
    updateWizard();
    return mySteps.get(myCurrentStep);
  }

  @Nullable
  @Override
  public Step previous() {
    return navigate(-1);
  }

  @Override
  public boolean canPerformFinishingActions() {
    return true;
  }

  @Override
  public boolean performFinishingActions() {
    myWizardPath.createModule();
    return true;
  }

  @Override
  public void updateCurrentStep() {
    // Do nothing
  }

  @Override
  public void setErrorHtml(String errorMessage) {
    // Do nothing
  }

  @NotNull
  @Override
  public Iterable<ModuleTemplate> getModuleTemplates() {
    return myTemplates;
  }

  public void updateWizard() {
    if (isPathVisible()) {
      for (ModuleWizardStep step : myWizardPath.getSteps()) {
        step.updateStep();
      }
      myWizard.updateButtons(true, canGoNext(), true, !canGoNext() && isStepValid(getCurrentStep()));
    }
  }

  @Override
  public boolean readyToLeavePath() {
    return true;
  }

  private static final class LegacyModuleTemplate extends AbstractModuleTemplate {
    private final File myLocation;


    public LegacyModuleTemplate(@NotNull ChooseTemplateStep.MetadataListItem listItem,
                                @NotNull String name,
                                @Nullable String description,
                                @Nullable Icon icon) {
      super(name, description, null, icon);
      myLocation = listItem.getTemplateFile();
    }

    public LegacyModuleTemplate(@NotNull ChooseTemplateStep.MetadataListItem listItem, @Nullable Icon icon) {
      this(listItem, listItem.toString(), listItem.getDescription(), icon);
    }

    @NotNull
    public File getLocation() {
      return myLocation;
    }

    @Override
    public void updateWizardState(@NotNull ScopedStateStore state) {
      // Do nothing
    }
  }
}
