package org.jetbrains.jps.android.builder;

import com.intellij.openapi.util.Pair;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAarDepsBuildTarget extends AndroidBuildTarget {


  public AndroidAarDepsBuildTarget(JpsModule module) {
    super(MyTargetType.INSTANCE, module);
  }

  @NotNull
  @Override
  protected List<BuildRootDescriptor> doComputeRootDescriptors(JpsModel model,
                                                               ModuleExcludeIndex index,
                                                               IgnoredFileIndex ignoredFileIndex,
                                                               BuildDataPaths dataPaths) {
    final Set<JpsLibrary> libraries = JpsJavaExtensionService.getInstance().enumerateDependencies(
      Collections.singleton(myModule)).runtimeOnly().productionOnly().getLibraries();
    final List<BuildRootDescriptor> result = new ArrayList<BuildRootDescriptor>();

    for (JpsLibrary library : libraries) {
      final Pair<File, List<File>> pair = AndroidJpsUtil.getResDirAndJarsIfAar(library);
      final List<File> jars = pair != null ? pair.getSecond() : null;

      if (jars != null) {
        for (File jar : jars) {
          result.add(new BuildRootDescriptorImpl(this, jar));
        }
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singleton(getOutputFile(context));
  }

  @NotNull
  public File getOutputFile(CompileContext context) {
    return getOutputFile(context.getProjectDescriptor().dataManager.getDataPaths());
  }

  @NotNull
  public File getOutputFile(BuildDataPaths dataPaths) {
    final JpsModule module = myModule;
    return getOutputFile(module, dataPaths);
  }

  @NotNull
  public static File getOutputFile(@NotNull JpsModule module, @NotNull BuildDataPaths dataPaths) {
    final File dir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(dataPaths, module);
    return new File(dir, AndroidCommonUtils.AAR_DEPS_JAR_FILE_NAME);
  }

  public static class MyTargetType extends AndroidBuildTargetType<AndroidAarDepsBuildTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    MyTargetType() {
      super(AndroidCommonUtils.AAR_DEPS_BUILD_TARGET_TYPE_ID, "AAR Dependencies Packaging");
    }

    @Nullable
    @Override
    public AndroidAarDepsBuildTarget createBuildTarget(@NotNull JpsAndroidModuleExtension extension) {
      return !extension.isLibrary() ? new AndroidAarDepsBuildTarget(extension.getModule()) : null;
    }
  }
}
