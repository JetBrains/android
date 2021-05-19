/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import static com.android.ide.common.repository.GoogleMavenRepository.MAVEN_GOOGLE_CACHE_DIR_KEY;
import static com.android.tools.lint.checks.DeprecatedSdkRegistryKt.DEPRECATED_SDK_CACHE_DIR_KEY;

import com.android.annotations.NonNull;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.util.PathString;
import com.android.manifmerger.Actions;
import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.diagnostics.crash.GenericStudioReport;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.android.tools.idea.editors.manifest.ManifestUtils;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository;
import com.android.tools.idea.lint.common.LintIdeClient;
import com.android.tools.idea.lint.common.LintResult;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.android.tools.idea.res.FileResourceReader;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Desugaring;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.utils.Pair;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlpull.v1.XmlPullParser;

/**
 * Android specific implementation of {@linkplain LintIdeClient}
 */
public class AndroidLintIdeClient extends LintIdeClient {
  protected static final Logger LOG = Logger.getInstance(AndroidLintIdeClient.class);

  @NonNull protected Project myProject;

  public AndroidLintIdeClient(@NonNull Project project, @NotNull LintResult lintResult) {
    super(project, lintResult);
    myProject = project;
  }

  @Nullable
  @Override
  public GradleVersion getHighestKnownVersion(@NonNull GradleCoordinate coordinate,
                                              @Nullable Predicate<GradleVersion> filter) {
    AndroidSdkHandler sdkHandler = getSdk();
    if (sdkHandler == null) {
      return null;
    }
    StudioLoggerProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
    RemotePackage sdkPackage = SdkMavenRepository.findLatestRemoteVersion(coordinate, sdkHandler, filter, logger);
    if (sdkPackage != null) {
      GradleCoordinate found = SdkMavenRepository.getCoordinateFromSdkPath(sdkPackage.getPath());
      if (found != null) {
        return found.getVersion();
      }
    }

    return null;
  }

  @Override
  @NotNull
  public byte[] readBytes(@NotNull PathString resourcePath) throws IOException {
    return FileResourceReader.readBytes(resourcePath);
  }

  @Nullable
  @Override
  public File getSdkHome() {
    Module module = getModule();
    if (module != null) {
      Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
      if (moduleSdk != null && moduleSdk.getSdkType() instanceof AndroidSdkType) {
        String path = moduleSdk.getHomePath();
        if (path != null) {
          File home = new File(path);
          if (home.exists()) {
            return home;
          }
        }
      }
    }

    File sdkHome = super.getSdkHome();
    if (sdkHome != null) {
      return sdkHome;
    }

    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      Sdk moduleSdk = ModuleRootManager.getInstance(m).getSdk();
      if (moduleSdk != null) {
        if (moduleSdk.getSdkType() instanceof AndroidSdkType) {
          String path = moduleSdk.getHomePath();
          if (path != null) {
            File home = new File(path);
            if (home.exists()) {
              return home;
            }
          }
        }
      }
    }

