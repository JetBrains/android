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

import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.tools.idea.npw.ModuleTemplate;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope;
import com.google.common.collect.ImmutableSet;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * Constants for template based wizards
 */
public class WizardConstants {

  // Colors
  public static final JBColor ANDROID_NPW_HEADER_COLOR = new JBColor(0x616161, 0x4B4B4B);

  // Dimensions
  public static final int STUDIO_WIZARD_INSET_SIZE = JBUI.scale(12);
  public static final Insets STUDIO_WIZARD_INSETS =
    new Insets(0, STUDIO_WIZARD_INSET_SIZE, STUDIO_WIZARD_INSET_SIZE, STUDIO_WIZARD_INSET_SIZE);
  public static final int STUDIO_WIZARD_TOP_INSET = 18;
  public static final Dimension DEFAULT_WIZARD_WINDOW_SIZE = JBUI.size(1080, 650);

  public static final Dimension DEFAULT_GALLERY_THUMBNAIL_SIZE = JBUI.size(192, 192);

  // State Store Keys
  public static final Key<String> BUILD_TOOLS_VERSION_KEY = createKey(ATTR_BUILD_TOOLS_VERSION, WIZARD, String.class);
  public static final Key<String> SDK_HOME_KEY = createKey(ATTR_SDK_DIR, WIZARD, String.class);
  @SuppressWarnings("unchecked") public static final Key<List<IPkgDesc>> INSTALL_REQUESTS_KEY =
    createKey("packagesToInstall", WIZARD, (Class<List<IPkgDesc>>)(Class)List.class);
  @SuppressWarnings("unchecked") public static final Key<List<IPkgDesc>> SKIPPED_INSTALL_REQUESTS_KEY =
    createKey("packagesSkipped", WIZARD, (Class<List<IPkgDesc>>)(Class)List.class);
  public static final Key<String> GRADLE_VERSION_KEY = createKey(TemplateMetadata.ATTR_GRADLE_VERSION, WIZARD, String.class);
  public static final Key<String> GRADLE_PLUGIN_VERSION_KEY = createKey(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, WIZARD, String.class);
  public static final Key<Boolean> USE_PER_MODULE_REPOS_KEY = createKey(TemplateMetadata.ATTR_PER_MODULE_REPOS, WIZARD, Boolean.class);
  public static final Key<Boolean> IS_NEW_PROJECT_KEY = createKey(ATTR_IS_NEW_PROJECT, WIZARD, Boolean.class);
  public static final Key<Boolean> IS_GRADLE_PROJECT_KEY = createKey(ATTR_IS_GRADLE, WIZARD, Boolean.class);
  public static final Key<String> SDK_DIR_KEY = createKey(ATTR_SDK_DIR, WIZARD, String.class);
  public static final Key<String> MAVEN_URL_KEY = createKey(ATTR_MAVEN_URL, WIZARD, String.class);
  public static final Key<String> DEBUG_KEYSTORE_SHA_1_KEY = createKey(ATTR_DEBUG_KEYSTORE_SHA1, WIZARD, String.class);
  public static final Key<String> APPLICATION_NAME_KEY = createKey(ATTR_APP_TITLE, WIZARD, String.class);
  public static final Key<ModuleTemplate> SELECTED_MODULE_TYPE_KEY =
    createKey("selectedModuleType", ScopedStateStore.Scope.WIZARD, ModuleTemplate.class);
  public static final Key<String> COMPANY_DOMAIN_KEY = createKey("companyDomain", STEP, String.class);
  public static final Key<String> PACKAGE_NAME_KEY = createKey(ATTR_PACKAGE_NAME, WIZARD, String.class);
  public static final Key<String> PROJECT_LOCATION_KEY = createKey(ATTR_TOP_OUT, WIZARD, String.class);
  public static final Key<Integer> NEWLY_INSTALLED_API_KEY = createKey("newly.installed.api.level", WIZARD, Integer.class);
  public static final Key<Boolean> IS_LIBRARY_KEY = createKey(ATTR_IS_LIBRARY_MODULE, WIZARD, Boolean.class);

  /**
   * Files to open in the editor window after the Wizard is finished.
   */
  @SuppressWarnings("unchecked") public static final Key<List<File>> FILES_TO_OPEN_KEY =
    createKey("files.to.open", WIZARD, (Class<List<File>>)(Class)List.class);

  @SuppressWarnings("unchecked") public static final Key<Collection<File>> TARGET_FILES_KEY =
    createKey("target.files", Scope.WIZARD, (Class<Collection<File>>)(Object)Collection.class);

  @SuppressWarnings("unchecked") public static final Key<List<String>> DEPENDENCIES_KEY =
    createKey(ATTR_DEPENDENCIES_LIST, Scope.WIZARD, (Class<List<String>>)(Class)String.class);

  // Patterns
  public static final String INVALID_FILENAME_CHARS = "[/\\\\?%*:|\"<>!;]";
  public static final Set<String> INVALID_WINDOWS_FILENAMES = ImmutableSet
    .of("con", "prn", "aux", "clock$", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2",
        "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "$mft", "$mftmirr", "$logfile", "$volume", "$attrdef", "$bitmap", "$boot",
        "$badclus", "$secure", "$upcase", "$extend", "$quota", "$objid", "$reparse");

  public static final String MODULE_TEMPLATE_NAME = "NewAndroidModule";
  public static final String PROJECT_TEMPLATE_NAME = "NewAndroidProject";
}
