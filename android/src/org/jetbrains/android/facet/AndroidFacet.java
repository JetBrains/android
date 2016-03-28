/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.facet;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.SourceProvider;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.res.*;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.templates.TemplateManager;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Processor;
import com.intellij.util.ThreeState;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.android.resourceManagers.SystemResourceManager;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.tools.idea.AndroidPsiUtils.getModuleSafely;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.JarFileSystem.JAR_SEPARATOR;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ArrayUtilRt.find;
import static org.jetbrains.android.compiler.AndroidCompileUtil.generate;
import static org.jetbrains.android.facet.AndroidRootUtil.*;
import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdk;
import static org.jetbrains.android.util.AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;
import static org.jetbrains.android.util.AndroidUtils.loadDomElement;

/**
 * @author yole
 */
public class AndroidFacet extends Facet<AndroidFacetConfiguration> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.facet.AndroidFacet");

  public static final FacetTypeId<AndroidFacet> ID = new FacetTypeId<AndroidFacet>("android");
  public static final String NAME = "Android";

  private static final Object APP_RESOURCES_LOCK = new Object();
  private static final Object PROJECT_RESOURCES_LOCK = new Object();
  private static final Object MODULE_RESOURCES_LOCK = new Object();
  private static boolean ourDynamicTemplateMenuCreated;

  private AvdManager myAvdManager = null;
  private AndroidSdkData mySdkData;
  private AndroidSdkHandler myHandler;
  private boolean myDataBindingEnabled = false;

  private SystemResourceManager myPublicSystemResourceManager;
  private SystemResourceManager myFullSystemResourceManager;
  private LocalResourceManager myLocalResourceManager;

  private final Map<String, Map<String, SmartPsiElementPointer<PsiClass>>> myInitialClassMaps = Maps.newHashMap();

  private Map<String, CachedValue<Map<String, PsiClass>>> myClassMaps = Maps.newHashMap();

  private final Object myClassMapLock = new Object();

  private final Set<AndroidAutogeneratorMode> myDirtyModes = EnumSet.noneOf(AndroidAutogeneratorMode.class);
  private final Map<AndroidAutogeneratorMode, Set<String>> myAutogeneratedFiles = Maps.newHashMap();

  private volatile boolean myAutogenerationEnabled = false;

  private ConfigurationManager myConfigurationManager;
  private LocalResourceRepository myModuleResources;
  private AppResourceRepository myAppResources;
  private ProjectResourceRepository myProjectResources;
  private AndroidModel myAndroidModel;
  private final ResourceFolderManager myFolderManager = new ResourceFolderManager(this);

  private SourceProvider myMainSourceSet;
  private IdeaSourceProvider myMainIdeaSourceSet;
  private final AndroidModuleInfo myAndroidModuleInfo = AndroidModuleInfo.create(this);
  private RenderService myRenderService;
  private DataBindingUtil.LightBrClass myLightBrClass;

  public AndroidFacet(@NotNull Module module, String name, @NotNull AndroidFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
    configuration.setFacet(this);
  }

  public boolean isAutogenerationEnabled() {
    return myAutogenerationEnabled;
  }

  /**
   * Indicates whether the project requires a {@link AndroidProject} (obtained from a build system. To check if a project is a "Gradle
   * project," please use the method {@link Projects#isBuildWithGradle(Project)}.
   * @return {@code true} if the project has a {@code AndroidProject}; {@code false} otherwise.
   */
  public boolean requiresAndroidModel() {
    return !getProperties().ALLOW_USER_CONFIGURATION;
  }

  /**
   * @return the Android model associated to this facet.
   */
  @Nullable
  public AndroidModel getAndroidModel() {
    return myAndroidModel;
  }

  /**
   * Associates the given Android model to this facet.
   *
   * @param androidModel the new Android model.
   */
  public void setAndroidModel(@Nullable AndroidModel androidModel) {
    myAndroidModel = androidModel;
    DataBindingUtil.onIdeaProjectSet(this);
  }

  public boolean isLibraryProject() {
    return getProperties().LIBRARY_PROJECT;
  }

  public void setLibraryProject(boolean library) {
    getProperties().LIBRARY_PROJECT = library;
  }

  /**
   * Returns the main source provider for the project. For projects that are not backed by an {@link AndroidProject}, this method returns a
   * {@link SourceProvider} wrapper which provides information about the old project.
   */
  @NotNull
  public SourceProvider getMainSourceProvider() {
    if (myAndroidModel != null) {
      //noinspection deprecation
      return myAndroidModel.getDefaultSourceProvider();
    } else {
      if (myMainSourceSet == null) {
        myMainSourceSet = new LegacySourceProvider();
      }
      return myMainSourceSet;
    }
  }

  @NotNull
  public IdeaSourceProvider getMainIdeaSourceProvider() {
    if (!requiresAndroidModel()) {
      if (myMainIdeaSourceSet == null) {
        myMainIdeaSourceSet = IdeaSourceProvider.create(this);
      }
    } else {
      SourceProvider mainSourceSet = getMainSourceProvider();
      if (myMainIdeaSourceSet == null || mainSourceSet != myMainSourceSet) {
        myMainIdeaSourceSet = IdeaSourceProvider.create(mainSourceSet);
      }
    }

    return myMainIdeaSourceSet;
  }

  public ResourceFolderManager getResourceFolderManager() {
    return myFolderManager;
  }

  /**
   * @return all resource directories, in the overlay order.
   */
  @NotNull
  public List<VirtualFile> getAllResourceDirectories() {
    return myFolderManager.getFolders();
  }


  /**
   * This returns the primary resource directory; the default location to place newly created resources etc.  This method is marked
   * deprecated since we should be gradually adding in UI to allow users to choose specific resource folders among the available flavors
   * (see {@link com.android.tools.idea.gradle.AndroidGradleModel#getFlavorSourceProviders()} etc).
   *
   * @return the primary resource dir, if any.
   */
  @Deprecated
  @Nullable
  public VirtualFile getPrimaryResourceDir() {
    List<VirtualFile> dirs = getAllResourceDirectories();
    if (!dirs.isEmpty()) {
      return dirs.get(0);
    }

    return null;
  }

  public boolean isGeneratedFileRemoved(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myAutogeneratedFiles) {
      Set<String> filePaths = myAutogeneratedFiles.get(mode);

      if (filePaths != null) {
        for (String path : filePaths) {
          VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);

          if (file == null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public void clearAutogeneratedFiles(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myAutogeneratedFiles) {
      Set<String> set = myAutogeneratedFiles.get(mode);
      if (set != null) {
        set.clear();
      }
    }
  }

  public void markFileAutogenerated(@NotNull AndroidAutogeneratorMode mode, @NotNull VirtualFile file) {
    synchronized (myAutogeneratedFiles) {
      Set<String> set = myAutogeneratedFiles.get(mode);

      if (set == null) {
        set = Sets.newHashSet();
        myAutogeneratedFiles.put(mode, set);
      }
      set.add(file.getPath());
    }
  }

  @NotNull
  public Set<String> getAutogeneratedFiles(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myAutogeneratedFiles) {
      Set<String> set = myAutogeneratedFiles.get(mode);
      return set != null ? Sets.newHashSet(set) : Collections.<String>emptySet();
    }
  }

  private void activateSourceAutogenerating() {
    myAutogenerationEnabled = true;
  }

  private void clearClassMaps() {
    synchronized (myClassMapLock) {
      myInitialClassMaps.clear();
    }
  }

  public void androidPlatformChanged() {
    myAvdManager = null;
    myLocalResourceManager = null;
    myPublicSystemResourceManager = null;
    clearClassMaps();
  }

  @NotNull
  public AvdInfo[] getAllAvds() {
    AvdManager manager = getAvdManagerSilently();
    if (manager != null) {
      if (reloadAvds(manager)) {
        return manager.getAllAvds();
      }
    }
    return new AvdInfo[0];
  }

  private boolean reloadAvds(AvdManager manager) {
    try {
      MessageBuildingSdkLog log = new MessageBuildingSdkLog();
      manager.reloadAvds(log);
      if (!log.getErrorMessage().isEmpty()) {
        String message = AndroidBundle.message("cant.load.avds.error.prefix") + ' ' + log.getErrorMessage();
        Messages.showErrorDialog(getModule().getProject(), message, CommonBundle.getErrorTitle());
      }
      return true;
    }
    catch (AndroidLocation.AndroidLocationException e) {
      Messages.showErrorDialog(getModule().getProject(), AndroidBundle.message("cant.load.avds.error"), CommonBundle.getErrorTitle());
    }
    return false;
  }

  @NotNull
  public AvdInfo[] getValidCompatibleAvds() {
    AvdManager manager = getAvdManagerSilently();
    List<AvdInfo> result = Lists.newArrayList();
    if (manager != null && reloadAvds(manager)) {
      addCompatibleAvds(result, manager.getValidAvds());
    }
    return result.toArray(new AvdInfo[result.size()]);
  }

  @NotNull
  private AvdInfo[] addCompatibleAvds(@NotNull List<AvdInfo> to, @NotNull AvdInfo[] from) {
    AndroidVersion minSdk = AndroidModuleInfo.get(this).getRuntimeMinSdkVersion();
    AndroidPlatform platform = getConfiguration().getAndroidPlatform();
    if (platform == null) {
      LOG.error("Android Platform not set for module: " + getModule().getName());
      return new AvdInfo[0];
    }

    for (AvdInfo avd : from) {
      ISystemImage systemImage = avd.getSystemImage();
      if (systemImage == null ||
          LaunchCompatibility.canRunOnAvd(minSdk, platform.getTarget(), systemImage).isCompatible() != ThreeState.NO) {
        to.add(avd);
      }
    }
    return to.toArray(new AvdInfo[to.size()]);
  }

  @Nullable
  public AvdManager getAvdManagerSilently() {
    try {
      return getAvdManager(new AvdManagerLog());
    }
    catch (AndroidLocation.AndroidLocationException ignored) {
    }
    return null;
  }

  public AvdManager getAvdManager(ILogger log) throws AndroidLocation.AndroidLocationException {
    if (myAvdManager == null) {
      // ensure the handler is created
      getSdkData();
      myAvdManager = AvdManager.getInstance(myHandler, log);
    }
    return myAvdManager;
  }

  @Nullable
  public AndroidSdkData getSdkData() {
    if (mySdkData == null) {
      AndroidPlatform platform = getConfiguration().getAndroidPlatform();
      if (platform != null) {
        mySdkData = platform.getSdkData();
        myHandler = mySdkData.getSdkHandler();
      }
      else {
        mySdkData = null;
        myHandler = AndroidSdkHandler.getInstance(null);
      }
    }

    return mySdkData;
  }

  public static void createDynamicTemplateMenu() {
    if (ourDynamicTemplateMenuCreated) {
      return;
    }
    ourDynamicTemplateMenuCreated = true;
    DefaultActionGroup newGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("NewGroup");
    newGroup.addSeparator();
    ActionGroup menu = TemplateManager.getInstance().getTemplateCreationMenu(null);

    if (menu != null) {
      newGroup.add(menu, new Constraints(Anchor.AFTER, "NewFromTemplate"));
    }
  }

  @Override
  public void initFacet() {
    StartupManager.getInstance(getModule().getProject()).runWhenProjectIsInitialized(new Runnable() {
        @Override
        public void run() {
          AndroidResourceFilesListener.notifyFacetInitialized(AndroidFacet.this);
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            return;
          }

          addResourceFolderToSdkRootsIfNecessary();

          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              Module module = getModule();
              Project project = module.getProject();
              if (project.isDisposed()) {
                return;
              }

              generate(module, AndroidAutogeneratorMode.AAPT);
              generate(module, AndroidAutogeneratorMode.AIDL);
              generate(module, AndroidAutogeneratorMode.RENDERSCRIPT);
              generate(module, AndroidAutogeneratorMode.BUILDCONFIG);

              activateSourceAutogenerating();
            }
          });
        }
      });

    getModule().getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      private Sdk myPrevSdk;

      @Override
      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (isDisposed()) {
              return;
            }
            ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());

            Sdk newSdk = rootManager.getSdk();
            if (newSdk != null && newSdk.getSdkType() instanceof AndroidSdkType && !newSdk.equals(myPrevSdk)) {
              androidPlatformChanged();

              synchronized (myDirtyModes) {
                myDirtyModes.addAll(Arrays.asList(AndroidAutogeneratorMode.values()));
              }
            } else {
              // When roots change, we need to rebuild the class inheritance map to make sure new dependencies
              // from libraries are added
              clearClassMaps();
            }
            myPrevSdk = newSdk;

            getLocalResourceManager().invalidateAttributeDefinitions();
          }
        });
      }
    });
    createDynamicTemplateMenu();
  }

  private void addResourceFolderToSdkRootsIfNecessary() {
    Module module = getModule();
    if (module.isDisposed()) {
      return;
    }
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || !isAndroidSdk(sdk)) {
      return;
    }

    AndroidPlatform platform = AndroidPlatform.getInstance(sdk);
    if (platform == null) {
      return;
    }

    String resFolderPath = platform.getTarget().getPath(IAndroidTarget.RESOURCES);
    if (resFolderPath == null) {
      return;
    }
    List<VirtualFile> filesToAdd = Lists.newArrayList();

    VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(toSystemIndependentName(resFolderPath));
    if (resFolder != null) {
      filesToAdd.add(resFolder);
    }

    if (platform.needToAddAnnotationsJarToClasspath()) {
      String sdkHomePath = toSystemIndependentName(platform.getSdkData().getLocation().getPath());
      VirtualFile annotationsJar = JarFileSystem.getInstance().findFileByPath(sdkHomePath + ANNOTATIONS_JAR_RELATIVE_PATH + JAR_SEPARATOR);
      if (annotationsJar != null) {
        filesToAdd.add(annotationsJar);
      }
    }

    addFilesToSdkIfNecessary(sdk, filesToAdd);
  }

  private static void addFilesToSdkIfNecessary(@NotNull Sdk sdk, @NotNull Collection<VirtualFile> files) {
    List<VirtualFile> newFiles = Lists.newArrayList(files);
    newFiles.removeAll(Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES)));

    if (newFiles.size() > 0) {
      SdkModificator modificator = sdk.getSdkModificator();

      for (VirtualFile file : newFiles) {
        modificator.addRoot(file, OrderRootType.CLASSES);
      }
      modificator.commitChanges();
    }
  }

  @Override
  public void disposeFacet() {
    if (myConfigurationManager != null) {
      Disposer.dispose(myConfigurationManager);
    }
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetByType(ID);
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull ConvertContext context) {
    Module module = context.getModule();
    return module != null ? getInstance(module) : null;
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull PsiElement element) {
    Module module = getModuleSafely(element);
    return module != null ? getInstance(module) : null;
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull DomElement element) {
    Module module = element.getModule();
    return module != null ? getInstance(module) : null;
  }

  @Nullable
  public ResourceManager getResourceManager(@Nullable String resourcePackage) {
    return getResourceManager(resourcePackage, null);
  }

  @Nullable
  public ResourceManager getResourceManager(@Nullable String resourcePackage, @Nullable PsiElement contextElement) {
    if (SYSTEM_RESOURCE_PACKAGE.equals(resourcePackage)) {
      return getSystemResourceManager();
    }
    if (contextElement != null && isInAndroidSdk(contextElement)) {
      return getSystemResourceManager();
    }
    return getLocalResourceManager();
  }

  private static boolean isInAndroidSdk(@NonNull PsiElement element) {
    PsiFile file = element.getContainingFile();

    if (file == null) {
      return false;
    }
    VirtualFile virtualFile = file.getVirtualFile();

    if (virtualFile == null) {
      return false;
    }
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    List<OrderEntry> entries = projectFileIndex.getOrderEntriesForFile(virtualFile);

    for (OrderEntry entry : entries) {
      if (entry instanceof JdkOrderEntry) {
        Sdk sdk = ((JdkOrderEntry)entry).getJdk();

        if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public LocalResourceManager getLocalResourceManager() {
    if (myLocalResourceManager == null) {
      myLocalResourceManager = new LocalResourceManager(this);
    }
    return myLocalResourceManager;
  }

  @Nullable
  public SystemResourceManager getSystemResourceManager() {
    return getSystemResourceManager(true);
  }

  @Nullable
  public SystemResourceManager getSystemResourceManager(boolean publicOnly) {
    if (publicOnly) {
      if (myPublicSystemResourceManager == null) {
        AndroidPlatform platform = getConfiguration().getAndroidPlatform();
        if (platform != null) {
          myPublicSystemResourceManager = new SystemResourceManager(this.getModule().getProject(), platform, true);
        }
      }
      return myPublicSystemResourceManager;
    }
    if (myFullSystemResourceManager == null) {
      AndroidPlatform platform = getConfiguration().getAndroidPlatform();
      if (platform != null) {
        myFullSystemResourceManager = new SystemResourceManager(this.getModule().getProject(), platform, false);
      }
    }
    return myFullSystemResourceManager;
  }

  @Nullable
  public Manifest getManifest() {
    VirtualFile manifestFile = getMainIdeaSourceProvider().getManifestFile();
    return manifestFile != null ? loadDomElement(getModule(), manifestFile, Manifest.class) : null;
  }

  @NotNull
  public static AndroidFacetType getFacetType() {
    return (AndroidFacetType)FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  // TODO: correctly support classes from external non-platform jars
  @NotNull
  public Map<String, PsiClass> getClassMap(@NotNull final String className) {
    synchronized (myClassMapLock) {
      CachedValue<Map<String, PsiClass>> value = myClassMaps.get(className);

      if (value == null) {
        value = CachedValuesManager.getManager(getModule().getProject()).createCachedValue(
          new CachedValueProvider<Map<String, PsiClass>>() {
          @Nullable
          @Override
          public Result<Map<String, PsiClass>> compute() {
            Map<String, PsiClass> map = computeClassMap(className);
            return Result.create(map, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
          }
        }, false);
        myClassMaps.put(className, value);
      }
      return value.getValue();
    }
  }

  @NotNull
  private Map<String, PsiClass> computeClassMap(@NotNull String className) {
    Map<String, SmartPsiElementPointer<PsiClass>> classMap = getInitialClassMap(className, false);
    Map<String, PsiClass> result = Maps.newHashMap();
    boolean shouldRebuildInitialMap = false;

    for (String key : classMap.keySet()) {
      SmartPsiElementPointer<PsiClass> pointer = classMap.get(key);

      if (!isUpToDate(pointer, key)) {
        shouldRebuildInitialMap = true;
        break;
      }
      PsiClass aClass = pointer.getElement();

      if (aClass != null) {
        result.put(key, aClass);
      }
    }

    if (shouldRebuildInitialMap) {
      result.clear();
      classMap = getInitialClassMap(className, true);

      for (String key : classMap.keySet()) {
        SmartPsiElementPointer<PsiClass> pointer = classMap.get(key);
        PsiClass aClass = pointer.getElement();

        if (aClass != null) {
          result.put(key, aClass);
        }
      }
    }
    Project project = getModule().getProject();
    fillMap(className, ProjectScope.getProjectScope(project), result, false);
    return result;
  }

  private static boolean isUpToDate(SmartPsiElementPointer<PsiClass> pointer, String tagName) {
    PsiClass aClass = pointer.getElement();
    if (aClass == null) {
      return false;
    }
    String[] tagNames = LayoutViewClassUtils.getTagNamesByClass(aClass, -1);
    return find(tagNames, tagName) >= 0;
  }

  @NotNull
  private Map<String, SmartPsiElementPointer<PsiClass>> getInitialClassMap(@NotNull String className, boolean forceRebuild) {
    Map<String, SmartPsiElementPointer<PsiClass>> viewClassMap = myInitialClassMaps.get(className);
    if (viewClassMap != null && !forceRebuild) return viewClassMap;
    Map<String, PsiClass> map = Maps.newHashMap();

    if (fillMap(className, getModule().getModuleWithDependenciesAndLibrariesScope(true), map, true)) {
      viewClassMap = Maps.newHashMapWithExpectedSize(map.size());
      SmartPointerManager manager = SmartPointerManager.getInstance(getModule().getProject());

      for (Map.Entry<String, PsiClass> entry : map.entrySet()) {
        viewClassMap.put(entry.getKey(), manager.createSmartPsiElementPointer(entry.getValue()));
      }
      myInitialClassMaps.put(className, viewClassMap);
    }
    return viewClassMap != null
           ? viewClassMap
           : Collections.<String, SmartPsiElementPointer<PsiClass>>emptyMap();
  }

  private boolean fillMap(@NotNull final String className, @NotNull GlobalSearchScope scope, final Map<String, PsiClass> map, final boolean libClassesOnly) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getModule().getProject());
    PsiClass baseClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      @Nullable
      public PsiClass compute() {
        PsiClass aClass;
        // facade.findClass uses index to find class by name, which might throw an IndexNotReadyException in dumb mode
        try {
          aClass = facade.findClass(className, getModule().getModuleWithDependenciesAndLibrariesScope(true));
        }
        catch (IndexNotReadyException e) {
          aClass = null;
        }
        return aClass;
      }
    });
    if (baseClass != null) {
      String[] baseClassTagNames = LayoutViewClassUtils.getTagNamesByClass(baseClass, getModuleMinApi());
      for (String tagName : baseClassTagNames) {
        map.put(tagName, baseClass);
      }
      try {
        ClassInheritorsSearch.search(baseClass, scope, true).forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass c) {
            if (libClassesOnly && c.getManager().isInProject(c)) {
              return true;
            }
            String[] tagNames = LayoutViewClassUtils.getTagNamesByClass(c, getModuleMinApi());
            for (String tagName : tagNames) {
              map.put(tagName, c);
            }
            return true;
          }
        });
      }
      catch (IndexNotReadyException e) {
        LOG.info(e);
        return false;
      }
    }
    return map.size() > 0;
  }

  /**
   * Returns minimum SDK version for current Android module
   */
  public int getModuleMinApi() {
    return getAndroidModuleInfo().getMinSdkVersion().getApiLevel();
  }

  public void scheduleSourceRegenerating(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myDirtyModes) {
      myDirtyModes.add(mode);
    }
  }

  public boolean cleanRegeneratingState(@NotNull AndroidAutogeneratorMode mode) {
    synchronized (myDirtyModes) {
      return myDirtyModes.remove(mode);
    }
  }

  @NotNull
  public ConfigurationManager getConfigurationManager() {
    return getConfigurationManager(true);
  }


  @Contract("true -> !null")
  @Nullable
  public ConfigurationManager getConfigurationManager(boolean createIfNecessary) {
    if (myConfigurationManager == null && createIfNecessary) {
      myConfigurationManager = ConfigurationManager.create(getModule());
      Disposer.register(this, myConfigurationManager);
    }

    return myConfigurationManager;
  }

  @Contract("true -> !null")
  @Nullable
  public AppResourceRepository getAppResources(boolean createIfNecessary) {
    synchronized (APP_RESOURCES_LOCK) {
      if (myAppResources == null && createIfNecessary) {
        myAppResources = AppResourceRepository.create(this);
      }
      return myAppResources;
    }
  }

  @Contract("true -> !null")
  @Nullable
  public ProjectResourceRepository getProjectResources(boolean createIfNecessary) {
    synchronized (PROJECT_RESOURCES_LOCK) {
      if (myProjectResources == null && createIfNecessary) {
        myProjectResources = ProjectResourceRepository.create(this);
      }
      return myProjectResources;
    }
  }

  @Contract("true -> !null")
  @Nullable
  public LocalResourceRepository getModuleResources(boolean createIfNecessary) {
    synchronized (MODULE_RESOURCES_LOCK) {
      if (myModuleResources == null && createIfNecessary) {
        myModuleResources = ModuleResourceRepository.create(this);
      }
      return myModuleResources;
    }
  }

  public void refreshResources() {
    myModuleResources = null;
    myProjectResources = null;
    myAppResources = null;
    myConfigurationManager.getResolverCache().reset();
    ResourceFolderRegistry.reset();
    FileResourceRepository.reset();
  }

  @NotNull
  public JpsAndroidModuleProperties getProperties() {
    JpsAndroidModuleProperties state = getConfiguration().getState();
    assert state != null;
    return state;
  }

  /**
   * Returns true if this facet includes data binding library
   *
   * @return True if data binding is enabled for this Facet
   */
  public boolean isDataBindingEnabled() {
    return myDataBindingEnabled;
  }

  /**
   * Called by the {@linkplain DataBindingUtil} to update whether this facet includes data binding or not.
   *
   * @param dataBindingEnabled True if Facet includes data binding, false otherwise.
   */
  public void setDataBindingEnabled(boolean dataBindingEnabled) {
    myDataBindingEnabled = dataBindingEnabled;
  }

  public void addListener(@NotNull GradleSyncListener listener) {
    Module module = getModule();
    GradleSyncState.subscribe(module.getProject(), listener, module);
  }

  @NotNull
  public AndroidModuleInfo getAndroidModuleInfo() {
    return myAndroidModuleInfo;
  }

  @NotNull
  public RenderService getRenderService() {
    if (myRenderService == null) {
      myRenderService = new RenderService(this);
    }
    return myRenderService;
  }

  /**
   * Set by {@linkplain DataBindingUtil} the first time we need it.
   *
   * @param lightBrClass
   * @see DataBindingUtil#getOrCreateBrClassFor(AndroidFacet)
   */
  public void setLightBrClass(DataBindingUtil.LightBrClass lightBrClass) {
    myLightBrClass = lightBrClass;
  }

  /**
   * Returns the light BR class for this facet if it is aready set.
   *
   * @return The BR class for this facet, if exists
   * @see DataBindingUtil#getOrCreateBrClassFor(AndroidFacet)
   */
  public DataBindingUtil.LightBrClass getLightBrClass() {
    return myLightBrClass;
  }

  // Compatibility bridge for old (non-AndroidProject-backed) projects. Also used in AndroidProject-backed projects before the module has
  // been synced.
  private class LegacySourceProvider implements SourceProvider {
    @NonNull
    @Override
    public String getName() {
      return "main";
    }

    @NonNull
    @Override
    public File getManifestFile() {
      Module module = getModule();
      VirtualFile manifestFile = getFileByRelativeModulePath(module, getProperties().MANIFEST_FILE_RELATIVE_PATH, true);
      if (manifestFile == null) {
        VirtualFile root = !requiresAndroidModel() ? getMainContentRoot(AndroidFacet.this) : null;
        if (root != null) {
          return new File(virtualToIoFile(root), ANDROID_MANIFEST_XML);
        } else {
          return new File(ANDROID_MANIFEST_XML);
        }
      } else {
        return virtualToIoFile(manifestFile);
      }
    }

    @NonNull
    @Override
    public Set<File> getJavaDirectories() {
      Set<File> dirs = Sets.newHashSet();

      Module module = getModule();
      VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      if (contentRoots.length != 0) {
        for (VirtualFile root : contentRoots) {
          dirs.add(virtualToIoFile(root));
        }
      }
      return dirs;
    }

    @NonNull
    @Override
    public Set<File> getResourcesDirectories() {
      return Collections.emptySet();
    }

    @NonNull
    @Override
    public Set<File> getAidlDirectories() {
      final VirtualFile dir = getAidlGenDir(AndroidFacet.this);
      return dir == null ? Collections.<File>emptySet() : Collections.singleton(virtualToIoFile(dir));
    }

    @NonNull
    @Override
    public Set<File> getRenderscriptDirectories() {
      VirtualFile dir = getRenderscriptGenDir(AndroidFacet.this);
      return dir == null ? Collections.<File>emptySet() : Collections.singleton(virtualToIoFile(dir));
    }

    @NonNull
    @Override
    public Set<File> getResDirectories() {
      String resRelPath = getProperties().RES_FOLDER_RELATIVE_PATH;
      VirtualFile dir =  getFileByRelativeModulePath(getModule(), resRelPath, true);
      return dir == null ? Collections.<File>emptySet() : Collections.singleton(virtualToIoFile(dir));
    }

    @NonNull
    @Override
    public Set<File> getAssetsDirectories() {
      final VirtualFile dir = getAssetsDir(AndroidFacet.this);
      return dir == null ? Collections.<File>emptySet() : Collections.singleton(virtualToIoFile(dir));
    }

    @NonNull
    @Override
    public Collection<File> getJniLibsDirectories() {
      return Collections.emptyList();
    }

    @Override
    public Collection<File> getShadersDirectories() {
      return Collections.emptyList();
    }

    @NonNull
    @Override
    public Collection<File> getCDirectories() {
      return Collections.emptyList();
    }

    @NonNull
    @Override
    public Collection<File> getCppDirectories() {
      return Collections.emptyList();
    }
  }
}
