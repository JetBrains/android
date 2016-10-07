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
package com.android.tools.idea.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidLibrary;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.lint.checks.PermissionHolder;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.lint.checks.PermissionRequirement.ATTR_PROTECTION_LEVEL;
import static com.android.tools.lint.checks.PermissionRequirement.VALUE_DANGEROUS;

/**
 * A database which records (and responds to queries about) which permissions
 * a given module or library depends on.
 */
public class DeclaredPermissionsLookup implements ProjectComponent {
  /** Number of milliseconds we'll wait before checking file stamps again */
  private static final int CACHE_MS = 1000;

  private final Project myProject;

  public DeclaredPermissionsLookup(Project project) {
    myProject = project;
  }

  /** Returns the {@link DeclaredPermissionsLookup} project component */
  public static DeclaredPermissionsLookup getInstance(Project project) {
    return project.getComponent(DeclaredPermissionsLookup.class);
  }

  /** Returns a {@link PermissionHolder} for the given module (and its dependencies) */
  @NonNull
  public static PermissionHolder getPermissionHolder(@NonNull Module module) {
    return getInstance(module.getProject()).getModulePermissions(module);
  }

  /** Resets any cached state */
  public void reset() {
    if (myLibraryPermissions != null) {
      myLibraryPermissions.clear();
    }
    if (myManifestPermissionsMap != null) {
      myModulePermissionsMap.clear();
    }
    if (myManifestPermissionsMap != null) {
      myManifestPermissionsMap.clear();
    }
  }

  private Map<AndroidLibrary, LibraryPermissions> myLibraryPermissions;
  private Map<Module, ModulePermissions> myModulePermissionsMap;
  private Map<VirtualFile, ManifestPermissions> myManifestPermissionsMap;

  @NonNull
  private LibraryPermissions getLibraryPermissions(@NonNull AndroidLibrary library) {
    if (myLibraryPermissions == null) {
      myLibraryPermissions = Maps.newIdentityHashMap();
    }
    LibraryPermissions libraryPermissions = myLibraryPermissions.get(library);
    if (libraryPermissions == null) {
      libraryPermissions = new LibraryPermissions(library);
      myLibraryPermissions.put(library, libraryPermissions);
    }

    return libraryPermissions;
  }

  @NonNull
  private synchronized ModulePermissions getModulePermissions(@NonNull Module module) {
    if (myModulePermissionsMap == null) {
      myModulePermissionsMap = Maps.newIdentityHashMap();
    }
    ModulePermissions modulePermissions = myModulePermissionsMap.get(module);
    if (modulePermissions != null) {
      return modulePermissions;
    }
    modulePermissions = new ModulePermissions(module);
    myModulePermissionsMap.put(module, modulePermissions);

    Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies(false);
    for (Module dependencyModule : dependencies) {
      ModulePermissions dependencyModulePermissions = getModulePermissions(dependencyModule);
      modulePermissions.addDependency(dependencyModulePermissions);
    }
    return modulePermissions;
  }

  private ManifestPermissions getManifestPermissions(VirtualFile manifest) {
    if (myManifestPermissionsMap == null) {
      myManifestPermissionsMap = Maps.newIdentityHashMap();
    }
    ManifestPermissions manifestPermissions = myManifestPermissionsMap.get(manifest);
    if (manifestPermissions == null) {
      manifestPermissions = new ManifestVirtualFilePermissions(myProject, manifest);
      myManifestPermissionsMap.put(manifest, manifestPermissions);
    }

    return manifestPermissions;
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "PermissionsLookup";
  }

  private static class PermissionStrings {
    @NotNull public final Set<String> granted;
    @NotNull public final Set<String> revocable;

    public PermissionStrings(@NotNull Set<String> granted, @NotNull Set<String> revocable) {
      this.granted = granted;
      this.revocable = revocable;
    }
  }

  private abstract static class ManifestPermissions {
    protected long myLastChecked;
    protected long myTimeStamp;

    private PermissionStrings myPermissions;

    public ManifestPermissions() {
    }

    public boolean hasPermission(@NonNull String permission) {
      return getPermissions().granted.contains(permission);
    }

    public boolean isRevocable(@NonNull String permission) {
      return getPermissions().revocable.contains(permission);
    }

    protected PermissionStrings getPermissions() {
      // If it's been more than a second since the last time we looked, check the file timestamps
      long time = System.currentTimeMillis();
      if (myPermissions != null && myLastChecked < time - CACHE_MS) {
        long timeStamp = getTimeStamp();
        if (timeStamp > myTimeStamp) {
          myPermissions = null;
        }
      }
      if (myPermissions == null) {
        myPermissions = readPermissions();
        myTimeStamp = getTimeStamp();
        myLastChecked = System.currentTimeMillis();
      }
      return myPermissions;
    }

    protected abstract PermissionStrings readPermissions();
    protected abstract long getTimeStamp();
  }

