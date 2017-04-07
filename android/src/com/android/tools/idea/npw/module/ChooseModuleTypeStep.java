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
package com.android.tools.idea.npw.module;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_INCLUDE_FORM_FACTOR;
import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * This step allows the user to select which type of module they want to create.
 */
public class ChooseModuleTypeStep extends ModelWizardStep<NewModuleModel> {
  private final List<ModuleGalleryEntry> myModuleGalleryEntryList;
  private final JComponent myRootPanel;

  private ASGallery<ModuleGalleryEntry> myFormFactorGallery;
  private Map<ModuleGalleryEntry, SkippableWizardStep> myModuleDescriptionToStepMap;

  public ChooseModuleTypeStep(@NotNull NewModuleModel model, @NotNull List<ModuleGalleryEntry> moduleGalleryEntries) {
    super(model, message("android.wizard.module.new.module.header"));

    myModuleGalleryEntryList = sortModuleEntries(moduleGalleryEntries);
    myRootPanel = createGallery();
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @NotNull
  @Override
  public Collection<? extends ModelWizardStep> createDependentSteps() {
    List<ModelWizardStep> allSteps = Lists.newArrayList();
    myModuleDescriptionToStepMap = new HashMap<>();
    for (ModuleGalleryEntry moduleGalleryEntry : myModuleGalleryEntryList) {
      SkippableWizardStep step = moduleGalleryEntry.createStep(getModel());
      allSteps.add(step);
      myModuleDescriptionToStepMap.put(moduleGalleryEntry, step);
    }

    return allSteps;
  }

  @NotNull
  private JComponent createGallery() {
    myFormFactorGallery = new ASGallery<ModuleGalleryEntry>(
      JBList.createDefaultListModel(),
      image -> image.getIcon() == null ? null : IconUtil.toImage(image.getIcon()),
      label -> label == null ? message("android.wizard.gallery.item.none") : label.getName(), DEFAULT_GALLERY_THUMBNAIL_SIZE,
      null
    ) {

      @Override
      public Dimension getPreferredScrollableViewportSize() {
        // The default implementations assigns a height as tall as the screen.
        // When calling setVisibleRowCount(2), the underlying implementation is buggy, and  will have a gap on the right and when the user
        // resizes, it enters on an adjustment loop at some widths (can't decide to fit 3 or for elements, and loops between the two)
        Dimension cellSize = computeCellSize();
        int heightInsets = getInsets().top + getInsets().bottom;
        int widthInsets = getInsets().left + getInsets().right;
        // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
        return new Dimension(cellSize.width * 5 + widthInsets, (int)(cellSize.height * 2.2) + heightInsets);
      }
    };

    myFormFactorGallery.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    AccessibleContext accessibleContext = myFormFactorGallery.getAccessibleContext();
    if (accessibleContext != null) {
      accessibleContext.setAccessibleDescription(getTitle());
    }
    return new JBScrollPane(myFormFactorGallery);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myFormFactorGallery.setModel(JBList.createDefaultListModel(myModuleGalleryEntryList.toArray()));
    myFormFactorGallery.setDefaultAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        wizard.goForward();
      }
    });

    myFormFactorGallery.setSelectedIndex(0);
  }

  @Override
  protected void onProceeding() {
    Map<String, Object> templateValues = getModel().getTemplateValues();
    templateValues.clear();

    // This wizard includes a step for each module, but we only visit the selected one. First, we hide all steps (in case we visited a
    // different module before and hit back), and then we activate the step we care about.
    ModuleGalleryEntry selectedEntry = myFormFactorGallery.getSelectedElement();
    myModuleDescriptionToStepMap.forEach((galleryEntry, step) -> {
      boolean shouldShow = (galleryEntry == selectedEntry);
      step.setShouldShow(shouldShow);

      if (galleryEntry instanceof ModuleTemplateGalleryEntry) {
        FormFactor formFactor = ((ModuleTemplateGalleryEntry) galleryEntry).getFormFactor();
        templateValues.put(formFactor.id + ATTR_INCLUDE_FORM_FACTOR, shouldShow);
      }
    });

    ModuleTemplateGalleryEntry templateEntry
      = (selectedEntry instanceof ModuleTemplateGalleryEntry) ? (ModuleTemplateGalleryEntry) selectedEntry : null;

    getModel().isLibrary().set(templateEntry == null ? false : templateEntry.isLibrary());
    getModel().templateFile().setNullableValue(templateEntry == null ? null : templateEntry.getTemplateFile());
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myFormFactorGallery;
  }

  @NotNull
  private static List<ModuleGalleryEntry> sortModuleEntries(@NotNull List<ModuleGalleryEntry> moduleTypesProviders) {
    List<ModuleGalleryEntry> res = new ArrayList<>(moduleTypesProviders);

    Collections.sort(res, (t1, t2) -> {
      FormFactor f1 = (t1 instanceof ModuleTemplateGalleryEntry) ? ((ModuleTemplateGalleryEntry)t1).getFormFactor() : null;
      FormFactor f2 = (t2 instanceof ModuleTemplateGalleryEntry) ? ((ModuleTemplateGalleryEntry)t2).getFormFactor() : null;

      if (f1 != null && f2 != null) {
        return f1.compareTo(f2);
      }

      if (f1 != null) {
        return -1;
      }

      if (f2 != null) {
        return 1;
      }

      return StringUtil.naturalCompare(t1.getName(), t2.getName());
    });

    return res;
  }
}
