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
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.manifmerger.Actions;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXParseException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;

import static com.android.SdkConstants.ANDROID_URI;

/**
 * Retrieves and caches manifest information such as the themes to be used for
 * a given activity.
 *
 * @see com.android.xml.AndroidManifest
 */
public final class ManifestInfo {

  private ManifestInfo() {
  }

  /**
   * Returns the value of the given attribute from the android namespace
   */
  @Nullable("if the attribute value is null or empty")
  static String getAttributeValue(@NotNull XmlTag xmlTag, @NotNull String attributeName) {
    return getAttributeValue(xmlTag, attributeName, ANDROID_URI);
  }

  /**
   * Returns the value of the given attribute
   */
  @Nullable("if the attribute value is null or empty")
  static String getAttributeValue(@NotNull XmlTag xmlTag, @NotNull String attributeName, @Nullable String attributeNamespace) {
    return Strings.emptyToNull(xmlTag.getAttributeValue(attributeName, attributeNamespace));
  }

  /**
   * @deprecated use {@link MergedManifest#get(Module)} instead
   */
  @Deprecated
  public static MergedManifest get(Module module, boolean ignored) {
    return MergedManifest.get(module);
  }

  @NotNull
  static MergingReport getMergedManifest(final @NotNull AndroidFacet facet, @NotNull final VirtualFile primaryManifestFile, @NotNull List<VirtualFile> flavorAndBuildTypeManifests, @NotNull List<VirtualFile> libManifests) throws ManifestMerger2.MergeFailureException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final File mainManifestFile = VfsUtilCore.virtualToIoFile(primaryManifestFile);

    ILogger logger = NullLogger.getLogger();
    ManifestMerger2.MergeType mergeType = facet.isLibraryProject() ? ManifestMerger2.MergeType.LIBRARY : ManifestMerger2.MergeType.APPLICATION;

    AndroidModel androidModel = facet.getAndroidModel();
    AndroidGradleModel gradleModel = AndroidGradleModel.get(facet);

    ManifestMerger2.Invoker manifestMergerInvoker = ManifestMerger2.newMerger(mainManifestFile, logger, mergeType);
    manifestMergerInvoker.addFlavorAndBuildTypeManifests(VfsUtilCore.virtualToIoFiles(flavorAndBuildTypeManifests).toArray(new File[flavorAndBuildTypeManifests.size()]));

