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
package org.jetbrains.android.facet;

import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.res.ProjectResourceRepositoryRootListener;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.AndroidGradleModel.EXPLODED_AAR;
import static com.android.tools.idea.gradle.AndroidGradleModel.EXPLODED_BUNDLES;

/**
 * The resource folder manager is responsible for returning the current set
 * of resource folders used in the project. It provides hooks for getting notified
 * when the set of folders changes (e.g. due to variant selection changes, or
 * the folder set changing due to the user editing the gradle files or after a
 * delayed project initialization), and it also provides some state caching between
 * IDE sessions such that before the gradle initialization is done, it returns
 * the folder set as it was before the IDE exited.
 */
public class ResourceFolderManager implements ModificationTracker {
  private final AndroidFacet myFacet;
  private List<VirtualFile> myResDirCache;
  private long myGeneration;
  private final List<ResourceFolderListener> myListeners = Lists.newArrayList();
  private boolean myVariantListenerAdded;

  /**
   * Should only be constructed by {@link AndroidFacet}; others should obtain instance
   * via {@link AndroidFacet#getResourceFolderManager}
   */
  ResourceFolderManager(AndroidFacet facet) {
    myFacet = facet;
  }

  /** Notifies the resource folder manager that the resource folder set may have changed */
  public void invalidate() {
    List<VirtualFile> old = myResDirCache;
    if (old == null) {
      return;
    }
    myResDirCache = null;
    getFolders(); // sets myResDirCache as a side effect
    //noinspection ConstantConditions
    if (!old.equals(myResDirCache)) {
      notifyChanged(old, myResDirCache);
    }
  }

  /**
   * Returns all resource directories, in the overlay order
   * <p>
   * TODO: This should be changed to be a {@code List<List<VirtualFile>>} in order to be
   * able to distinguish overlays (e.g. flavor directories) versus resource folders at
   * the same level where duplicates are NOT allowed: [[flavor1], [flavor2], [main1,main2]]
   *
   * @return a list of all resource directories
   */
  @NotNull
  public List<VirtualFile> getFolders() {
    if (myResDirCache == null) {
      myResDirCache = computeFolders();
    }

    return myResDirCache;
  }

  private List<VirtualFile> computeFolders() {
    if (myFacet.requiresAndroidModel()) {
      JpsAndroidModuleProperties state = myFacet.getConfiguration().getState();
      AndroidModel androidModel = myFacet.getAndroidModel();
      List<VirtualFile> resDirectories = new ArrayList<>();
      if (androidModel == null) {
        // Read string property
        if (state != null) {
          String path = state.RES_FOLDERS_RELATIVE_PATH;
          if (path != null) {
            VirtualFileManager manager = VirtualFileManager.getInstance();
            // Deliberately using ';' instead of File.pathSeparator; see comment later in code below which
            // writes the property
            for (String url : Splitter.on(';').omitEmptyStrings().trimResults().split(path)) {
              VirtualFile dir = manager.findFileByUrl(url);
              if (dir != null) {
                resDirectories.add(dir);
              }
            }
          } else {
            // First time; have not yet computed the res folders
            // just try the default: src/main/res/ (from Gradle templates), res/ (from exported Eclipse projects)
            String mainRes = '/' + FD_SOURCES + '/' + FD_MAIN + '/' + FD_RES;
            VirtualFile dir =  AndroidRootUtil.getFileByRelativeModulePath(myFacet.getModule(), mainRes, true);
            if (dir != null) {
              resDirectories.add(dir);
            } else {
              String res = '/' + FD_RES;
              dir =  AndroidRootUtil.getFileByRelativeModulePath(myFacet.getModule(), res, true);
              if (dir != null) {
                resDirectories.add(dir);
              }
            }
          }
        }
      } else {
        for (IdeaSourceProvider provider : IdeaSourceProvider.getCurrentSourceProviders(myFacet)) {
          resDirectories.addAll(provider.getResDirectories());
        }

        // Write string property such that subsequent restarts can look up the most recent list
        // before the gradle model has been initialized asynchronously
        if (state != null) {
          StringBuilder path = new StringBuilder(400);
          for (VirtualFile dir : resDirectories) {
            if (path.length() != 0) {
              // Deliberately using ';' instead of File.pathSeparator since on Unix File.pathSeparator is ":"
              // which is also used in URLs, meaning we could end up with something like "file://foo:file://bar"
              path.append(';');
            }
            path.append(dir.getUrl());
          }
          state.RES_FOLDERS_RELATIVE_PATH = path.toString();
        }

        // Also refresh the app resources whenever the variant changes
        if (!myVariantListenerAdded) {
          myVariantListenerAdded = true;
          BuildVariantView.getInstance(myFacet.getModule().getProject()).addListener(this::invalidate);
        }
      }
      // Listen to root change events. Be notified when project is initialized so we can update the
      // resource set, if necessary.
      ProjectResourceRepositoryRootListener.ensureSubscribed(myFacet.getModule().getProject());

      return resDirectories;
    } else {
      return new ArrayList<>(myFacet.getMainIdeaSourceProvider().getResDirectories());
    }
  }

