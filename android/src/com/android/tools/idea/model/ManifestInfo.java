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

import com.android.SdkConstants;
import com.android.builder.model.*;
import com.android.manifmerger.*;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_FEATURE;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

/**
 * Retrieves and caches manifest information such as the themes to be used for
 * a given activity.
 *
 * @see com.android.xml.AndroidManifest
 */
final class ManifestInfo {

  private ManifestInfo() {
  }

  @NotNull
  static MergingReport getMergedManifest(@NotNull AndroidFacet facet,
                                         @NotNull VirtualFile primaryManifestFile,
                                         @NotNull List<VirtualFile> flavorAndBuildTypeManifests,
                                         @NotNull List<VirtualFile> libManifests) throws ManifestMerger2.MergeFailureException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final File mainManifestFile = VfsUtilCore.virtualToIoFile(primaryManifestFile);

    ILogger logger = NullLogger.getLogger();
    ManifestMerger2.MergeType mergeType = facet.isAppProject() || facet.getProjectType() == PROJECT_TYPE_FEATURE ? ManifestMerger2.MergeType.APPLICATION : ManifestMerger2.MergeType.LIBRARY;

    AndroidModel androidModel = facet.getAndroidModel();
    AndroidModuleModel gradleModel = AndroidModuleModel.get(facet);

