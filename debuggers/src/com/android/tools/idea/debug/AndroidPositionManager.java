/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.debug;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.FD_ANDROID_SOURCES;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.PlatformTarget;
import com.android.tools.idea.editors.AttachAndroidSdkSourcesNotificationProvider;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AndroidPositionManager provides android java specific position manager augmentations on top of
 * {@link PositionManagerImpl} such as:
 * <ul>
 * <li>Providing synthesized classes during android build.</li>
 * <li>Locating SDK sources that match the user's current target device.</li>
 * </ul>
 * Unlike {@link PositionManagerImpl}, {@link AndroidPositionManager} is not a cover-all position
 * manager and should fallback to other position managers if it encounters a situation it cannot
 * handle.
 */
public class AndroidPositionManager extends PositionManagerImpl {
  @Language("JAVA")
  private static final String GENERATED_FILE_CONTENTS_FORMAT =
    Joiner.on(System.lineSeparator()).join(
      "/*********************************************************************",
      " * The Android SDK of the device under debug has API level %d.",
      " * Android SDK source code for this API level cannot be found.",
      " ********************************************************************/");

  private static final String GENERATED_FILE_NAME = "Unavailable Source";

  private static Logger LOG = Logger.getInstance(AndroidPositionManager.class);

  private final DebugProcessImpl myDebugProcess;

  private final Supplier<Map<AndroidVersion, VirtualFile>> mySourceFoldersByApiLevel =
    Suppliers.memoize(AndroidPositionManager::createSourcePackagesByApiLevel);

  private final Supplier<PsiFile> myGeneratedPsiFile = Suppliers.memoize(this::createGeneratedPsiFile);

  private boolean debugSessionListenerRegistered = false;

  public AndroidPositionManager(DebugProcessImpl debugProcess) {
    super(debugProcess);
    this.myDebugProcess = debugProcess;
  }

  @NotNull
  @Override
  public List<ReferenceType> getAllClasses(@NotNull SourcePosition position) throws NoDataException {
    // For desugaring, we also need to add the extra synthesized classes that may contain the source position.
    List<ReferenceType> referenceTypes =
      DesugarUtils.addExtraClassesIfNeeded(myDebugProcess, position, super.getAllClasses(position), this);
    if (referenceTypes.isEmpty()) {
      throw NoDataException.INSTANCE;
    }
    return referenceTypes;
  }

  @NotNull
  @Override
  public List<ClassPrepareRequest> createPrepareRequests(@NotNull ClassPrepareRequestor requestor,
                                                         @NotNull SourcePosition position) throws NoDataException {
    // For desugaring, we also need to add prepare requests for the extra synthesized classes that may contain the source position.
    List<ClassPrepareRequest> requests =
      DesugarUtils.addExtraPrepareRequestsIfNeeded(myDebugProcess, requestor, position, super.createPrepareRequests(requestor, position));
    if (requests.isEmpty()) {
      throw NoDataException.INSTANCE;
    }
    return requests;
  }

  @Nullable
  @Override
  public Set<? extends FileType> getAcceptedFileTypes() {
    // When setting breakpoints or debugging into SDK source, the Location's sourceName() method
    // returns a string of the form "FileName.java"; this resolves into a JavaFileType.
    return ImmutableSet.of(JavaFileType.INSTANCE);
  }

  @Override
  @Nullable
  public SourcePosition getSourcePosition(final Location location) throws NoDataException {
    if (location == null) {
      throw NoDataException.INSTANCE;
    }

    Project project = myDebugProcess.getProject();
    AndroidVersion version = getAndroidVersionFromDebugSession(project);
    if (version == null) {
      LOG.debug("getSourcePosition cannot determine version from device.");
      throw NoDataException.INSTANCE;
    }

    PsiFile file = getPsiFileByLocation(project, location);
    if (file == null || !AndroidSdks.getInstance().isInAndroidSdk(file)) {
      throw NoDataException.INSTANCE;
    }

    // Since we have an Android SDK file, return the SDK source if it's available.
    SourcePosition sourcePosition = getSourceFileForApiLevel(project, version, file, location);
    if (sourcePosition != null) {
      return sourcePosition;
    }

    // Otherwise, return a generated file with a comment indicating that sources are unavailable.
    return getGeneratedFileSourcePosition(project);
  }

  @Override
  @VisibleForTesting
  protected PsiFile getPsiFileByLocation(final Project project, final Location location) {
    return super.getPsiFileByLocation(project, location);
  }