  private static class ManifestFilePermissions extends ManifestPermissions {
    private @NonNull final File myFile;

    public ManifestFilePermissions(@NonNull File file) {
      myFile = file;
    }

    @Override
    protected PermissionStrings readPermissions() {
      Set<String> permissions = Sets.newHashSetWithExpectedSize(30);
      Set<String> revocable = Sets.newHashSetWithExpectedSize(2);
      addPermissions(permissions, revocable, myFile);
      myLastChecked = myFile.lastModified();
      return new PermissionStrings(permissions, revocable);
    }

    @Override
    protected long getTimeStamp() {
      return myFile.lastModified();
    }
  }

  private static class ManifestVirtualFilePermissions extends ManifestPermissions implements Computable<PermissionStrings> {
    private @NonNull final Project myProject;
    private @NonNull final VirtualFile myFile;

    public ManifestVirtualFilePermissions(@NonNull Project project, @NonNull VirtualFile file) {
      myFile = file;
      myProject = project;
    }

    @Override
    protected PermissionStrings readPermissions() {
      return ApplicationManager.getApplication().runReadAction(this);
    }

    @Override
    protected long getTimeStamp() {
      return myFile.getModificationStamp();
    }

    /**
     * Computes the result of {@link #readPermissions()} but in a {@link Computable} interface such that
     * it can be directly passed as a read action since it needs PSI access
     */
    @Override
    public PermissionStrings compute() {
      Set<String> permissions = Sets.newHashSetWithExpectedSize(30);
      Set<String> revocable = Sets.newHashSetWithExpectedSize(2);
      // First look for the PSI file and attempt to use it instead (since it will pick up on edited but
      // not yet saved to disk changes)
      PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
      if (file != null) {
        addPermissions(permissions, revocable, file);
      } else {
        addPermissions(permissions, revocable, myFile);
      }
      return new PermissionStrings(permissions, revocable);
    }
  }

  private class LibraryPermissions {
    @NonNull private final AndroidLibrary myLibrary;
    @Nullable private final ManifestFilePermissions myManifest;

    public LibraryPermissions(@NonNull AndroidLibrary library) {
      myLibrary = library;
      File manifest = library.getManifest();
      if (manifest.exists()) {
        myManifest = new ManifestFilePermissions(manifest);
      } else {
        myManifest = null;
      }
    }

    public boolean hasPermission(@NonNull String permission) {
      if (myManifest != null) {
        return myManifest.hasPermission(permission);
      }
      for (AndroidLibrary library : myLibrary.getLibraryDependencies()) {
        if (getLibraryPermissions(library).hasPermission(permission)) {
          return true;
        }
      }
      return false;
    }

    public boolean isRevocable(@NonNull String permission) {
      if (myManifest != null) {
        return myManifest.isRevocable(permission);
      }
      for (AndroidLibrary library : myLibrary.getLibraryDependencies()) {
        if (getLibraryPermissions(library).isRevocable(permission)) {
          return true;
        }
      }
      return false;
    }
  }

  private class ModulePermissions implements PermissionHolder {
    private final AndroidFacet myFacet;
    private List<ManifestPermissions> myManifests;
    private List<LibraryPermissions> myLibraries;
    private List<ModulePermissions> myDependencies = Lists.newArrayList();
    private Set<String> myFoundCache = Sets.newHashSet();
    private Map<String,Boolean> myRevocableCache = Maps.newHashMap();

    public ModulePermissions(Module module) {
      myFacet = AndroidFacet.getInstance(module);
      if (myFacet != null) {
        myManifests = Lists.newArrayListWithExpectedSize(4);
        for (IdeaSourceProvider provider : IdeaSourceProvider.getAllIdeaSourceProviders(myFacet)) {
          VirtualFile manifest = provider.getManifestFile();
          if (manifest != null) {
            myManifests.add(getManifestPermissions(manifest));
          }
        }
        AndroidGradleModel androidGradleModel = AndroidGradleModel.get(myFacet);
        if (androidGradleModel != null) {
          Collection<AndroidLibrary> libraries = androidGradleModel.getSelectedMainCompileDependencies().getLibraries();
          myLibraries = Lists.newArrayList();
          for (AndroidLibrary library : libraries) {
            myLibraries.add(getLibraryPermissions(library));
          }
        }
      }
    }

    private void addDependency(@NotNull ModulePermissions modulePermissions) {
      myDependencies.add(modulePermissions);
    }

    @Override
    public boolean hasPermission(@NonNull String permission) {
      return hasPermission(permission, Sets.<ModulePermissions> newHashSet());
    }