    return IdeSdks.getInstance().getAndroidSdkPath();
  }

  private AndroidSdkHandler sdk = null;

  @Nullable
  @Override
  public AndroidSdkHandler getSdk() {
    if (sdk == null) {
      Module module = getModule();
      AndroidSdkHandler localSdk = getLocalSdk(module);
      if (localSdk != null) {
        sdk = localSdk;
      }
      else {
        for (Module m : ModuleManager.getInstance(myProject).getModules()) {
          localSdk = getLocalSdk(m);
          if (localSdk != null) {
            sdk = localSdk;
            break;
          }
        }

        if (localSdk == null) {
          sdk = super.getSdk();
        }
      }
    }

    return sdk;
  }

  @Nullable
  private static AndroidSdkHandler getLocalSdk(@Nullable Module module) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidSdkData sdkData = AndroidSdkData.getSdkData(facet);
        if (sdkData != null) {
          return sdkData.getSdkHandler();
        }
      }
    }

    return null;
  }

  @Override
  @Nullable
  public Revision getBuildToolsRevision(@NonNull com.android.tools.lint.detector.api.Project project) {
    if (project.isGradleProject()) {
      Module module = getModule(project);
      if (module != null) {
        AndroidModuleModel model = AndroidModuleModel.get(module);
        if (model != null) {
          GradleVersion version = model.getModelVersion();
          if (version != null && version.isAtLeast(2, 1, 0)) {
            String buildToolsVersion = model.getAndroidProject().getBuildToolsVersion();
            if (buildToolsVersion != null) {
              AndroidSdkHandler sdk = getSdk();
              if (sdk != null) {
                try {
                  Revision revision = Revision.parseRevision(buildToolsVersion);
                  BuildToolInfo buildToolInfo = sdk.getBuildToolInfo(revision, getRepositoryLogger());
                  if (buildToolInfo != null) {
                    return buildToolInfo.getRevision();
                  }
                }
                catch (NumberFormatException ignore) {
                  // Fall through and use the latest
                }
              }
            }
          }
        }
      }
    }

    return super.getBuildToolsRevision(project);
  }

  @Override
  public boolean isGradleProject(@NotNull com.android.tools.lint.detector.api.Project project) {
    Module module = getModule(project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && AndroidModel.isRequired(facet);
    }
    return AndroidProjectInfo.getInstance(myProject).requiresAndroidModel();
  }

  @Nullable
  @Override
  public File getCacheDir(@Nullable String name, boolean create) {
    if (MAVEN_GOOGLE_CACHE_DIR_KEY.equals(name)) {
      // Share network cache with existing implementation
      return IdeGoogleMavenRepository.INSTANCE.getCacheDir();
    }

    if (DEPRECATED_SDK_CACHE_DIR_KEY.equals(name)) {
      // Share network cache with existing implementation
      return IdeDeprecatedSdkRegistry.INSTANCE.getCacheDir();
    }

    return super.getCacheDir(name, create);
  }

  private static final String MERGED_MANIFEST_INFO = "lint-merged-manifest-info";

  @Nullable
  @Override
  public org.w3c.dom.Document getMergedManifest(@NonNull com.android.tools.lint.detector.api.Project project) {
    final Module module = findModuleForLintProject(myProject, project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        MergedManifestSnapshot mergedManifest = MergedManifestManager.getSnapshot(facet);
        org.w3c.dom.Document document = mergedManifest.getDocument();
        if (document != null) {
          Element root = document.getDocumentElement();
          if (root != null && !isMergeManifestNode(root)) {
            resolveMergeManifestSources(document, project.getDir());
            document.setUserData(MERGED_MANIFEST_INFO, mergedManifest, null);
          }
          return document;
        }
      }
    }

    return null;
  }

  @Override
  @Nullable
  public Pair<File, ? extends Node> findManifestSourceNode(@NonNull Node mergedNode) {
    Map<Node, Pair<File, ? extends Node>> sourceNodeCache = getSourceNodeCache();
    Pair<File, ? extends Node> source = sourceNodeCache.get(mergedNode);
    if (source != null) {
      if (source == NOT_FOUND) {
        return null;
      }
      return source;
    }

    org.w3c.dom.Document doc = mergedNode.getOwnerDocument();
    if (doc == null) {
      return null;
    }
    MergedManifestSnapshot mergedManifest = (MergedManifestSnapshot)doc.getUserData(MERGED_MANIFEST_INFO);
    if (mergedManifest == null) {
      return null;
    }

    source = NOT_FOUND;
    List<? extends Actions.Record> records = ManifestUtils.getRecords(mergedManifest, mergedNode);
    for (Actions.Record record : records) {
      if (record.getActionType() == Actions.ActionType.ADDED ||
          record.getActionType() == Actions.ActionType.MERGED) {
        Node sourceNode = ManifestUtils.getSourceNode(mergedManifest.getModule(), record);
        if (sourceNode != null) {
          // Cache for next time
          File file = record.getActionLocation().getFile().getSourceFile();
          source = Pair.of(file, sourceNode);
          break;
        }
      }
    }

    sourceNodeCache.put(mergedNode, source);

    return source != NOT_FOUND ? source : null;
  }

  @NotNull
  @Override
  public Set<Desugaring> getDesugaring(@NotNull com.android.tools.lint.detector.api.Project project) {
    Module module = getModule(project);
    if (module == null) {
      return Desugaring.DEFAULT;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return Desugaring.DEFAULT;
    }

    AndroidModel model = AndroidModel.get(module);
    if (model == null) {
      return Desugaring.DEFAULT;
    }

    return model.getDesugaring();
  }

  @NonNull
  @Override
  public List<File> getResourceFolders(@NonNull com.android.tools.lint.detector.api.Project project) {
    Module module = getModule(project);
    if (module == null) {
      return super.getResourceFolders(project);
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      return getResourceFolders(facet);
    }

    return super.getResourceFolders(project);
  }

  /**
   * Returns the resource directories to use for the given module
   */
  @NotNull
  public static List<File> getResourceFolders(@NotNull AndroidFacet facet) {
    List<File> resDirectories = new ArrayList<>();
    for (IdeaSourceProvider sourceProvider : SourceProviderManager.getInstance(facet).getCurrentSourceProviders()) {
      for (VirtualFile resDirectory : sourceProvider.getResDirectories()) {
        resDirectories.add(VfsUtilCore.virtualToIoFile(resDirectory));
      }
    }
    return resDirectories;
  }


  @Nullable
  @Override
  public ResourceRepository getResourceRepository(@NotNull com.android.tools.lint.detector.api.Project project,
                                                  boolean includeModuleDependencies,
                                                  boolean includeLibraries) {
    final Module module = findModuleForLintProject(myProject, project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        if (includeLibraries) {
          return ResourceRepositoryManager.getAppResources(facet);
        }
        else if (includeModuleDependencies) {
          return ResourceRepositoryManager.getProjectResources(facet);
        }
        else {
          return ResourceRepositoryManager.getModuleResources(facet);
        }
      }
    }

    return null;
  }

  @Override
  @NonNull
  public Location.Handle createResourceItemHandle(@NonNull ResourceItem item) {
    XmlTag tag = IdeResourcesUtil.getItemTag(myProject, item);
    if (tag != null) {
      PathString source = item.getSource();
      assert source != null : item;
      File file = source.toFile();
      assert file != null : item;
      return new LocationHandle(file, tag);
    }
    return super.createResourceItemHandle(item);
  }

  @NonNull
  @Override
  public ResourceVisibilityLookup.Provider getResourceVisibilityProvider() {
    Module module = getModule();
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        ResourceRepositoryManager repoManager = ResourceRepositoryManager.getInstance(facet);
        ResourceVisibilityLookup.Provider provider = repoManager.getResourceVisibilityProvider();
        if (provider != null) {
          return provider;
        }
      }
    }
    return super.getResourceVisibilityProvider();
  }

  @Override
  @Nullable
  public XmlPullParser createXmlPullParser(@NotNull PathString resourcePath) throws IOException {
    return FileResourceReader.createXmlPullParser(resourcePath);
  }

  @Override
  protected void notifyReadCanceled(StackTraceElement[] stackDumpRaw, long cancelTimeMs, long actionTimeMs) {
    StringBuilder sb = new StringBuilder();
    for (StackTraceElement e : stackDumpRaw) {
      sb.append(e.toString());
      sb.append("\n");
    }
    String stackDump = sb.toString();

    StudioCrashReporter.getInstance().submit(
      new GenericStudioReport.Builder("LintReadActionDelay")
        .addDataNoPii("summary",
                      "Android Lint either took too long to run a read action (" + actionTimeMs + "ms),\n" +
                      "or took too long to cancel and yield to a pending write action (" + cancelTimeMs + "ms)")
        .addDataNoPii("timeToCancelMs", String.valueOf(cancelTimeMs))
        .addDataNoPii("readActionTimeMs", String.valueOf(actionTimeMs))
        .addDataNoPii("stackDump", stackDump)
        .build()
    );
  }

  private static class LocationHandle implements Location.Handle, Computable<Location> {
    private final File myFile;
    private final XmlElement myNode;
    private Object myClientData;

    LocationHandle(File file, XmlElement node) {
      myFile = file;
      myNode = node;
    }

    @NonNull
    @Override
    public Location resolve() {
      if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
        return ApplicationManager.getApplication().runReadAction(this);
      }
      TextRange textRange = myNode.getTextRange();

      // For elements, don't highlight the entire element range; instead, just
      // highlight the element name
      if (myNode instanceof XmlTag) {
        String tag = ((XmlTag)myNode).getName();
        int index = myNode.getText().indexOf(tag);
        if (index != -1) {
          int start = textRange.getStartOffset() + index;
          textRange = new TextRange(start, start + tag.length());
        }
      }

      Position start = new DefaultPosition(-1, -1, textRange.getStartOffset());
      Position end = new DefaultPosition(-1, -1, textRange.getEndOffset());
      return Location.create(myFile, start, end);
    }

    @Override
    public Location compute() {
      return resolve();
    }

    @Override
    public void setClientData(@Nullable Object clientData) {
      myClientData = clientData;
    }

    @Override
    @Nullable
    public Object getClientData() {
      return myClientData;
    }
  }
}
