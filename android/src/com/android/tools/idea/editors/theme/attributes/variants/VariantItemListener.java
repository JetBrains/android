/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.variants;

import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * {@link ItemListener} implementation to use with {@link VariantsComboItem}. This listener will select the configuration
 * contained in the {@link VariantsComboItem} as the current configuration.
 */
public class VariantItemListener implements ItemListener {
  private final ThemeEditorContext myContext;

  public VariantItemListener(@NotNull ThemeEditorContext context) {
    myContext = context;
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    if (e.getStateChange() != ItemEvent.SELECTED) {
      return;
    }

    VariantsComboItem item = (VariantsComboItem)e.getItem();
    Configuration oldConfiguration = myContext.getConfiguration();
    ConfigurationManager manager = oldConfiguration.getConfigurationManager();
    Configuration newConfiguration = Configuration.create(manager, null, null, item.getRestrictedConfiguration());

    // Target and locale are global so we need to set them in the configuration manager when updated
    VersionQualifier newVersionQualifier = item.getRestrictedConfiguration().getVersionQualifier();
    if (newVersionQualifier != null) {
      IAndroidTarget realTarget = manager.getHighestApiTarget() != null ? manager.getHighestApiTarget() : manager.getTarget();
      assert realTarget != null;
      manager.setTarget(new CompatibilityRenderTarget(realTarget, newVersionQualifier.getVersion(), null));
    }
    else {
      manager.setTarget(null);
    }

    LocaleQualifier newLocaleQualifier = item.getRestrictedConfiguration().getLocaleQualifier();
    manager.setLocale(newLocaleQualifier != null ? Locale.create(newLocaleQualifier) : Locale.ANY);

    oldConfiguration.setDevice(null, false);
    Configuration.copyCompatible(newConfiguration, oldConfiguration);
    oldConfiguration.updated(ConfigurationListener.MASK_FOLDERCONFIG);
  }
}