    private boolean hasPermission(@NonNull String permission, Set<ModulePermissions> seen) {
      // Permission already found to be available?
      if (myFoundCache.contains(permission)) {
        return true;
      }

      boolean hasPermission = computeHasPermission(permission, seen);
      if (hasPermission) {
        // We only cache *successfully* found permissions. If you've already
        // declared a permission, it's unlikely that it will disappear, so we
        // don't want to keep looking for it while the editor repeatedly analyzes
        // the same set of calls.
        //
        // However, if we're looking for a permission that *isn't* available,
        // it's likely that the user will be told about this, and will add the
        // permission, and in that case we don't want to risk a stale cache.
        myFoundCache.add(permission);
      }

      return hasPermission;
    }

    private boolean computeHasPermission(@NonNull String permission, Set<ModulePermissions> seen) {
      if (myFacet == null) {
        return false;
      }
      if (!seen.add(this)) {
        return false;
      }

      // TODO: Every n seconds, check for sync/updates to module structure

      for (ManifestPermissions manifest : myManifests) {
        if (manifest.hasPermission(permission)) {
          return true;
        }
      }

      if (myDependencies != null) {
        for (ModulePermissions module : myDependencies) {
          if (module.hasPermission(permission, seen)) {
            return true;
          }
        }
      }

      if (myLibraries != null) {
        for (LibraryPermissions library : myLibraries) {
          if (library.hasPermission(permission)) {
            return true;
          }
        }
      }

      return false;
    }

    @Override
    public boolean isRevocable(@NonNull String permission) {
      return isRevocable(permission, Sets.<ModulePermissions> newHashSet());
    }

    private boolean isRevocable(@NonNull String permission,
                                @NonNull Set<ModulePermissions> seen) {
      // Permission already found to be available?
      Boolean cached = myRevocableCache.get(permission);
      if (cached != null) {
        return cached;
      }

      boolean isRevocable = computeRevocable(permission, seen);
      myRevocableCache.put(permission, isRevocable);
      return isRevocable;
    }

    @NonNull
    @Override
    public AndroidVersion getMinSdkVersion() {
      return AndroidModuleInfo.get(myFacet).getMinSdkVersion();
    }

    @NonNull
    @Override
    public AndroidVersion getTargetSdkVersion() {
      return AndroidModuleInfo.get(myFacet).getTargetSdkVersion();
    }

    private boolean computeRevocable(@NonNull String permission,
                                     @NonNull Set<ModulePermissions> seen) {
      if (myFacet == null) {
        return false;
      }
      if (!seen.add(this)) {
        return false;
      }

      for (ManifestPermissions manifest : myManifests) {
        if (manifest.isRevocable(permission)) {
          return true;
        }
      }

      if (myDependencies != null) {
        for (ModulePermissions module : myDependencies) {
          if (module.isRevocable(permission, seen)) {
            return true;
          }
        }
      }

      if (myLibraries != null) {
        for (LibraryPermissions library : myLibraries) {
          if (library.isRevocable(permission)) {
            return true;
          }
        }
      }

      return false;
    }
  }

  private static void addPermissions(@NonNull Set<String> permissions,
                                     @NonNull Set<String> revocable,
                                     @NonNull PsiFile manifest) {
    String xml = manifest.getText();
    addPermissions(permissions, revocable, xml);
  }

  private static void addPermissions(@NonNull Set<String> permissions,
                                     @NonNull Set<String> revocable,
                                     @NonNull VirtualFile manifest) {
    try {
      String xml = new String(manifest.contentsToByteArray());
      addPermissions(permissions, revocable, xml);
    }
    catch (IOException ignore) {
    }
  }

  private static void addPermissions(@NonNull Set<String> permissions,
                                     @NonNull Set<String> revocable,
                                     @NonNull File manifest) {
    try {
      String xml = Files.toString(manifest, Charsets.UTF_8);
      addPermissions(permissions, revocable, xml);
    }
    catch (IOException ignore) {
    }
  }

  private static void addPermissions(@NonNull Set<String> permissions,
                                     @NonNull Set<String> revocable,
                                     @NonNull String xml) {
    Document document = XmlUtils.parseDocumentSilently(xml, true);
    if (document == null) {
      return;
    }
    Element root = document.getDocumentElement();
    if (root == null) {
      return;
    }
    NodeList children = root.getChildNodes();
    for (int i = 0, n = children.getLength(); i < n; i++) {
      Node item = children.item(i);
      if (item.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      String nodeName = item.getNodeName();
      if (nodeName.equals(TAG_USES_PERMISSION)
          || nodeName.equals(TAG_USES_PERMISSION_SDK_23)
          || nodeName.equals(TAG_USES_PERMISSION_SDK_M)) {
        Element element = (Element)item;
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!name.isEmpty()) {
          permissions.add(name);
        }
      } else if (nodeName.equals(TAG_PERMISSION)) {
        Element element = (Element)item;
        String protectionLevel = element.getAttributeNS(ANDROID_URI,
                                                        ATTR_PROTECTION_LEVEL);
        if (VALUE_DANGEROUS.equals(protectionLevel)) {
          String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
          if (!name.isEmpty()) {
            revocable.add(name);
          }
        }
      }
    }
  }
}