    ManifestMerger2.Invoker manifestMergerInvoker = ManifestMerger2.newMerger(mainManifestFile, logger, mergeType);
    manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.SKIP_BLAME, ManifestMerger2.Invoker.Feature.SKIP_XML_STRING);
    manifestMergerInvoker.addFlavorAndBuildTypeManifests(VfsUtilCore.virtualToIoFiles(flavorAndBuildTypeManifests).toArray(new File[0]));

    List<Pair<String, File>> libraryManifests = new ArrayList<>();
    for (VirtualFile file : libManifests) {
      libraryManifests.add(Pair.of(file.getName(), VfsUtilCore.virtualToIoFile(file)));
    }
    manifestMergerInvoker.addBundleManifests(libraryManifests);

    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getMinSdkVersion();
      if (minSdkVersion != null) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.MIN_SDK_VERSION, minSdkVersion.getApiString());
      }
      AndroidVersion targetSdkVersion = androidModel.getTargetSdkVersion();
      if (targetSdkVersion != null) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.TARGET_SDK_VERSION, targetSdkVersion.getApiString());
      }
      Integer versionCode = androidModel.getVersionCode();
      if (versionCode != null && versionCode > 0) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.VERSION_CODE, String.valueOf(versionCode));
      }
      String packageOverride = androidModel.getApplicationId();
      if (!Strings.isNullOrEmpty(packageOverride)) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.PACKAGE, packageOverride);
      }
    }

    if (gradleModel != null) {
      BuildTypeContainer buildTypeContainer = gradleModel.findBuildType(gradleModel.getSelectedVariant().getBuildType());
      assert buildTypeContainer != null;
      BuildType buildType = buildTypeContainer.getBuildType();

      ProductFlavor mergedProductFlavor = gradleModel.getSelectedVariant().getMergedFlavor();
      // copy-paste from {@link VariantConfiguration#getManifestPlaceholders()}
      Map<String, Object> placeHolders = new HashMap<>(mergedProductFlavor.getManifestPlaceholders());
      placeHolders.putAll(buildType.getManifestPlaceholders());
      manifestMergerInvoker.setPlaceHolderValues(placeHolders);

      // @deprecated maxSdkVersion has been ignored since Android 2.1 (API level 7)
      Integer maxSdkVersion = mergedProductFlavor.getMaxSdkVersion();
      if (maxSdkVersion != null) {
        manifestMergerInvoker.setOverride(ManifestSystemProperty.MAX_SDK_VERSION, maxSdkVersion.toString());
      }

      // TODO we should have version Name for non-gradle projects
      // copy-paste from {@link VariantConfiguration#getVersionName()}
      String versionName = mergedProductFlavor.getVersionName();
      String flavorVersionNameSuffix = null;
      if (gradleModel.getFeatures().isProductFlavorVersionSuffixSupported()) {
        flavorVersionNameSuffix = getVersionNameSuffix(mergedProductFlavor);
      }
      String versionNameSuffix = Joiner.on("").skipNulls().join(flavorVersionNameSuffix, getVersionNameSuffix(buildType));
      if (!Strings.isNullOrEmpty(versionName) || !Strings.isNullOrEmpty(versionNameSuffix)) {
        if (Strings.isNullOrEmpty(versionName)) {
          Manifest manifest = facet.getManifest();
          if (manifest != null) {
            versionName = manifest.getXmlTag().getAttributeValue(SdkConstants.ATTR_VERSION_NAME, ANDROID_URI);
          }
        }
        if (!Strings.isNullOrEmpty(versionNameSuffix)) {
          versionName = Strings.nullToEmpty(versionName) + versionNameSuffix;
        }
        manifestMergerInvoker.setOverride(ManifestSystemProperty.VERSION_NAME, versionName);
      }
    }

    if (mergeType == ManifestMerger2.MergeType.APPLICATION) {
      manifestMergerInvoker.withFeatures(ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS);
    }

    final Module module = facet.getModule();
    final Project project = module.getProject();

    manifestMergerInvoker.withFileStreamProvider(new ManifestMerger2.FileStreamProvider() {
      @Override
      protected InputStream getInputStream(@NotNull File file) throws FileNotFoundException {
        VirtualFile vFile;
        if (file == mainManifestFile) {
          // Some tests use VirtualFile files (e.g. temp:///src/AndroidManifest.xml) for the main manifest
          vFile = primaryManifestFile;
        }
        else {
          vFile = VfsUtil.findFileByIoFile(file, false);
        }
        if (vFile == null ) {
          // Gracefully handle case where file doesn't exist; this can happen for example
          // when a Gradle sync is needed after version control etc (see issue 65541477)
          //noinspection ZeroLengthArrayAllocation
          return new ByteArrayInputStream("<manifest/>".getBytes(StandardCharsets.UTF_8));
        }

        // We do not want to do this check if we have no library manifests.
        // findModuleForFile does not work for other build systems (e.g. bazel)
        if (!libManifests.isEmpty()) {
          Module moduleContainingManifest = getAndroidModuleForManifest(vFile);
          if (moduleContainingManifest != null && !module.equals(moduleContainingManifest)) {
            MergedManifest manifest = MergedManifest.get(moduleContainingManifest);

            Document document = manifest.getDocument();
            if (document != null) { // normally the case, but can fail on merge fail
              // This is not very efficient. Consider enhancing the manifest merger API
              // such that I can pass back a fully merged DOM document instead of
              // an XML string since it will need to turn around and parse it anyway.
              String text = XmlUtils.toXml(document);
              return new ByteArrayInputStream(text.getBytes(Charsets.UTF_8));
            }
          }
        }

        try {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
          if (psiFile != null) {
            String text = psiFile.getText();
            return new ByteArrayInputStream(text.getBytes(Charsets.UTF_8));
          }
        } catch (ProcessCanceledException ignore) {
          // During startup we may receive a progress canceled exception here,
          // but we don't *need* to read from PSI; we can read directly from
          // disk. PSI is useful when the file has been modified, but that's not
          // the case in the typical scenario where we hit process canceled.
        }
        return super.getInputStream(file);
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

          List<VirtualFile> manifestFiles = IdeaSourceProvider.getManifestFiles(androidFacet);
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

  // TODO: Remove once Android plugin v. 2.3 is the "recommended" version.
  @Nullable
  @Deprecated
  // TODO replace with IdeBaseConfig#getVersionNameSuffix
  private static String getVersionNameSuffix(@NotNull BaseConfig config) {
    try {
      return config.getVersionNameSuffix();
    }
    catch (UnsupportedOperationException e) {
      Logger.getInstance(ManifestInfo.class).warn("Method 'getVersionNameSuffix' not found", e);
      return null;
    }
  }

  static class ManifestFile {
    private final @NotNull AndroidFacet myFacet;
    private @Nullable Document myDocument;
    private @Nullable List<VirtualFile> myManifestFiles;
    private @Nullable Map<Object, Long> myLastModifiedMap;

    private @Nullable ImmutableList<MergingReport.Record> myLoggingRecords;
    private @Nullable Actions myActions;

    private AtomicLong myLastSyncTimestamp;

    private ManifestFile(@NotNull AndroidFacet facet) {
      myFacet = facet;

      myLastSyncTimestamp = new AtomicLong(-1L);
      myFacet.getModule().getMessageBus().connect(myFacet).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
        if (result != ProjectSystemSyncManager.SyncResult.CANCELLED) {
          myLastSyncTimestamp.set(System.currentTimeMillis());
        }
      });
    }

    @NotNull
    public static ManifestFile create(@NotNull AndroidFacet facet) {
      return new ManifestFile(facet);
    }

    @Nullable
    private Document parseManifest(@NotNull final VirtualFile primaryManifestFile,
                                   @NotNull List<VirtualFile> flavorAndBuildTypeManifests,
                                   @NotNull List<VirtualFile> libManifests) {
      ApplicationManager.getApplication().assertReadAccessAllowed();

      Project project = myFacet.getModule().getProject();
      if (project.isDisposed()) {
        return null;
      }

      try {
        MergingReport mergingReport = getMergedManifest(myFacet, primaryManifestFile, flavorAndBuildTypeManifests, libManifests);
        myLoggingRecords = mergingReport.getLoggingRecords();
        myActions = mergingReport.getActions();

        XmlDocument doc = mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);
        if (doc != null) {
          return doc.getXml();
        }
        else {
          Logger.getInstance(ManifestInfo.class).warn("getMergedManifest failed " + mergingReport.getReportString());
        }
      }
      catch (ManifestMerger2.MergeFailureException ex) {
        // action cancelled
        if (ex.getCause() instanceof ProcessCanceledException) {
          return null;
        }
        // user is in the middle of editing the file
        if (ex.getCause() instanceof SAXParseException) {
          return null;
        }
        Logger.getInstance(ManifestInfo.class).warn("getMergedManifest exception", ex);
      }

      PsiFile psiFile = PsiManager.getInstance(project).findFile(primaryManifestFile);
      if (psiFile != null) {
        String text = psiFile.getText();
        return XmlUtils.parseDocumentSilently(text, true);
      }

      return null;
    }

    public boolean refresh() {
      Map<Object, Long> lastModifiedMap = new HashMap<>();

      VirtualFile primaryManifestFile = AndroidRootUtil.getPrimaryManifestFile(myFacet);
      if (primaryManifestFile == null) {
        return false;
      }

      lastModifiedMap.put(primaryManifestFile, getFileModificationStamp(primaryManifestFile));
      lastModifiedMap.put("sync", myLastSyncTimestamp.get());

      List<VirtualFile> flavorAndBuildTypeManifests = getFlavorAndBuildTypeManifests(myFacet);
      trackChanges(lastModifiedMap, flavorAndBuildTypeManifests);

      List<VirtualFile> libraryManifests = Collections.emptyList();
      if (myFacet.isAppProject() || myFacet.getProjectType() == PROJECT_TYPE_FEATURE) {
        libraryManifests = getLibManifests(myFacet);
        trackChanges(lastModifiedMap, libraryManifests);
      }

      // we want to track changes in these files, but we do not actually use them directly
      List<VirtualFile> flavorAndBuildTypeManifestsOfLibs = new ArrayList<>();
      List<AndroidFacet> dependencies = AndroidUtils.getAllAndroidDependencies(myFacet.getModule(), true);
      for (AndroidFacet dependency : dependencies) {
        flavorAndBuildTypeManifestsOfLibs.addAll(getFlavorAndBuildTypeManifests(dependency));
      }
      trackChanges(lastModifiedMap, flavorAndBuildTypeManifestsOfLibs);

      if (myDocument == null || !lastModifiedMap.equals(myLastModifiedMap)) {
        myDocument = parseManifest(primaryManifestFile, flavorAndBuildTypeManifests, libraryManifests);
        if (myDocument == null) {
          myManifestFiles = null;
          return false;
        }

        myManifestFiles = Lists.newArrayList();
        myManifestFiles.add(primaryManifestFile);
        myManifestFiles.addAll(flavorAndBuildTypeManifests);
        myManifestFiles.addAll(libraryManifests);

        myLastModifiedMap = lastModifiedMap;
        return true;
      } else {
        return false;
      }
    }

    private void trackChanges(@NotNull Map<Object, Long> lastModifiedMap, @NotNull List<VirtualFile> files) {
      for (VirtualFile libraryManifest : files) {
        lastModifiedMap.put(libraryManifest, getFileModificationStamp(libraryManifest));
      }
    }

    @NotNull
    private static List<VirtualFile> getFlavorAndBuildTypeManifests(@NotNull AndroidFacet facet) {
      // get all other manifests for this module, (NOT including the default one)
      List<VirtualFile> flavorAndBuildTypeManifests = new ArrayList<>();
      IdeaSourceProvider defaultSourceProvider = facet.getMainIdeaSourceProvider();
      for (IdeaSourceProvider provider : IdeaSourceProvider.getCurrentSourceProviders(facet)) {
        if (!defaultSourceProvider.equals(provider)) {
          VirtualFile flavorOrBuildTypeManifest = provider.getManifestFile();
          if (flavorOrBuildTypeManifest != null) {
            flavorAndBuildTypeManifests.add(flavorOrBuildTypeManifest);
          }
        }
      }
      return flavorAndBuildTypeManifests;
    }

    @NotNull
    private static List<VirtualFile> getLibManifests(@NotNull AndroidFacet facet) {
      List<VirtualFile> libraryManifests = new ArrayList<>();

      List<AndroidFacet> dependencies = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);

      AndroidModuleModel androidModuleModel = AndroidModuleModel.get(facet);
      if (androidModuleModel != null) {
        Collection<AndroidLibrary> libraries = androidModuleModel.getSelectedMainCompileDependencies().getLibraries();
        Set<File> set = new HashSet<>();
        for (AndroidLibrary dependency : libraries) {
          addAarManifests(dependency, set, dependencies);
        }
        for (File file : set) {
          VirtualFile libraryManifest = VfsUtil.findFileByIoFile(file, false);
          if (libraryManifest != null) { // some sort of user error, they don't have a manifest for a lib
            libraryManifests.add(libraryManifest);
          } // else the file is specified in the model, but not actually available yet, such as exploded AAR manifests
        }
      }

      for (AndroidFacet dependency : dependencies) {
        // we will NOT actually be reading from this file, as we will need to recursively get the info from the modules MergedManifest
        VirtualFile vFile = dependency.getMainIdeaSourceProvider().getManifestFile();
        if (vFile != null) {
          libraryManifests.add(vFile);
        }
      }
      return libraryManifests;
    }

    private static void addAarManifests(@NotNull AndroidLibrary lib, @NotNull Set<File> result, @NotNull List<AndroidFacet> moduleDeps) {
      String projectName = lib.getProject();
      if (projectName != null) {
        // The model ends up with AndroidLibrary references both to normal, source modules,
        // as well as AAR dependency wrappers. We don't want to add an AAR reference for
        // normal libraries (so we find these and just return below), but we *do* want to
        // include AAR wrappers.
        for (AndroidFacet f : moduleDeps) {
          if (projectName.equals(GradleUtil.getGradlePath(f.getModule()))) {
            return;
          }
        }
      }
      if (!result.contains(lib.getManifest())) {
        result.add(lib.getManifest());
        for (AndroidLibrary dependency : lib.getLibraryDependencies()) {
          addAarManifests(dependency, result, moduleDeps);
        }
      }
    }

    private long getFileModificationStamp(@NotNull VirtualFile file) {
      try {
        PsiFile psiFile = PsiManager.getInstance(myFacet.getModule().getProject()).findFile(file);
        return psiFile == null ? file.getModificationStamp() : psiFile.getModificationStamp();
      } catch (ProcessCanceledException ignore) {
        return 0L;
      }
    }

    public Document getXmlDocument() {
      return myDocument;
    }

    @Nullable
    public List<VirtualFile> getManifestFiles() {
      return myManifestFiles;
    }

    @NotNull
    public ImmutableList<MergingReport.Record> getLoggingRecords() {
      return myLoggingRecords == null ? ImmutableList.of() : myLoggingRecords;
    }

    @Nullable
    public Actions getActions() {
      return myActions;
    }
  }
}
