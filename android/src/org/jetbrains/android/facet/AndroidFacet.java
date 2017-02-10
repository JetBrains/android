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
import com.android.tools.idea.apk.AndroidApkFacet;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.res.*;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.templates.TemplateManager;
import com.android.utils.ILogger;
import com.intellij.CommonBundle;
import com.intellij.ProjectTopics;
import com.intellij.facet.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.ClassMaps;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.AndroidPsiUtils.getModuleSafely;
import static com.android.tools.idea.databinding.DataBindingUtil.refreshDataBindingStatus;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.JarFileSystem.JAR_SEPARATOR;
import static org.jetbrains.android.util.AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH;
import static org.jetbrains.android.util.AndroidUtils.SYSTEM_RESOURCE_PACKAGE;
import static org.jetbrains.android.util.AndroidUtils.loadDomElement;

/**
 * @author yole
 */
public class AndroidFacet extends Facet<AndroidFacetConfiguration> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.facet.AndroidFacet");

  public static final FacetTypeId<AndroidFacet> ID = new FacetTypeId<>("android");
  public static final String NAME = "Android";

  private static final Object PROJECT_RESOURCES_LOCK = new Object();
  private static final Object MODULE_RESOURCES_LOCK = new Object();
  private static boolean ourDynamicTemplateMenuCreated;

  private AvdManager myAvdManager = null;
  private AndroidSdkData mySdkData;
  private AndroidSdkHandler myHandler;

  private SystemResourceManager myPublicSystemResourceManager;
  private SystemResourceManager myFullSystemResourceManager;
  private LocalResourceManager myLocalResourceManager;

  private LocalResourceRepository myModuleResources;
  private ProjectResourceRepository myProjectResources;
  private AndroidModel myAndroidModel;
  private final ResourceFolderManager myFolderManager = new ResourceFolderManager(this);

  private SourceProvider myMainSourceSet;
  private IdeaSourceProvider myMainIdeaSourceSet;

  @Nullable
  public static AndroidFacet getInstance(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    AndroidFacet facet = getInstance(module);
    if (facet == null) {
      // facet may be present, but not visible if ModifiableFacetModel has not been committed yet (e.g. in the case of a new project.)
      ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(module);
      facet = facetModel.getFacetByType(ID);
    }
    return facet;
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull ConvertContext context) {
    return findAndroidFacet(context.getModule());
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull PsiElement element) {
    return findAndroidFacet(getModuleSafely(element));
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull DomElement element) {
    return findAndroidFacet(element.getModule());
  }

  @Nullable
  private static AndroidFacet findAndroidFacet(@Nullable Module module) {
    return module != null ? getInstance(module) : null;
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull Module module) {
    return !module.isDisposed() ? FacetManager.getInstance(module).getFacetByType(ID) : null;
  }

  public AndroidFacet(@NotNull Module module, @NotNull String name, @NotNull AndroidFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
    configuration.setFacet(this);
  }

  /**
   * Indicates whether the project requires a {@link AndroidProject} (obtained from a build system. To check if a project is a "Gradle
   * project," please use the method {@link Projects#isBuildWithGradle(Project)}.
   *
   * @return {@code true} if the project has a {@code AndroidProject}; {@code false} otherwise.
   */
  public boolean requiresAndroidModel() {
    return !getProperties().ALLOW_USER_CONFIGURATION && AndroidApkFacet.getInstance(getModule()) == null;
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
    refreshDataBindingStatus(this);
  }

  public boolean isAppProject() {
    int projectType = getProjectType();
    return projectType == PROJECT_TYPE_APP || projectType == PROJECT_TYPE_INSTANTAPP;
  }

  public boolean canBeDependency() {
    int projectType = getProjectType();
    return projectType == PROJECT_TYPE_LIBRARY || projectType == PROJECT_TYPE_ATOM;
  }

  public boolean isLibraryProject() {
    return getProjectType() == PROJECT_TYPE_LIBRARY;
  }

  public int getProjectType() {
    return getProperties().PROJECT_TYPE;
  }

  public void setProjectType(int type) {
    getProperties().PROJECT_TYPE = type;
  }

  public static boolean hasAndroid(@NotNull Project project) {
    return ReadAction.compute(() -> !project.isDisposed() && ProjectFacetManager.getInstance(project).hasFacets(ID));
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
    }
    else {
      if (myMainSourceSet == null) {
        myMainSourceSet = new LegacySourceProvider(this);
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
    }
    else {
      SourceProvider mainSourceSet = getMainSourceProvider();
      if (myMainIdeaSourceSet == null || mainSourceSet != myMainSourceSet) {
        myMainIdeaSourceSet = IdeaSourceProvider.create(mainSourceSet);
      }
    }

    return myMainIdeaSourceSet;
  }

  @NotNull
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
   * (see {@link AndroidModuleModel#getFlavorSourceProviders()} etc).
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

  public void androidPlatformChanged() {
    myAvdManager = null;
    myLocalResourceManager = null;
    myPublicSystemResourceManager = null;
    ClassMaps.getInstance(this).clear();
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
        Messages.showErrorDialog(getProject(), message, CommonBundle.getErrorTitle());
      }
      return true;
    }
    catch (AndroidLocation.AndroidLocationException e) {
      Messages.showErrorDialog(getProject(), AndroidBundle.message("cant.load.avds.error"), CommonBundle.getErrorTitle());
    }
    return false;
  }

  @NotNull
  public AvdInfo[] getValidCompatibleAvds() {
    AvdManager manager = getAvdManagerSilently();
    List<AvdInfo> result = new ArrayList<>();
    if (manager != null && reloadAvds(manager)) {
      addCompatibleAvds(result, manager.getValidAvds());
    }
    return result.toArray(new AvdInfo[result.size()]);
  }

  @NotNull
  private AvdInfo[] addCompatibleAvds(@NotNull List<AvdInfo> to, @NotNull AvdInfo[] from) {
    AndroidVersion minSdk = AndroidModuleInfo.getInstance(this).getRuntimeMinSdkVersion();
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

  private static void createDynamicTemplateMenu() {
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
    StartupManager.getInstance(getProject()).runWhenProjectIsInitialized(() -> {
      AndroidResourceFilesListener.notifyFacetInitialized(this);
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      addResourceFolderToSdkRootsIfNecessary();
      ModuleSourceAutogenerating.initialize(this);
    });

    getModule().getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      private Sdk myPrevSdk;

      @Override
      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (isDisposed()) {
            return;
          }
          ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());

          Sdk newSdk = rootManager.getSdk();
          if (newSdk != null && newSdk.getSdkType() instanceof AndroidSdkType && !newSdk.equals(myPrevSdk)) {
            androidPlatformChanged();

            ModuleSourceAutogenerating autogenerating = ModuleSourceAutogenerating.getInstance(AndroidFacet.this);
            if (autogenerating != null) {
              autogenerating.resetRegeneratingState();
            }
          }
          else {
            // When roots change, we need to rebuild the class inheritance map to make sure new dependencies
            // from libraries are added
            ClassMaps.getInstance(AndroidFacet.this).clear();
          }
          myPrevSdk = newSdk;

          getLocalResourceManager().invalidateAttributeDefinitions();
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
    if (sdk == null || !AndroidSdks.getInstance().isAndroidSdk(sdk)) {
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
    List<VirtualFile> filesToAdd = new ArrayList<>();

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
    List<VirtualFile> newFiles = new ArrayList<>(files);
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
    myAndroidModel = null;
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

  public static boolean isInAndroidSdk(@NonNull PsiElement element) {
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
          myPublicSystemResourceManager = new SystemResourceManager(getProject(), platform, true);
        }
      }
      return myPublicSystemResourceManager;
    }
    if (myFullSystemResourceManager == null) {
      AndroidPlatform platform = getConfiguration().getAndroidPlatform();
      if (platform != null) {
        myFullSystemResourceManager = new SystemResourceManager(getProject(), platform, false);
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

  @Contract("true -> !null")
  @Nullable
  public ProjectResourceRepository getProjectResources(boolean createIfNecessary) {
    synchronized (PROJECT_RESOURCES_LOCK) {
      if (myProjectResources == null && createIfNecessary) {
        myProjectResources = ProjectResourceRepository.create(this);
        Disposer.register(this, myProjectResources);
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
        Disposer.register(this, myModuleResources);
      }
      return myModuleResources;
    }
  }

  public void refreshResources() {
    synchronized (MODULE_RESOURCES_LOCK) {
      if (myModuleResources != null) {
        Disposer.dispose(myModuleResources);
        myModuleResources = null;
      }
    }

    synchronized (PROJECT_RESOURCES_LOCK) {
      if (myProjectResources != null) {
        Disposer.dispose(myProjectResources);
        myProjectResources = null;
      }
    }

    AppResourceRepository.refreshResources(this);
    ConfigurationManager.getOrCreateInstance(getModule()).getResolverCache().reset();
    ResourceFolderRegistry.reset();
    FileResourceRepository.reset();
  }

  @NotNull
  public JpsAndroidModuleProperties getProperties() {
    JpsAndroidModuleProperties state = getConfiguration().getState();
    assert state != null;
    return state;
  }

  @NotNull
  private Project getProject() {
    return getModule().getProject();
  }
}
