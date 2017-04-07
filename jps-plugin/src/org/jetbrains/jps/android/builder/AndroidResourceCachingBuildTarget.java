package org.jetbrains.jps.android.builder;

import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceCachingBuildTarget extends AndroidBuildTarget {
  public AndroidResourceCachingBuildTarget(@NotNull JpsModule module) {
    super(MyTargetType.INSTANCE, module);
  }

  @NotNull
  @Override
  protected List<BuildRootDescriptor> doComputeRootDescriptors(JpsModel model,
                                                               ModuleExcludeIndex index,
                                                               IgnoredFileIndex ignoredFileIndex,
                                                               BuildDataPaths dataPaths) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);

    if (extension != null) {
      final List<BuildRootDescriptor> result = new ArrayList<BuildRootDescriptor>();

      for (File resOverlayDir : extension.getResourceOverlayDirs()) {
        result.add(new BuildRootDescriptorImpl(this, resOverlayDir));
      }
      final File resourceDir = AndroidJpsUtil.getResourceDirForCompilationPath(extension);

      if (resourceDir != null) {
        result.add(new BuildRootDescriptorImpl(this, resourceDir));
      }
      if (!extension.isLibrary()) {
        final Set<String> aarResDirPaths = new HashSet<String>();
        AndroidJpsUtil.collectResDirectoriesFromAarDeps(myModule, aarResDirPaths);

        for (JpsAndroidModuleExtension depExtension : AndroidJpsUtil.getAllAndroidDependencies(myModule, true)) {
          AndroidJpsUtil.collectResDirectoriesFromAarDeps(depExtension.getModule(), aarResDirPaths);
        }
        for (String path : aarResDirPaths) {
          result.add(new BuildRootDescriptorImpl(this, new File(path)));
        }
      }
      return result;
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singletonList(getOutputDir(context));
  }

  @NotNull
  public File getOutputDir(CompileContext context) {
    return AndroidJpsUtil.getResourcesCacheDir(myModule, context.getProjectDescriptor().dataManager.getDataPaths());
  }

  public static class MyTargetType extends AndroidBuildTargetType<AndroidResourceCachingBuildTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    private MyTargetType() {
      super(AndroidCommonUtils.RESOURCE_CACHING_BUILD_TARGET_ID, "Resource Caching");
    }

    @Override
    public AndroidResourceCachingBuildTarget createBuildTarget(@NotNull JpsAndroidModuleExtension extension) {
      return new AndroidResourceCachingBuildTarget(extension.getModule());
    }
  }
}
