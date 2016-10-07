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

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.WizardConstants;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Provides basic new module templates.
 */
public final class AndroidModuleTemplatesProvider implements ModuleTemplateProvider {
  /**
   * Helper method for requesting module templates associated with the specified metadata and
   * target form factors. Usually this is a 1:1 relationship, but, for example, the metadata for
   * the mobile form factor can be used to generate two separate module templates (one which acts
   * as a main entry point for your app, while the other acts as a library which can be shared
   * across apps).
   */
  @NotNull
  private static Collection<ModuleTemplate> getModuleTemplates(@NotNull TemplateMetadata metadata,
                                                               @NotNull FormFactor formFactor) {
    if (formFactor.equals(FormFactor.MOBILE)) {
      CreateModuleTemplate androidApplication =
        new CreateModuleTemplate(metadata, formFactor, "Phone & Tablet Module", AndroidIcons.ModuleTemplates.Mobile);
      androidApplication.setCustomValue(WizardConstants.IS_LIBRARY_KEY, false);
      CreateModuleTemplate androidLibrary =
        new CreateModuleTemplate(metadata, formFactor, "Android Library", AndroidIcons.ModuleTemplates.Android);
      androidLibrary.setCustomValue(WizardConstants.IS_LIBRARY_KEY, true);
      return ImmutableSet.of(androidApplication, androidLibrary);
    }
    else {
      return ImmutableSet.of(new CreateModuleTemplate(metadata, formFactor, metadata.getTitle(),
                                                      getModuleTypeIcon(formFactor)));
    }
  }

  private static Icon getModuleTypeIcon(@NotNull FormFactor enumValue) {
    switch (enumValue) {
      case CAR:
        return AndroidIcons.ModuleTemplates.Car;
      case GLASS:
        return AndroidIcons.ModuleTemplates.Glass;
      case MOBILE:
        return AndroidIcons.ModuleTemplates.Mobile;
      case TV:
        return AndroidIcons.ModuleTemplates.Tv;
      case WEAR:
        return AndroidIcons.ModuleTemplates.Wear;
      default:
        throw new IllegalArgumentException(enumValue.name());
    }
  }

  @NotNull
  @Override
  public Iterable<ModuleTemplate> getModuleTemplates() {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    List<ModuleTemplate> moduleTemplates = Lists.newArrayList();
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplateMetadata(templateFile);
      if (metadata == null) {
        continue;
      }
      if (metadata.getFormFactor() != null) {
        moduleTemplates.addAll(getModuleTemplates(metadata, FormFactor.get(metadata.getFormFactor())));
      }
    }

    Collections.sort(moduleTemplates, new Comparator<ModuleTemplate>() {
      @Override
      public int compare(ModuleTemplate t1, ModuleTemplate t2) {
        FormFactor f1 = t1.getFormFactor();
        FormFactor f2 = t2.getFormFactor();
        assert f1 != null : t1; // because of null check before we added ot moduleTemplates list
        assert f2 != null : t2;
        return f1.compareTo(f2);
      }
    });

    return moduleTemplates;
  }
}
