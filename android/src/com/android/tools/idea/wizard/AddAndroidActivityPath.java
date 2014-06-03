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

import com.android.builder.model.SourceProvider;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.templates.*;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.templates.KeystoreUtils.getDebugKeystore;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Wizard path for adding a new activity.
 */
public final class AddAndroidActivityPath extends DynamicWizardPath {
  public static final Key<Boolean> KEY_IS_LAUNCHER = createKey("is.launcher.activity", PATH, Boolean.class);
  public static final Key<TemplateEntry> KEY_SELECTED_TEMPLATE = createKey("selected.template", PATH, TemplateEntry.class);
  public static final Key<AndroidVersion> KEY_MIN_SDK = createKey(TemplateMetadata.ATTR_MIN_API, PATH, AndroidVersion.class);
  public static final Key<Integer> KEY_BUILD_SDK = createKey(TemplateMetadata.ATTR_BUILD_API, PATH, Integer.class);
  public static final Key<String> KEY_PACKAGE_NAME = createKey(TemplateMetadata.ATTR_PACKAGE_NAME, PATH, String.class);
  public static final Key<SourceProvider> KEY_SOURCE_PROVIDER = createKey("source.provider", PATH, SourceProvider.class);
  private static final Key<Boolean> KEY_OPEN_EDITORS = createKey("open.editors", WIZARD, Boolean.class);

  private static final Logger LOG = Logger.getInstance(AddAndroidActivityPath.class);

  private final ActivityGalleryStep myGalleryStep;
  private final TemplateParameterStep2 myParameterStep;
  private final boolean myIsNewModule;
  private VirtualFile myTargetFolder;

  /**
   * Creates a new instance of the wizard path.
   */
  public AddAndroidActivityPath(@Nullable VirtualFile targetFolder,
                                @NotNull Map<String, Object> predefinedParameterValues,
                                @NotNull Disposable parentDisposable) {
    myIsNewModule = false;
    myTargetFolder = targetFolder;
    myGalleryStep = new ActivityGalleryStep(null, AndroidIcons.Wizards.FormFactorPhoneTablet,
                                            false, KEY_SELECTED_TEMPLATE, parentDisposable);
    myParameterStep = new TemplateParameterStep2(predefinedParameterValues, myTargetFolder, parentDisposable);
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
  public static String getPackageFromDirectory(@NotNull VirtualFile directory,
                                               @NotNull SourceProvider sourceProvider,
                                               @NotNull Module module, @NotNull String srcDir) {
    File javaSourceRoot;
    File javaDir = findSrcDirectory(sourceProvider);
    if (javaDir == null) {
      javaSourceRoot = new File(AndroidRootUtil.getModuleDirPath(module), srcDir);
    }
    else {
      javaSourceRoot = new File(javaDir.getPath());
    }

    File javaSourcePackageRoot = VfsUtilCore.virtualToIoFile(directory);
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

  @Nullable
  private static File getModuleRoot(@NotNull Module module) {
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      return VfsUtilCore.virtualToIoFile(roots[0]);
    }
    else {
      return null;
    }
  }

  private static Map<String, Object> selectSourceProvider(@NotNull SourceProvider sourceProvider,
                                                          @NotNull IdeaAndroidProject gradleProject,
                                                          @NotNull String packageName) {
    Map<String, Object> paths = Maps.newHashMap();
    // Look up the resource directories inside this source set
    VirtualFile moduleDir = gradleProject.getRootDir();
    File ioModuleDir = VfsUtilCore.virtualToIoFile(moduleDir);
    File javaDir = findSrcDirectory(sourceProvider);
    String javaPath = getJavaPath(ioModuleDir, javaDir);
    paths.put(ATTR_SRC_DIR, javaPath);

    File resDir = findResDirectory(sourceProvider);
    if (resDir != null) {
      String resPath = FileUtil.getRelativePath(ioModuleDir, resDir);
      if (resPath != null) {
        resPath = FileUtil.toSystemIndependentName(resPath);
      }
      paths.put(ATTR_RES_DIR, resPath);
      paths.put(ATTR_RES_OUT, FileUtil.toSystemIndependentName(resDir.getPath()));
    }
    File manifestDir = findManifestDirectory(sourceProvider);
    if (manifestDir != null) {
      String manifestPath = FileUtil.getRelativePath(ioModuleDir, manifestDir);
      paths.put(ATTR_MANIFEST_DIR, manifestPath);
      paths.put(ATTR_MANIFEST_OUT, FileUtil.toSystemIndependentName(manifestDir.getPath()));
    }
    File aidlDir = findAidlDir(sourceProvider);
    if (aidlDir != null) {
      String aidlPath = FileUtil.getRelativePath(ioModuleDir, aidlDir);
      paths.put(ATTR_AIDL_DIR, aidlPath);
      paths.put(ATTR_AIDL_OUT, FileUtil.toSystemIndependentName(aidlDir.getPath()));
    }

    assert javaPath != null;
    // Calculate package name
    paths.put(TemplateMetadata.ATTR_PACKAGE_ROOT, packageName);
    File srcOut = new File(javaDir, packageName.replace('.', File.separatorChar));
    paths.put(ATTR_APPLICATION_PACKAGE, packageName);
    paths.put(ATTR_SRC_OUT, FileUtil.toSystemIndependentName(srcOut.getPath()));
    paths.put(ATTR_SOURCE_PROVIDER_NAME, sourceProvider.getName());
    return paths;
  }

  // TODO: Should the package name be r/o if the action was executed on a folder?

  @Nullable
  private static String getJavaPath(File ioModuleDir, @Nullable File javaDir) {
    String javaPath = null;
    if (javaDir != null) {
      javaPath = FileUtil.getRelativePath(ioModuleDir, javaDir);
      if (javaPath != null) {
        javaPath = FileUtil.toSystemIndependentName(javaPath);
      }
    }
    return javaPath;
  }

  @Override
  protected void init() {
    Module module = getModule();
    assert module != null;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    assert platform != null;
    myState.put(KEY_BUILD_SDK, platform.getTarget().getVersion().getApiLevel());

    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);
    AndroidVersion minSdkVersion = moduleInfo.getMinSdkVersion();

    myState.put(KEY_MIN_SDK, minSdkVersion);
    myState.put(KEY_PACKAGE_NAME, getInitialPackageName(module, facet));
    myState.put(KEY_OPEN_EDITORS, true);
    addStep(myGalleryStep);
    addStep(myParameterStep);

    // TODO Assets support
    //myAssetSetStep = new AssetSetStep(myState, myProject, myModule, null, this, myTargetFolder);
    //Disposer.register(getDisposable(), myAssetSetStep);
    //mySteps.add(myAssetSetStep);
    //myAssetSetStep.setVisible(false);
  }

