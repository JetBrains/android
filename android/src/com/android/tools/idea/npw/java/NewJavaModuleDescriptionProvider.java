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
package com.android.tools.idea.npw.java;

import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public class NewJavaModuleDescriptionProvider implements ModuleDescriptionProvider {
  @Override
  public Collection<ModuleGalleryEntry> getDescriptions() {
    return Collections.singletonList(new JavaModuleTemplateGalleryEntry());
  }

  private static class JavaModuleTemplateGalleryEntry implements ModuleGalleryEntry {
    @NotNull private TemplateHandle myTemplateHandle;

    JavaModuleTemplateGalleryEntry() {
      myTemplateHandle = new TemplateHandle(TemplateManager.getInstance().getTemplateFile(Template.CATEGORY_APPLICATION, "Java Library"));
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AndroidIcons.ModuleTemplates.Android;
    }

    @NotNull
    @Override
    public String getName() {
      return myTemplateHandle.getMetadata().getTitle();
    }

    @Nullable
    @Override
    public String getDescription() {
      return myTemplateHandle.getMetadata().getDescription();
    }

    @Override
    public String toString() {
      return getName();
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new ConfigureJavaModuleStep(new NewJavaModuleModel(model.getProject().getValue(), myTemplateHandle), getName());
    }
  }
}
