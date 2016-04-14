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
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Path for creating a plain Java Module
 */
public class JavaModuleDynamicPath extends DynamicWizardPath implements NewModuleDynamicPath {
  public static final String JAVA_LIBRARY = "Java Library";
  private static final ScopedStateStore.Key<String> SRC_DIR = ScopedStateStore.createKey(ATTR_SRC_DIR, ScopedStateStore.Scope.PATH, String.class);
  private static final ScopedStateStore.Key<String> PACKAGE_NAME = ScopedStateStore.createKey(ATTR_PACKAGE_NAME, ScopedStateStore.Scope.PATH, String.class);

  @NotNull private final Disposable myDisposable;
  private final TemplateMetadata myMetadata;
  private final Template myTemplate;
  private CreateModuleTemplate myModuleTemplate;

  public JavaModuleDynamicPath(@NotNull Disposable disposable) {

    myDisposable = disposable;
    TemplateManager instance = TemplateManager.getInstance();
    myMetadata = instance.getTemplateMetadata(Template.CATEGORY_APPLICATION, JAVA_LIBRARY);
    assert myMetadata != null;
    File templateFile = instance.getTemplateFile(Template.CATEGORY_APPLICATION, JAVA_LIBRARY);
    assert templateFile != null;
    myTemplate = Template.createFromPath(templateFile);
    myModuleTemplate = new CreateModuleTemplate(myMetadata, null, myMetadata.getTitle(), AndroidIcons.ModuleTemplates.Android);
  }

  @Override
  protected void init() {
    TemplateParameterStep2 parameterStep =
      new TemplateParameterStep2(null, ImmutableMap.<String, Object>of(), myDisposable, PACKAGE_NAME,
                                 new SourceProvider[]{}, JAVA_LIBRARY) {
        @Override
        public boolean isStepVisible() {
          return true;
        }
      };
    addStep(parameterStep);
    myState.put(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE, new TemplateEntry(myTemplate.getRootPath(), myMetadata));
    myState.put(SRC_DIR, "src/main/java");

    put(ATTR_RES_DIR, "src/main/res");
    put(ATTR_AIDL_DIR, "src/main/aidl");
    put(ATTR_MANIFEST_DIR, "src/main");
    put(ATTR_TEST_DIR, "src/androidTest");
  }

  private void put(String attr, String value) {
    ScopedStateStore.Key<String> key = ScopedStateStore.createKey(attr, ScopedStateStore.Scope.PATH, String.class);
    myState.put(key, value);
  }

  @Override
  public boolean isPathVisible() {
    return myModuleTemplate.equals(myState.get(WizardConstants.SELECTED_MODULE_TYPE_KEY));
  }

  @NotNull
  @Override
  public String getPathName() {
    return "New Java Module";
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
    Project project = getProject();
    assert project != null;
    Map<String, Object> parameterValueMap = Maps.newHashMap();

    // Grab our parameters from the state
    parameterValueMap.putAll(myState.flatten());

    // Compute the module directory
    String projectName = (String)parameterValueMap.get(FormFactorUtils.ATTR_MODULE_NAME);
    String moduleName = WizardUtils.computeModuleName(projectName, getProject());
    String modulePath = FileUtil.toSystemIndependentName(FileUtil.join(project.getBasePath(), moduleName));
    parameterValueMap.put(TemplateMetadata.ATTR_PROJECT_OUT, modulePath);
    parameterValueMap.put(FormFactorUtils.ATTR_MODULE_NAME, moduleName);

    // Compute the output directory
    String packageName = myState.get(PACKAGE_NAME);
    assert packageName != null;
    String packagePath = FileUtil.join(myState.getNotNull(SRC_DIR, "src/main/java/"), packageName.replace('.', '/'));
    String srcOut = FileUtil.toSystemIndependentName(FileUtil.join(modulePath, packagePath));
    parameterValueMap.put(TemplateMetadata.ATTR_SRC_OUT, srcOut);
    parameterValueMap.put(TemplateMetadata.ATTR_IS_NEW_PROJECT, true);
    parameterValueMap.put(ATTR_IS_LIBRARY_MODULE, true);

    // @formatter:off
    RenderingContext context = RenderingContext.Builder.newContext(myTemplate, project)
      .withCommandName("New Java Library")
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withModuleRoot(new File(FileUtil.toSystemDependentName(modulePath)))
      .withParams(parameterValueMap)
      .withGradleSync(false)
      .build();
    // @formatter:on
    return myTemplate.render(context);
  }

  @NotNull
  @Override
  public Iterable<ModuleTemplate> getModuleTemplates() {
    return ImmutableSet.<ModuleTemplate>of(myModuleTemplate);
  }
}
