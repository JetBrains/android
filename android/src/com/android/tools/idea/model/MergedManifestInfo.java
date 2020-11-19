/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.model;

import static com.android.tools.idea.projectsystem.SourceProvidersKt.getManifestFiles;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.concurrency.Immutable;
import com.android.annotations.concurrency.Slow;
import com.android.manifmerger.Actions;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.tools.idea.project.SyncTimestampUtil;
import com.android.tools.idea.projectsystem.ManifestOverrides;
import com.android.tools.idea.projectsystem.MergedManifestContributors;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import gnu.trove.TObjectLongHashMap;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.io.input.CharSequenceInputStream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

/**
 * Immutable data object encapsulating the result of merging all of the manifest files related to a particular
 * Android module, including the merged manifest itself, a record of the actions the merger took, and logs related
 * to the merge that the user might find useful.
 *
 * A MergedManifestInfo is also capable of detecting when the merged manifest needs to be updated, as reported by
 * the {@link #isUpToDate} method.
 */
@Immutable
final class MergedManifestInfo {
  private static final Logger LOG = Logger.getInstance(MergedManifestInfo.class);

  @NotNull private final AndroidFacet myFacet;
  /**
   * The Java DOM document corresponding to the merged manifest (not an IntelliJ {@link com.intellij.openapi.editor.Document}).
   * If the merge failed, then this may reference the module's primary manifest instead. If the merge fails and we couldn't
   * find or parse the primary manifest, then myDomDocument will be null.
   */
  @Nullable private final Document myDomDocument;
  @NotNull private final ModificationStamps myModificationStamps;
  private final long mySyncTimestamp;
  @Nullable private final ImmutableList<MergingReport.Record> myLoggingRecords;
  @Nullable private final Actions myActions;

  /**
   * Relevant information extracted from the result of running the manifest merger,
   * including a DOM representation of the merged manifest, the actions taken by the
   * merger to produce the manifest, and any logs related to the merge that the user
   * might find useful.
   *
   * A null document indicates that the merge was unsuccessful.
   */
  private static class ParsedMergeResult {
    @Nullable final Document document;
    @NotNull final ImmutableList<MergingReport.Record> loggingRecords;
    @NotNull final Actions actions;

    ParsedMergeResult(@Nullable Document document,
                      @NotNull ImmutableList<MergingReport.Record> loggingRecords,
                      @NotNull Actions actions) {
      this.document = document;
      this.loggingRecords = loggingRecords;
      this.actions = actions;
    }
  }

  /** A record of the VFS and PSI modification stamps of a set of files at a given point in time. */
  private static class ModificationStamps {
    @NotNull private final ImmutableList<VirtualFile> files;
    /**
     * A mapping from PsiFile (or VirtualFile if the corresponding PsiFile is unavailable) to its modification stamp at this point in time.
     */
    @NotNull private final TObjectLongHashMap<Object> modificationStamps;

    private ModificationStamps(@NotNull ImmutableList<VirtualFile> files, @NotNull TObjectLongHashMap<Object> modificationStamps) {
      this.files = files;
      this.modificationStamps = modificationStamps;
    }

    @NotNull
    public static ModificationStamps forFiles(@NotNull Project project, @NotNull List<VirtualFile> files) {
      ImmutableList.Builder<VirtualFile> fileListBuilder = ImmutableList.builder();
      TObjectLongHashMap<Object> modificationStamps = new TObjectLongHashMap<>();
      PsiManager psiManager = PsiManager.getInstance(project);
      for (VirtualFile file : files) {
        fileListBuilder.add(file);
        try {
          PsiFile psiFile = psiManager.findFile(file);
          if (psiFile == null) {
            // TODO(b/137394236): When does this happen? Should we just ignore these files?
            modificationStamps.put(file, file.getModificationStamp());
          } else {
            modificationStamps.put(psiFile, psiFile.getModificationStamp());
          }
          // TODO(b/137394236): We should probably allow this exception to propagate up the call
          //  stack, since the result should no longer be needed.
        } catch (ProcessCanceledException ignore) {}
      }
      return new ModificationStamps(fileListBuilder.build(), modificationStamps);
    }