  private void notifyChanged(@NotNull List<VirtualFile> before, @NotNull List<VirtualFile> after) {
    myGeneration++;
    Set<VirtualFile> added = new HashSet<>(after.size());
    added.addAll(after);
    added.removeAll(before);

    Set<VirtualFile> removed = new HashSet<>(before.size());
    removed.addAll(before);
    removed.removeAll(after);

    for (ResourceFolderListener listener : new ArrayList<>(myListeners)) {
      listener.resourceFoldersChanged(myFacet, after, added, removed);
    }
  }

  @Override
  public long getModificationCount() {
    return myGeneration;
  }

  public synchronized void addListener(@NotNull ResourceFolderListener listener) {
    myListeners.add(listener);
  }

  public synchronized void removeListener(@NotNull ResourceFolderListener listener) {
    myListeners.remove(listener);
  }

  /** Adds in any AAR library resource directories found in the library definitions for the given facet */
  public static void addAarsFromModuleLibraries(@NotNull AndroidFacet facet, @NotNull Map<File, String> dirs) {
    Module module = facet.getModule();
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrSdkOrderEntry) {
        if (orderEntry.isValid() && isAarDependency(facet, orderEntry)) {
          final LibraryOrSdkOrderEntry entry = (LibraryOrSdkOrderEntry)orderEntry;
          final VirtualFile[] libClasses = entry.getRootFiles(OrderRootType.CLASSES);
          String libraryName = entry.getPresentableName();
          File res = null;
          for (VirtualFile root : libClasses) {
            if (root.getName().equals(FD_RES)) {
              res = VfsUtilCore.virtualToIoFile(root);
              break;
            }
          }

          if (res == null) {
            for (VirtualFile root : libClasses) {
              // Switch to file IO: The root may be inside a jar file system, where
              // getParent() returns null (and to get the real parent is ugly;
              // e.g. ((PersistentFSImpl.JarRoot)root).getParentLocalFile()).
              // Besides, we need the java.io.File at the end of this anyway.
              File file = new File(VfsUtilCore.virtualToIoFile(root).getParentFile(), FD_RES);
              if (file.exists()) {
                res = file;
                break;
              }
            }
          }

          if (res != null) {
            dirs.put(res, libraryName);
          }
        }
      }
    }
  }

  private static boolean isAarDependency(@NotNull AndroidFacet facet, @NotNull OrderEntry orderEntry) {
    if (facet.requiresAndroidModel() && orderEntry instanceof LibraryOrderEntry) {
      VirtualFile[] files = orderEntry.getFiles(OrderRootType.CLASSES);
      if (files.length >= 2) {
        for (VirtualFile file : files) {
          if (FD_RES.equals(file.getName()) && file.isDirectory()) {
            return true;
          }
        }
      }
      return false;
    }
    return AndroidMavenUtil.isMavenAarDependency(facet.getModule(), orderEntry);
  }

  /**
   * Returns true if the given resource file (such as a given layout XML file) is an extracted library (AAR) resource file
   *
   * @param file the file to check
   * @return true if the file is a library resource file
   */
  public static boolean isLibraryResourceFile(@Nullable VirtualFile file) {
    if (file != null) {
      return isLibraryResourceFolder(file.getParent());
    }

    return false;
  }

  /**
   * Returns true if the given resource folder (such as a given "layout") is an extracted library (AAR) resource folder
   *
   * @param folder the folder to check
   * @return true if the folder is a library resource folder
   */
  public static boolean isLibraryResourceFolder(@Nullable VirtualFile folder) {
    if (folder != null) {
      return isLibraryResourceRoot(folder.getParent());
    }

    return false;
  }

  /**
   * Returns true if the given resource folder (such as a given "res" folder, a parent of say a layout folder) is an extracted
   * library (AAR) resource folder
   *
   * @param res the folder to check
   * @return true if the folder is a library resource folder
   */
  public static boolean isLibraryResourceRoot(@Nullable VirtualFile res) {
    if (res != null) {
      VirtualFile aar = res.getParent();
      if (aar != null) {
        VirtualFile exploded = aar.getParent();
        if (exploded != null) {
          String name = exploded.getName();
          if (name.equals(EXPLODED_BUNDLES) || name.equals(EXPLODED_AAR)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /** Listeners for resource folder changes */
  public interface ResourceFolderListener {
    /** The resource folders in this project has changed */
    void resourceFoldersChanged(@NotNull AndroidFacet facet,
                                @NotNull List<VirtualFile> folders,
                                @NotNull Collection<VirtualFile> added,
                                @NotNull Collection<VirtualFile> removed);
  }
}
