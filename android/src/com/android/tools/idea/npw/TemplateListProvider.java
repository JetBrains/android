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

import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Prepares list of templates based on the state of the wizard value store.
 */
class TemplateListProvider extends ScopedDataBinder.ValueDeriver<TemplateEntry[]> {
  private final TemplateEntry[] myTemplates;

  public TemplateListProvider(@NotNull FormFactor formFactor, @NotNull Set<String> categories, @NotNull Set<String> excluded) {
    ArrayList<TemplateEntry> templates = Lists.newArrayList();
    for (String category : categories) {
      templates.addAll(Arrays.asList(getTemplateList(formFactor, category, excluded)));
    }

    // Special case for Android Wear and Android Auto: These tend not to be activities; allow
    // you to create a module with for example just a watch face
    if (formFactor == FormFactor.WEAR) {
      templates.addAll(Arrays.asList(getTemplateList(formFactor, "Wear", excluded)));
    }
    if (formFactor == FormFactor.CAR) {
      templates.addAll(Arrays.asList(getTemplateList(formFactor, "Android Auto", excluded)));
    }

    Collections.sort(templates, (o1, o2) -> {
      TemplateMetadata m1 = o1.getMetadata();
      TemplateMetadata m2 = o2.getMetadata();
      return StringUtil.naturalCompare(m1.getTitle(), m2.getTitle());
    });
    myTemplates = templates.toArray(new TemplateEntry[templates.size()]);
  }

  /**
   * Search the given folder for a list of templates and populate the display list.
   */
  private static TemplateEntry[] getTemplateList(@NotNull FormFactor formFactor, @NotNull String category,
                                                 @Nullable Set<String> excluded) {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplatesInCategory(category);
    List<TemplateEntry> metadataList = new ArrayList<>(templates.size());
    for (File template : templates) {
      TemplateMetadata metadata = manager.getTemplateMetadata(template);
      if (metadata == null || !metadata.isSupported()) {
        continue;
      }
      // Don't include this template if it's been excluded
      if (excluded != null && excluded.contains(metadata.getTitle())) {
        continue;
      }
      // If a form factor has been specified, ensure that requirement is met.
      if (!formFactor.id.equalsIgnoreCase(metadata.getFormFactor())) {
        continue;
      }
      metadataList.add(new TemplateEntry(template, metadata));
    }
    return ArrayUtil.toObjectArray(metadataList, TemplateEntry.class);
  }

  @Nullable
  @Override
  public Set<ScopedStateStore.Key<?>> getTriggerKeys() {
    return ImmutableSet.of(AddAndroidActivityPath.KEY_IS_LAUNCHER);
  }

  @NotNull
  @Override
  public TemplateEntry[] deriveValue(@NotNull ScopedStateStore state,
                                     ScopedStateStore.Key changedKey,
                                     @Nullable TemplateEntry[] currentValue) {
    final boolean hasCppSupport = state.getNotNull(WizardConstants.INCLUDE_CPP_SUPPORT_KEY, false);
    if (hasCppSupport) {
      List<TemplateEntry> filtered = Lists.newArrayList();
      for (TemplateEntry template : myTemplates) {
        final String title = template.getTitle();
        if ("Empty Activity".equals(title) || "Basic Activity".equals(title)) {
          filtered.add(template);
        }
      }

      if (!filtered.isEmpty()) {
        return filtered.toArray(new TemplateEntry[filtered.size()]);
      }
    }

    Boolean isLauncher = state.get(AddAndroidActivityPath.KEY_IS_LAUNCHER);
    if (!Boolean.TRUE.equals(isLauncher)) {
      return myTemplates;
    }
    List<TemplateEntry> list = Lists.newArrayListWithExpectedSize(myTemplates.length);
    for (TemplateEntry entry : Arrays.asList(myTemplates)) {
      if (entry.getMetadata().getParameter(TemplateMetadata.ATTR_IS_LAUNCHER) != null) {
        list.add(entry);
      }
    }
    return ArrayUtil.toObjectArray(list, TemplateEntry.class);
  }
}
