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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.ide.common.repository.GoogleMavenRepository.MAVEN_GOOGLE_CACHE_DIR_KEY;
import static com.android.tools.lint.checks.GooglePlaySdkIndex.GOOGLE_PLAY_SDK_INDEX_KEY;

import com.android.annotations.NonNull;
import com.android.ide.common.gradle.Version;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.util.PathString;
import com.android.manifmerger.Actions;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.RemotePackage;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.editors.manifest.ManifestUtils;
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository;
import com.android.tools.idea.lint.common.LintIdeClient;
import com.android.tools.idea.lint.common.LintResult;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndexKt;
import com.android.tools.idea.res.FileResourceReader;
import com.android.tools.idea.res.FrameworkResourceRepositoryManager;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.lint.client.api.PlatformLookup;
import com.android.tools.lint.client.api.ResourceRepositoryScope;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Desugaring;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.utils.Pair;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlpull.v1.XmlPullParser;

/**
 * Android specific implementation of {@linkplain LintIdeClient}
 */
public class AndroidLintIdeClient extends LintIdeClient {
  protected static final Logger LOG = Logger.getInstance("#com.android.tools.idea.lint.AndroidLintIdeClient");

  @NonNull protected Project myProject;

  public AndroidLintIdeClient(@NonNull Project project, @NotNull LintResult lintResult) {
    super(project, lintResult);
    myProject = project;
  }

  @Nullable
  @Override
  public Version getHighestKnownVersion(@NonNull GradleCoordinate coordinate, @Nullable Predicate<Version> filter) {
    AndroidSdkHandler sdkHandler = getSdk();
    if (sdkHandler == null) {
      return null;
    }
    StudioLoggerProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
    RemotePackage sdkPackage = SdkMavenRepository.findLatestRemoteVersion(coordinate, sdkHandler, filter, logger);
    if (sdkPackage != null) {
      GradleCoordinate found = SdkMavenRepository.getCoordinateFromSdkPath(sdkPackage.getPath());
      if (found != null) {
        return found.getLowerBoundVersion();
      }
    }

    return null;
  }