    List<Pair<String, File>> libraryManifests = new ArrayList<Pair<String, File>>();
    for (VirtualFile file : libManifests) {
      libraryManifests.add(Pair.of(file.getName(), VfsUtilCore.virtualToIoFile(file)));
    }
    manifestMergerInvoker.addLibraryManifests(libraryManifests);

    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getMinSdkVersion();
      if (minSdkVersion != null) {
        manifestMergerInvoker.setOverride(ManifestMerger2.SystemProperty.MIN_SDK_VERSION, minSdkVersion.getApiString());
      }
      AndroidVersion targetSdkVersion = androidModel.getTargetSdkVersion();
      if (targetSdkVersion != null) {
        manifestMergerInvoker.setOverride(ManifestMerger2.SystemProperty.TARGET_SDK_VERSION, targetSdkVersion.getApiString());
      }
      Integer versionCode = androidModel.getVersionCode();
      if (versionCode != null && versionCode > 0) {
        manifestMergerInvoker.setOverride(ManifestMerger2.SystemProperty.VERSION_CODE, String.valueOf(versionCode));
      }
      String packageOverride = androidModel.getApplicationId();
      if (!Strings.isNullOrEmpty(packageOverride)) {
        manifestMergerInvoker.setOverride(ManifestMerger2.SystemProperty.PACKAGE, packageOverride);
      }
    }

    if (gradleModel != null) {
      BuildTypeContainer buildTypeContainer = gradleModel.findBuildType(gradleModel.getSelectedVariant().getBuildType());
      assert buildTypeContainer != null;
      BuildType buildType = buildTypeContainer.getBuildType();

      // copy-paste from {@link VariantConfiguration#getManifestPlaceholders()}
      Map<String, Object> placeHolders = new HashMap<String, Object>(gradleModel.getSelectedVariant().getMergedFlavor().getManifestPlaceholders());
      placeHolders.putAll(buildType.getManifestPlaceholders());
      manifestMergerInvoker.setPlaceHolderValues(placeHolders);

      // @deprecated maxSdkVersion has been ignored since Android 2.1 (API level 7)
      Integer maxSdkVersion = gradleModel.getSelectedVariant().getMergedFlavor().getMaxSdkVersion();
      if (maxSdkVersion != null) {
        manifestMergerInvoker.setOverride(ManifestMerger2.SystemProperty.MAX_SDK_VERSION, maxSdkVersion.toString());
      }

      // TODO we should have version Name for non-gradle projects
      // copy-paste from {@link VariantConfiguration#getVersionName()}
      String versionName = gradleModel.getSelectedVariant().getMergedFlavor().getVersionName();
      String versionNameSuffix = buildType.getVersionNameSuffix();
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
        manifestMergerInvoker.setOverride(ManifestMerger2.SystemProperty.VERSION_NAME, versionName);
      }
    }

    final Module module = facet.getModule();
    final Project project = module.getProject();

    manifestMergerInvoker.withFileStreamProvider(new ManifestMerger2.FileStreamProvider() {
      @Override
      protected InputStream getInputStream(@NotNull File file) throws FileNotFoundException {
        VirtualFile vFile;
        if (file == mainManifestFile) {
          // some tests use VirtualFile files (e.g. temp:///src/AndroidManifest.xml) for the main manifest
          vFile = primaryManifestFile;
        }
        else {
          vFile = VfsUtil.findFileByIoFile(file, false);
        }
        assert vFile != null : file;

        // we do not want to do this check if we have no lib manifests
        // findModuleForFile does not work for other build systems (e.g. bazel)
        if (!libManifests.isEmpty()) {
          Module fileModule = ModuleUtilCore.findModuleForFile(vFile, project);
          if (fileModule != null && !module.equals(fileModule)) {
            assert libManifests.contains(vFile); // if it's a different module, it must be one of the lib manifests
            MergedManifest manifest = MergedManifest.get(fileModule);
            XmlTag xmlTag = manifest.getXmlTag();
            assert xmlTag != null; // we must have a file here as we had a source VirtualFile
            String text = xmlTag.getContainingFile().getText();
            return new ByteArrayInputStream(text.getBytes(Charsets.UTF_8));
          }
        }

        PsiFile psiFile = PsiManager.getInstance(project).findFile(vFile);
        if (psiFile != null) {
          String text = psiFile.getText();
          return new ByteArrayInputStream(text.getBytes(Charsets.UTF_8));
        }
        Logger.getInstance(ManifestInfo.class).warn("can not find PSI FIle for " + file);
        return super.getInputStream(file);
      }
    });

    return manifestMergerInvoker.merge();
  }

  static class ManifestFile {
    private final @NotNull AndroidFacet myFacet;
    private @Nullable XmlFile myXmlFile;
    private @Nullable Map<Object, Long> myLastModifiedMap;

    private @Nullable ImmutableList<MergingReport.Record> myLoggingRecords;
    private @Nullable Actions myActions;

    private ManifestFile(@NotNull AndroidFacet facet) {
      myFacet = facet;
    }

    @NotNull
    public static synchronized ManifestFile create(@NotNull AndroidFacet facet) {
      return new ManifestFile(facet);
    }

    @Nullable
    private XmlFile parseManifest(@NotNull final VirtualFile primaryManifestFile, @NotNull List<VirtualFile> flavorAndBuildTypeManifests, @NotNull List<VirtualFile> libManifests) {
      ApplicationManager.getApplication().assertReadAccessAllowed();

      Project project = myFacet.getModule().getProject();
      if (project.isDisposed()) {
        return null;
      }

      try {
        MergingReport mergingReport = getMergedManifest(myFacet, primaryManifestFile, flavorAndBuildTypeManifests, libManifests);
        myLoggingRecords = mergingReport.getLoggingRecords();
        myActions = mergingReport.getActions();

        String doc = mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED);
        if (!Strings.isNullOrEmpty(doc)) {
          return (XmlFile)PsiFileFactory.getInstance(project).createFileFromText(SdkConstants.FN_ANDROID_MANIFEST_XML, XMLLanguage.INSTANCE, doc);
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
      return (psiFile instanceof XmlFile) ? (XmlFile)psiFile : null;
    }

    public synchronized boolean refresh() {
      Map<Object, Long> lastModifiedMap = new HashMap<Object, Long>();

      VirtualFile primaryManifestFile = AndroidRootUtil.getPrimaryManifestFile(myFacet);
      if (primaryManifestFile == null) {
        return false;
      }
      lastModifiedMap.put(primaryManifestFile, getFileModificationStamp(primaryManifestFile));

      long lastGradleSyncTimestamp = GradleSyncState.getInstance(myFacet.getModule().getProject()).getLastGradleSyncTimestamp();
      lastModifiedMap.put("gradle-sync", lastGradleSyncTimestamp);

      List<VirtualFile> flavorAndBuildTypeManifests = getFlavorAndBuildTypeManifests(myFacet);
      trackChanges(lastModifiedMap, flavorAndBuildTypeManifests);

      List<VirtualFile> libraryManifests = Collections.emptyList();
      if (!myFacet.isLibraryProject()) {
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

      if (myXmlFile == null || !lastModifiedMap.equals(myLastModifiedMap)) {
        myXmlFile = parseManifest(primaryManifestFile, flavorAndBuildTypeManifests, libraryManifests);
        if (myXmlFile == null) {
          return false;
        }
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
      List<VirtualFile> flavorAndBuildTypeManifests = new ArrayList<VirtualFile>();
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
      AndroidGradleModel androidGradleModel = AndroidGradleModel.get(facet);
      if (androidGradleModel != null) {
        Collection<AndroidLibrary> libraries = androidGradleModel.getSelectedMainCompileDependencies().getLibraries();
        Set<File> set = new HashSet<>();
        for (AndroidLibrary dependency : libraries) {
          addAarManifests(dependency, set);
        }
        for (File file : set) {
          VirtualFile libraryManifest = VfsUtil.findFileByIoFile(file, false);
          if (libraryManifest != null) { // some sort of user error, they don't have a manifest for a lib
            libraryManifests.add(libraryManifest);
          }
          else {
            Logger.getInstance(ManifestInfo.class).warn("Manifest not found: " + file);
          }
        }
      }

      List<AndroidFacet> dependencies = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
      for (AndroidFacet dependency : dependencies) {
        // we will NOT actually be reading from this file, as we will need to recursively get the info from the modules MergedManifest
        VirtualFile vFile = dependency.getMainIdeaSourceProvider().getManifestFile();
        if (vFile != null) { // user error
          libraryManifests.add(vFile);
        }
        else {
          Logger.getInstance(ManifestInfo.class).warn("Manifest not found for Module " + dependency.getName());
        }
      }
      return libraryManifests;
    }

    private static void addAarManifests(@NotNull AndroidLibrary lib, @NotNull Set<File> result) {
      // we check getProject == null to make sure we don't add a lib that's already a module dependancy
      if (lib.getProject() == null && !result.contains(lib.getManifest())) {
        result.add(lib.getManifest());
        for (AndroidLibrary dependency : lib.getLibraryDependencies()) {
          addAarManifests(dependency, result);
        }
      }
    }

    private long getFileModificationStamp(@NotNull VirtualFile file) {
      PsiFile psiFile = PsiManager.getInstance(myFacet.getModule().getProject()).findFile(file);
      return psiFile == null ? file.getModificationStamp() : psiFile.getModificationStamp();
    }

    @Nullable
    public synchronized XmlFile getXmlFile() {
      return myXmlFile;
    }

    @NotNull
    public ImmutableList<MergingReport.Record> getLoggingRecords() {
      return myLoggingRecords == null ? ImmutableList.<MergingReport.Record>of() : myLoggingRecords;
    }

    @Nullable
    public Actions getActions() {
      return myActions;
    }
  }
}
