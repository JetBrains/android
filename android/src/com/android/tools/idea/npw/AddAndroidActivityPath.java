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
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep;
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RecentsManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.sdk.AndroidPlatform;
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
 *
 * @deprecated Replaced by {@link RenderTemplateModel} and {@link ConfigureTemplateParametersStep}.
 */
public final class AddAndroidActivityPath extends DynamicWizardPath {
  public static final Key<Boolean> KEY_IS_LAUNCHER = createKey("is.launcher.activity", PATH, Boolean.class);
  public static final Key<TemplateEntry> KEY_SELECTED_TEMPLATE = createKey("selected.template", PATH, TemplateEntry.class);
  public static final Key<AndroidVersion> KEY_MIN_SDK = createKey(ATTR_MIN_API, PATH, AndroidVersion.class);
  public static final Key<AndroidVersion> KEY_TARGET_API = createKey(ATTR_TARGET_API, PATH, AndroidVersion.class);
  public static final Key<Integer> KEY_BUILD_SDK = createKey(ATTR_BUILD_API, PATH, Integer.class);
  public static final Key<String> KEY_PACKAGE_NAME = createKey(ATTR_PACKAGE_NAME, PATH, String.class);
  public static final Key<SourceProvider> KEY_SOURCE_PROVIDER = createKey("source.provider", PATH, SourceProvider.class);
  public static final Key<String> KEY_SOURCE_PROVIDER_NAME = createKey(ATTR_SOURCE_PROVIDER_NAME, PATH, String.class);
  public static final Set<String> PACKAGE_NAME_PARAMETERS = ImmutableSet.of(ATTR_PACKAGE_NAME);
  public static final Set<String> CLASS_NAME_PARAMETERS = ImmutableSet.of(ATTR_PARENT_ACTIVITY_CLASS);
  public static final Key<Boolean> KEY_OPEN_EDITORS = createKey("open.editors", WIZARD, Boolean.class);
  public static final Set<Key<?>> IMPLICIT_PARAMETERS = ImmutableSet.of(KEY_PACKAGE_NAME, KEY_SOURCE_PROVIDER_NAME);

  private static final Logger LOG = Logger.getInstance(AddAndroidActivityPath.class);
  public static final String CUSTOMIZE_ACTIVITY_TITLE = "Customize the Activity";

  private TemplateParameterStep2 myParameterStep;
  private final boolean myIsNewModule;
  private IconStep myAssetStudioStep;
  private final VirtualFile myTargetFolder;
  @Nullable private final File myTemplate;
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

  private static FormFactor getFormFactor(@Nullable @SuppressWarnings("UnusedParameters") VirtualFile targetFolder) {
    // TODO There should be some way for this wizard to figure out form factor from a target or from a template
    return FormFactor.MOBILE;
  }

