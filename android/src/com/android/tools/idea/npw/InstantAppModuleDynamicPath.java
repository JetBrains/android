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
package com.android.tools.idea.npw;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.recipe.RenderingContext;
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
public class InstantAppModuleDynamicPath extends DynamicWizardPath implements NewModuleDynamicPath {
  public static final String JAVA_LIBRARY = "Instant Application";
  private static final ScopedStateStore.Key<String> PACKAGE_NAME =
    ScopedStateStore.createKey(ATTR_PACKAGE_NAME, ScopedStateStore.Scope.PATH, String.class);

  @NotNull private final Disposable myDisposable;
  private final TemplateMetadata myMetadata;
  private final Template myTemplate;
  private CreateModuleTemplate myModuleTemplate;

  public InstantAppModuleDynamicPath(@NotNull Disposable disposable) {

    myDisposable = disposable;
    TemplateManager instance = TemplateManager.getInstance();
    myMetadata = instance.getTemplateMetadata(Template.CATEGORY_APPLICATION, JAVA_LIBRARY);
    assert myMetadata != null;
    File templateFile = instance.getTemplateFile(Template.CATEGORY_APPLICATION, JAVA_LIBRARY);
    assert templateFile != null;
    myTemplate = Template.createFromPath(templateFile);
    myModuleTemplate = new CreateModuleTemplate(myMetadata, null, myMetadata.getTitle(), AndroidIcons.ModuleTemplates.InstantAppModule);
  }

  @Override
  protected void init() {
    TemplateParameterStep2 parameterStep =
      new TemplateParameterStep2(null, ImmutableMap.of(), myDisposable, PACKAGE_NAME,
                                 new SourceProvider[]{}, JAVA_LIBRARY) {
        @Override
        public boolean isStepVisible() {
          return true;
        }
      };
    addStep(parameterStep);
    myState.put(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE, new TemplateEntry(myTemplate.getRootPath(), myMetadata));
  }

  @Override
  public boolean isPathVisible() {
    return myModuleTemplate.equals(myState.get(WizardConstants.SELECTED_MODULE_TYPE_KEY));
  }

  @NotNull
  @Override
  public String getPathName() {
    return "New Instant App";
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
    String projectName = (String)parameterValueMap.get(ATTR_MODULE_NAME);
    String moduleName = WizardUtils.computeModuleName(projectName, getProject());
    String modulePath = FileUtil.toSystemIndependentName(FileUtil.join(project.getBasePath(), moduleName));
    parameterValueMap.put(ATTR_PROJECT_OUT, modulePath);
    parameterValueMap.put(ATTR_MODULE_NAME, moduleName);

    // @formatter:off
    RenderingContext context = RenderingContext.Builder.newContext(myTemplate, project)
      .withCommandName("New Java Library")
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withModuleRoot(new File(FileUtil.toSystemDependentName(modulePath)))
      .withParams(parameterValueMap)
      .withPerformSync(true)
      .build();
    // @formatter:on
    return myTemplate.render(context);
  }

  @NotNull
  @Override
  public Iterable<ModuleTemplate> getModuleTemplates() {
    return ImmutableSet.of(myModuleTemplate);
  }
}
