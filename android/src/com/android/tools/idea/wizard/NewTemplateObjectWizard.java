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

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.rendering.ManifestInfo;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;

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
  private VirtualFile myTargetFolder;

  public NewTemplateObjectWizard(@Nullable Project project, Module module, VirtualFile invocationTarget, String templateCategory) {
    super("New " + templateCategory, project);
    myProject = project;
    myModule = module;
    myTemplateCategory = templateCategory;
    if (invocationTarget == null) {
      myTargetFolder = null;
    } else if (invocationTarget.isDirectory()) {
      myTargetFolder = invocationTarget;
    } else {
      myTargetFolder = invocationTarget.getParent();
    }

    init();
  }

  @Override
  protected void init() {
    myWizardState = new TemplateWizardState();
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;
    AndroidPlatform platform = AndroidPlatform.getInstance(myModule);
    assert platform != null;
    myWizardState.put(ATTR_BUILD_API, platform.getTarget().getVersion().getApiLevel());

    // Read minSdkVersion and package from manifest and/or build.gradle files
    int minSdkVersion = -1;
    String minSdkName;
    ManifestInfo manifestInfo = ManifestInfo.get(myModule);
    String packageName = manifestInfo.getPackage();
    IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
    if (gradleProject != null) {
      minSdkVersion = gradleProject.getSelectedVariant().getMergedFlavor().getMinSdkVersion();
      packageName = IdeaAndroidProject.computePackageName(gradleProject, packageName);
    }
    if (minSdkVersion < 1) { // Not specified in Gradle file
      minSdkVersion = manifestInfo.getMinSdkVersion();
      minSdkName = manifestInfo.getMinSdkName();
    } else {
      minSdkName = Integer.toString(minSdkVersion);
    }
    myWizardState.put(ATTR_MIN_API, minSdkName);
    myWizardState.put(ATTR_MIN_API_LEVEL, minSdkVersion);
    myWizardState.put(TemplateMetadata.ATTR_PACKAGE_NAME, packageName);

    // Look up the default resource directories
    if (gradleProject != null) {
      IdeaSourceProvider sourceSet = facet.getMainIdeaSourceSet();
      VirtualFile moduleDir = LocalFileSystem.getInstance().findFileByIoFile(gradleProject.getRootDir());
      if (moduleDir == null) {
        VirtualFile moduleFile = myModule.getModuleFile();
        if (moduleFile != null) {
          moduleDir = moduleFile.getParent();
        }
      }

      if (moduleDir != null) {
        Set<VirtualFile> javaDirectories = sourceSet.getJavaDirectories();
        if (!javaDirectories.isEmpty()) {
          VirtualFile javaDir = javaDirectories.iterator().next();
          String relativePath = VfsUtilCore.getRelativePath(javaDir, moduleDir, '/'); // templates use / not File.separatorChar
          if (relativePath != null) {
            myWizardState.put(ATTR_SRC_DIR, relativePath);
          }
        }
        Set<VirtualFile> resDirectories = sourceSet.getResDirectories();
        if (!resDirectories.isEmpty()) {
          VirtualFile resDir = resDirectories.iterator().next();
          String relativePath = VfsUtilCore.getRelativePath(resDir, moduleDir, '/');
          if (relativePath != null) {
            myWizardState.put(ATTR_RES_DIR, relativePath);
          }
        }

        VirtualFile manifestFile = sourceSet.getManifestFile();
        if (manifestFile != null) {
          VirtualFile manifestDir = manifestFile.getParent();
          if (manifestDir != null) {
            String relativePath = VfsUtilCore.getRelativePath(manifestDir, moduleDir, '/');
            if (relativePath != null) {
              myWizardState.put(ATTR_MANIFEST_DIR, relativePath);
            }
          }
        }
      }
    }

    mySteps.add(new ChooseTemplateStep(myWizardState, myTemplateCategory, myProject, null, this, null));
    mySteps.add(new TemplateParameterStep(myWizardState, myProject, null, this));

    myWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, myProject.getBasePath());
    // We're really interested in the directory name on disk, not the module name. These will be different if you give a module the same
    // name as its containing project.
    String moduleName = new File(myModule.getModuleFilePath()).getParentFile().getName();
    myWizardState.put(NewProjectWizardState.ATTR_MODULE_NAME, moduleName);

    if (myTargetFolder != null) {
      myWizardState.myHidden.add(TemplateMetadata.ATTR_PACKAGE_NAME);
      myWizardState.put(TemplateMetadata.ATTR_PACKAGE_ROOT, myTargetFolder.getPath());
    }

    myWizardState.myFinal.add(TemplateMetadata.ATTR_PACKAGE_ROOT);

    super.init();

    // Ensure that the window is large enough to accommodate the contents without clipping the validation error label
    Dimension preferredSize = getContentPanel().getPreferredSize();
    getContentPanel().setPreferredSize(new Dimension(Math.max(600, preferredSize.width), Math.max(700, preferredSize.height)));
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
            // Open any new files specified by the template
            TemplateUtils.openEditors(myProject, myWizardState.myTemplate.getFilesToOpen(), true);
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }
}
