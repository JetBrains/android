package org.jetbrains.jps.android;

import com.android.SdkConstants;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.android.tools.idea.jps.AndroidTargetBuilder;
import com.android.utils.NullLogger;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
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

    if (!doMergeManifests(context,  manifestFile, libManifests, outputFile)) {
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


    ImmutableList.Builder<Pair<String, File>> libraryFiles = ImmutableList.builder();
    for (File f : libManifests) {
      libraryFiles.add(Pair.of(f.getName(), f));
    }

    final ManifestMerger2.Invoker manifestMergerInvoker =
      ManifestMerger2.newMerger(manifestFile, NullLogger.getLogger(), ManifestMerger2.MergeType.APPLICATION).addLibraryManifests(
        (File[])libManifests.stream().toArray());

    MergingReport mergingReport;
    try {
      mergingReport = manifestMergerInvoker.merge();
    }
    catch (ManifestMerger2.MergeFailureException e) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, e.getMessage()));
      return false;
    }

    MergingReport.Result result = mergingReport.getResult();

    for (MergingReport.Record record : mergingReport.getLoggingRecords()) {
      SourceFilePosition position = record.getSourceLocation();
      File sourceFile = position.getFile().getSourceFile();
      String sourceFilePath = sourceFile != null ? sourceFile.getAbsolutePath() : null;
      SourcePosition pos = position.getPosition();
      MergingReport.Record.Severity severity = record.getSeverity();

      if (severity != MergingReport.Record.Severity.INFO) {
        context.processMessage(
          new CompilerMessage(BUILDER_NAME, toBuildMessageKind(record.getSeverity()), record.getMessage(), sourceFilePath,
                              pos.getStartOffset(), pos.getEndOffset(), pos.getEndOffset(), pos.getEndLine(), pos.getEndColumn()));
      }
    }

    if (!result.isError()) {
      String xmlDocument = mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED);
      Files.write(xmlDocument, outputFile, Charsets.UTF_8);
    }
    return result.isSuccess();
  }

  private static BuildMessage.Kind toBuildMessageKind(MergingReport.Record.Severity severity) {
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
