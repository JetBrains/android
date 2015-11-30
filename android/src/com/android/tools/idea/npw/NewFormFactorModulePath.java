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

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.*;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.templates.TemplateUtils.checkedCreateDirectoryIfMissing;
import static com.android.tools.idea.npw.AddAndroidActivityPath.KEY_SELECTED_TEMPLATE;
import static com.android.tools.idea.npw.ConfigureFormFactorStep.NUM_ENABLED_FORM_FACTORS_KEY;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_CREATE_ACTIVITY;
import static com.android.tools.idea.wizard.WizardConstants.*;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * Module creation for a given form factor
 */
public class NewFormFactorModulePath extends DynamicWizardPath {
  private static final Logger LOG = Logger.getInstance(NewFormFactorModulePath.class);
  private static final Key<Boolean> CREATE_ACTIVITY_KEY = createKey(ATTR_CREATE_ACTIVITY, PATH, Boolean.class);

  private static final Key<String> MODULE_LOCATION_KEY = createKey(ATTR_PROJECT_OUT, PATH, String.class);
  private static final Key<String> RES_DIR_KEY = createKey(ATTR_RES_DIR, PATH, String.class);
  private static final Key<String> SRC_DIR_KEY = createKey(ATTR_SRC_DIR, PATH, String.class);
  private static final Key<String> AIDL_DIR_KEY = createKey(ATTR_AIDL_DIR, PATH, String.class);
  private static final Key<String> MANIFEST_DIR_KEY = createKey(ATTR_MANIFEST_DIR, PATH, String.class);
  private static final Key<String> TEST_DIR_KEY = createKey(ATTR_TEST_DIR, PATH, String.class);

  private static final Key<String> RES_OUT_KEY = createKey(ATTR_RES_OUT, PATH, String.class);
  private static final Key<String> SRC_OUT_KEY = createKey(ATTR_SRC_OUT, PATH, String.class);
  private static final Key<String> AIDL_OUT_KEY = createKey(ATTR_AIDL_OUT, PATH, String.class);
  private static final Key<String> MANIFEST_OUT_KEY = createKey(ATTR_MANIFEST_OUT, PATH, String.class);
  private static final Key<String> TEST_OUT_KEY = createKey(ATTR_TEST_OUT, PATH, String.class);

  private static final String RELATIVE_SRC_ROOT = FileUtil.join(TemplateWizard.MAIN_FLAVOR_SOURCE_PATH, TemplateWizard.JAVA_SOURCE_PATH);
  private static final String RELATIVE_TEST_ROOT = FileUtil.join(TemplateWizard.TEST_SOURCE_PATH, TemplateWizard.JAVA_SOURCE_PATH);

  private FormFactorUtils.FormFactor myFormFactor;
  private File myTemplateFile;
  private Disposable myDisposable;
  private final Key<Boolean> myIsIncludedKey;
  private final Key<String> myModuleNameKey;
  private TemplateParameterStep2 myParameterStep;
  private String myDefaultModuleName = null;
  private boolean myGradleSyncIfNecessary = true;

  public static List<NewFormFactorModulePath> getAvailableFormFactorModulePaths(@NotNull Disposable disposable) {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> applicationTemplates = manager.getTemplatesInCategory(Template.CATEGORY_APPLICATION);
    List<NewFormFactorModulePath> toReturn = Lists.newArrayList();
    for (File templateFile : applicationTemplates) {
      TemplateMetadata metadata = manager.getTemplateMetadata(templateFile);
      if (metadata == null || metadata.getFormFactor() == null) {
        continue;
      }
      FormFactorUtils.FormFactor formFactor = FormFactorUtils.FormFactor.get(metadata.getFormFactor());
      if (formFactor == null) {
        continue;
      }
      NewFormFactorModulePath path = new NewFormFactorModulePath(formFactor, templateFile, disposable);
      toReturn.add(path);
    }
    Collections.sort(toReturn, new Comparator<NewFormFactorModulePath>() {
      @Override
      public int compare(NewFormFactorModulePath p1, NewFormFactorModulePath p2) {
        return p1.myFormFactor.compareTo(p2.myFormFactor);
      }
    });
    return toReturn;
  }

  public NewFormFactorModulePath(@NotNull FormFactorUtils.FormFactor formFactor,
                                 @NotNull File templateFile,
                                 @NotNull Disposable disposable) {
    myFormFactor = formFactor;
    myTemplateFile = templateFile;
    myDisposable = disposable;
    myIsIncludedKey = FormFactorUtils.getInclusionKey(formFactor);
    myModuleNameKey = FormFactorUtils.getModuleNameKey(formFactor);
  }

