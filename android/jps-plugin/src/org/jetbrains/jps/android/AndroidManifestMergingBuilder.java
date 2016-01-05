package org.jetbrains.jps.android;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.manifmerger.ICallback;
import com.android.manifmerger.IMergerLog;
import com.android.manifmerger.ManifestMerger;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.jps.AndroidTargetBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.StopBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidManifestMergingBuilder
  extends AndroidTargetBuilder<AndroidManifestMergingTarget.MyRootDescriptor, AndroidManifestMergingTarget> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidManifestMergingBuilder");

  private static final String BUILDER_NAME = "Android Manifest Merger";

  public AndroidManifestMergingBuilder() {
    super(Collections.singletonList(AndroidManifestMergingTarget.MyTargetType.INSTANCE));
  }

  @Override
  protected void buildTarget(@NotNull AndroidManifestMergingTarget target,
                             @NotNull DirtyFilesHolder<AndroidManifestMergingTarget.MyRootDescriptor, AndroidManifestMergingTarget> holder,
                             @NotNull BuildOutputConsumer outputConsumer,
                             @NotNull CompileContext context) throws ProjectBuildException, IOException {
    if (!holder.hasDirtyFiles() && !holder.hasRemovedFiles()) {
      return;
    }

    try {
      if (!doManifestMerging(target, context, outputConsumer)) {
        throw new StopBuildException();
      }
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      AndroidJpsUtil.handleException(context, e, BUILDER_NAME, LOG);
    }
  }

  private static boolean doManifestMerging(AndroidManifestMergingTarget target,
                                           CompileContext context,
                                           BuildOutputConsumer outputConsumer) throws IOException {
    final JpsModule module = target.getModule();
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    assert extension != null;
    assert !extension.isLibrary();
    assert extension.isManifestMergingEnabled();

    final File outputDir = target.getOutputDirectory(context);

    if (!outputDir.exists() && !outputDir.mkdirs()) {
      context.processMessage(new CompilerMessage(
        BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle.message(
        "android.jps.cannot.create.directory", outputDir.getPath())));
      return false;
    }
    File manifestFile = null;
    final List<File> libManifests = new ArrayList<File>();
    final List<AndroidManifestMergingTarget.MyRootDescriptor> roots =
      context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context);

    for (AndroidManifestMergingTarget.MyRootDescriptor root : roots) {
      if (root.isLibManifestRoot()) {
        libManifests.add(root.getRootFile());
      }
      else {
        manifestFile = root.getRootFile();
      }
    }

    if (manifestFile == null) {
      context.processMessage(new CompilerMessage(
        BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle.message(
        "android.jps.errors.manifest.not.found", module.getName())));
      return false;
    }
    final File outputFile = new File(outputDir, SdkConstants.FN_ANDROID_MANIFEST_XML);
    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);

    if (platform == null) {
      return false;
    }
    if (!doMergeManifests(context, platform.getSdkHandler(), manifestFile, libManifests, outputFile)) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                 "[" + module.getName() + "] Cannot perform manifest merging"));
      return false;
    }
    final List<String> srcPaths = new ArrayList<String>();
    srcPaths.add(manifestFile.getPath());

    for (File libManifest : libManifests) {
      srcPaths.add(libManifest.getPath());
    }
    outputConsumer.registerOutputFile(outputFile, srcPaths);
    return true;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  private static boolean doMergeManifests(final CompileContext context,
                                          final AndroidSdkHandler sdkHandler,
                                          File manifestFile,
                                          List<File> libManifests,
                                          File outputFile)
    throws IOException {
    final AndroidBuildTestingManager testingManager = AndroidBuildTestingManager.getTestingManager();

    if (testingManager != null) {
      final StringBuilder messageBuilder = new StringBuilder("manifest_merging\n");
      messageBuilder.append(manifestFile.getPath()).append('\n');
      Collections.sort(libManifests);

      for (File libManifest : libManifests) {
        messageBuilder.append(libManifest.getPath()).append('\n');
      }
      messageBuilder.append(outputFile.getPath());
      testingManager.getCommandExecutor().log(messageBuilder.toString());
    }

    final ManifestMerger manifestMerger = new ManifestMerger(new IMergerLog() {
      @Override
      public void error(@NonNull Severity severity,
                        @NonNull FileAndLine location,
                        @NonNull String message,
                        Object... msgParams) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, toBuildMessageKind(severity), formatMessage(message, msgParams),
                                                   location.getFileName(), -1L, -1L, -1L, location.getLine(), -1L));
      }

      @Override
      public void conflict(@NonNull Severity severity,
                           @NonNull FileAndLine location1,
                           @NonNull FileAndLine location2,
                           @NonNull String message,
                           Object... msgParams) {
        final StringBuilder builder = new StringBuilder("Conflicts:\n");
        final String filePath1 = location1.getFileName();

        if (filePath1 != null) {
          builder.append(FileUtil.toSystemDependentName(filePath1)).append(": line ").append(location1.getLine());
        }
        else {
          builder.append("unknown");
        }
        builder.append('\n');
        final String filePath2 = location2.getFileName();

        if (filePath2 != null) {
          builder.append(FileUtil.toSystemDependentName(filePath2)).append(": line ").append(location1.getLine());
        }
        else {
          builder.append("unknown");
        }
        builder.append('\n').append(formatMessage(message, msgParams));
        context.processMessage(new CompilerMessage(BUILDER_NAME, toBuildMessageKind(severity),
                                                   builder.toString(), filePath1, -1L, -1L, -1L, location1.getLine(), -1L));
      }

      private String formatMessage(String message, Object... msgParams) {
        try {
          return String.format(message, msgParams);
        }
        catch (IllegalFormatException e) {
          LOG.debug(e);
          return message;
        }
      }
    }, new ICallback() {
      @Override
      public int queryCodenameApiLevel(@NonNull String codename) {
        try {
          AndroidVersion version = new AndroidVersion(codename);
          String hashString = AndroidTargetHash.getPlatformHashString(version);
          AndroidJpsUtil.RepoLogger log = new AndroidJpsUtil.RepoLogger();
          IAndroidTarget t = sdkHandler.getAndroidTargetManager(log).getTargetFromHashString(hashString, log);
          if (t != null) {
            return t.getVersion().getApiLevel();
          }
        }
        catch (AndroidVersion.AndroidVersionException ignore) {
        }
        return ICallback.UNKNOWN_CODENAME;
      }
    });
    return manifestMerger.process(outputFile, manifestFile, libManifests.toArray(new File[libManifests.size()]), null, null);
  }

  private static BuildMessage.Kind toBuildMessageKind(IMergerLog.Severity severity) {
    if (severity == null) {
      return BuildMessage.Kind.INFO;
    }
    switch (severity) {
      case INFO:
        return BuildMessage.Kind.INFO;
      case WARNING:
        return BuildMessage.Kind.WARNING;
      case ERROR:
        return BuildMessage.Kind.ERROR;
      default:
        return BuildMessage.Kind.INFO;
    }
  }
}
