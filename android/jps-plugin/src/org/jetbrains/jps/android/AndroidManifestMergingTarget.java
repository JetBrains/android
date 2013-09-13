package org.jetbrains.jps.android;

import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.TargetTypeRegistry;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidManifestMergingTarget extends ModuleBasedTarget<AndroidManifestMergingTarget.MyRootDescriptor> {
  public AndroidManifestMergingTarget(ModuleBasedBuildTargetType<?> targetType, @NotNull JpsModule module) {
    super(targetType, module);
  }

  @Override
  public boolean isCompiledBeforeModuleLevelBuilders() {
    return true;
  }

  @Override
  public boolean isTests() {
    return false;
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    final BuildTargetType<?> mavenResourcesProductionType =
      TargetTypeRegistry.getInstance().getTargetType("maven-resources-production");

    if (mavenResourcesProductionType != null) {
      return new ArrayList<BuildTarget<?>>(targetRegistry.getAllTargets(mavenResourcesProductionType));
    }
    return Collections.emptyList();
  }

  @Override
  public void writeConfiguration(ProjectDescriptor pd, PrintWriter out) {
  }

  @NotNull
  @Override
  public List<MyRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);
    assert extension != null;
    final List<MyRootDescriptor> result = new ArrayList<MyRootDescriptor>();

    final File manifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(extension);

    if (manifestFile != null) {
      result.add(new MyRootDescriptor(this, manifestFile, false));
    }

    for (JpsAndroidModuleExtension libExtension : AndroidJpsUtil.getAllAndroidDependencies(myModule, true)) {
      final File libManifest = AndroidJpsUtil.getManifestFileForCompilationPath(libExtension);

      if (libManifest != null && libManifest.exists()) {
        result.add(new MyRootDescriptor(this, libManifest, true));
      }
    }
    return result;
  }

  @Nullable
  @Override
  public MyRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    for (MyRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
      if (descriptor.getRootId().equals(rootId)) {
        return descriptor;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Android Manifest Merging";
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singletonList(getOutputDirectory(context));
  }

  @NotNull
  public File getOutputDirectory(@NotNull CompileContext context) {
    return AndroidJpsUtil.getPreprocessedManifestDirectory(
      myModule, context.getProjectDescriptor().dataManager.getDataPaths());
  }

  public static class MyTargetType extends ModuleBasedBuildTargetType<AndroidManifestMergingTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    private MyTargetType() {
      super(AndroidCommonUtils.MANIFEST_MERGING_BUILD_TARGET_TYPE_ID);
    }

    @NotNull
    @Override
    public List<AndroidManifestMergingTarget> computeAllTargets(@NotNull JpsModel model) {
      if (!AndroidJpsUtil.isAndroidProjectWithoutGradleFacet(model.getProject())) {
        return Collections.emptyList();
      }
      final List<AndroidManifestMergingTarget> targets = new ArrayList<AndroidManifestMergingTarget>();

      for (JpsModule module : model.getProject().getModules()) {
        final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

        if (extension != null && !extension.isLibrary() && extension.isManifestMergingEnabled()) {
          targets.add(new AndroidManifestMergingTarget(this, module));
        }
      }
      return targets;
    }

    @NotNull
    @Override
    public BuildTargetLoader<AndroidManifestMergingTarget> createLoader(@NotNull JpsModel model) {
      final Map<String, AndroidManifestMergingTarget> targetMap = new HashMap<String, AndroidManifestMergingTarget>();

      for (AndroidManifestMergingTarget target : computeAllTargets(model)) {
        targetMap.put(target.getId(), target);
      }
      return new BuildTargetLoader<AndroidManifestMergingTarget>() { {}
        @Nullable
        @Override
        public AndroidManifestMergingTarget createTarget(@NotNull String targetId) {
          return targetMap.get(targetId);
        }
      };
    }
  }

  public static class MyRootDescriptor extends BuildRootDescriptorImpl {
    private final boolean myLibManifestRoot;

    private MyRootDescriptor(@NotNull BuildTarget target, @NotNull File root, boolean libManifestRoot) {
      super(target, root);
      myLibManifestRoot = libManifestRoot;
    }

    public boolean isLibManifestRoot() {
      return myLibManifestRoot;
    }
  }
}