  @Override
  protected void init() {
    myState.put(WizardConstants.IS_LIBRARY_KEY, false);
    myState.put(SRC_DIR_KEY, calculateSrcDir());
    myState.put(RES_DIR_KEY, "src/main/res");
    myState.put(AIDL_DIR_KEY, "src/main/aidl");
    myState.put(MANIFEST_DIR_KEY, "src/main");
    myState.put(TEST_DIR_KEY, "src/androidTest");
    myState.put(CREATE_ACTIVITY_KEY, false);

    addStep(new ConfigureAndroidModuleStepDynamic(myDisposable, myFormFactor));
    addStep(new ActivityGalleryStep(myFormFactor, true, KEY_SELECTED_TEMPLATE, null, myDisposable));

    Object packageName = myState.get(PACKAGE_NAME_KEY);
    if (packageName == null) {
      packageName = "";
    }
    Map<String, Object> presetsMap = ImmutableMap
      .of(PACKAGE_NAME_KEY.name, packageName, TemplateMetadata.ATTR_IS_LAUNCHER, true, TemplateMetadata.ATTR_PARENT_ACTIVITY_CLASS, "");
    myParameterStep = new TemplateParameterStep2(myFormFactor, presetsMap, myDisposable, PACKAGE_NAME_KEY, new SourceProvider[0], AddAndroidActivityPath.CUSTOMIZE_ACTIVITY_TITLE);
    addStep(myParameterStep);
  }

  @Override
  public void onPathStarted(boolean fromBeginning) {
    super.onPathStarted(fromBeginning);
    updatePackageDerivedValues();
  }

  void updatePackageDerivedValues() {
    // TODO: Refactor handling of presets in TemplateParameterStep2 so that this isn't necessary
    myParameterStep.setPresetValue(PACKAGE_NAME_KEY.name, myState.get(PACKAGE_NAME_KEY));

    Set<Key> keys = Sets.newHashSetWithExpectedSize(5);
    keys.add(PACKAGE_NAME_KEY);
    keys.add(SRC_DIR_KEY);
    keys.add(TEST_DIR_KEY);
    keys.add(PROJECT_LOCATION_KEY);
    keys.add(NUM_ENABLED_FORM_FACTORS_KEY);
    deriveValues(keys);
  }

  @NotNull
  private String calculateSrcDir() {
    String packageSegment = myState.get(PACKAGE_NAME_KEY);
    if (packageSegment == null) {
      packageSegment = "";
    }
    else {
      packageSegment = packageSegment.replace('.', File.separatorChar);
    }
    return FileUtil.join(RELATIVE_SRC_ROOT, packageSegment);
  }

  @NotNull
  private String calculateTestDir() {
    String packageSegment = myState.get(PACKAGE_NAME_KEY);
    if (packageSegment == null) {
      packageSegment = "";
    }
    else {
      packageSegment = packageSegment.replace('.', File.separatorChar);
    }
    return FileUtil.join(RELATIVE_TEST_ROOT, packageSegment);
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    if (modified.contains(NUM_ENABLED_FORM_FACTORS_KEY)) {
      String moduleName = updateDefaultModuleName(myState.getNotNull(NUM_ENABLED_FORM_FACTORS_KEY, 0), myState.get(myModuleNameKey));
      myState.put(myModuleNameKey, moduleName);
    }
    boolean basePathModified = modified.contains(PROJECT_LOCATION_KEY) || modified.contains(myModuleNameKey);
    if (basePathModified) {
      myState.put(MODULE_LOCATION_KEY, FileUtil.join(myState.get(PROJECT_LOCATION_KEY), myState.get(myModuleNameKey)));
    }
    if (modified.contains(SRC_DIR_KEY) || modified.contains(PACKAGE_NAME_KEY)) {
      myState.put(SRC_DIR_KEY, calculateSrcDir());
    }
    if (modified.contains(TEST_DIR_KEY) || modified.contains(PACKAGE_NAME_KEY)) {
      myState.put(TEST_DIR_KEY, calculateTestDir());
    }
    if (modified.contains(SRC_DIR_KEY) || basePathModified) {
      updateOutputPath(SRC_DIR_KEY, SRC_OUT_KEY);
    }
    if (modified.contains(RES_DIR_KEY) || basePathModified) {
      updateOutputPath(RES_DIR_KEY, RES_OUT_KEY);
    }
    if (modified.contains(AIDL_DIR_KEY) || basePathModified) {
      updateOutputPath(AIDL_DIR_KEY, AIDL_OUT_KEY);
    }
    if (modified.contains(MANIFEST_DIR_KEY) || basePathModified) {
      updateOutputPath(MANIFEST_DIR_KEY, MANIFEST_OUT_KEY);
    }
    if (modified.contains(TEST_DIR_KEY) || basePathModified) {
      updateOutputPath(TEST_DIR_KEY, TEST_OUT_KEY);
    }
    if (myState.containsKey(NEWLY_INSTALLED_API_KEY)) {
      Integer newApiLevel = myState.get(NEWLY_INSTALLED_API_KEY);
      assert newApiLevel != null;
      Key<Integer> targetApiLevelKey = FormFactorUtils.getTargetApiLevelKey(myFormFactor);
      Integer currentTargetLevel = myState.get(targetApiLevelKey);
      if (currentTargetLevel == null || newApiLevel > currentTargetLevel) {
        // If the newly installed is greater than the current target, we know we're not targeting
        // a preview version, so we can safely set build/target api levels to the newly installed level
        String newApiString = Integer.toString(newApiLevel);
        myState.put(targetApiLevelKey, newApiLevel);
        myState.put(FormFactorUtils.getTargetApiStringKey(myFormFactor), newApiString);
        myState.put(FormFactorUtils.getBuildApiLevelKey(myFormFactor), newApiLevel);
        myState.put(FormFactorUtils.getBuildApiKey(myFormFactor), newApiString);
      }
    }
  }

