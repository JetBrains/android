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
package com.android.tools.idea.npw;

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.SourceProvider;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.templates.KeystoreUtils.getDebugKeystore;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * NewTemplateObjectWizard is a base class for templates that instantiate new Android objects based on templates. These aren't for
 * complex objects like projects or modules that get customized wizards, but objects simple enough that we can show a generic template
 * parameter page and run the template against the source tree.
 *
 * Deprecated. Extend from {@link DynamicWizard} instead.
 */
@Deprecated
public class NewTemplateObjectWizard extends TemplateWizard implements TemplateWizardStep.UpdateListener,
                                                                       ChooseTemplateStep.TemplateChangeListener,
                                                                       ChooseSourceSetStep.SourceProviderSelectedListener {
  private static final Logger LOG = Logger.getInstance("#" + NewTemplateObjectWizard.class.getName());

  protected TemplateWizardState myWizardState;
  protected Project myProject;
  protected Module myModule;
  private String myTemplateCategory;
  private String myTemplateName;
  private VirtualFile myTargetFolder;
  private Set<String> myExcluded;
  @VisibleForTesting RasterAssetSetStep myAssetSetStep;
  @VisibleForTesting ChooseTemplateStep myChooseTemplateStep;
  private List<SourceProvider> mySourceProviders;
  private AndroidModel myAndroidModel;
  protected TemplateParameterStep myTemplateParameterStep;
  protected ChooseSourceSetStep myChooseSourceSetStep;
  private File myTemplateFile;

  public NewTemplateObjectWizard(@Nullable Project project,
                                 @Nullable Module module,
                                 @Nullable VirtualFile invocationTarget,
                                 @Nullable String templateCategory) {
    this(project, module, invocationTarget, templateCategory, null, null);
    init();
  }

  public NewTemplateObjectWizard(@Nullable Project project,
                                 @Nullable Module module,
                                 @Nullable VirtualFile invocationTarget,
                                 @Nullable String title,
                                 @Nullable File templateFile) {
    this(project, module, invocationTarget, null, title, null);
    if (templateFile != null) {
      myWizardState.setTemplateLocation(templateFile);
      myTemplateFile = templateFile;
    }
    init();
  }

  public NewTemplateObjectWizard(@Nullable Project project,
                                 @Nullable Module module,
                                 @Nullable VirtualFile invocationTarget,
                                 @Nullable String title,
                                 @NotNull List<File> templateFiles) {
    this(project, module, invocationTarget, null, title, null);
    init();
    myChooseTemplateStep.setListData(ChooseTemplateStep.getTemplateList(myWizardState, templateFiles, null));
  }

  private NewTemplateObjectWizard(@Nullable Project project,
                                  @Nullable Module module,
                                  @Nullable VirtualFile invocationTarget,
                                  @Nullable String templateCategory,
                                  @Nullable String templateName,
                                  @Nullable Set<String> excluded) {
    super("New " + (templateName != null ? templateName : templateCategory), project);
    myProject = project;
    myModule = module;
    myTemplateCategory = templateCategory;
    myTemplateName = templateName;
    if (invocationTarget == null) {
      myTargetFolder = null;
    }
    else if (invocationTarget.isDirectory()) {
      myTargetFolder = invocationTarget;
    }
    else {
      myTargetFolder = invocationTarget.getParent();
    }

    myExcluded = excluded;
    myWizardState = new TemplateWizardState();
  }

  @Override
  protected void init() {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;
    AndroidPlatform platform = AndroidPlatform.getInstance(myModule);
    assert platform != null;
    AndroidVersion version = platform.getTarget().getVersion();
    myWizardState.put(ATTR_BUILD_API, version.getFeatureLevel());
    myWizardState.put(ATTR_BUILD_API_STRING, TemplateMetadata.getBuildApiString(version));

    // Read minSdkVersion and package from manifest and/or build.gradle files
    int minSdkVersion;
    String minSdkName;
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);
    AndroidModel androidModel = facet.getAndroidModel();

    if (androidModel != null) {
      myAndroidModel = androidModel;
      // Select the source set that we're targeting
      mySourceProviders = IdeaSourceProvider.getSourceProvidersForFile(facet, myTargetFolder, facet.getMainSourceProvider());
      SourceProvider sourceProvider = mySourceProviders.get(0);

      selectSourceProvider(sourceProvider, androidModel);
    }

    minSdkVersion = moduleInfo.getMinSdkVersion().getFeatureLevel();
    minSdkName = moduleInfo.getMinSdkVersion().getApiString();

    myWizardState.put(ATTR_MIN_API, minSdkName);
    myWizardState.put(ATTR_MIN_API_LEVEL, minSdkVersion);
    myWizardState.put(ATTR_TARGET_API, moduleInfo.getTargetSdkVersion().getFeatureLevel());
    myWizardState.put(ATTR_TARGET_API_STRING, moduleInfo.getTargetSdkVersion().getApiString());

    myWizardState.put(ATTR_IS_LIBRARY_MODULE, facet.isLibraryProject());

    try {
      myWizardState.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(getDebugKeystore(facet)));
    }
    catch (Exception e) {
      LOG.info("Could not compute SHA1 hash of debug keystore.", e);
      myWizardState.put(ATTR_DEBUG_KEYSTORE_SHA1, "");
    }

    File templateFile = myTemplateFile != null ? myTemplateFile : TemplateManager.getInstance().getTemplateFile(myTemplateCategory, myTemplateName);
    if (myTemplateName == null || templateFile == null) {
      myChooseTemplateStep = new ChooseTemplateStep(myWizardState, myTemplateCategory, myProject, myModule, null, this, this, myExcluded);
      mySteps.add(myChooseTemplateStep);
    } else {
      myWizardState.setTemplateLocation(templateFile);
    }
    if (mySourceProviders != null && mySourceProviders.size() != 1) {
      myChooseSourceSetStep = new ChooseSourceSetStep(myWizardState, myProject, myModule, null, this, this, mySourceProviders);
      mySteps.add(myChooseSourceSetStep);
    }
    myTemplateParameterStep = new TemplateParameterStep(myWizardState, myProject, myModule, null, this);
    mySteps.add(myTemplateParameterStep);
    myAssetSetStep = new RasterAssetSetStep(myWizardState, myProject, myModule, null, this, myTargetFolder);
    Disposer.register(getDisposable(), myAssetSetStep);
    mySteps.add(myAssetSetStep);
    myAssetSetStep.setVisible(false);


    myWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, myProject.getBasePath());
    // We're really interested in the directory name on disk, not the module name. These will be different if you give a module the same
    // name as its containing project.
    String moduleName = new File(myModule.getModuleFilePath()).getParentFile().getName();
    myWizardState.put(FormFactorUtils.ATTR_MODULE_NAME, moduleName);


    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      super.init();
      // Ensure that the window is large enough to accommodate the contents without clipping the validation error label
      Dimension preferredSize = getContentPanel().getPreferredSize();
      getContentPanel()
        .setPreferredSize(new Dimension(Math.max(JBUI.scale(800), preferredSize.width), Math.max(JBUI.scale(640), preferredSize.height)));
    }
  }

  private void selectSourceProvider(@NotNull SourceProvider sourceProvider, @NotNull AndroidModel androidModel) {
    // Look up the resource directories inside this source set
    File moduleDirPath = androidModel.getRootDirPath();
    File javaDir = findSrcDirectory(sourceProvider);
    if (javaDir != null) {
      String javaPath = FileUtil.getRelativePath(moduleDirPath, javaDir);
      if (javaPath != null) {
        javaPath = FileUtil.toSystemIndependentName(javaPath);
      }
      myWizardState.put(ATTR_SRC_DIR, javaPath);
    }

    File resDir = findResDirectory(sourceProvider);
    if (resDir != null) {
      String resPath = FileUtil.getRelativePath(moduleDirPath, resDir);
      if (resPath != null) {
        resPath = FileUtil.toSystemIndependentName(resPath);
      }
      myWizardState.put(ATTR_RES_DIR, resPath);
      myWizardState.put(ATTR_RES_OUT, FileUtil.toSystemIndependentName(resDir.getPath()));
    }
    File manifestDir = findManifestDirectory(sourceProvider);
    if (manifestDir != null) {
      String manifestPath = FileUtil.getRelativePath(moduleDirPath, manifestDir);
      myWizardState.put(ATTR_MANIFEST_DIR, manifestPath);
      myWizardState.put(ATTR_MANIFEST_OUT, FileUtil.toSystemIndependentName(manifestDir.getPath()));
    }
    File aidlDir = findAidlDir(sourceProvider);
    if (aidlDir != null) {
      String aidlPath = FileUtil.getRelativePath(moduleDirPath, aidlDir);
      myWizardState.put(ATTR_AIDL_DIR, aidlPath);
      myWizardState.put(ATTR_AIDL_OUT, FileUtil.toSystemIndependentName(aidlDir.getPath()));
    }

    // Calculate package name
    String applicationPackageName = MergedManifest.get(myModule).getPackage();
    String packageName = null;
    if (myTargetFolder != null && IdeaSourceProvider.containsFile(sourceProvider, VfsUtilCore.virtualToIoFile(myTargetFolder))) {
      packageName = getPackageFromDirectory(VfsUtilCore.virtualToIoFile(myTargetFolder), sourceProvider, myModule, myWizardState);
      if (packageName != null && !packageName.equals(applicationPackageName)) {
        // If we have selected a folder, make sure we pass along the application package
        // so that we can do proper imports
        myWizardState.put(ATTR_APPLICATION_PACKAGE, applicationPackageName);
        myWizardState.myFinal.add(ATTR_APPLICATION_PACKAGE);
      }
    }
    if (packageName == null) {
      // Fall back to the application package but allow the user to edit
      packageName = applicationPackageName;
    } else {
      myWizardState.myHidden.add(TemplateMetadata.ATTR_PACKAGE_NAME);
      myWizardState.myFinal.add(TemplateMetadata.ATTR_PACKAGE_NAME);
      if (javaDir != null) {
        String packagePath = FileUtil.toSystemDependentName(packageName.replace('.', '/'));
        File packageRoot = new File(javaDir, packagePath);
        myWizardState.put(TemplateMetadata.ATTR_PACKAGE_ROOT, FileUtil.toSystemIndependentName(packageRoot.getPath()));
        myWizardState.myFinal.add(TemplateMetadata.ATTR_PACKAGE_ROOT);
      }
    }
    if (javaDir != null) {
      File srcOut = new File(javaDir, packageName.replace('.', File.separatorChar));
      myWizardState.put(ATTR_SRC_OUT, FileUtil.toSystemIndependentName(srcOut.getPath()));
    }
    myWizardState.put(ATTR_PACKAGE_NAME, packageName);
    myWizardState.put(ATTR_SOURCE_PROVIDER_NAME, sourceProvider.getName());

    myWizardState.setSourceProvider(sourceProvider);
  }

  /**
   * Finds and returns the main src directory for the given project or null if one cannot be found.
   */
  @Nullable
  public static File findSrcDirectory(@NotNull SourceProvider sourceProvider) {
    Collection<File> javaDirectories = sourceProvider.getJavaDirectories();
    return javaDirectories.isEmpty() ? null : javaDirectories.iterator().next();
  }

  /**
   * Finds and returns the main res directory for the given project or null if one cannot be found.
   */
  @Nullable
  public static File findResDirectory(@NotNull SourceProvider sourceProvider) {
    Collection<File> resDirectories = sourceProvider.getResDirectories();
    File resDir = null;
    if (!resDirectories.isEmpty()) {
      resDir = resDirectories.iterator().next();
    }
    return resDir;
  }

  /**
   * Finds and returns the main res directory for the given project or null if one cannot be found.
   */
  @Nullable
  public static File findAidlDir(@NotNull SourceProvider sourceProvider) {
    Collection<File> aidlDirectories = sourceProvider.getAidlDirectories();
    File resDir = null;
    if (!aidlDirectories.isEmpty()) {
      resDir = aidlDirectories.iterator().next();
    }
    return resDir;
  }

  /**
   * Finds and returns the main manifest directory for the given project or null if one cannot be found.
   */
  @Nullable
  public static File findManifestDirectory(@NotNull SourceProvider sourceProvider) {
    File manifestFile = sourceProvider.getManifestFile();
    File manifestDir = manifestFile.getParentFile();
    if (manifestDir != null) {
      return manifestDir;
    }
    return null;
  }

  /**
   * Calculate the package name from the given target directory. Returns the package name or null if no package name could
   * be calculated.
   */
  @Nullable
  public static String getPackageFromDirectory(@NotNull File directory, @NotNull SourceProvider sourceProvider,
                                        @NotNull Module module, @NotNull TemplateWizardState wizardState) {
    File javaSourceRoot;
    File javaDir = findSrcDirectory(sourceProvider);
    if (javaDir == null) {
      javaSourceRoot = new File(AndroidRootUtil.getModuleDirPath(module),
                                FileUtil.toSystemDependentName(wizardState.getString(ATTR_SRC_DIR)));
    }
    else {
      javaSourceRoot = new File(javaDir.getPath());
    }

    File javaSourcePackageRoot = new File(directory.getPath());
    if (!FileUtil.isAncestor(javaSourceRoot, javaSourcePackageRoot, true)) {
      return null;
    }

    String relativePath = FileUtil.getRelativePath(javaSourceRoot, javaSourcePackageRoot);
    String packageName = relativePath != null ? FileUtil.toSystemIndependentName(relativePath).replace('/', '.') : null;
    if (packageName == null || !AndroidUtils.isValidJavaPackageName(packageName)) {
      return null;
    }
    return packageName;
  }

  /**
   * Exclude the given template name from the selection presented to the user
   */
  public void exclude(String templateName) {
    myExcluded.add(templateName);
  }

  public void createTemplateObject(final boolean openEditors) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          myWizardState.populateDirectoryParameters();
          myWizardState.populateRelativePackage(myModule);
          File projectRoot = new File(myProject.getBasePath());

          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
          VirtualFile[] contentRoots = moduleRootManager.getContentRoots();
          if (contentRoots.length > 0) {
            VirtualFile rootDir = contentRoots[0];
            File moduleRoot = VfsUtilCore.virtualToIoFile(rootDir);
            // @formatter:off
            RenderingContext context = RenderingContext.Builder.newContext(myWizardState.myTemplate, myProject)
              .withOutputRoot(projectRoot)
              .withModuleRoot(moduleRoot)
              .withParams(myWizardState.myParameters)
              .build();
            // @formatter:on
            myWizardState.myTemplate.render(context);
            // Render the assets if necessary
            if (myAssetSetStep.isStepVisible()) {
              myAssetSetStep.createAssets(myModule);
            }
            // Open any new files specified by the template
            if (openEditors) {
              TemplateUtils.openEditors(myProject, context.getFilesToOpen(), true);
            }
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  public void createTemplateObject() {
    createTemplateObject(true);
  }

  @Override
  public void templateChanged(String templateName) {
    if (myChooseTemplateStep != null) {
      TemplateMetadata chosenTemplateMetadata = myChooseTemplateStep.getSelectedTemplateMetadata();
      updateAssetSetStep(chosenTemplateMetadata);
    }
  }

  protected void updateAssetSetStep(@Nullable TemplateMetadata chosenTemplateMetadata) {
    if (chosenTemplateMetadata != null && chosenTemplateMetadata.getIconType() != null) {
      myAssetSetStep.finalizeAssetType(AssetStudioAssetGenerator.AssetType.of(chosenTemplateMetadata.getIconType()));
      myWizardState.put(ATTR_ICON_NAME, chosenTemplateMetadata.getIconName());
      myAssetSetStep.setVisible(true);
    }
    else {
      myAssetSetStep.setVisible(false);
    }
  }

  @Override
  public void sourceProviderSelected(@NotNull SourceProvider sourceProvider) {
    if (myAndroidModel != null) {
      selectSourceProvider(sourceProvider, myAndroidModel);
    }
  }

  @Override
  protected boolean canFinish() {
    if (!super.canFinish()) {
      return false;
    }
    TemplateMetadata metadata = myWizardState.getTemplateMetadata();
    int minApi = myWizardState.getInt(ATTR_MIN_API_LEVEL);
    if (metadata != null && metadata.getMinSdk() > minApi) {
      ((TemplateWizardStep)getCurrentStepObject()).setErrorHtml("This template requires a minimum API level of " + metadata.getMinSdk());
      return false;
    }
    return true;
  }
}
