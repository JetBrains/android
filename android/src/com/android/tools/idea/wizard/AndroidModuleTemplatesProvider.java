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

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Provides basic new module templates.
 */
public final class AndroidModuleTemplatesProvider implements ModuleTemplateProvider {
  @NotNull
  @Override
  public Iterable<ModuleTemplate> getModuleTemplates() {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    List<ModuleTemplate> moduleTemplates = Lists.newArrayList();
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplate(templateFile);
      if (metadata == null) {
        continue;
      }
      if (metadata.getFormFactor() != null) {
        final FormFactorUtils.FormFactor formFactor = FormFactorUtils.FormFactor.get(metadata.getFormFactor());
        if (formFactor == null) {
          continue;
        }
        moduleTemplates.addAll(getModuleTypes(metadata, formFactor));
      }
    }
    return moduleTemplates;
  }

  @NotNull
  private static Collection<ModuleTemplate> getModuleTypes(@NotNull TemplateMetadata metadata,
                                                       @NotNull FormFactorUtils.FormFactor formFactor) {
    if (formFactor.equals(FormFactorUtils.FormFactor.MOBILE)) {
      CreateModuleTemplate androidApplication = new CreateModuleTemplate(metadata, formFactor,
                                                                     formFactor.toString() + " Application", true, true);
      androidApplication.setCustomValue(WizardConstants.IS_LIBRARY_KEY, false);
      CreateModuleTemplate androidLibrary = new CreateModuleTemplate(metadata, formFactor, "Android Library", true, false);
      androidLibrary.setCustomValue(WizardConstants.IS_LIBRARY_KEY, true);
      return ImmutableSet.<ModuleTemplate>of(androidApplication, androidLibrary);
    } else {
      return ImmutableSet.<ModuleTemplate>of(new CreateModuleTemplate(metadata, formFactor, metadata.getTitle(), true, true));
    }
  }

}