  @NotNull
  private String updateDefaultModuleName(int enabledFormfactorsCount, @Nullable String currentModuleName) {
    if (currentModuleName == null || currentModuleName.equals(myDefaultModuleName)) {
      myDefaultModuleName = enabledFormfactorsCount == 1 ? "app" : FormFactorUtils.getModuleName(myFormFactor);
      return myDefaultModuleName;
    }
    else {
      return currentModuleName;
    }
  }

  private void updateOutputPath(@NotNull Key<String> relativeDirKey, @NotNull Key<String> outputDirKey) {
    String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    String moduleName = myState.get(myModuleNameKey);
    String relativeLocation = myState.get(relativeDirKey);
    if (relativeLocation == null || projectLocation == null || moduleName == null) {
      return;
    }
    File baseLocation = new File(projectLocation, moduleName);
    relativeLocation = FileUtil.toSystemDependentName(relativeLocation);
    myState.put(outputDirKey, new File(baseLocation, relativeLocation).getPath());
  }

  @NotNull
  @Override
  public String getPathName() {
    return myFormFactor + " Module Creation Path";
  }

  @Override
  public boolean isPathVisible() {
    Boolean included = myState.get(myIsIncludedKey);
    return included == null ? false : included;
  }

  @Override
  public boolean canPerformFinishingActions() {
    return performFinishingOperation(true);
  }

  @Override
  public boolean performFinishingActions() {
    return performFinishingOperation(false);
  }

  private boolean performFinishingOperation(boolean dryRun) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.setText(String.format("Initializing module (%1$s)", myFormFactor));
    }
    String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    if (projectLocation != null) {
      File projectRoot = new File(projectLocation);

      String moduleName = myState.get(myModuleNameKey);
      assert moduleName != null;

      File moduleRoot = new File(projectRoot, moduleName);
      try {
        checkedCreateDirectoryIfMissing(moduleRoot);
      }
      catch (IOException e) {
        LOG.error(e);
        return false;
      }

      Template template = Template.createFromPath(myTemplateFile);
      Map<String, Object> templateState = FormFactorUtils.scrubFormFactorPrefixes(myFormFactor, myState.flatten());
      // @formatter:off
      RenderingContext context = RenderingContext.Builder.newContext(template, myWizard.getProject())
        .withCommandName("New Module")
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withOutputRoot(projectRoot)
        .withModuleRoot(moduleRoot)
        .withParams(templateState)
        .withGradleSync(myGradleSyncIfNecessary)
        .intoTargetFiles(myState.get(WizardConstants.TARGET_FILES_KEY))
        .intoOpenFiles(myState.get(FILES_TO_OPEN_KEY))
        .intoDependencies(myState.get(DEPENDENCIES_KEY))
        .build();
      // @formatter:on
      if (!template.render(context)) {
        return false;
      }

      TemplateEntry templateEntry = myState.get(KEY_SELECTED_TEMPLATE);
      if (templateEntry == null) {
        return true;
      }
      Template activityTemplate = templateEntry.getTemplate();
      for (Parameter parameter : templateEntry.getMetadata().getParameters()) {
        templateState.put(parameter.id, myState.get(myParameterStep.getParameterKey(parameter)));
      }
      // @formatter:off
      RenderingContext activityContext = RenderingContext.Builder.newContext(activityTemplate, myWizard.getProject())
        .withCommandName("New Module")
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withOutputRoot(projectRoot)
        .withModuleRoot(moduleRoot)
        .withParams(templateState)
        .withGradleSync(myGradleSyncIfNecessary)
        .intoTargetFiles(myState.get(WizardConstants.TARGET_FILES_KEY))
        .intoOpenFiles(myState.get(FILES_TO_OPEN_KEY))
        .intoDependencies(myState.get(DEPENDENCIES_KEY))
        .build();
      // @formatter:on
      if (!activityTemplate.render(activityContext)) {
        return false;
      }

      return true;
    }
    else {
      return false;
    }
  }

  protected void setGradleSyncIfNecessary(boolean value) {
    myGradleSyncIfNecessary = value;
  }
}