  @Nullable
  @VisibleForTesting
  static AndroidVersion getAndroidVersionFromDebugSession(@NotNull Project project) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) {
      return null;
    }

    return session.getDebugProcess().getProcessHandler().getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL);
  }

  @Nullable
  private SourcePosition getSourceFileForApiLevel(
    @NotNull Project project, @NotNull AndroidVersion version, @NotNull PsiFile file, @NotNull Location location) {
    String relPath = getRelPathForJavaSource(project, file);
    if (relPath == null) {
      LOG.debug("getApiSpecificPsi returned null because relPath is null for file: " + file.getName());
      return null;
    }

    VirtualFile sourceFolder = mySourceFoldersByApiLevel.get().get(version);
    if (sourceFolder == null) {
      return null;
    }

    VirtualFile vfile = sourceFolder.findFileByRelativePath(relPath);
    if (vfile == null) {
      LOG.debug("getSourceForApiLevel returned null because " + relPath + " is not present in " + sourceFolder);
      return null;
    }

    PsiFile apiSpecificSourceFile = PsiManager.getInstance(project).findFile(vfile);
    if (apiSpecificSourceFile == null) {
      return null;
    }

    int lineNumber = DebuggerUtilsEx.getLineNumber(location, true);
    return SourcePosition.createFromLine(apiSpecificSourceFile, lineNumber);
  }

  @NotNull
  private SourcePosition getGeneratedFileSourcePosition(@NotNull Project project) {
    PsiFile generatedPsiFile = myGeneratedPsiFile.get();

    // If we don't already have one, create a new listener that will close the generated files after the debugging session completes.
    // Since this method is always called on DebuggerManagerThreadImpl, there's no concern around locking here.
    if (!debugSessionListenerRegistered) {
      debugSessionListenerRegistered = true;
      myDebugProcess.getSession().getXDebugSession()
        .addSessionListener(new MyXDebugSessionListener(generatedPsiFile.getVirtualFile(), project));
    }

    return SourcePosition.createFromLine(generatedPsiFile, -1);
  }

  @NotNull
  private PsiFile createGeneratedPsiFile() {
    Project project = myDebugProcess.getProject();
    AndroidVersion version = getAndroidVersionFromDebugSession(project);
    int apiLevel = version.getApiLevel();
    String fileContent = String.format(Locale.getDefault(), GENERATED_FILE_CONTENTS_FORMAT, apiLevel);

    PsiFile generatedPsiFile =
      PsiFileFactory.getInstance(project).createFileFromText(GENERATED_FILE_NAME, JavaLanguage.INSTANCE, fileContent, true, true);

    VirtualFile generatedVirtualFile = generatedPsiFile.getVirtualFile();
    try {
      generatedVirtualFile.setWritable(false);
    }
    catch (IOException e) {
      // Swallow. This isn't expected; but if it happens and the user can for some reason edit this file, it won't make any difference.
      LOG.info("Unable to set generated file not writable.", e);
    }

    // Add data indicating that we want to put up a banner offering to download sources.
    List<AndroidVersion> requiredSourceDownload = ImmutableList.of(version);
    generatedVirtualFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, requiredSourceDownload);

    return generatedPsiFile;
  }

  @NotNull
  private static Map<AndroidVersion, VirtualFile> createSourcePackagesByApiLevel() {
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    RepoManager sdkManager =
      sdkHandler.getSdkManager(new StudioLoggerProgressIndicator(AndroidPositionManager.class));
    Collection<LocalPackage> sourcePackages = sdkManager.getPackages().getLocalPackagesForPrefix(FD_ANDROID_SOURCES);

    Map<AndroidVersion, VirtualFile> sourcesByApi = Maps.newHashMap();
    for (LocalPackage sourcePackage : sourcePackages) {
      TypeDetails typeDetails = sourcePackage.getTypeDetails();
      if (!(typeDetails instanceof DetailsTypes.ApiDetailsType)) {
        LOG.warn("Unable to get type details for source package @ " + sourcePackage.getLocation());
        continue;
      }

      DetailsTypes.ApiDetailsType details = (DetailsTypes.ApiDetailsType)typeDetails;
      AndroidVersion version = details.getAndroidVersion();

      VirtualFile sourceFolder = VfsUtil.findFile(sourcePackage.getLocation(), false);
      if (sourceFolder != null && sourceFolder.isValid()) {
        sourcesByApi.put(version, sourceFolder);
      }
    }

    return ImmutableMap.copyOf(sourcesByApi);
  }

  @Nullable
  @VisibleForTesting
  static String getRelPathForJavaSource(@NotNull Project project, @NotNull PsiFile file) {
    FileType fileType = file.getFileType();
    if (fileType == JavaFileType.INSTANCE) {
      // When the compile SDK sources are present, they are indexed and the incoming PsiFile is a JavaFileType that refers to them. The
      // relative path for the same file in target SDK sources can be directly determined.
      ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
      VirtualFile sourceRoot = fileIndex.getSourceRootForFile(file.getVirtualFile());
      if (sourceRoot == null) {
        LOG.debug("Could not determine source root for file: " + file.getVirtualFile().getPath());
        return null;
      }

      return VfsUtilCore.getRelativePath(file.getVirtualFile(), sourceRoot);
    }
    else if (fileType == JavaClassFileType.INSTANCE) {
      // When the compile SDK sources are not present, the incoming PsiFile is a JavaClassFileType coming from the compile SDK android.jar.
      // We can figure out the relative path to the class file, and make the assumption that the java file will have the same path.
      VirtualFile virtualFile = file.getVirtualFile();
      String relativeClassPath = VfsUtilCore.getRelativePath(virtualFile, VfsUtilCore.getRootFile(virtualFile));

      // The class file should end in ".class", but we're interested in the corresponding java file.
      return changeClassExtensionToJava(relativeClassPath);
    }

    return null;
  }

  @Nullable
  @VisibleForTesting
  static String changeClassExtensionToJava(@Nullable String file) {
    if (file != null && file.endsWith(DOT_CLASS)) {
      return file.substring(0, file.length() - DOT_CLASS.length()) + DOT_JAVA;
    }

    return file;
  }

  /**
   * Listener that's responsible for closing the generated "no sources available" file when a debug session completes.
   */
  @VisibleForTesting
  static class MyXDebugSessionListener implements XDebugSessionListener {
    private final WeakReference<VirtualFile> myFileToClose;
    private final Project myProject;

    @VisibleForTesting
    MyXDebugSessionListener(@NotNull VirtualFile fileToClose, @NotNull Project project) {
      myFileToClose = new WeakReference<>(fileToClose);
      myProject = project;
    }

    @Override
    public void sessionStopped() {
      // When debugging is complete, close the generated file that was opened due to debugging into missing sources.
      VirtualFile file = myFileToClose.get();
      if (file != null) {
        FileEditorManager.getInstance(myProject).closeFile(file);
      }
    }
  }
}
