package org.jetbrains.jps.android;

import com.android.tools.idea.jps.AndroidTargetBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.builder.AndroidPreDexBuildTarget;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.StopBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPreDexBuilder extends AndroidTargetBuilder<AndroidPreDexBuildTarget.MyRootDescriptor, AndroidPreDexBuildTarget> {

  @NonNls private static final String BUILDER_NAME = "Android Pre Dex";

  protected AndroidPreDexBuilder() {
    super(Collections.singletonList(AndroidPreDexBuildTarget.MyTargetType.INSTANCE));
  }

  @Nullable
  public static String getOutputFileNameForExternalJar(@NotNull File srcFile) {
    final String canonicalPath = FileUtil.toCanonicalPath(srcFile.getAbsolutePath());

    if (canonicalPath == null) {
      return null;
    }
    if (AndroidBuildTestingManager.getTestingManager() != null) {
      return srcFile.getName();
    }
    final int hashCode = canonicalPath.hashCode();
    return FileUtil.getNameWithoutExtension(srcFile) + "-" +
           Integer.toHexString(hashCode) + ".jar";
  }

  @Override
  protected void buildTarget(@NotNull AndroidPreDexBuildTarget target,
                             @NotNull DirtyFilesHolder<AndroidPreDexBuildTarget.MyRootDescriptor, AndroidPreDexBuildTarget> holder,
                             @NotNull BuildOutputConsumer outputConsumer,
                             @NotNull final CompileContext context) throws ProjectBuildException, IOException {
    if (!doBuild(target, holder, outputConsumer, context)) {
      throw new StopBuildException();
    }
  }

  private static boolean doBuild(@NotNull AndroidPreDexBuildTarget target,
                                 @NotNull DirtyFilesHolder<AndroidPreDexBuildTarget.MyRootDescriptor, AndroidPreDexBuildTarget> holder,
                                 @NotNull BuildOutputConsumer outputConsumer,
                                 @NotNull CompileContext context) throws IOException, ProjectBuildException {
    final List<Pair<File, String>> filesToPreDex = new ArrayList<Pair<File, String>>();

    holder.processDirtyFiles(new FileProcessor<AndroidPreDexBuildTarget.MyRootDescriptor, AndroidPreDexBuildTarget>() {
      @Override
      public boolean apply(AndroidPreDexBuildTarget target, File file, AndroidPreDexBuildTarget.MyRootDescriptor root) throws IOException {
        if (canBePreDexed(file) && file.isFile()) {
          filesToPreDex.add(Pair.create(file, root.getModuleName()));
        }
        return true;
      }
    });
    final JpsProject project = target.getProject();
    AndroidPlatform platform = null;

    for (JpsModule module : project.getModules()) {
      if (AndroidJpsUtil.getExtension(module) != null) {
        platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
        break;
      }
    }

    if (platform == null) {
      return false;
    }
    if (!filesToPreDex.isEmpty()) {
      final File outputDir = target.getOutputFile(context);

      for (Pair<File, String> pair : filesToPreDex) {
        context.checkCanceled();

        final File srcFile = pair.getFirst();
        final String moduleName = pair.getSecond();
        final String srcFilePath = srcFile.getAbsolutePath();
        final File outputFile;

        if (moduleName != null) {
          context.processMessage(new ProgressMessage("Pre-dex [" + moduleName + "]"));
          outputFile = new File(new File(outputDir, moduleName), srcFile.getName());
        }
        else {
          context.processMessage(new ProgressMessage("Pre-dex: " + srcFile.getName()));
          final String outputFileName = getOutputFileNameForExternalJar(srcFile);

          if (outputFileName == null) {
            context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                       "Cannot pre-dex file " + srcFilePath + ": incorrect path", srcFilePath));
            return false;
          }
          outputFile = new File(outputDir, outputFileName);
        }

        if (AndroidJpsUtil.createDirIfNotExist(outputFile.getParentFile(), context, BUILDER_NAME) == null) {
          return false;
        }

        if (!AndroidDexBuilder.runDex(platform, outputFile.getPath(), new String[]{srcFilePath}, context,
          project, outputConsumer, BUILDER_NAME, srcFile.getName(), null)) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean canBePreDexed(@NotNull File file) {
    return "jar".equals(FileUtilRt.getExtension(file.getName()));
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }
}
