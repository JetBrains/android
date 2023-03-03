/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import static com.android.SdkConstants.SUPPORT_LIB_GROUP_ID;

import com.android.annotations.NonNull;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.support.AndroidxNameUtils;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.lint.common.LintIdeClient;
import com.android.tools.idea.lint.common.LintIdeProject;
import com.android.tools.idea.lint.model.LintModelFactory;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPathKt;
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath;
import com.android.tools.idea.res.AndroidDependenciesCache;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.ApiConstraint;
import com.android.tools.lint.detector.api.LintModelModuleAndroidLibraryProject;
import com.android.tools.lint.detector.api.LintModelModuleProject;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ExtensionSdk;
import com.android.tools.lint.model.LintModelAndroidLibrary;
import com.android.tools.lint.model.LintModelDependency;
import com.android.tools.lint.model.LintModelLibrary;
import com.android.tools.lint.model.LintModelModule;
import com.android.tools.lint.model.LintModelModuleType;
import com.android.tools.lint.model.LintModelVariant;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.graph.Graph;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.config.KotlinModuleKind;
import org.jetbrains.kotlin.idea.facet.KotlinFacet;

/**
 * An {@linkplain LintIdeProject} represents a lint project, which typically corresponds to a {@link Module},
 * but can also correspond to a library "project" such as an {@link LintModelAndroidLibrary}.
 */
public class AndroidLintIdeProject extends LintIdeProject {
  AndroidLintIdeProject(@NonNull LintClient client,
                        @NonNull File dir,
                        @NonNull File referenceDir) {
    super(client, dir, referenceDir);
  }

  /**
   * Creates a set of projects for the given IntelliJ modules
   */
  @NonNull
  public static List<Project> create(@NonNull LintIdeClient client, @Nullable List<VirtualFile> files, @NonNull Module... modules) {
    List<Project> projects = new ArrayList<>();

    Map<Project, Module> projectMap = Maps.newHashMap();
    Map<Module, Project> moduleMap = Maps.newHashMap();
    Map<LintModelAndroidLibrary, Project> libraryMap = Maps.newHashMap();
    if (files != null && !files.isEmpty()) {
      // Wrap list with a mutable list since we'll be removing the files as we see them
      files = Lists.newArrayList(files);
    }
    for (Module module : Arrays.stream(modules).map(AndroidLintIdeProject::getMainModule).distinct().collect(Collectors.toList())) {
      addProjects(client, module, files, moduleMap, libraryMap, projectMap, projects, false);
    }

    client.setModuleMap(projectMap);

    if (projects.size() > 1) {
      // Partition the projects up such that we only return projects that aren't
      // included by other projects (e.g. because they are library projects)
      Set<Project> roots = new HashSet<>(projects);
      for (Project project : projects) {
        roots.removeAll(project.getAllLibraries());
      }
      return Lists.newArrayList(roots);
    }
    else {
      return projects;
    }
  }

  @NotNull
  private static Module getMainModule(@NotNull Module module) {
    GradleProjectPath path = GradleProjectPathKt.getGradleProjectPath(module);
    if (path == null) return module;
    GradleSourceSetProjectPath pathToMain = GradleProjectPathKt.toSourceSetPath(path, IdeModuleWellKnownSourceSet.MAIN);
    Module mainModule = GradleProjectPathKt.resolveIn(pathToMain, module.getProject());
    return mainModule != null ? mainModule : module;
  }

