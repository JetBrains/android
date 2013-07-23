/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.rendering.ManifestInfo;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API_LEVEL;

/**
 * NewTemplateObjectWizard is a base class for templates that instantiate new Android objects based on templates. These aren't for
 * complex objects like projects or modules that get customized wizards, but objects simple enough that we can show a generic template
 * parameter page and run the template against the source tree.
 */
public class NewTemplateObjectWizard extends TemplateWizard implements TemplateParameterStep.UpdateListener {
  private static final Logger LOG = Logger.getInstance("#" + NewTemplateObjectWizard.class.getName());

  private TemplateWizardState myWizardState;
  private Project myProject;
  private Module myModule;
  private String myTemplateCategory;

  public NewTemplateObjectWizard(@Nullable Project project, Module module, String templateCategory) {
    super("New " + templateCategory, project);
    myProject = project;
    myModule = module;
    myTemplateCategory = templateCategory;
    init();
  }

  @Override
  protected void init() {
    myWizardState = new TemplateWizardState();
    myWizardState.put(ATTR_BUILD_API, AndroidPlatform.getInstance(myModule).getTarget().getVersion().getApiLevel());
    myWizardState.put(ATTR_MIN_API_LEVEL, ManifestInfo.get(myModule).getMinSdkVersion());

    mySteps.add(new ChooseTemplateStep(myWizardState, myTemplateCategory, myProject, null, this, null));
    mySteps.add(new TemplateParameterStep(myWizardState, myProject, null, this));

    myWizardState.put(NewProjectWizardState.ATTR_PROJECT_LOCATION, myProject.getBasePath());
    // We're really interested in the directory name on disk, not the module name. These will be different if you give a module the same
    // name as its containing project.
    String moduleName = new File(myModule.getModuleFilePath()).getParentFile().getName();
    myWizardState.put(NewProjectWizardState.ATTR_MODULE_NAME, moduleName);

    myWizardState.myHidden.add(TemplateMetadata.ATTR_PACKAGE_NAME);
    myWizardState.put(TemplateMetadata.ATTR_PACKAGE_NAME, ManifestInfo.get(myModule).getPackage());

    super.init();
  }

  public void createTemplateObject() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          myWizardState.populateDirectoryParameters();
          File projectRoot = new File(myProject.getBasePath());

          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
          VirtualFile[] contentRoots = moduleRootManager.getContentRoots();
          if (contentRoots.length > 0) {
            VirtualFile rootDir = contentRoots[0];
            File moduleRoot = new File(rootDir.getCanonicalPath());
            myWizardState.myTemplate.render(projectRoot, moduleRoot, myWizardState.myParameters);
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }
}
