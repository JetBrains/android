package org.jetbrains.jps.android.builder;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.AndroidPlatform;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;

import java.io.File;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPreDexBuildTarget extends BuildTarget<AndroidPreDexBuildTarget.MyRootDescriptor> {
  private static final String ID = "only";

  private final JpsProject myProject;

  public static final String PRE_DEXED_LIBS_DIRECTORY_NAME = "pre_dexed_libs";

  public AndroidPreDexBuildTarget(@NotNull JpsProject project) {
    super(MyTargetType.INSTANCE);
    myProject = project;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    final List<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>();

    for (JpsModule module : myProject.getModules()) {
      final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

      if (extension != null) {
        if (extension.isLibrary()) {
          result.add(new AndroidLibraryPackagingTarget(module));
        }
        else {
          result.add(new AndroidAarDepsBuildTarget(module));
        }
      }
    }
    return result;
  }

  private static void fillDepsRecursively(@NotNull JpsModule root,
                                          @NotNull Set<JpsModule> libModules,
                                          @NotNull Set<String> externalJars,
                                          @NotNull BuildDataPaths dataPaths,
                                          @NotNull AndroidPlatform platform) {
    for (String jarOrLibDirPath : AndroidJpsUtil.getExternalLibraries(dataPaths, root, platform, false, false, true)) {
      final String path = FileUtil.toCanonicalPath(jarOrLibDirPath);

      if (path != null) {
        externalJars.add(path);
      }
    }

    for (JpsDependencyElement dependencyElement : root.getDependenciesList().getDependencies()) {
      if (dependencyElement instanceof JpsModuleDependency) {
        final JpsModule depModule = ((JpsModuleDependency)dependencyElement).getModule();

        if (depModule != null) {
          final JpsAndroidModuleExtension depExtension = AndroidJpsUtil.getExtension(depModule);

          if (depExtension != null && depExtension.isLibrary()) {
            if (libModules.add(depModule)) {
              fillDepsRecursively(depModule, libModules, externalJars, dataPaths, platform);
            }
          }
        }
      }
    }
  }

  @NotNull
  @Override
  public List<AndroidPreDexBuildTarget.MyRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    final List<AndroidPreDexBuildTarget.MyRootDescriptor> result =
      new ArrayList<AndroidPreDexBuildTarget.MyRootDescriptor>();
    final Set<JpsModule> libModules = new HashSet<JpsModule>();
    final Set<String> externalJars = new HashSet<String>();

    for (JpsModule module : myProject.getModules()) {
      final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

      if (extension != null && !extension.isLibrary() && extension.isPreDexingEnabled()) {
        final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, null, null);

        if (platform != null) {
          fillDepsRecursively(module, libModules, externalJars, dataPaths, platform);
        }
      }
    }

    for (JpsModule libModule : libModules) {
      final File classesJarFile = new AndroidLibraryPackagingTarget(libModule).getOutputFile(dataPaths);
      result.add(new MyRootDescriptor(this, classesJarFile, libModule.getName()));
    }

    for (String externalJarPath : externalJars) {
      result.add(new MyRootDescriptor(this, new File(externalJarPath), null));
    }
    return result;
  }

  @Nullable
  @Override
  public AndroidPreDexBuildTarget.MyRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    for (AndroidPreDexBuildTarget.MyRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Android Pre-DEX";
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singletonList(getOutputFile(context));
  }

  @NotNull
  public File getOutputFile(CompileContext context) {
    return getOutputDir(context.getProjectDescriptor().dataManager.getDataPaths());
  }

  @NotNull
  public static File getOutputDir(@NotNull BuildDataPaths dataPaths) {
    final File dir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(dataPaths);
    return new File(dir, PRE_DEXED_LIBS_DIRECTORY_NAME);
  }

  @NotNull
  public JpsProject getProject() {
    return myProject;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidPreDexBuildTarget target = (AndroidPreDexBuildTarget)o;

    if (!myProject.equals(target.myProject)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myProject.hashCode();
  }

  public static class MyTargetType extends BuildTargetType<AndroidPreDexBuildTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    private MyTargetType() {
      super(AndroidCommonUtils.PRE_DEX_BUILD_TARGET_TYPE_ID);
    }

    @NotNull
    @Override
    public List<AndroidPreDexBuildTarget> computeAllTargets(@NotNull JpsModel model) {
      if (!AndroidJpsUtil.isAndroidProjectWithoutGradleFacet(model.getProject())) {
        return Collections.emptyList();
      }
      return Collections.singletonList(new AndroidPreDexBuildTarget(model.getProject()));
    }

    @NotNull
    @Override
    public BuildTargetLoader<AndroidPreDexBuildTarget> createLoader(@NotNull final JpsModel model) {
      final JpsProject project = model.getProject();

      return new BuildTargetLoader<AndroidPreDexBuildTarget>() {
        @Nullable
        @Override
        public AndroidPreDexBuildTarget createTarget(@NotNull String targetId) {
          return ID.equals(targetId) && AndroidJpsUtil.isAndroidProjectWithoutGradleFacet(project) ? new AndroidPreDexBuildTarget(project) : null;
        }
      };
    }
  }

  public static class MyRootDescriptor extends BuildRootDescriptorImpl {
    private final String myModuleName;

    private MyRootDescriptor(@NotNull BuildTarget target, @NotNull File root, @Nullable String moduleName) {
      super(target, root);
      myModuleName = moduleName;
    }

    @Nullable
    public String getModuleName() {
      return myModuleName;
    }
  }
}
