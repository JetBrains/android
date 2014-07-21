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

import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.ui.JBColor;

import java.awt.*;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Constants for template based wizards
 */
public class WizardConstants {

  // Colors
  public static final JBColor ANDROID_NPW_TITLE_COLOR = new JBColor(0x689F38, 0xFFFFFF);
  public static final JBColor ANDROID_NPW_HEADER_COLOR = new JBColor(0x689F38, 0x356822);

  // Dimensions
  public static final Insets STUDIO_WIZARD_INSETS = new Insets(0, 12, 12, 12);
  public static final int STUDIO_WIZARD_TOP_INSET = 18;
  public static final Dimension DEFAULT_WIZARD_WINDOW_SIZE = new Dimension(1080, 650);

  // State Store Keys
  public static final ScopedStateStore.Key<String> BUILD_TOOLS_VERSION_KEY = createKey(ATTR_BUILD_TOOLS_VERSION, WIZARD, String.class);
  public static final ScopedStateStore.Key<String> SDK_HOME_KEY = createKey(ATTR_SDK_DIR, WIZARD, String.class);
  public static final ScopedStateStore.Key<List> INSTALL_REQUESTS_KEY = createKey("packagesToInstall", WIZARD, List.class);
  public static final ScopedStateStore.Key<String> GRADLE_VERSION_KEY =
    createKey(TemplateMetadata.ATTR_GRADLE_VERSION, WIZARD, String.class);
  public static final ScopedStateStore.Key<String> GRADLE_PLUGIN_VERSION_KEY =
    createKey(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, WIZARD, String.class);
  public static final ScopedStateStore.Key<Boolean> USE_PER_MODULE_REPOS_KEY =
    createKey(TemplateMetadata.ATTR_PER_MODULE_REPOS, WIZARD, Boolean.class);
  public static final ScopedStateStore.Key<Boolean> IS_NEW_PROJECT_KEY = createKey(ATTR_IS_NEW_PROJECT, WIZARD, Boolean.class);
  public static final ScopedStateStore.Key<Boolean> IS_GRADLE_PROJECT_KEY = createKey(ATTR_IS_GRADLE, WIZARD, Boolean.class);
  public static final ScopedStateStore.Key<String> SDK_DIR_KEY = createKey(ATTR_SDK_DIR, WIZARD, String.class);
  public static final ScopedStateStore.Key<String> MAVEN_URL_KEY = createKey(ATTR_MAVEN_URL, WIZARD, String.class);
  public static final ScopedStateStore.Key<String> DEBUG_KEYSTORE_SHA_1_KEY = createKey(ATTR_DEBUG_KEYSTORE_SHA1, WIZARD, String.class);
  public static final ScopedStateStore.Key<String> APPLICATION_NAME_KEY = createKey(ATTR_APP_TITLE, WIZARD, String.class);
  public static final ScopedStateStore.Key<String> COMPANY_DOMAIN_KEY = createKey("companyDomain", STEP, String.class);
  public static final ScopedStateStore.Key<String> PACKAGE_NAME_KEY = createKey(ATTR_PACKAGE_NAME, WIZARD, String.class);
  public static final ScopedStateStore.Key<String> PROJECT_LOCATION_KEY = createKey(ATTR_TOP_OUT, WIZARD, String.class);
}