    @NotNull
    public ImmutableList<VirtualFile> getFiles() {
      return files;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ModificationStamps)) {
        return false;
      }
      return modificationStamps.equals(((ModificationStamps)other).modificationStamps);
    }

    @Override
    public int hashCode() {
      return modificationStamps.hashCode();
    }
  }

  private MergedManifestInfo(@NotNull AndroidFacet facet,
                             @Nullable Document domDocument,
                             @NotNull ModificationStamps modificationStamps,
                             long syncTimestamp,
                             @Nullable ImmutableList<MergingReport.Record> loggingRecords,
                             @Nullable Actions actions) {
    myFacet = facet;
    myDomDocument = domDocument;
    myModificationStamps = modificationStamps;
    mySyncTimestamp = syncTimestamp;
    myLoggingRecords = loggingRecords;
    myActions = actions;
  }

  /**
   * Must be called from within a read action.
   */
  @Slow
  @NotNull
  public static MergedManifestInfo create(@NotNull AndroidFacet facet) {
    Project project = facet.getModule().getProject();
    long syncTimestamp = SyncTimestampUtil.getLastSyncTimestamp(project);

    MergedManifestContributors contributors = ProjectSystemUtil.getModuleSystem(facet).getMergedManifestContributors();
    ModificationStamps modificationStamps = ModificationStamps.forFiles(project, contributors.allFiles);

    Document document = null;
    ImmutableList<MergingReport.Record> loggingRecords = null;
    Actions actions = null;

    ParsedMergeResult result = mergeManifests(facet, contributors);
    if (result != null) {
      document = result.document;
      loggingRecords = result.loggingRecords;
      actions = result.actions;
    }

    return new MergedManifestInfo(facet, document, modificationStamps, syncTimestamp, loggingRecords, actions);
  }

  @Slow
  @Nullable
  private static ParsedMergeResult mergeManifests(@NotNull AndroidFacet facet, @NotNull MergedManifestContributors manifests) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Project project = facet.getModule().getProject();
    if (project.isDisposed() || manifests.primaryManifest == null) {
      return null;
    }

    try {
      MergingReport mergingReport = getMergedManifest(facet,
                                                      manifests.primaryManifest,
                                                      manifests.flavorAndBuildTypeManifests,
                                                      manifests.libraryManifests,
                                                      manifests.navigationFiles);
      XmlDocument doc = mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);
      if (doc != null) {
        return new ParsedMergeResult(doc.getXml(), mergingReport.getLoggingRecords(), mergingReport.getActions());
      }
      else {
        LOG.warn("getMergedManifestSupplier failed " + mergingReport.getReportString());
        return new ParsedMergeResult(null, mergingReport.getLoggingRecords(), mergingReport.getActions());
      }
    }
    catch (ManifestMerger2.MergeFailureException e) {
      if (e.getCause() instanceof ProcessCanceledException) {
        throw (ProcessCanceledException) e.getCause();
      }
      throw new MergedManifestException.MergingError(facet.getModule(), e);
    }
  }

  /**
   * Must be called from within a read action.
   *
   * @return false if the merged manifest needs to be re-computed due to changes to the set of relevant manifests
   */
  public boolean isUpToDate() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (Disposer.isDisposed(myFacet)) {
      return true;
    }
    MergedManifestContributors manifests = ProjectSystemUtil.getModuleSystem(myFacet).getMergedManifestContributors();

    if (manifests.primaryManifest == null) {
      return true;
    }
    long lastSyncTimestamp = SyncTimestampUtil.getLastSyncTimestamp(myFacet.getModule().getProject());
    if (myDomDocument == null || mySyncTimestamp != lastSyncTimestamp) {
      return false;
    }
    // TODO(b/128854237): We should use something backed with an iterator here so that we can early
    //  return without computing all the files we might care about first.
    return myModificationStamps.equals(ModificationStamps.forFiles(myFacet.getModule().getProject(), manifests.allFiles));
  }

  public boolean hasSevereError() {
    return myLoggingRecords != null
           && myLoggingRecords.stream().anyMatch(record -> record.getSeverity() == MergingReport.Record.Severity.ERROR);
  }

  /**
   * Returns the merged manifest as a Java DOM document if available, the primary manifest if the merge was unsuccessful,
   * or null if the merge failed and we were also unable to parse the primary manifest.
   */
  @Nullable
  public Document getXmlDocument() {
    return myDomDocument;
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public ImmutableList<VirtualFile> getFiles() {
    return myModificationStamps.getFiles();
  }

  @NotNull
  public ImmutableList<MergingReport.Record> getLoggingRecords() {
    return myLoggingRecords == null ? ImmutableList.of() : myLoggingRecords;
  }

  @Nullable
  public Actions getActions() {
    return myActions;
  }

  @Slow
  @NotNull
  static MergingReport getMergedManifest(@NotNull AndroidFacet facet,
                                         @NotNull VirtualFile primaryManifestFile,
                                         @NotNull List<VirtualFile> flavorAndBuildTypeManifests,
                                         @NotNull List<VirtualFile> libManifests,
                                         @NotNull List<VirtualFile> navigationFiles) throws ManifestMerger2.MergeFailureException {
    ApplicationManager.getApplication().assertReadAccessAllowed();


    File mainManifestFile = VfsUtilCore.virtualToIoFile(primaryManifestFile);

    ILogger logger = NullLogger.getLogger();
    ManifestMerger2.MergeType mergeType =
      facet.getConfiguration().isAppOrFeature() ? ManifestMerger2.MergeType.APPLICATION : ManifestMerger2.MergeType.LIBRARY;

    ManifestMerger2.Invoker manifestMergerInvoker = ManifestMerger2.newMerger(mainManifestFile, logger, mergeType);
    manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.SKIP_BLAME, ManifestMerger2.Invoker.Feature.SKIP_XML_STRING);
    manifestMergerInvoker.addFlavorAndBuildTypeManifests(VfsUtilCore.virtualToIoFiles(flavorAndBuildTypeManifests).toArray(new File[0]));
    manifestMergerInvoker.addNavigationFiles(VfsUtilCore.virtualToIoFiles(navigationFiles));

    List<Pair<String, File>> libraryManifests = new ArrayList<>();
    for (VirtualFile file : libManifests) {
      libraryManifests.add(Pair.of(file.getName(), VfsUtilCore.virtualToIoFile(file)));
    }
    manifestMergerInvoker.addBundleManifests(libraryManifests);

    ManifestOverrides overrides = ProjectSystemUtil.getModuleSystem(facet.getModule()).getManifestOverrides();
    overrides.getPlaceholders().forEach((placeholder, value) -> manifestMergerInvoker.setPlaceHolderValue(placeholder, value));
    overrides.getDirectOverrides().forEach((property, value) -> manifestMergerInvoker.setOverride(property, value));

    if (mergeType == ManifestMerger2.MergeType.APPLICATION) {
      manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
    }

    Module module = facet.getModule();
    Project project = module.getProject();
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();

    manifestMergerInvoker.withFileStreamProvider(new ManifestMerger2.FileStreamProvider() {
      @Override
      protected InputStream getInputStream(@NotNull File file) throws IOException {
        ProgressManager.checkCanceled();
        VirtualFile vFile;
        if (file == mainManifestFile) {
          // Some tests use VirtualFile files (e.g. temp:///src/AndroidManifest.xml) for the main manifest
          vFile = primaryManifestFile;
        }
        else {
          vFile = VfsUtil.findFileByIoFile(file, false);
        }
        if (vFile == null) {
          // Gracefully handle case where file doesn't exist; this can happen for example
          // when a Gradle sync is needed after version control etc (see issue 65541477)
          //noinspection ZeroLengthArrayAllocation
          return new ByteArrayInputStream("<manifest/>".getBytes(UTF_8));
        }

        // We do not want to do this check if we have no library manifests.
        // findModuleForFile does not work for other build systems (e.g. bazel)
        if (!libManifests.isEmpty()) {
          Module moduleContainingManifest = getAndroidModuleForManifest(vFile);
          if (moduleContainingManifest != null && !module.equals(moduleContainingManifest)) {
            MergedManifestSnapshot manifest = MergedManifestManager.getFreshSnapshotInCallingThread(moduleContainingManifest);

            Document document = manifest.getDocument();
            if (document != null) { // normally the case, but can fail on merge fail
              // This is not very efficient. Consider enhancing the manifest merger API
              // such that I can pass back a fully merged DOM document instead of
              // an XML string since it will need to turn around and parse it anyway.
              String text = XmlUtils.toXml(document);
              return new ByteArrayInputStream(text.getBytes(UTF_8));
            }
          }
        }

        // If it exists, read from the in-memory document for this file.
        // This ensures that we pick up any unsaved edits.
        com.intellij.openapi.editor.Document document = fileDocumentManager.getCachedDocument(vFile);
        if (document != null) {
          return new CharSequenceInputStream(document.getCharsSequence(), UTF_8);
        }

        // Read from the VirtualFile (instead of the java.io.File) because the VFS
        // caches file contents on disk. This could matter if the original file resides
        // on a network file system, for example.
        return vFile.getInputStream();
      }

      @Nullable
      private Module getAndroidModuleForManifest(@NotNull VirtualFile vFile) {
        // See https://code.google.com/p/android/issues/detail?id=219141
        // Earlier, we used to get the module containing a manifest by doing: ModuleUtilCore.findModuleForFile(vFile, project)
        // This method of getting the module simply returns the module that contains this file. However, if the manifest sources are
        // remapped, this could be incorrect. i.e. for a project with the following structure:
        //     root
        //       |--- modules/a
        //       |       |------- build.gradle
        //       |--- external/a
        //               |------- AndroidManifest.xml
        // where the build.gradle remaps the sources to point to $root/external/a/AndroidManifest.xml, obtaining the module containing the
        // file will return root where it should have been "a". So the correct scheme is to actually iterate through all the modules in the
        // project and look at their source providers
        for (Module m : ModuleManager.getInstance(project).getModules()) {
          AndroidFacet androidFacet = AndroidFacet.getInstance(m);
          if (androidFacet == null) {
            continue;
          }

          Collection<VirtualFile> manifestFiles = getManifestFiles(androidFacet);
          for (VirtualFile manifestFile : manifestFiles) {
            if (vFile.equals(manifestFile)) {
              return m;
            }
          }
        }

        return null;
      }
    });


    return manifestMergerInvoker.merge();
  }
}