  /**
   * Creates a project for a single file. Also optionally creates a main project for the file, if applicable.
   *
   * @param client the lint client
   * @param file   the file to create a project for
   * @param module the module to create a project for
   * @return a project for the file, as well as a project (or null) for the main Android module
   */
  @NonNull
  public static Pair<Project, Project> createForSingleFile(@NonNull LintIdeClient client,
                                                           @Nullable VirtualFile file,
                                                           @NonNull Module module) {
    // TODO: Can make this method even more lightweight: we don't need to initialize anything in the project (source paths etc)
    // other than the metadata necessary for this file's type
    Project project = createModuleProject(client, module, true);
    Project main = null;
    Map<Project, Module> projectMap = Maps.newHashMap();
    if (project != null) {
      project.setDirectLibraries(Collections.emptyList());
      if (file != null) {
        project.addFile(VfsUtilCore.virtualToIoFile(file));
      }
      projectMap.put(project, module);

      // Supply a main project too, such that when you for example edit a file in a Java library,
      // and lint asks for getMainProject().getMinSdk(), we return the min SDK of an application
      // using the library, not "1" (the default for a module without a manifest)
      if (!project.isAndroidProject()) {
        Module androidModule = findAndroidModule(module);
        if (androidModule != null) {
          main = createModuleProject(client, androidModule, true);
          if (main != null) {
            projectMap.put(main, androidModule);
            main.setDirectLibraries(Collections.singletonList(project));
          }
        }
      }
    }
    client.setModuleMap(projectMap);

    //noinspection ConstantConditionsx
    return Pair.create(project, main);
  }

  /**
   * Find an Android module that depends on this module; prefer app modules over library modules
   */
  @Nullable
  private static Module findAndroidModule(@NonNull final Module module) {
    if (module.isDisposed()) {
      return null;
    }

    // Search for dependencies of this module
    Graph<Module> graph = ApplicationManager.getApplication().runReadAction((Computable<Graph<Module>>)() -> {
      com.intellij.openapi.project.Project project = module.getProject();
      return ModuleManager.getInstance(project).moduleGraph();
    });

    if (graph == null) {
      return null;
    }

    Set<AndroidFacet> facets = Sets.newHashSet();
    HashSet<Module> seen = Sets.newHashSet();
    seen.add(module);
    addAndroidModules(facets, seen, graph, module);

    // Prefer Android app modules
    for (AndroidFacet facet : facets) {
      if (facet.getConfiguration().isAppProject()) {
        return facet.getMainModule();
      }
    }

    // Resort to library modules if no app module depends directly on it
    if (!facets.isEmpty()) {
      return facets.iterator().next().getMainModule();
    }

    return null;
  }

  private static void addAndroidModules(Set<AndroidFacet> androidFacets, Set<Module> seen, Graph<Module> graph, Module module) {
    Iterator<Module> iterator = graph.getOut(module);
    while (iterator.hasNext()) {
      Module dep = getMainModule(iterator.next());
      AndroidFacet facet = AndroidFacet.getInstance(dep);
      if (facet != null) {
        androidFacets.add(facet);
      }

      if (!seen.contains(dep)) {
        seen.add(dep);
        addAndroidModules(androidFacets, seen, graph, dep);
      }
    }
  }

