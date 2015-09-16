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
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RecentsManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.templates.KeystoreUtils.getDebugKeystore;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * Wizard path for adding a new activity.
 */
public final class AddAndroidActivityPath extends DynamicWizardPath {
  public static final Key<Boolean> KEY_IS_LAUNCHER = createKey("is.launcher.activity", PATH, Boolean.class);
  public static final Key<TemplateEntry> KEY_SELECTED_TEMPLATE = createKey("selected.template", PATH, TemplateEntry.class);
  public static final Key<AndroidVersion> KEY_MIN_SDK = createKey(TemplateMetadata.ATTR_MIN_API, PATH, AndroidVersion.class);
  public static final Key<AndroidVersion> KEY_TARGET_API = createKey(TemplateMetadata.ATTR_TARGET_API, PATH, AndroidVersion.class);
  public static final Key<Integer> KEY_BUILD_SDK = createKey(TemplateMetadata.ATTR_BUILD_API, PATH, Integer.class);
  public static final Key<String> KEY_PACKAGE_NAME = createKey(TemplateMetadata.ATTR_PACKAGE_NAME, PATH, String.class);
  public static final Key<SourceProvider> KEY_SOURCE_PROVIDER = createKey("source.provider", PATH, SourceProvider.class);
  public static final Key<String> KEY_SOURCE_PROVIDER_NAME = createKey(ATTR_SOURCE_PROVIDER_NAME, PATH, String.class);
  public static final Set<String> PACKAGE_NAME_PARAMETERS = ImmutableSet.of(TemplateMetadata.ATTR_PACKAGE_NAME);
  public static final Set<String> CLASS_NAME_PARAMETERS = ImmutableSet.of(TemplateMetadata.ATTR_PARENT_ACTIVITY_CLASS);
  public static final Key<Boolean> KEY_OPEN_EDITORS = createKey("open.editors", WIZARD, Boolean.class);
  public static final Set<Key<?>> IMPLICIT_PARAMETERS = ImmutableSet.<Key<?>>of(KEY_PACKAGE_NAME, KEY_SOURCE_PROVIDER_NAME);

  private static final Logger LOG = Logger.getInstance(AddAndroidActivityPath.class);

  private TemplateParameterStep2 myParameterStep;
  private final boolean myIsNewModule;
  private IconStep myAssetStudioStep;
  private final VirtualFile myTargetFolder;
  @Nullable private File myTemplate;
  private final Map<String, Object> myPredefinedParameterValues;
  private final Disposable myParentDisposable;

  /**
   * Creates a new instance of the wizard path.
   */
  public AddAndroidActivityPath(@Nullable VirtualFile targetFolder,
                                @Nullable File template,
                                Map<String, Object> predefinedParameterValues,
                                Disposable parentDisposable) {
    myTemplate = template;
    myPredefinedParameterValues = predefinedParameterValues;
    myParentDisposable = parentDisposable;
    myIsNewModule = false;
    myTargetFolder = targetFolder != null && !targetFolder.isDirectory() ? targetFolder.getParent() : targetFolder;
  }

  private static FormFactorUtils.FormFactor getFormFactor(@Nullable VirtualFile targetFolder) {
    // TODO There should be some way for this wizard to figure out form factor from a target or from a template
    return FormFactorUtils.FormFactor.MOBILE;
  }

  /**
   * Finds and returns the main src directory for the given project or null if one cannot be found.
   */
  @Nullable
  public static File findSrcDirectory(@NotNull SourceProvider sourceProvider) {
    return Iterables.getFirst(sourceProvider.getJavaDirectories(), null);
  }

