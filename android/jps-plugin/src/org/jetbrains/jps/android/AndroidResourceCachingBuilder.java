package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.builder.AndroidResourceCachingBuildTarget;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceCachingBuilder extends TargetBuilder<BuildRootDescriptor, AndroidResourceCachingBuildTarget> {
  @NonNls private static final String BUILDER_NAME = "Android Resource Caching";

  protected AndroidResourceCachingBuilder() {
    super(Collections.singletonList(AndroidResourceCachingBuildTarget.MyTargetType.INSTANCE));
  }

  @Override
  public void build(@NotNull AndroidResourceCachingBuildTarget target,
                    @NotNull DirtyFilesHolder<BuildRootDescriptor, AndroidResourceCachingBuildTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException, IOException {
    if (!holder.hasDirtyFiles()) {
      return;
    }

    if (!runPngCaching(target, context)) {
      throw new ProjectBuildException();
    }
  }

  private boolean runPngCaching(AndroidResourceCachingBuildTarget target,
                                CompileContext context) throws IOException {
    final JpsModule module = target.getModule();
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    assert extension != null;

    context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO,
                                               AndroidJpsBundle.message("android.jps.progress.res.caching", module.getName())));
    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);

    if (platform == null) {
      return false;
    }
    final File resCacheDir = target.getOutputDir(context);

    // todo: probably it may be done automatically
    if (context.isProjectRebuild() && resCacheDir.exists()) {
      if (!FileUtil.delete(resCacheDir)) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.cannot.create.directory", resCacheDir.getPath())));
        return false;
      }
    }

    if (!resCacheDir.exists()) {
      if (!resCacheDir.mkdirs()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.cannot.create.directory", resCacheDir.getPath())));
        return false;
      }
    }
    final IAndroidTarget androidTarget = platform.getTarget();

    final List<BuildRootDescriptor> roots = context.getProjectDescriptor().
      getBuildRootIndex().getTargetRoots(target, context);
    final List<String> inputDirs = new ArrayList<String>(roots.size());

    for (BuildRootDescriptor root : roots) {
      inputDirs.add(root.getRootFile().getPath());
    }
    final Map<AndroidCompilerMessageKind, List<String>> messages =
      AndroidApt.crunch(androidTarget, inputDirs, resCacheDir.getPath());
    AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME, module.getName());

    return messages.get(AndroidCompilerMessageKind.ERROR).isEmpty();
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }
}