  /**
   * Recursively add lint projects for the given module, and any other module or library it depends on, and also
   * populate the reverse maps so we can quickly map from a lint project to a corresponding module/library (used
   * by the lint client
   */
  private static void addProjects(@NonNull LintClient client,
                                  @NonNull Module module,
                                  @Nullable List<VirtualFile> files,
                                  @NonNull Map<Module, Project> moduleMap,
                                  @NonNull Map<LintModelAndroidLibrary, Project> libraryMap,
                                  @NonNull Map<Project, Module> projectMap,
                                  @NonNull List<Project> projects,
                                  boolean shallowModel) {
    if (moduleMap.containsKey(module)) {
      return;
    }

    Project project = createModuleProject(client, module, shallowModel);

    if (project == null) {
      // It's possible for the module to *depend* on Android code, e.g. in a Gradle
      // project there will be a top-level non-Android module
      List<Module> dependentModules =
        AndroidDependenciesCache.getAllAndroidDependencies(module, false).stream()
          .map(AndroidFacet::getMainModule)
          .distinct()
          .collect(Collectors.toList());
      for (Module dependentModule : dependentModules) {
        addProjects(client, dependentModule, files, moduleMap, libraryMap, projectMap, projects, true);
      }
      return;
    }

    project.setIdeaProject(module.getProject());

    projects.add(project);
    moduleMap.put(module, project);
    projectMap.put(project, module);

    if (processFileFilter(module, files, project)) {
      // No need to process dependencies when doing single file analysis
      return;
    }

    List<Project> dependencies = new ArrayList<>();
    // No, this shouldn't use getAllAndroidDependencies; we may have non-Android dependencies that this won't include
    // (e.g. Java-only modules)
    List<Module> dependentModules =
        AndroidDependenciesCache.getAllAndroidDependencies(module, true).stream()
          .map(AndroidFacet::getMainModule)
          .distinct()
          .collect(Collectors.toList());
    for (Module dependentModule : dependentModules) {
      Project p = moduleMap.get(dependentModule);
      if (p != null) {
        dependencies.add(p);
      }
      else {
        addProjects(client, dependentModule, files, moduleMap, libraryMap, projectMap, dependencies, true);
      }
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      LintModelVariant variant = project.getBuildVariant();
      if (variant != null) {
        List<LintModelDependency> roots = variant.getMainArtifact().getDependencies().getCompileDependencies().getRoots();
        addGradleLibraryProjects(client, files, libraryMap, projects, facet, project, projectMap, dependencies, roots);
      }
    }

    project.setDirectLibraries(dependencies);
  }