  /**
   * Finds and returns the main src directory for the given project or null if one cannot be found.
   */
  @Nullable
  private static File findSrcDirectory(@NotNull SourceProvider sourceProvider) {
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
  private static File findResDirectory(@NotNull SourceProvider sourceProvider) {
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
  private static File findAidlDir(@NotNull SourceProvider sourceProvider) {
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
  private static File findManifestDirectory(@NotNull SourceProvider sourceProvider) {
    File manifestFile = sourceProvider.getManifestFile();
    File manifestDir = manifestFile.getParentFile();
    if (manifestDir != null) {
      return manifestDir;
    }
    return null;
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

    String sourceRootPackagePrefix = getSourceDirectoryPackagePrefix(module, javaDir);
    String relativePackageName = removeCommonPackagePrefix(sourceRootPackagePrefix, packageName);

    // Calculate package name
    paths.put(ATTR_PACKAGE_NAME, packageName);
    String relativePackageDir = relativePackageName.replace('.', File.separatorChar);
    File srcOut = new File(javaDir, relativePackageDir);
    File testOut = new File(testDir, relativePackageDir);
    paths.put(ATTR_TEST_DIR, FileUtil.toSystemIndependentName(testDir.getAbsolutePath()));
    paths.put(ATTR_TEST_OUT, FileUtil.toSystemIndependentName(testOut.getAbsolutePath()));
    paths.put(ATTR_APPLICATION_PACKAGE, MergedManifest.get(module).getPackage());
    paths.put(ATTR_SRC_OUT, FileUtil.toSystemIndependentName(srcOut.getAbsolutePath()));
    return paths;
  }

  /**
   * Returns the package prefix of the module's content root if it can be found.
   */
  @NotNull
  private static String getSourceDirectoryPackagePrefix(@NotNull Module module, File javaDir) {
    VirtualFile javaVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(javaDir);
    if (javaVirtualFile == null) {
      return "";
    }
    SourceFolder sourceFolder = ProjectRootsUtil.findSourceFolder(module, javaVirtualFile);
    if (sourceFolder == null) {
      return "";
    }
    return sourceFolder.getPackagePrefix();
  }

  /**
   * Makes the package name relative to a package prefix.
   *
   * Examples:
   * getRelativePackageName("com.google", "com.google.android") -> "android"
   * getRelativePackageName("com.google.android", "com.google.android") -> ""
   * getRelativePackageName("com.google.android", "not.google.android") -> "not.google.android"
   */
  @NotNull
  static String removeCommonPackagePrefix(@NotNull String packagePrefix, @NotNull String packageName) {
    String relativePackageName = packageName;
    if (packageName.equals(packagePrefix)) {
      relativePackageName = "";
    }
    else if (packageName.length() > packagePrefix.length()
             && packageName.startsWith(packagePrefix)
             && packageName.charAt(packagePrefix.length()) == '.') {
      relativePackageName = relativePackageName.substring(packagePrefix.length() + 1);
    }
    return relativePackageName;
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
    return entries == null ? ImmutableList.of() : entries;
  }

  public static String getRecentHistoryKey(@Nullable String parameter) {
    return "android.template." + parameter;
  }

  private static void saveRecentValues(@NotNull Project project, @NotNull Map<String, Object> state) {
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
      FormFactor formFactor = getFormFactor(myTargetFolder);
      myState.put(FormFactorUtils.getMinApiLevelKey(formFactor), minSdkVersion.getApiLevel());
      myState.put(FormFactorUtils.getBuildApiLevelKey(formFactor), moduleInfo.getTargetSdkVersion().getApiLevel());

      ActivityGalleryStep galleryStep = new ActivityGalleryStep(formFactor, false, KEY_SELECTED_TEMPLATE, module, myParentDisposable);
      addStep(galleryStep);
    }
    else {
      TemplateMetadata templateMetadata = TemplateManager.getInstance().getTemplateMetadata(myTemplate);
      assert templateMetadata != null;
      myState.put(KEY_SELECTED_TEMPLATE, new TemplateEntry(myTemplate, templateMetadata));
    }
    SourceProvider[] sourceProviders = getSourceProviders(module, myTargetFolder);
    myParameterStep =
      new TemplateParameterStep2(getFormFactor(myTargetFolder), myPredefinedParameterValues, myParentDisposable, KEY_PACKAGE_NAME,
                                 sourceProviders, CUSTOMIZE_ACTIVITY_TITLE);
    myAssetStudioStep = new IconStep(KEY_SELECTED_TEMPLATE, KEY_SOURCE_PROVIDER, myParentDisposable);

    addStep(myParameterStep);
    addStep(myAssetStudioStep);
  }

  @NotNull
  private static SourceProvider[] getSourceProviders(@Nullable Module module, @Nullable VirtualFile targetDirectory) {
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
          String packageName = ProjectRootManager.getInstance(module.getProject())
            .getFileIndex()
            .getPackageNameByDirectory(myTargetFolder);
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
  public boolean canPerformFinishingActions() {
    return performFinishingOperation(true, null, null);
  }

  @Override
  public boolean performFinishingActions() {
    final Project project = getProject();
    assert project != null;

    final List<File> filesToOpen = Lists.newArrayList();
    final List<File> filesToReformat = Lists.newArrayList();

    boolean success = new WriteCommandAction<Boolean>(project, "New Activity") {
      @Override
      protected void run(@NotNull Result<Boolean> result) throws Throwable {
        boolean success = performFinishingOperation(false, filesToOpen, filesToReformat);
        if (success) {
          myAssetStudioStep.createAssets();
        }
        result.setResult(success);
      }
    }.execute().getResultObject();
    if (!success) {
      return false;
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (Boolean.TRUE.equals(myState.get(KEY_OPEN_EDITORS))) {
          TemplateUtils.openEditors(project, filesToOpen, true);
        }
      }
    });
    return true;
  }

  private boolean performFinishingOperation(boolean dryRun, @Nullable List<File> filesToOpen, @Nullable List<File> filesToReformat) {
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
    // @formatter:off
    final RenderingContext context = RenderingContext.Builder.newContext(template, project)
      .withCommandName("New Activity")
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withModule(module)
      .withParams(parameterMap)
      .intoOpenFiles(filesToOpen)
      .intoTargetFiles(filesToReformat)
      .build();
    // @formatter:on
    return template.render(context);
  }

  @NotNull
  private Map<String, Object> getTemplateParameterMap(@NotNull TemplateMetadata template) {
    Map<String, Object> parameterValueMap = Maps.newHashMap();
    parameterValueMap.put(ATTR_IS_NEW_PROJECT, myIsNewModule);
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
      parameterValueMap.put(ATTR_PROJECT_OUT, FileUtil.toSystemIndependentName(moduleRoot.getAbsolutePath()));
    }
    if (Objects.equal(getApplicationPackageName(), parameterValueMap.get(ATTR_PACKAGE_NAME))) {
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