  /**
   * Initial package name is either a package user selected when invoking the wizard or default package for the module.
   */
  private String getInitialPackageName(Module module, AndroidFacet facet) {
    IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
    assert gradleProject != null;
    if (myTargetFolder != null) {
      List<SourceProvider> sourceProviders = IdeaSourceProvider.getSourceProvidersForFile(facet, myTargetFolder, facet.getMainSourceSet());
      File targetDirectoryFile = VfsUtilCore.virtualToIoFile(myTargetFolder);
      if (sourceProviders.size() > 0 && IdeaSourceProvider.containsFile(sourceProviders.get(0), targetDirectoryFile)) {
        File srcDirectory = findSrcDirectory(sourceProviders.get(0));
        if (srcDirectory != null) {
          String packageName = getPackageFromDirectory(myTargetFolder, sourceProviders.get(0), module, srcDirectory.toString());
          if (packageName != null) {
            return packageName;
          }
        }
      }
    }
    return gradleProject.computePackageName();
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Add Android activity";
  }

  @Override
  public boolean performFinishingActions() {
    TemplateEntry templateEntry = myState.get(KEY_SELECTED_TEMPLATE);
    Project project = getProject();
    Module module = getModule();
    assert templateEntry != null;
    assert project != null && module != null;
    Template template = Template.createFromPath(templateEntry.getTemplate());
    File moduleRoot = getModuleRoot(module);
    if (moduleRoot == null) {
      return false;
    }
    template
      .render(VfsUtilCore.virtualToIoFile(project.getBaseDir()), moduleRoot, getTemplateParameterMap(templateEntry.getMetadata()), project);
    // TODO Assets support
    //if (myAssetSetStep.isStepVisible()) {
    //  myAssetSetStep.createAssets(myModule);
    //}
    if (Boolean.TRUE.equals(myState.get(KEY_OPEN_EDITORS))) {
      TemplateUtils.openEditors(project, template.getFilesToOpen(), true);
    }
    return true;
  }

  @NotNull
  private Map<String, Object> getTemplateParameterMap(@NotNull TemplateMetadata template) {
    Map<String, Object> parameterValueMap = Maps.newHashMap();
    parameterValueMap.put(TemplateMetadata.ATTR_IS_NEW_PROJECT, myIsNewModule);
    parameterValueMap.putAll(getDirectories());
    for (Parameter parameter : template.getParameters()) {
      parameterValueMap.put(parameter.id, myState.get(myParameterStep.getParameterKey(parameter)));
    }
    return parameterValueMap;
  }

  private Map<String, Object> getDirectories() {
    Map<String, Object> templateParameters = Maps.newHashMap();

    Module module = getModule();
    assert module != null;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    assert platform != null;
    templateParameters.put(ATTR_BUILD_API, platform.getTarget().getVersion().getApiLevel());

    // Read minSdkVersion and package from manifest and/or build.gradle files
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);
    IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();

    SourceProvider sourceProvider1 = myState.get(KEY_SOURCE_PROVIDER);
    if (sourceProvider1 != null && gradleProject != null) {
      String packageName = myState.get(KEY_PACKAGE_NAME);
      assert packageName != null;
      templateParameters.putAll(selectSourceProvider(sourceProvider1, gradleProject, packageName));
    }
    AndroidVersion minSdkVersion = moduleInfo.getMinSdkVersion();
    String minSdkName = minSdkVersion.getApiString();

    templateParameters.put(ATTR_MIN_API, minSdkName);
    templateParameters.put(ATTR_MIN_API_LEVEL, minSdkVersion.getApiLevel());

    templateParameters.put(ATTR_IS_LIBRARY_MODULE, facet.isLibraryProject());

    try {
      templateParameters.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(getDebugKeystore(facet)));
    }
    catch (Exception e) {
      LOG.info("Could not compute SHA1 hash of debug keystore.", e);
      templateParameters.put(ATTR_DEBUG_KEYSTORE_SHA1, "");
    }

    Project project = getProject();
    assert project != null;
    templateParameters.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, project.getBasePath());
    // We're really interested in the directory name on disk, not the module name. These will be different if you give a module the same
    // name as its containing project.
    String moduleName = new File(module.getModuleFilePath()).getParentFile().getName();
    templateParameters.put(NewProjectWizardState.ATTR_MODULE_NAME, moduleName);

    return templateParameters;
  }
}