  /**
   * Creates a new module project
   */
  @Nullable
  private static Project createModuleProject(@NonNull LintClient client, @NonNull Module module, boolean shallowModel) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    File dir = getLintProjectDirectory(module, facet);
    if (dir == null) return null;
    Project project;
    if (facet == null) {
      KotlinFacet kotlinFacet = KotlinFacet.Companion.get(module);
      if(
        kotlinFacet != null &&
        kotlinFacet.getConfiguration().getSettings().getMppVersion() != null &&
        kotlinFacet.getConfiguration().getSettings().getKind() != KotlinModuleKind.COMPILATION_AND_SOURCE_SET_HOLDER
      ) {
        return null;
      }

      project = new LintModuleProject(client, dir, dir, module);
      AndroidFacet f = findAndroidFacetInProject(module.getProject());
      if (f != null) {
        project.gradleProject = AndroidModel.isRequired(f);
      }
    }
    else if (AndroidModel.isRequired(facet)) {
      AndroidModel androidModel = AndroidModel.get(facet);
      if (androidModel instanceof GradleAndroidModel) {
        GradleAndroidModel model = (GradleAndroidModel)androidModel;
        String variantName = model.getSelectedVariantName();

        LintModelModule lintModel = getLintModuleModel(facet, shallowModel);
        LintModelVariant variant = lintModel.findVariant(variantName);
        if (variant == null) {
          variant = lintModel.getVariants().get(0);
        }
        project = new LintGradleProject(client, dir, dir, variant, facet, model);
      }
      else if (androidModel != null) {
        project = new LintAndroidModelProject(client, dir, dir, facet, androidModel);
      }
      else {
        project = new LintAndroidProject(client, dir, dir, facet);
      }
    }
    else {
      project = new LintAndroidProject(client, dir, dir, facet);
    }
    project.setIdeaProject(module.getProject());
    client.registerProject(dir, project);
    return project;
  }

  @NotNull
  private static LintModelModule getLintModuleModel(AndroidFacet facet, boolean shallowModel) {
    final var project = facet.getModule().getProject();
    final var cacheValueManager = CachedValuesManager.getManager(project);
    return cacheValueManager.getCachedValue(facet, () -> buildModuleModel(facet, shallowModel));
  }

  @NotNull
  private static Result<LintModelModule> buildModuleModel(AndroidFacet facet, boolean shallowModel) {
    GradleAndroidModel model = GradleAndroidModel.get(facet);
    if (model == null) throw new IllegalStateException("GradleAndroidModel not available for " + facet);
    IdeAndroidProject builderModelProject = model.getAndroidProject();
    String externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(facet.getModule());
    if (externalProjectPath == null) throw new IllegalStateException("No external project path for " + facet.getModule());
    File dir = new File(externalProjectPath);
    LintModelModule module = new LintModelFactory().create(builderModelProject, model.getVariants(), dir, !shallowModel);
    return Result.create(module, ProjectSyncModificationTracker.getInstance(facet.getModule().getProject()));
  }

  /**
   * Returns the  directory lint would use for a project wrapping the given module
   */
  @Nullable
  public static File getLintProjectDirectory(@NonNull Module module, @Nullable AndroidFacet facet) {

    if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleUtil.GRADLE_SYSTEM_ID, module)) {
      String externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
      if (!Strings.isNullOrEmpty(externalProjectPath)) {
        return new File(externalProjectPath);
      }
    }
    File dir;

    if (facet != null) {
      final VirtualFile mainContentRoot = AndroidRootUtil.getMainContentRoot(facet);

      if (mainContentRoot == null) {
        return null;
      }
      dir = VfsUtilCore.virtualToIoFile(mainContentRoot);
    }
    else {
      // For Java modules we just use the first content root that is we can find
      VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
      if (roots.length == 0) {
        return null;
      }
      dir = VfsUtilCore.virtualToIoFile(roots[0]);
    }
    return dir;
  }

  public static boolean hasAndroidModule(@NonNull com.intellij.openapi.project.Project project) {
    return ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID);
  }

  @Nullable
  private static AndroidFacet findAndroidFacetInProject(@NonNull com.intellij.openapi.project.Project project) {
    @NotNull List<AndroidFacet> androidFacetsInRandomOrder = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    return androidFacetsInRandomOrder.isEmpty() ? null : androidFacetsInRandomOrder.get(0);
  }

  /**
   * Adds any gradle library projects to the dependency list
   */
  private static void addGradleLibraryProjects(@NonNull LintClient client,
                                               @Nullable List<VirtualFile> files,
                                               @NonNull Map<LintModelAndroidLibrary, Project> libraryMap,
                                               @NonNull List<Project> projects,
                                               @NonNull AndroidFacet facet,
                                               @NonNull Project project,
                                               @NonNull Map<Project, Module> projectMap,
                                               @NonNull List<Project> dependencies,
                                               @NonNull List<LintModelDependency> graphItems) {
    com.intellij.openapi.project.Project ideaProject = facet.getModule().getProject();
    for (LintModelDependency dependency : graphItems) {
      LintModelLibrary l = dependency.findLibrary();
      if (!(l instanceof LintModelAndroidLibrary)) {
        continue;
      }
      LintModelAndroidLibrary library = (LintModelAndroidLibrary)l;
      Project p = libraryMap.get(library);
      if (p == null) {
        File dir = library.getFolder();
        p = new LintGradleLibraryProject(client, dir, dir, dependency, library);
        p.setIdeaProject(ideaProject);
        libraryMap.put(library, p);
        projectMap.put(p, facet.getMainModule());
        projects.add(p);

        if (files != null) {
          VirtualFile libraryDir = LocalFileSystem.getInstance().findFileByIoFile(dir);
          if (libraryDir != null) {
            ListIterator<VirtualFile> iterator = files.listIterator();
            while (iterator.hasNext()) {
              VirtualFile file = iterator.next();
              if (VfsUtilCore.isAncestor(libraryDir, file, false)) {
                project.addFile(VfsUtilCore.virtualToIoFile(file));
                iterator.remove();
              }
            }
          }
          if (files.isEmpty()) {
            files = null; // No more work in other modules
          }
        }
      }
      dependencies.add(p);
    }
  }

  @Override
  protected void initialize() {
    // NOT calling super: super performs ADT/ant initialization. Here we want to use
    // the gradle data instead
  }

  /**
   * Wraps an Android module
   */
  private static class LintAndroidProject extends LintModuleProject {
    protected final AndroidFacet myFacet;

    private LintAndroidProject(@NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir, @NonNull AndroidFacet facet) {
      super(client, dir, referenceDir, facet.getMainModule());
      myFacet = facet;

      gradleProject = false;
      library = myFacet.getConfiguration().isLibraryProject();

      AndroidPlatform platform = AndroidPlatforms.getInstance(myFacet.getMainModule());
      if (platform != null) {
        buildSdk = platform.getApiLevel();
      }
    }

    @Override
    public boolean isAndroidProject() {
      return true;
    }

    @NonNull
    @Override
    public String getName() {
      return myFacet.getHolderModule().getName();
    }

    @NonNull
    @Override
    public LintModelModuleType getType() {
      return LintModelFactory.getModuleType(myFacet.getConfiguration().getProjectType());
    }

    @Override
    @NonNull
    public List<File> getManifestFiles() {
      if (manifestFiles == null) {
        VirtualFile manifestFile = AndroidRootUtil.getPrimaryManifestFile(myFacet);
        if (manifestFile != null) {
          manifestFiles = Collections.singletonList(VfsUtilCore.virtualToIoFile(manifestFile));
        }
        else {
          manifestFiles = Collections.emptyList();
        }
      }

      return manifestFiles;
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
      if (proguardFiles == null) {
        final AndroidFacetProperties properties = myFacet.getProperties();

        if (properties.RUN_PROGUARD) {
          final List<String> urls = properties.myProGuardCfgFiles;

          if (!urls.isEmpty()) {
            proguardFiles = new ArrayList<>();

            for (String osPath : AndroidUtils.urlsToOsPaths(urls, null)) {
              if (!osPath.contains(AndroidFacetProperties.SDK_HOME_MACRO)) {
                proguardFiles.add(new File(osPath));
              }
            }
          }
        }

        if (proguardFiles == null) {
          proguardFiles = Collections.emptyList();
        }
      }

      return proguardFiles;
    }

    @NonNull
    @Override
    public List<File> getResourceFolders() {
      if (resourceFolders == null) {
        List<VirtualFile> folders = ResourceFolderManager.getInstance(myFacet).getFolders();
        List<File> dirs = Lists.newArrayListWithExpectedSize(folders.size());
        for (VirtualFile folder : folders) {
          dirs.add(VfsUtilCore.virtualToIoFile(folder));
        }
        resourceFolders = dirs;
      }

      return resourceFolders;
    }

    @Nullable
    @Override
    public Boolean dependsOn(@NonNull String artifact) {
      GradleCoordinate queryCoordinate = GradleCoordinate.parseCoordinateString(artifact + ":+");
      if (queryCoordinate != null) {
        GradleCoordinate foundDependency = ProjectSystemUtil.getModuleSystem(myFacet.getModule()).getResolvedDependency(queryCoordinate);
        if (foundDependency != null) {
          return Boolean.TRUE;
        }

        // Check new AndroidX namespace too
        if (artifact.startsWith(SUPPORT_LIB_GROUP_ID)) {
          String newArtifact = AndroidxNameUtils.getCoordinateMapping(artifact);
          if (!newArtifact.equals(artifact)) {
            queryCoordinate = GradleCoordinate.parseCoordinateString(newArtifact + ":+");
            if (queryCoordinate != null) {
              foundDependency = ProjectSystemUtil.getModuleSystem(myFacet.getModule()).getResolvedDependency(queryCoordinate);
              if (foundDependency != null) {
                return Boolean.TRUE;
              }
            }
          }
        }
      }

      return super.dependsOn(artifact);
    }
  }

  private static class LintAndroidModelProject extends LintAndroidProject {
    private final AndroidModel myAndroidModel;

    private LintAndroidModelProject(
      @NonNull LintClient client,
      @NonNull File dir,
      @NonNull File referenceDir,
      @NonNull AndroidFacet facet,
      @NonNull AndroidModel androidModel) {
      super(client, dir, referenceDir, facet);
      myAndroidModel = androidModel;
    }

    @Nullable
    @Override
    public String getPackage() {
      String manifestPackage = super.getPackage();
      if (manifestPackage != null) {
        return manifestPackage;
      }

      return myAndroidModel.getApplicationId();
    }

    @NonNull
    @Override
    public AndroidVersion getMinSdkVersion() {
      AndroidVersion version = myAndroidModel.getMinSdkVersion();
      if (version != null) {
        return version;
      }
      return super.getMinSdkVersion();
    }

    @NotNull
    @Override
    public ApiConstraint getMinSdkVersions() {
      AndroidVersion version = myAndroidModel.getMinSdkVersion();
      if (version != null) {
        // TODO: Handle codenames better?
        return ApiConstraint.get(version.getFeatureLevel(), ExtensionSdk.ANDROID_SDK_ID);
      }
      return super.getMinSdkVersions();
    }

    @NonNull
    @Override
    public AndroidVersion getTargetSdkVersion() {
      AndroidVersion version = myAndroidModel.getTargetSdkVersion();
      if (version != null) {
        return version;
      }
      return super.getTargetSdkVersion();
    }
  }

  private static class LintGradleProject extends LintModelModuleProject {
    private final GradleAndroidModel myAndroidModuleModel;
    private final AndroidFacet myFacet;

    /**
     * Creates a new Project. Use one of the factory methods to create.
     */
    private LintGradleProject(
      @NonNull LintClient client,
      @NonNull File dir,
      @NonNull File referenceDir,
      @NonNull LintModelVariant variant,
      @NonNull AndroidFacet facet,
      @NonNull GradleAndroidModel androidModuleModel) {
      super(client, dir, referenceDir, variant, null);
      gradleProject = true;
      mergeManifests = true;
      myFacet = facet;
      myAndroidModuleModel = androidModuleModel;
    }

    @NonNull
    @Override
    public List<File> getJavaClassFolders() {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        return super.getJavaClassFolders();
      } else {
        return Collections.emptyList();
      }
    }

    @NonNull
    @Override
    public List<File> getJavaLibraries(boolean includeProvided) {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        return super.getJavaLibraries(includeProvided);
      } else {
        return Collections.emptyList();
      }
    }

    @Override
    public int getBuildSdk() {
      String compileTarget = myAndroidModuleModel.getAndroidProject().getCompileTarget();
      AndroidVersion version = AndroidTargetHash.getPlatformVersion(compileTarget);
      if (version != null) {
        return version.getFeatureLevel();
      }

      AndroidPlatform platform = AndroidPlatforms.getInstance(myFacet.getModule());
      if (platform != null) {
        return platform.getApiVersion().getFeatureLevel();
      }

      return super.getBuildSdk();
    }

    @Nullable
    @Override
    public String getBuildTargetHash() {
      return myAndroidModuleModel.getAndroidProject().getCompileTarget();
    }
  }

  private static class LintGradleLibraryProject extends LintModelModuleAndroidLibraryProject {
    private LintGradleLibraryProject(@NonNull LintClient client,
                                     @NonNull File dir,
                                     @NonNull File referenceDir,
                                     @NonNull LintModelDependency dependency,
                                     @NonNull LintModelAndroidLibrary library) {
      super(client, dir, referenceDir, dependency, library);
    }

    @NotNull
    @Override
    public List<File> getJavaClassFolders() {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        return super.getJavaClassFolders();
      } else {
        return Collections.emptyList();
      }
    }

    @NonNull
    @Override
    public List<File> getJavaLibraries(boolean includeProvided) {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        return super.getJavaLibraries(includeProvided);
      }
      else {
        return Collections.emptyList();
      }
    }
  }
}
