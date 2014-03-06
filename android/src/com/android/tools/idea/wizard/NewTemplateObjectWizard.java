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

import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidUtils;
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
public class NewTemplateObjectWizard extends TemplateWizard implements TemplateParameterStep.UpdateListener,
                                                                       ChooseTemplateStep.TemplateChangeListener {
  private static final Logger LOG = Logger.getInstance("#" + NewTemplateObjectWizard.class.getName());

  private TemplateWizardState myWizardState;
  private Project myProject;
  private Module myModule;
  private String myTemplateCategory;
  private VirtualFile myTargetFolder;
  private Set<String> myExcluded;
  private AssetSetStep myAssetSetStep;
  private ChooseTemplateStep myChooseTemplateStep;

  public NewTemplateObjectWizard(@Nullable Project project,
                                 @Nullable Module module,
                                 @Nullable VirtualFile invocationTarget,
                                 String templateCategory) {
   this(project, module, invocationTarget, templateCategory, null);
  }

  public NewTemplateObjectWizard(@Nullable Project project,
                                 @Nullable Module module,
                                 @Nullable VirtualFile invocationTarget,
                                 String templateCategory,
                                 @Nullable Set<String> excluded) {
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

    myExcluded = excluded;

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
    int minSdkVersion;
    String minSdkName;
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);
    String packageName = null;
    IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();

    // Look up the default resource directories
    VirtualFile javaDir = null;
    if (gradleProject != null) {
      IdeaSourceProvider sourceSet = facet.getMainIdeaSourceSet();
      VirtualFile moduleDir = gradleProject.getRootDir();
      Set<VirtualFile> javaDirectories = sourceSet.getJavaDirectories();
      if (!javaDirectories.isEmpty()) {
        javaDir = javaDirectories.iterator().next();
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

    if (myTargetFolder != null) {
      File javaSourceRoot;
      if (javaDir == null) {
        javaSourceRoot = new File(AndroidRootUtil.getModuleDirPath(myModule),
                                  FileUtil.toSystemDependentName(myWizardState.getString(ATTR_SRC_DIR)));
      } else {
        javaSourceRoot = new File(javaDir.getPath());
      }

      File javaSourcePackageRoot = new File(myTargetFolder.getPath());
      String relativePath = FileUtil.getRelativePath(javaSourceRoot, javaSourcePackageRoot);
      packageName = relativePath != null ? FileUtil.toSystemIndependentName(relativePath).replace('/', '.') : null;
      if (!AndroidUtils.isValidJavaPackageName(packageName)) {
        packageName = null;
        myTargetFolder = null;
      }
    }
    if (packageName == null) {
      packageName = moduleInfo.getPackage();
    }

    minSdkVersion = moduleInfo.getMinSdkVersion();
    minSdkName = moduleInfo.getMinSdkName();

    myWizardState.put(ATTR_PACKAGE_NAME, packageName);
    myWizardState.put(ATTR_MIN_API, minSdkName);
    myWizardState.put(ATTR_MIN_API_LEVEL, minSdkVersion);

    myChooseTemplateStep = new ChooseTemplateStep(myWizardState, myTemplateCategory, myProject, null, this, this, myExcluded);
    mySteps.add(myChooseTemplateStep);
    mySteps.add(new TemplateParameterStep(myWizardState, myProject, null, this));
    myAssetSetStep = new AssetSetStep(myWizardState, myProject, null, this);
    mySteps.add(myAssetSetStep);


    myWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, myProject.getBasePath());
    // We're really interested in the directory name on disk, not the module name. These will be different if you give a module the same
    // name as its containing project.
    String moduleName = new File(myModule.getModuleFilePath()).getParentFile().getName();
    myWizardState.put(NewProjectWizardState.ATTR_MODULE_NAME, moduleName);

    if (myTargetFolder != null) {
      myWizardState.myHidden.add(TemplateMetadata.ATTR_PACKAGE_NAME);
      myWizardState.myFinal.add(TemplateMetadata.ATTR_PACKAGE_NAME);
      myWizardState.put(TemplateMetadata.ATTR_PACKAGE_ROOT, myTargetFolder.getPath());
    }

    myWizardState.myFinal.add(TemplateMetadata.ATTR_PACKAGE_ROOT);

    myWizardState.put(TemplateMetadata.ATTR_IS_LIBRARY_MODULE, facet.isLibraryProject());

    super.init();

    // Ensure that the window is large enough to accommodate the contents without clipping the validation error label
    Dimension preferredSize = getContentPanel().getPreferredSize();
    getContentPanel().setPreferredSize(new Dimension(Math.max(800, preferredSize.width), Math.max(640, preferredSize.height)));
  }

  /**
   * Exclude the given template name from the selection presented to the user
   */
  public void exclude(String templateName) {
     myExcluded.add(templateName);
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
            // Render the assets if necessary
            if (myAssetSetStep.isStepVisible()) {
              myAssetSetStep.createAssets(myModule);
            }
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

  @Override
  public void templateChanged(String templateName) {
    if (myChooseTemplateStep != null) {
      TemplateMetadata chosenTemplateMetadata = myChooseTemplateStep.getSelectedTemplateMetadata();
      if (chosenTemplateMetadata != null && chosenTemplateMetadata.getIconType() != null) {
        myAssetSetStep.finalizeAssetType(chosenTemplateMetadata.getIconType());
        myWizardState.put(ATTR_ICON_NAME, chosenTemplateMetadata.getIconName());
        myAssetSetStep.setVisible(true);
      } else {
        myAssetSetStep.setVisible(false);
      }
    }
  }
}
