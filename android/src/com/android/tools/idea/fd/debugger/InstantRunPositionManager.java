/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.fd.debugger;

import com.android.SdkConstants;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class InstantRunPositionManager extends PositionManagerImpl {
  private static Logger LOG = Logger.getInstance(InstantRunPositionManager.class);

  private Map<AndroidVersion, VirtualFile> mySourceFoldersByApiLevel;

  public InstantRunPositionManager(DebugProcessImpl debugProcess) {
    super(debugProcess);
  }

  /**
   * Returns the PSI file corresponding to the given JDI location.
   * This method is overridden so that we may provide an API specific version of the sources for platform (android.jar) classes.
   *
   * TODO: This method is implemented as part of InstantRunPositionManager, although it has nothing to do with instant run.
   * The issue is that the position manager extension API is a little odd: the position manager that was added last is the one that gets
   * used, and luckily for us, the instant run position manager is being added last. Ideally, we should move just the code that identifies
   * the Psi File given a location to a separate extension mechanism. See b.android.com/208140
   */
  @Nullable
  @Override
  protected PsiFile getPsiFileByLocation(Project project, Location location) {
    PsiFile file = super.getPsiFileByLocation(project, location);
    if (file == null) {
      return null;
    }

    if (!DebuggerSettings.getInstance().SHOW_ALTERNATIVE_SOURCE) {
      return file;
    }

    AndroidVersion version = getAndroidVersionFromDebugSession(project);
    if (version == null) {
      LOG.debug("getPsiFileByLocation returned null because cannot determine version from device.");
      return file;
    }

    PsiFile source = getApiSpecificPsi(project, file, version);
    return source == null ? file : source;
  }

  @Nullable
  protected PsiFile getApiSpecificPsi(@NotNull Project project, @NotNull PsiFile file, @NotNull AndroidVersion version) {
    // we only care about providing an alternate source for files inside the platform.
    if (!AndroidSdks.getInstance().isInAndroidSdk(file)) {
      return null;
    }

    String relPath = getRelPathFromSourceRoot(project, file);
    if (relPath == null) {
      LOG.debug("getApiSpecificPsi returned null because relPath is null for file: " + file.getName());
      return null;
    }

    return getSourceForApiLevel(project, version, relPath);
  }

  @Nullable
  private static AndroidVersion getAndroidVersionFromDebugSession(@NotNull Project project) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) {
      return null;
    }

    return session.getDebugProcess().getProcessHandler().getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL);
  }

  @Nullable
  private PsiFile getSourceForApiLevel(@NotNull Project project, @NotNull AndroidVersion version, @NotNull String relPath) {
    VirtualFile sourceFolder = getSourceFolder(version);
    if (sourceFolder == null) {
      // TODO: could we offer to download sources?
      return null;
    }

    VirtualFile vfile = sourceFolder.findFileByRelativePath(relPath);
    if (vfile == null) {
      LOG.debug("getSourceForApiLevel returned null because " + relPath + " is not present in " + sourceFolder);
      return null;
    }
    return PsiManager.getInstance(project).findFile(vfile);
  }

  @Nullable
  private VirtualFile getSourceFolder(@NotNull AndroidVersion version) {
    if (mySourceFoldersByApiLevel == null) {
      mySourceFoldersByApiLevel = createSourcesByApiLevel();

      // log if requested version is missing, but only do it once per debug session to avoid spamming logs
      if (mySourceFoldersByApiLevel.get(version) == null) {
        LOG.debug("getSourceFolder returned null for version: " + version);
      }
    }

    return mySourceFoldersByApiLevel.get(version);
  }

  private static Map<AndroidVersion, VirtualFile> createSourcesByApiLevel() {
    Collection<? extends LocalPackage> sourcePackages = getAllPlatformSourcePackages();
    Map<AndroidVersion, VirtualFile> sourcesByApi = Maps.newHashMap();
    for (LocalPackage sourcePackage : sourcePackages) {
      TypeDetails typeDetails = sourcePackage.getTypeDetails();
      if (!(typeDetails instanceof DetailsTypes.ApiDetailsType)) {
        LOG.warn("Unable to get type details for source package @ " + sourcePackage.getLocation().getPath());
        continue;
      }

      DetailsTypes.ApiDetailsType details = (DetailsTypes.ApiDetailsType)typeDetails;
      AndroidVersion version = details.getAndroidVersion();
      VirtualFile sourceFolder = VfsUtil.findFileByIoFile(sourcePackage.getLocation(), false);
      if (sourceFolder != null && sourceFolder.isValid()) {
        sourcesByApi.put(version, sourceFolder);
      }
    }

    return ImmutableMap.copyOf(sourcesByApi);
  }

  private static Collection<? extends LocalPackage> getAllPlatformSourcePackages() {
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    RepoManager sdkManager =
      sdkHandler.getSdkManager(new StudioLoggerProgressIndicator(InstantRunPositionManager.class));
    return sdkManager.getPackages().getLocalPackagesForPrefix(SdkConstants.FD_ANDROID_SOURCES);
  }

  @Nullable
  private static String getRelPathFromSourceRoot(@NotNull Project project, @NotNull PsiFile file) {
    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    VirtualFile sourceRoot = fileIndex.getSourceRootForFile(file.getVirtualFile());
    if (sourceRoot == null) {
      LOG.debug("Could not determine source root for file: " + file.getVirtualFile().getPath());
      return null;
    }

    return VfsUtilCore.getRelativePath(file.getVirtualFile(), sourceRoot);
  }
}