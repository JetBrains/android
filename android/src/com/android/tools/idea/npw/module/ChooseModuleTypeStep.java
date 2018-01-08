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

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
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

import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;
import static java.util.stream.Collectors.toMap;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * This step allows the user to select which type of module they want to create.
 */
public class ChooseModuleTypeStep extends ModelWizardStep.WithoutModel {
  private final List<ModuleGalleryEntry> myModuleGalleryEntryList;
  private final JComponent myRootPanel;
  private final Project myProject;

  private ASGallery<ModuleGalleryEntry> myFormFactorGallery;
  private Map<ModuleGalleryEntry, SkippableWizardStep> myModuleDescriptionToStepMap;

  public ChooseModuleTypeStep(@NotNull Project project, @NotNull List<ModuleGalleryEntry> moduleGalleryEntries) {
    super(message("android.wizard.module.new.module.header"));

    myProject = project;
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
      NewModuleModel model = new NewModuleModel(myProject);
      if (moduleGalleryEntry instanceof ModuleTemplateGalleryEntry) {
        ModuleTemplateGalleryEntry templateEntry =  (ModuleTemplateGalleryEntry) moduleGalleryEntry;
        model.isLibrary().set(templateEntry.isLibrary());
        model.instantApp().set(templateEntry.isInstantApp());
        model.templateFile().setValue(templateEntry.getTemplateFile());
      }

      SkippableWizardStep step = moduleGalleryEntry.createStep(model);
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
    // This wizard includes a step for each module, but we only visit the selected one. First, we hide all steps (in case we visited a
    // different module before and hit back), and then we activate the step we care about.
    ModuleGalleryEntry selectedEntry = myFormFactorGallery.getSelectedElement();
    myModuleDescriptionToStepMap.forEach((galleryEntry, step) -> step.setShouldShow(galleryEntry == selectedEntry));
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myFormFactorGallery;
  }

  @VisibleForTesting
  @NotNull
  static List<ModuleGalleryEntry> sortModuleEntries(@NotNull List<ModuleGalleryEntry> moduleTypesProviders) {
    // To have a sequence specified by design, we hardcode the sequence. Everything else is added at the end (sorted by name)
    String[] orderedNames = {
      "Phone & Tablet Module", "Android Library", "Instant App", "Feature Module", "Android Wear Module", "Android TV Module",
      "Android Things Module", "Import Gradle Project", "Import Eclipse ADT Project", "Import .JAR/.AAR Package", "Java Library",
      "Google Cloud Module",
    };
    Map<String, ModuleGalleryEntry> entryMap = moduleTypesProviders.stream().collect(toMap(ModuleGalleryEntry::getName, c -> c));

    List<ModuleGalleryEntry> result = new ArrayList<>();
    for (String name : orderedNames) {
      ModuleGalleryEntry entry = entryMap.remove(name);
      if (entry != null) {
        result.add(entry);
      }
    }

    List<ModuleGalleryEntry> secondHalf = new ArrayList<>(entryMap.values());
    Collections.sort(secondHalf, Comparator.comparing(ModuleGalleryEntry::getName));

    result.addAll(secondHalf);
    return result;
  }
}
