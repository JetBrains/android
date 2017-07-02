package org.jetbrains.jps.android.builder;

import com.android.tools.idea.jps.AndroidTargetBuilder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.AndroidJpsBundle;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.StopBuildException;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarOutputStream;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAarDepsBuilder extends AndroidTargetBuilder<BuildRootDescriptor, AndroidAarDepsBuildTarget> {
  @NonNls private static final String BUILDER_NAME = "Android AAR Dependencies Packaging";

  public AndroidAarDepsBuilder() {
    super(Collections.singleton(AndroidAarDepsBuildTarget.MyTargetType.INSTANCE));
  }

  @Override
  protected void buildTarget(@NotNull AndroidAarDepsBuildTarget target,
                             @NotNull DirtyFilesHolder<BuildRootDescriptor, AndroidAarDepsBuildTarget> holder,
                             @NotNull BuildOutputConsumer outputConsumer,
                             @NotNull CompileContext context) throws ProjectBuildException, IOException {
    if (!holder.hasDirtyFiles() && !holder.hasRemovedFiles()) {
      return;
    }
    assert !AndroidJpsUtil.isLightBuild(context);

    if (!doBuild(context, target, outputConsumer)) {
      throw new StopBuildException();
    }
  }

  private static boolean doBuild(final CompileContext context,
                                 AndroidAarDepsBuildTarget target,
                                 BuildOutputConsumer outputConsumer) {
    final JpsModule module = target.getModule();
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    if (extension == null || extension.isLibrary()) {
      return true;
    }

    File outputDir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);
    outputDir = AndroidJpsUtil.createDirIfNotExist(outputDir, context, BUILDER_NAME);
    if (outputDir == null) {
      return false;
    }
    final List<String> srcJarFiles = new ArrayList<String>();

    for (BuildRootDescriptor descriptor : context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context)) {
      final File file = descriptor.getRootFile();

      if (file.exists()) {
        srcJarFiles.add(file.getPath());
      }
    }
    if (srcJarFiles.isEmpty()) {
      return true;
    }
    context.processMessage(new ProgressMessage(AndroidJpsBundle.message(
      "android.jps.progress.aar.dependencies.packaging", module.getName())));

    File tempDir = null;

    try {
      tempDir = FileUtil.createTempDirectory("extracted_aar_deps", "tmp");

      for (int i = srcJarFiles.size() - 1; i >= 0; i--) {
        ZipUtil.extract(new File(srcJarFiles.get(i)), tempDir, null, true);
      }
      final File outputJarFile = new File(outputDir, AndroidCommonUtils.AAR_DEPS_JAR_FILE_NAME);

      if (!packDirectoryIntoJar(tempDir, outputJarFile, context)) {
        return false;
      }
      final AndroidBuildTestingManager testingManager = AndroidBuildTestingManager.getTestingManager();

      if (testingManager != null && outputJarFile.isFile()) {
        testingManager.getCommandExecutor().checkJarContent("aar_dependencies_package_jar", outputJarFile.getPath());
      }
      outputConsumer.registerOutputFile(outputJarFile, srcJarFiles);
      return true;
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      return false;
    }
    finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
    }
  }

  private static boolean packDirectoryIntoJar(final File srcDir, File dstJar, final CompileContext context) throws IOException {
    final JarOutputStream jos = new JarOutputStream(new FileOutputStream(dstJar));

    try {
      return FileUtil.processFilesRecursively(srcDir, new Processor<File>() {
        @Override
        public boolean process(File file) {
          if (FileUtilRt.extensionEquals(file.getName(), "class")) {
            final String relPath = FileUtil.getRelativePath(srcDir, file);

            if (relPath != null) {
              try {
                AndroidCommonUtils.packIntoJar(jos, file, relPath);
              }
              catch (IOException e) {
                AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
                return false;
              }
            }
          }
          return true;
        }
      });
    }
    finally {
      jos.close();
    }
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }
}
