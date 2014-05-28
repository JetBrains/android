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

import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_GRADLE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_NEW_PROJECT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_SDK_DIR;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Presents a wizard to the user to create a new project.
 */
public class NewProjectWizardDynamic extends DynamicWizard {

  public static final Key<String> GRADLE_VERSION_KEY = createKey(TemplateMetadata.ATTR_GRADLE_VERSION, WIZARD, String.class);
  public static final Key<String> GRADLE_PLUGIN_VERSION_KEY = createKey(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, WIZARD, String.class);
  public static final Key<Boolean> USE_PER_MODULE_REPOS_KEY = createKey(TemplateMetadata.ATTR_PER_MODULE_REPOS, WIZARD, Boolean.class);
  public static final Key<Boolean> IS_NEW_PROJECT_KEY = createKey(ATTR_IS_NEW_PROJECT, WIZARD, Boolean.class);
  public static final Key<Boolean> IS_GRADLE_PROJECT_KEY = createKey(ATTR_IS_GRADLE, WIZARD, Boolean.class);
  public static final Key<String> SDK_DIR_KEY = createKey(ATTR_SDK_DIR, WIZARD, String.class);

  public NewProjectWizardDynamic(@Nullable Project project, @Nullable Module module) {
    super(project, module, "New Project");
  }

  @Override
  protected void init() {
    addPath(new ConfigureAndroidProjectPath());
    for (NewFormFactorModulePath path : NewFormFactorModulePath.getAvailableFormFactorModulePaths()) {
      addPath(path);
    }
    ScopedStateStore state = getState();
    state.put(GRADLE_VERSION_KEY, GradleUtil.GRADLE_LATEST_VERSION);
    state.put(GRADLE_PLUGIN_VERSION_KEY, GradleUtil.GRADLE_PLUGIN_LATEST_VERSION);
    state.put(USE_PER_MODULE_REPOS_KEY, false);
    state.put(IS_NEW_PROJECT_KEY, true);
    state.put(IS_GRADLE_PROJECT_KEY, true);
    state.put(SDK_DIR_KEY, AndroidSdkUtils.tryToChooseAndroidSdk().getLocation().getPath());
    super.init();
  }

  @Override
  public void performFinishingActions() {

  }
}
