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
package com.android.tools.idea.npw.importing;

import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.collect.ImmutableList;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class ImportModuleGalleryEntryProvider implements ModuleDescriptionProvider {
  @Override
  public Collection<ModuleGalleryEntry> getDescriptions() {
    return ImmutableList.of(
      new SourceImportModuleGalleryEntry(AndroidBundle.message("android.wizard.module.import.eclipse.title"),
                                         AndroidBundle.message("android.wizard.module.import.eclipse.description"),
                                         AndroidIcons.ModuleTemplates.EclipseModule),
      new SourceImportModuleGalleryEntry(AndroidBundle.message("android.wizard.module.import.gradle.title"),
                                         AndroidBundle.message("android.wizard.module.import.gradle.description"),
                                         AndroidIcons.ModuleTemplates.GradleModule),
      new ArchiveImportModuleGalleryEntry()
    );
  }

  private static class SourceImportModuleGalleryEntry implements ModuleGalleryEntry {

    private final String myDescription;
    Icon myIcon;
    String myName;

    SourceImportModuleGalleryEntry(String name, String description, Icon icon) {
      myName = name;
      myIcon = icon;
      myDescription = description;
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myIcon;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Nullable
    @Override
    public String getDescription() {
      return myDescription;
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new SourceToGradleModuleStep(new SourceToGradleModuleModel(model.getProject().getValue()));
    }
  }

  private static class ArchiveImportModuleGalleryEntry implements ModuleGalleryEntry {

    @Nullable
    @Override
    public Icon getIcon() {
      return AndroidIcons.ModuleTemplates.Android;
    }

    @NotNull
    @Override
    public String getName() {
      return AndroidBundle.message("android.wizard.module.import.title");
    }

    @Nullable
    @Override
    public String getDescription() {
      return AndroidBundle.message("android.wizard.module.import.description");
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new ArchiveToGradleModuleStep(new ArchiveToGradleModuleModel(model.getProject().getValue()));
    }
  }
}
