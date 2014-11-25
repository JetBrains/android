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
package com.android.tools.idea.wizard;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * Integrates paths from old module import wizard into the new ("dynamic") module wizard
 */
public class LegacyPathWrapper implements NewModuleDynamicPath {
  private final NewModuleWizardState myWizardState;
  private final WizardPath myWizardPath;
  private final List<ModuleWizardStep> mySteps;
  private Iterable<ModuleTemplate> myTypes;
  private DynamicWizard myWizard;
  private int myCurrentStep;

  public LegacyPathWrapper(NewModuleWizardState wizardState, WizardPath wizardPath) {
    myWizardState = wizardState;
    myWizardPath = wizardPath;
    mySteps = ImmutableList.copyOf(myWizardPath.getSteps());
  }

  private static boolean isStepValid(ModuleWizardStep currentStep) {
    try {
      return currentStep.validate();
    }
    catch (ConfigurationException e) {
      return false;
    }
  }

  @Override
  public void onPathStarted(boolean fromBeginning) {
    if (fromBeginning) {
      ModuleTemplate moduleTemplate = myWizard.getState().get(WizardConstants.SELECTED_MODULE_TYPE_KEY);
      if (moduleTemplate instanceof TemplateEntryModuleTemplate) {
        myWizardState.setTemplateLocation(((TemplateEntryModuleTemplate)moduleTemplate).getTemplateFile());
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
    return mySteps.get(myCurrentStep);
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
    return moduleTemplate != null && Iterables.contains(myTypes, moduleTemplate);
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
    if (myTypes == null) {
      myTypes = ImmutableList
        .copyOf(Iterables.transform(myWizardPath.getBuiltInTemplates(), new Function<ChooseTemplateStep.MetadataListItem, ModuleTemplate>() {
          @Override
          public ModuleTemplate apply(ChooseTemplateStep.MetadataListItem input) {
            return new TemplateEntryModuleTemplate(input);
          }
        }));
    }
    return myTypes;
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

  private static final class TemplateEntryModuleTemplate implements ModuleTemplate {
    private final ChooseTemplateStep.MetadataListItem myTemplate;

    public TemplateEntryModuleTemplate(ChooseTemplateStep.MetadataListItem template) {
      myTemplate = template;
    }

    @Override
    public boolean isGalleryModuleType() {
      return false;
    }

    @Nullable
    @Override
    public Icon getIcon() {
      // TODO
      return null;
    }

    @Override
    public String toString() {
      return myTemplate.toString();
    }

    @Override
    public String getName() {
      return myTemplate.toString();
    }

    @Nullable
    @Override
    public String getDescription() {
      return myTemplate != null ? myTemplate.getDescription() : "";
    }

    @Override
    public void updateWizardStateOnSelection(ScopedStateStore state) {
      // Do nothing
    }

    @Nullable
    @Override
    public FormFactorUtils.FormFactor getFormFactor() {
      return null;
    }

    public File getTemplateFile() {
      return myTemplate.getTemplateFile();
    }
  }
}
