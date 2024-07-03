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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.concurrency.Immutable;
import com.android.annotations.concurrency.Slow;
import com.android.manifmerger.Actions;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlDocument;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ManifestOverrides;
import com.android.tools.idea.projectsystem.MergedManifestContributors;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.io.input.CharSequenceInputStream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;

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
  private final long mySyncModificationCount;
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
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile == null) {
          // No PSI has been created for the file yet.
          modificationStamps.put(file, file.getModificationStamp());
        } else {
          modificationStamps.put(psiFile, psiFile.getModificationStamp());
        }
      }
      return new ModificationStamps(fileListBuilder.build(), modificationStamps);
    }

    public boolean isCurrent(@NotNull Project project, @NotNull List<VirtualFile> files) {
      if (!files.equals(this.files)) {
        return false;
      }
      PsiManager psiManager = PsiManager.getInstance(project);
      for (VirtualFile file : files) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile == null) {
          // No PSI has been created for the file yet.
          if (file.getModificationStamp() != modificationStamps.get(file)) {
            return false;
          }
        } else {
          if (psiFile.getModificationStamp() != modificationStamps.get(psiFile)) {
            return false;
          }
        }
      }
      return true;
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
                             long syncModificationCount,
                             @Nullable ImmutableList<MergingReport.Record> loggingRecords,
                             @Nullable Actions actions) {
    myFacet = facet;
    myDomDocument = domDocument;
    myModificationStamps = modificationStamps;
    mySyncModificationCount = syncModificationCount;
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

    MergedManifestContributors contributors = ProjectSystemUtil.getModuleSystem(facet).getMergedManifestContributors();
    ModificationStamps modificationStamps = ModificationStamps.forFiles(project, contributors.allFiles);
    long syncModificationCount = ProjectSyncModificationTracker.getInstance(project).getModificationCount();

    Document document = null;
    ImmutableList<MergingReport.Record> loggingRecords = null;
    Actions actions = null;

    ParsedMergeResult result = mergeManifests(facet, contributors);
    if (result != null) {
      document = result.document;
      loggingRecords = result.loggingRecords;
      actions = result.actions;
    }

    return new MergedManifestInfo(facet, document, modificationStamps, syncModificationCount, loggingRecords, actions);
  }

  @NotNull
  public static MergedManifestInfo createEmpty(@NotNull AndroidFacet facet) {
    Project project = facet.getModule().getProject();

    MergedManifestContributors contributors = ProjectSystemUtil.getModuleSystem(facet).getMergedManifestContributors();
    ModificationStamps modificationStamps = ModificationStamps.forFiles(project, contributors.allFiles);
    long syncModificationCount = ProjectSyncModificationTracker.getInstance(project).getModificationCount();

    return new MergedManifestInfo(facet, null, modificationStamps, syncModificationCount, null, null);
  }

  @Slow
  @Nullable
  private static ParsedMergeResult mergeManifests(@NotNull AndroidFacet facet, @NotNull MergedManifestContributors manifests) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (manifests.primaryManifest == null) {
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
      if (e.getCause() instanceof SAXParseException) {
        throw new MergedManifestException.ParsingError(e.getCause());
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
    if (myFacet.isDisposed()) {
      return true;
    }
    MergedManifestContributors manifests = ProjectSystemUtil.getModuleSystem(myFacet).getMergedManifestContributors();

    if (manifests.primaryManifest == null) {
      return true;
    }
    long modificationCount = ProjectSyncModificationTracker.getInstance(myFacet.getModule().getProject()).getModificationCount();
    if (myDomDocument == null || modificationCount != mySyncModificationCount) {
      return false;
    }
    return myModificationStamps.isCurrent(myFacet.getModule().getProject(), manifests.allFiles);
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
    manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.SKIP_BLAME, ManifestMerger2.Invoker.Feature.SKIP_XML_STRING, ManifestMerger2.Invoker.Feature.KEEP_GOING_AFTER_ERRORS);
    manifestMergerInvoker.addFlavorAndBuildTypeManifests(VfsUtilCore.virtualToIoFiles(flavorAndBuildTypeManifests).toArray(new File[0]));
    manifestMergerInvoker.addNavigationFiles(VfsUtilCore.virtualToIoFiles(navigationFiles));
    manifestMergerInvoker.withProcessCancellationChecker(ProgressManager::checkCanceled);

    for (VirtualFile file : libManifests) {
      manifestMergerInvoker.addLibraryManifest(file.getName(), VfsUtilCore.virtualToIoFile(file));
    }

    AndroidModuleSystem androidModuleSystem = ProjectSystemUtil.getModuleSystem(facet.getModule());
    String packageName = androidModuleSystem.getPackageName();
    if (packageName != null) {
      manifestMergerInvoker.setNamespace(packageName);
    }
    ManifestOverrides overrides = androidModuleSystem.getManifestOverrides();
    overrides.getPlaceholders().forEach(manifestMergerInvoker::setPlaceHolderValue);
    overrides.getDirectOverrides().forEach(manifestMergerInvoker::setOverride);

    if (mergeType == ManifestMerger2.MergeType.APPLICATION) {
      manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
    }

    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();

    manifestMergerInvoker.withManifestDocumentProvider(new ManifestMerger2.ManifestDocumentProvider() {
      @Override
      public Optional<Document> getManifestDocument(@NotNull File file) {
        ProgressManager.checkCanceled();
        VirtualFile vFile = MergedManifestInfo.getVirtualFile(file, mainManifestFile, primaryManifestFile);
        if (vFile != null && !libManifests.isEmpty()) {
          Module moduleContainingManifest = getAndroidModuleForFileIfManifest(facet.getModule().getProject(), vFile);
          if (moduleContainingManifest != null && !facet.getModule().equals(moduleContainingManifest)) {
            MergedManifestSnapshot manifest = MergedManifestManager.getFreshSnapshotInCallingThread(moduleContainingManifest);
            return Optional.ofNullable(manifest.getDocument());
          }
        }
        return Optional.empty();
      }
    });
    manifestMergerInvoker.withFileStreamProvider(new ManifestMerger2.FileStreamProvider() {

    @Override
      protected InputStream getInputStream(@NotNull File file) throws IOException {
        ProgressManager.checkCanceled();
        VirtualFile vFile = getVirtualFile(file, mainManifestFile, primaryManifestFile);
        if (vFile == null) {
          // Gracefully handle case where file doesn't exist; this can happen for example
          // when a Gradle sync is needed after version control etc (see issue 65541477)
          //noinspection ZeroLengthArrayAllocation
          return new ByteArrayInputStream("<manifest/>".getBytes(UTF_8));
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
    });

    Project project = facet.getModule().getProject();
    AndroidProjectSystem projectSystem = ProjectSystemUtil.getProjectSystem(project);
    Optional<MergedManifestInfoToken<AndroidProjectSystem>> maybeToken =
      Arrays.stream(MergedManifestInfoToken.EP_NAME.getExtensions(project))
        .filter(t -> t.isApplicable(projectSystem))
        .findFirst();
    maybeToken.ifPresent(token -> token.withProjectSystemFeatures(projectSystem, manifestMergerInvoker));

    return manifestMergerInvoker.merge();
  }

  private static VirtualFile getVirtualFile(@NotNull File file, @NotNull File mainManifestFile, @NotNull VirtualFile primaryManifestFile) {
    VirtualFile vFile;
    if (file == mainManifestFile) {
      // Some tests use VirtualFile files (e.g. temp:///src/AndroidManifest.xml) for the main manifest
      vFile = primaryManifestFile;
    } else {
      vFile = VfsUtil.findFileByIoFile(file, false);
    }
    return vFile;
  }

  @Nullable
  private static Module getAndroidModuleForFileIfManifest(@NotNull Project project, @NotNull VirtualFile vFile) {
    Module module = ModuleUtilCore.findModuleForFile(vFile, project);
    if (module == null) {
      return null;
    }
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      return null;
    }
    SourceProviders sourceProviders = SourceProviders.getInstance(androidFacet);
    if (Iterables.tryFind(sourceProviders.getSources().getManifestFiles(), it -> Objects.equals(it, vFile)).isPresent()) {
      return ModuleSystemUtil.getMainModule(androidFacet.getModule());
    }
    return null;
  }
}