  @Override
  @NotNull
  public byte[] readBytes(@NotNull PathString resourcePath) throws IOException {
    ProgressManager.checkCanceled();
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
  private PlatformLookup platformLookup = null;

  @Nullable
  @Override
  public PlatformLookup getPlatformLookup() {
    if (platformLookup == null) {
      AndroidSdkHandler handler = getSdk();
      if (handler != null) {
        StudioLoggerProgressIndicator logger = new StudioLoggerProgressIndicator(AndroidLintIdeClient.class);
        platformLookup = new SdkManagerPlatformLookup(handler, logger);
      } else {
        platformLookup = super.getPlatformLookup();
      }
    }
    return platformLookup;
  }

  @Nullable
  protected AndroidSdkHandler getSdk() {
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
          File sdkHome = getSdkHome();
          if (sdkHome != null) {
            sdk = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, sdkHome.toPath());
          }
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
  public boolean isGradleProject(@NotNull com.android.tools.lint.detector.api.Project project) {
    Module module = getModule(project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && AndroidModel.isRequired(facet);
    }
    return ProjectSystemUtil.requiresAndroidModel(myProject);
  }

  @Nullable
  @Override
  public File getCacheDir(@Nullable String name, boolean create) {
    if (MAVEN_GOOGLE_CACHE_DIR_KEY.equals(name)) {
      // Share network cache with existing implementation
      Path cacheDir = IdeGoogleMavenRepository.INSTANCE.getCacheDir();
      return cacheDir == null ? null : cacheDir.toFile();
    }

    if (GOOGLE_PLAY_SDK_INDEX_KEY.equals(name)) {
      Path cacheDir = IdeGooglePlaySdkIndexKt.getCacheDir();
      return cacheDir == null ? null : cacheDir.toFile();
    }

    return super.getCacheDir(name, create);
  }

  private static final String MERGED_MANIFEST_INFO = "lint-merged-manifest-info";

  @Nullable
  @Override
  public org.w3c.dom.Document getMergedManifest(@NonNull com.android.tools.lint.detector.api.Project project) {
    final Module module = findModuleForLintProject(myProject, project);
    if (module != null) {
      MergedManifestSnapshot mergedManifest = MergedManifestManager.getFreshSnapshot(module);
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

    // Fallback if there's no manifest merger record for this; for example,
    // it's common for the manifest merger to not have records for the <application> tag
    if (source == NOT_FOUND && mergedNode.getNodeType() == Node.ELEMENT_NODE) {
      for (Actions.Record record : records) {
        File file = record.getActionLocation().getFile().getSourceFile();
        if (file != null) {
          try {
            Document document = getXmlParser().parseXml(file);
            if (document != null) {
              Node sourceNode = Lint.matchXmlElement((Element)mergedNode, document);
              if (sourceNode != null) {
                source = Pair.of(file, sourceNode);
                break;
              }
            }
          }
          catch (Throwable e) {
            log(e, "Can't parse %1$s", file);
          }
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
  public Iterable<File> findRuleJars(@NotNull com.android.tools.lint.detector.api.Project project) {
    Module module = getModule(project);
    if (module != null) {
      AndroidModel model = AndroidModel.get(module);
      if (model != null) {
        Iterable<File> lintRuleJars = model.getLintRuleJarsOverride();
        if (lintRuleJars != null) {
          return lintRuleJars;
        }
      }
    }
    return super.findRuleJars(project);
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
    IdeaSourceProvider sourceProvider = SourceProviderManager.getInstance(facet).getSources();
    for (VirtualFile resDirectory : sourceProvider.getResDirectories()) {
      resDirectories.add(VfsUtilCore.virtualToIoFile(resDirectory));
    }
    return resDirectories;
  }


  @NonNull
  @Override
  public ResourceRepository getResources(@NonNull com.android.tools.lint.detector.api.Project project,
                                         @NonNull ResourceRepositoryScope scope) {
    final Module module = findModuleForLintProject(myProject, project);
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        if (scope == ResourceRepositoryScope.ANDROID) {
          IAndroidTarget target = project.getBuildTarget();
          if (target != null) {
            return FrameworkResourceRepositoryManager.getInstance().getFrameworkResources(
              target.getPath(IAndroidTarget.RESOURCES),
              // TBD: Do we need to get the framework resources to provide all languages?
              false, Collections.emptySet());
          }  else {
            return super.getResources(project, scope); // can't find framework: empty repository
          }
        } else if (scope.includesLibraries()) {
          return ResourceRepositoryManager.getAppResources(facet);
        }
        else if (scope.includesDependencies()) {
          return ResourceRepositoryManager.getProjectResources(facet);
        }
        else {
          return ResourceRepositoryManager.getModuleResources(facet);
        }
      }
    }

    return super.getResources(project, scope);
  }

  @Override
  @NonNull
  public Location.ResourceItemHandle createResourceItemHandle(@NonNull ResourceItem item, boolean nameOnly, boolean valueOnly) {
    XmlTag tag = IdeResourcesUtil.getItemTag(myProject, item);
    if (tag != null) {
      PathString source = item.getSource();
      assert source != null : item;
      File file = source.toFile();
      assert file != null : item;
      return new LocationHandle(file, item, tag, nameOnly, valueOnly);
    }
    return super.createResourceItemHandle(item, nameOnly, valueOnly);
  }

  @Override
  @Nullable
  public XmlPullParser createXmlPullParser(@NotNull PathString resourcePath) throws IOException {
    ProgressManager.checkCanceled();
    return FileResourceReader.createXmlPullParser(resourcePath);
  }

  private class LocationHandle extends Location.ResourceItemHandle
      implements Location.Handle, Computable<Location> {
    private final File myFile;
    private final XmlElement myNode;
    private final boolean myNameOnly;
    private final boolean myValueOnly;
    private Object myClientData;

    LocationHandle(File file, ResourceItem item, XmlElement node, boolean nameOnly, boolean valueOnly) {
      super(AndroidLintIdeClient.this, item, nameOnly, valueOnly);
      myNameOnly = nameOnly;
      myValueOnly = valueOnly;
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
        XmlTag element = (XmlTag)myNode;
        if (myNameOnly) {
          XmlAttribute attribute = element.getAttribute(ATTR_NAME);
          if (attribute != null) {
            textRange = attribute.getValueTextRange();
          }
        } else if (myValueOnly) {
          XmlTagValue value = element.getValue();
          textRange = value.getTextRange();
        } else {
          String tag = element.getName();
          int index = myNode.getText().indexOf(tag);
          if (index != -1) {
            int start = textRange.getStartOffset() + index;
            textRange = new TextRange(start, start + tag.length());
          }
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