  @Nullable
  private static File findTestDirectory(@NotNull Module module) {
    List<VirtualFile> testsRoot = ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.TESTS);
    return testsRoot.size() == 0 ? null : VfsUtilCore.virtualToIoFile(testsRoot.get(0));
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
                                               @NotNull Module module,
                                               @NotNull String srcDir) {
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
  private static File getModuleRoot(@Nullable Module module) {
    if (module == null) {
      return null;
    }
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      return VfsUtilCore.virtualToIoFile(roots[0]);
    }
    else {
      return null;
    }
  }

  private static Map<String, Object> selectSourceProvider(@NotNull SourceProvider sourceProvider,
                                                          @NotNull AndroidModel androidModel,
                                                          @NotNull Module module,
                                                          @NotNull String packageName) {
    Map<String, Object> paths = Maps.newHashMap();
    // Look up the resource directories inside this source set
    File moduleDirPath = androidModel.getRootDirPath();
    File javaDir = findSrcDirectory(sourceProvider);
    File testDir = findTestDirectory(module);
    String javaPath = getJavaPath(moduleDirPath, javaDir);
    paths.put(ATTR_SRC_DIR, javaPath);

    File resDir = findResDirectory(sourceProvider);
    if (resDir != null) {
      String resPath = FileUtil.getRelativePath(moduleDirPath, resDir);
      if (resPath != null) {
        resPath = FileUtil.toSystemIndependentName(resPath);
      }
      paths.put(ATTR_RES_DIR, resPath);
      paths.put(ATTR_RES_OUT, FileUtil.toSystemIndependentName(resDir.getPath()));
    }
    File manifestDir = findManifestDirectory(sourceProvider);
    if (manifestDir != null) {
      String manifestPath = FileUtil.getRelativePath(moduleDirPath, manifestDir);
      paths.put(ATTR_MANIFEST_DIR, manifestPath);
      paths.put(ATTR_MANIFEST_OUT, FileUtil.toSystemIndependentName(manifestDir.getPath()));
    }
    File aidlDir = findAidlDir(sourceProvider);
    if (aidlDir != null) {
      String aidlPath = FileUtil.getRelativePath(moduleDirPath, aidlDir);
      paths.put(ATTR_AIDL_DIR, aidlPath);
      paths.put(ATTR_AIDL_OUT, FileUtil.toSystemIndependentName(aidlDir.getPath()));
    }
    if (testDir == null) {
      @SuppressWarnings("deprecation") VirtualFile rootDir = androidModel.getRootDir();

      String absolutePath = Joiner.on('/').join(rootDir.getPath(), TemplateWizard.TEST_SOURCE_PATH, TemplateWizard.JAVA_SOURCE_PATH);
      testDir = new File(FileUtil.toSystemDependentName(absolutePath));
    }
    assert javaPath != null;
    // Calculate package name
    paths.put(TemplateMetadata.ATTR_PACKAGE_NAME, packageName);
    String relativePackageDir = packageName.replace('.', File.separatorChar);
    File srcOut = new File(javaDir, relativePackageDir);
    File testOut = new File(testDir, relativePackageDir);
    paths.put(ATTR_TEST_DIR, FileUtil.toSystemIndependentName(testDir.getAbsolutePath()));
    paths.put(ATTR_TEST_OUT, FileUtil.toSystemIndependentName(testOut.getAbsolutePath()));
    paths.put(ATTR_APPLICATION_PACKAGE, ManifestInfo.get(module, false).getPackage());
    paths.put(ATTR_SRC_OUT, FileUtil.toSystemIndependentName(srcOut.getAbsolutePath()));
    return paths;
  }

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

  public static List<String> getParameterValueHistory(@NotNull Parameter parameter, Project project) {
    List<String> entries = RecentsManager.getInstance(project).getRecentEntries(getRecentHistoryKey(parameter.id));
    return entries == null ? ImmutableList.<String>of() : entries;
  }

  public static String getRecentHistoryKey(@Nullable String parameter) {
    return "android.template." + parameter;
  }

  public static void saveRecentValues(@NotNull Project project, @NotNull Map<String, Object> state) {
    for (String id : Iterables.concat(PACKAGE_NAME_PARAMETERS, CLASS_NAME_PARAMETERS)) {
      String value = (String)state.get(id);
      if (!StringUtil.isEmpty(value)) {
        RecentsManager.getInstance(project).registerRecentEntry(getRecentHistoryKey(id), value);
      }
    }
  }

  @Override
  protected void init() {
    Module module = getModule();
    assert module != null;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    AndroidPlatform platform = AndroidPlatform.getInstance(module);

    if (platform != null) {
      myState.put(KEY_BUILD_SDK, platform.getTarget().getVersion().getFeatureLevel());
    }

    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);
    AndroidVersion minSdkVersion = moduleInfo.getMinSdkVersion();

    myState.put(KEY_MIN_SDK, minSdkVersion);
    myState.put(KEY_TARGET_API, moduleInfo.getTargetSdkVersion());
    myState.put(KEY_PACKAGE_NAME, getInitialPackageName(module, facet));
    myState.put(KEY_OPEN_EDITORS, true);

    if (myTemplate == null) {
      FormFactorUtils.FormFactor formFactor = getFormFactor(myTargetFolder);
      myState.put(FormFactorUtils.getMinApiLevelKey(formFactor), minSdkVersion.getApiLevel());
      myState.put(FormFactorUtils.getBuildApiLevelKey(formFactor), moduleInfo.getTargetSdkVersion().getApiLevel());

      ActivityGalleryStep galleryStep = new ActivityGalleryStep(formFactor, false, KEY_SELECTED_TEMPLATE, module, myParentDisposable);
      addStep(galleryStep);
    }
    else {
      TemplateMetadata templateMetadata = TemplateManager.getInstance().getTemplate(myTemplate);
      assert templateMetadata != null;
      myState.put(KEY_SELECTED_TEMPLATE, new TemplateEntry(myTemplate, templateMetadata));
    }
    SourceProvider[] sourceProviders = getSourceProviders(module, myTargetFolder);
    myParameterStep =
      new TemplateParameterStep2(getFormFactor(myTargetFolder), myPredefinedParameterValues, myParentDisposable, KEY_PACKAGE_NAME,
                                 sourceProviders);
    myAssetStudioStep = new IconStep(KEY_SELECTED_TEMPLATE, KEY_SOURCE_PROVIDER, myParentDisposable);

    addStep(myParameterStep);
    addStep(myAssetStudioStep);
  }

  @NotNull
  public static SourceProvider[] getSourceProviders(@Nullable Module module, @Nullable VirtualFile targetDirectory) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        List<SourceProvider> providers;
        if (targetDirectory != null) {
          providers = IdeaSourceProvider.getSourceProvidersForFile(facet, targetDirectory, facet.getMainSourceProvider());
        }
        else {
          providers = IdeaSourceProvider.getAllSourceProviders(facet);
        }
        return ArrayUtil.toObjectArray(providers, SourceProvider.class);
      }
    }
    return new SourceProvider[0];
  }


  /**
   * Initial package name is either a package user selected when invoking the wizard or default package for the module.
   */
  private String getInitialPackageName(Module module, AndroidFacet facet) {
    if (myTargetFolder != null) {
      List<SourceProvider> sourceProviders =
        IdeaSourceProvider.getSourceProvidersForFile(facet, myTargetFolder, facet.getMainSourceProvider());
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
    return getApplicationPackageName();
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Add Android activity";
  }

  @Override
  public boolean performFinishingActions() {
    TemplateEntry templateEntry = myState.get(KEY_SELECTED_TEMPLATE);
    final Project project = getProject();
    Module module = getModule();
    assert templateEntry != null;
    assert project != null && module != null;
    final Template template = templateEntry.getTemplate();
    File moduleRoot = getModuleRoot(module);
    if (moduleRoot == null) {
      return false;
    }
    Map<String, Object> parameterMap = getTemplateParameterMap(templateEntry.getMetadata());
    saveRecentValues(project, parameterMap);
    template.render(VfsUtilCore.virtualToIoFile(project.getBaseDir()), moduleRoot, parameterMap, project);
    myAssetStudioStep.createAssets();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        TemplateUtils.reformatAndRearrange(project, template.getTargetFiles());

        if (Boolean.TRUE.equals(myState.get(KEY_OPEN_EDITORS))) {
          TemplateUtils.openEditors(project, template.getFilesToOpen(), true);
        }
      }
    });

    return true;
  }

  @NotNull
  private Map<String, Object> getTemplateParameterMap(@NotNull TemplateMetadata template) {
    Map<String, Object> parameterValueMap = Maps.newHashMap();
    parameterValueMap.put(TemplateMetadata.ATTR_IS_NEW_PROJECT, myIsNewModule);
    parameterValueMap.putAll(getDirectories());
    for (Key<?> parameter : IMPLICIT_PARAMETERS) {
      parameterValueMap.put(parameter.name, myState.get(parameter));
    }
    for (Parameter parameter : template.getParameters()) {
      parameterValueMap.put(parameter.id, myState.get(myParameterStep.getParameterKey(parameter)));
    }
    try {
      parameterValueMap.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(KeystoreUtils.getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception e) {
      LOG.info("Could not compute SHA1 hash of debug keystore.", e);
    }
    File moduleRoot = getModuleRoot(getModule());
    if (moduleRoot != null) {
      parameterValueMap.put(TemplateMetadata.ATTR_PROJECT_OUT, FileUtil.toSystemIndependentName(moduleRoot.getAbsolutePath()));
    }
    if (Objects.equal(getApplicationPackageName(), parameterValueMap.get(TemplateMetadata.ATTR_PACKAGE_NAME))) {
      parameterValueMap.remove(ATTR_APPLICATION_PACKAGE);
    }
    return parameterValueMap;
  }

  private String getApplicationPackageName() {
    //noinspection ConstantConditions
    AndroidModel androidModel = AndroidFacet.getInstance(getModule()).getAndroidModel();
    assert androidModel != null;
    return androidModel.getApplicationId();
  }

  private Map<String, Object> getDirectories() {
    Map<String, Object> templateParameters = Maps.newHashMap();

    Module module = getModule();
    assert module != null;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    AndroidPlatform platform = AndroidPlatform.getInstance(module);

    if (platform != null) {
      templateParameters.put(ATTR_BUILD_API, platform.getTarget().getVersion().getFeatureLevel());
      templateParameters.put(ATTR_BUILD_API_STRING, getBuildApiString(platform.getTarget().getVersion()));
    }
    // Read minSdkVersion and package from manifest and/or build.gradle files
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);
    AndroidModel androidModel = facet.getAndroidModel();

    SourceProvider sourceProvider1 = myState.get(KEY_SOURCE_PROVIDER);
    if (sourceProvider1 != null && androidModel != null) {
      String packageName = myState.get(KEY_PACKAGE_NAME);
      assert packageName != null;
      templateParameters.putAll(selectSourceProvider(sourceProvider1, androidModel, module, packageName));
    }
    AndroidVersion minSdkVersion = moduleInfo.getMinSdkVersion();
    String minSdkName = minSdkVersion.getApiString();

    templateParameters.put(ATTR_MIN_API, minSdkName);
    templateParameters.put(ATTR_TARGET_API, moduleInfo.getTargetSdkVersion().getApiLevel());
    templateParameters.put(ATTR_MIN_API_LEVEL, minSdkVersion.getFeatureLevel());

    templateParameters.put(ATTR_IS_LIBRARY_MODULE, facet.isLibraryProject());

    try {
      templateParameters.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(getDebugKeystore(facet)));
    }
    catch (Exception e) {
      LOG.info("Could not compute SHA1 hash of debug keystore.", e);
      templateParameters.put(ATTR_DEBUG_KEYSTORE_SHA1, "");
    }

    @SuppressWarnings("deprecation") String projectLocation = NewModuleWizardState.ATTR_PROJECT_LOCATION;

    Project project = getProject();
    assert project != null;

    templateParameters.put(projectLocation, project.getBasePath());
    // We're really interested in the directory name on disk, not the module name. These will be different if you give a module the same
    // name as its containing project.
    String moduleName = new File(module.getModuleFilePath()).getParentFile().getName();
    templateParameters.put(FormFactorUtils.ATTR_MODULE_NAME, moduleName);

    return templateParameters;
  }

  public String getActionDescription() {
    TemplateEntry template = myState.get(KEY_SELECTED_TEMPLATE);
    return String.format("Add %1$s", template == null ? "Template" : template.getTitle());
  }
}
