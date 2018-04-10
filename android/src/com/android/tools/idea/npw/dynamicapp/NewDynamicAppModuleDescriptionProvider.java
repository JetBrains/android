/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.dynamicapp;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.module.ModuleTemplateGalleryEntry;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.templates.Template.ANDROID_MODULE_TEMPLATE;
import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static org.jetbrains.android.util.AndroidBundle.message;

public class NewDynamicAppModuleDescriptionProvider implements ModuleDescriptionProvider {
  @Override
  public Collection<ModuleGalleryEntry> getDescriptions() {
    if (!StudioFlags.NPW_DYNAMIC_APP_MODULE.get()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new FeatureTemplateGalleryEntry());
  }

  private static class FeatureTemplateGalleryEntry implements ModuleTemplateGalleryEntry {
    @NotNull private final File myTemplateFile;
    @NotNull private TemplateMetadata myTemplateMetadata;

    FeatureTemplateGalleryEntry() {
      myTemplateFile = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_MODULE_TEMPLATE);
      myTemplateMetadata = new TemplateHandle(myTemplateFile).getMetadata();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      // TODO: This is just a placeholder. New icon: http://b/77840752
      return AndroidIcons.ModuleTemplates.FeatureModule;
    }

    @NotNull
    @Override
    public String getName() {
      return message("android.wizard.module.new.dynamic.module");
    }

    @Nullable
    @Override
    public String getDescription() {
      return myTemplateMetadata.getDescription();
    }

    @Override
    public String toString() {
      return getName();
    }

    @NotNull
    @Override
    public File getTemplateFile() {
      return myTemplateFile;
    }

    @NotNull
    @Override
    public FormFactor getFormFactor() {
      return FormFactor.MOBILE;
    }

    @Override
    public boolean isLibrary() {
      return false;
    }

    @Override
    public boolean isInstantApp() {
      return false;
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new ConfigureDynamicModuleStep(model, getName());
    }
  }
}
