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
package com.android.tools.idea.res;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.tools.lint.detector.api.LintUtils.stripIdPrefix;


/**
 * Repository for Android application resources, e.g. those that show up in {@code R}, not {@code android.R}
 * (which are referred to as framework resources.). Note that this includes resources from Gradle libraries
 * too, even though you may not think of these as "local" (they do however (a) end up in the application
 * namespace, and (b) get extracted by Gradle into the project's build folder where they are merged with
 * the other resources.)
 * <p>
 * For a given Android module, you can obtain either the resources for the module itself, or for a module and all
 * its libraries. Most clients should use the module with all its dependencies included; when a user is
 * using code completion for example, they expect to be offered not just the drawables in this module, but
 * all the drawables available in this module which includes the libraries.
 * </p>
 * <p>
 * The module repository is implemented using several layers. Consider a Gradle project where the main module has
 * two flavors, and depends on a library module. In this case, the {@linkplain LocalResourceRepository} for the
 * module with dependencies will contain these components:
 * <ul>
 *   <li> A {@link AppResourceRepository} which contains a
 *          {@link FileResourceRepository} wrapping each AAR library dependency, and merges this with
 *          the project resource repository </li>
 *   <li> A {@link ProjectResourceRepository} representing the collection of module repositories</li>
 *   <li> For each module (e.g. the main module and library module}, a {@link ModuleResourceRepository}</li>
 *   <li> For each resource directory in each module, a {@link ResourceFolderRepository}</li>
 * </ul>
 * These different repositories are merged together by the {@link MultiResourceRepository} class,
 * which represents a repository that just combines the resources from each of its children.
 * All of {@linkplain AppResourceRepository}, {@linkplain ModuleResourceRepository} and
 * {@linkplain ProjectResourceRepository} are instances of a {@linkplain MultiResourceRepository}.
 * </p>
 * <p>
 * The {@link ResourceFolderRepository} is the lowest level of repository. It is associated with just
 * a single resource folder. Therefore, it does not have to worry about trying to mask resources between
 * different flavors; that task is done by the {@link ModuleResourceRepository} which combines
 * {@linkplain ResourceFolderRepository} instances. Instead, the {@linkplain ResourceFolderRepository} just
 * needs to compute the resource items for the resource folders, including qualifier variations.
 * </p>
 * <p>
 * The resource repository automatically stays up to date. You can call {@linkplain #getModificationCount()}
 * to see whether anything has changed since your last data fetch. This is for example how the resource
 * string folding in the source editors work; they fetch the current values of the resource strings, and
 * store those along with the current project resource modification count into the folding data structures.
 * When the editor wants to see if the folding sections are up to date, those are compared with the current
 * {@linkplain #getModificationCount()} version, and only if they differ is the folding structure updated.
 * </p>
 * <p>
 * Only the {@linkplain ResourceFolderRepository} needs to listen for user edits and file changes. It
 * uses {@linkplain PsiProjectListener}, a single listener which is shared by all repositories in the
 * same project, to get notified when something in one of its resource files changes, and it uses the
 * PSI change event to selectively update the repository data structures, if possible.
 * </p>
 * <p>
 * The {@linkplain ResourceFolderRepository} can also have a pointer to its parent. This is possible
 * since a resource folder can only be in a single module. The parent reference is used to quickly
 * invalidate the cache of the parent {@link MultiResourceRepository}. For example, let's say the
 * project has two flavors. When the PSI change event is used to update the name of a string resource,
 * the repository will also notify the parent that its {@link ResourceType#ID} map is out of date.
 * The {@linkplain MultiResourceRepository} will use this to null out its map cache of strings, and
 * on the next read, it will merge in the string maps from all its {@linkplain ResourceFolderRepository}
 * children.
 * </p>
 * <p>
 * One common type of "update" is changing the current variant in the IDE. With the above scheme,
 * this just means reordering the {@linkplain ResourceFolderRepository} instances in the
 * {@linkplain ModuleResourceRepository}; it does not have to rescan the resources as it did in the
 * previous implementation.
 * </p>
 * <p>
 * The {@linkplain ProjectResourceRepository} is similar, but it combines {@link ModuleResourceRepository}
 * instances rather than {@link ResourceFolderRepository} instances. Note also that the way these
 * resource repositories work is slightly different from the way the resource items are used by
 * the builder: The builder will bail if it encounters duplicate declarations unless they are in alternative
 * folders of the same flavor. For the resource repository we never want to bail on merging; the repository
 * is kept up to date and live as the user is editing, so it is normal for the repository to sometimes
 * reflect invalid user edits (in the same way a Java editor in an IDE sometimes is showing uncompilable
 * source code) and it needs to be able to handle this case and offer a state that is as close to possible
 * as the intended meaning. Error handling is done by another part of the IDE.
 * </p>
 * <p>
 * Finally, note that the resource repository is showing the current state of the resources for the
 * currently selected variant. Note however that the above approach also lets us query resources for
 * example for <b>all</b> flavors, not just the currently selected flavor. We can offer APIs to iterate
 * through all available {@link ResourceFolderRepository} instances, not just the set of instances for
 * the current module's current flavor. This will allow us to for example preview the string translations
 * for a given resource name not just for the current flavor but for all other flavors as well.
 * </p>
 */
@SuppressWarnings("deprecation") // Deprecated com.android.util.Pair is required by ProjectCallback interface
public abstract class LocalResourceRepository extends AbstractResourceRepository implements Disposable, ModificationTracker {
  protected static final Logger LOG = Logger.getInstance(LocalResourceRepository.class);

  protected static final AtomicLong ourModificationCounter = new AtomicLong();

  private final String myDisplayName;

  @Nullable private List<MultiResourceRepository> myParents;

  protected long myGeneration;

  private final Object RESOURCE_DIRS_LOCK = new Object();
  @Nullable private Set<VirtualFile> myResourceDirs;

  protected LocalResourceRepository(@NotNull String displayName) {
    super(false);
    myDisplayName = displayName;
    myGeneration = ourModificationCounter.incrementAndGet();
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public void dispose() {
  }

  @Override
  public final boolean isFramework() {
    return false;
  }

  public void addParent(@NonNull MultiResourceRepository parent) {
    if (myParents == null) {
      myParents = Lists.newArrayListWithExpectedSize(2); // Don't expect many parents
    }
    myParents.add(parent);
  }

  public void removeParent(@NonNull MultiResourceRepository parent) {
    if (myParents != null) {
      myParents.remove(parent);
    }
  }

  protected void invalidateItemCaches(@Nullable ResourceType... types) {
    if (myParents != null) {
      for (MultiResourceRepository parent : myParents) {
        parent.invalidateCache(this, types);
      }
    }
  }

  /** If this repository has not already been visited, merge its items of the given type into result. */
  protected final void merge(@NotNull Set<LocalResourceRepository> visited,
                             @NotNull ResourceType type,
                             @NotNull SetMultimap<String, String> seenQualifiers,
                             @NotNull ListMultimap<String, ResourceItem> result) {
    if (visited.contains(this)) {
      return;
    }
    visited.add(this);
    doMerge(visited, type, seenQualifiers, result);
  }

  protected void doMerge(@NotNull Set<LocalResourceRepository> visited,
                         @NotNull ResourceType type,
                         @NotNull SetMultimap<String, String> seenQualifiers,
                         @NotNull ListMultimap<String, ResourceItem> result) {
    ListMultimap<String, ResourceItem> items = getMap(type, false);
    if (items == null) {
      return;
    }
    for (ResourceItem item : items.values()) {
      String name = item.getName();
      String qualifiers = item.getQualifiers();
      if (!result.containsKey(name) || type == ResourceType.ID || !seenQualifiers.containsEntry(name, qualifiers)) {
        // We only add a duplicate item if there isn't an item with the same qualifiers (and it's
        // not an id; id's are allowed to be defined in multiple places even with the same
        // qualifiers)
        result.put(name, item);
        seenQualifiers.put(name, qualifiers);
      }
    }
  }

  protected boolean computeHasResourcesOfType(@NotNull ResourceType type, @NotNull Set<LocalResourceRepository> visited) {
    if (!visited.add(this)) {
      return false;
    }
    return hasResourcesOfType(type);
  }

  // ---- Implements ModificationCount ----

  /**
   * Returns the current generation of the app resources. Any time the app resources are updated,
   * the generation increases. This can be used to force refreshing of layouts etc (which will cache
   * configured app resources) when the project resources have changed since last render.
   * <p>
   * Note that the generation is not a simple change count. If you change the contents of a layout drawable XML file,
   * that will not affect the {@link ResourceItem} and {@link ResourceValue} results returned from
   * this repository; we only store the presence of file based resources like layouts, menus, and drawables.
   * Therefore, only additions or removals of these files will cause a generation change.
   * <p>
   * Value resource files, such as string files, will cause generation changes when they are edited (unless
   * the change is determined to not be relevant to resource values, such as a change in an XML comment, etc.
   *
   * @return the generation id
   */
  @Override
  public long getModificationCount() {
    return myGeneration;
  }

  @Nullable
  public VirtualFile getMatchingFile(@NonNull VirtualFile file, @NonNull ResourceType type, @NonNull FolderConfiguration config) {
    List<VirtualFile> matches = getMatchingFiles(file, type, config);
    return matches.isEmpty() ? null : matches.get(0);
  }

  @NonNull
  public List<VirtualFile> getMatchingFiles(@NonNull VirtualFile file, @NonNull ResourceType type, @NonNull FolderConfiguration config) {
    List<ResourceFile> matches = super.getMatchingFiles(ResourceHelper.getResourceName(file), type, config);
    List<VirtualFile> matchesFiles = new ArrayList<VirtualFile>(matches.size());
    for (ResourceFile match : matches) {
      if (match != null) {
        if (match instanceof PsiResourceFile) {
          matchesFiles.add(((PsiResourceFile)match).getPsiFile().getVirtualFile());
        }
        else {
          matchesFiles.add(LocalFileSystem.getInstance().findFileByIoFile(match.getFile()));
        }
      }
    }
    return matchesFiles;
  }

  /** @deprecated Use {@link #getMatchingFile(VirtualFile, ResourceType, FolderConfiguration)} in the plugin code */
  @Nullable
  @Override
  @Deprecated
  public ResourceFile getMatchingFile(@NonNull String name, @NonNull ResourceType type, @NonNull FolderConfiguration config) {
    assert name.indexOf('.') == -1 : name;
    return super.getMatchingFile(name, type, config);
  }

  @Nullable
  public DataBindingInfo getDataBindingInfoForLayout(String layoutName) {
    return null;
  }

  @Nullable
  public Map<String, DataBindingInfo> getDataBindingResourceFiles() {
    return null;
  }

  @VisibleForTesting
  public boolean isScanPending(@NonNull PsiFile psiFile) {
    return false;
  }

  /** Returns the {@link PsiFile} corresponding to the source of the given resource item, if possible */
  @Nullable
  public static PsiFile getItemPsiFile(@NonNull Project project, @NonNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      PsiResourceItem psiResourceItem = (PsiResourceItem)item;
      return psiResourceItem.getPsiFile();
    }

    ResourceFile source = item.getSource();
    if (source == null) { // most likely a dynamically defined value
      return null;
    }

    if (source instanceof PsiResourceFile) {
      PsiResourceFile prf = (PsiResourceFile)source;
      return prf.getPsiFile();
    }

    File file = source.getFile();
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    if (virtualFile != null) {
      PsiManager psiManager = PsiManager.getInstance(project);
      return psiManager.findFile(virtualFile);
    }

    return null;
  }

  /**
   * Returns the {@link XmlTag} corresponding to the given resource item. This is only
   * defined for resource items in value files.
   */
  @Nullable
  public static XmlTag getItemTag(@NonNull Project project, @NonNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      PsiResourceItem psiResourceItem = (PsiResourceItem)item;
      return psiResourceItem.getTag();
    }

    PsiFile psiFile = getItemPsiFile(project, item);
    if (psiFile instanceof XmlFile) {
      String resourceName = item.getName();
      XmlFile xmlFile = (XmlFile)psiFile;
      ApplicationManager.getApplication().assertReadAccessAllowed();
      XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag != null && rootTag.isValid()) {
        XmlTag[] subTags = rootTag.getSubTags();
        for (XmlTag tag : subTags) {
          if (tag.isValid() && resourceName.equals(tag.getAttributeValue(SdkConstants.ATTR_NAME))) {
            return tag;
          }
        }
      }

      // This method should only be called on value resource types
      assert FolderTypeRelationship.getRelatedFolders(item.getType()).contains(ResourceFolderType.VALUES) : item.getType();
    }

    return null;
  }

  @Nullable
  public String getViewTag(@NonNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      PsiResourceItem psiItem = (PsiResourceItem)item;
      XmlTag tag = psiItem.getTag();

      final String id = item.getName();

      if (tag != null && tag.isValid()
          // Make sure that the id attribute we're searching for is actually
          // defined for this tag, not just referenced from this tag.
          // For example, we could have
          //    <Button a:alignLeft="@+id/target" a:id="@+id/something ...>
          // and this should *not* return "Button" as the view tag for
          // @+id/target!
          && id.equals(stripIdPrefix(tag.getAttributeValue(ATTR_ID, ANDROID_URI)))) {
        return tag.getName();
      }


      PsiFile file = psiItem.getPsiFile();
      if (file.isValid() && file instanceof XmlFile) {
        XmlFile xmlFile = (XmlFile)file;
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag != null && rootTag.isValid()) {
          return findViewTag(rootTag, id);
        }
      }
    }

    return null;
  }

  @Nullable
  private static String findViewTag(XmlTag tag, String target) {
    String id = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
    if (id != null && id.endsWith(target) && target.equals(stripIdPrefix(id))) {
      return tag.getName();
    }

    for (XmlTag sub : tag.getSubTags()) {
      if (sub.isValid()) {
        String found = findViewTag(sub, target);
        if (found != null) {
          return found;
        }
      }
    }

    return null;
  }

  /**
   * Forces the repository to update itself synchronously, if necessary (in case there
   * are pending updates). This method must be called on the event dispatch thread!
   */
  public void sync() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  @NotNull
  public final Set<VirtualFile> getResourceDirs() {
    synchronized (RESOURCE_DIRS_LOCK) {
      if (myResourceDirs != null) {
        return myResourceDirs;
      }
      myResourceDirs = computeResourceDirs();
      return myResourceDirs;
    }
  }

  @NotNull protected abstract Set<VirtualFile> computeResourceDirs();

  public final void invalidateResourceDirs() {
    synchronized (RESOURCE_DIRS_LOCK) {
      myResourceDirs = null;
    }
    if (myParents != null) {
      for (LocalResourceRepository parent : myParents) {
        parent.invalidateResourceDirs();
      }
    }
  }
}
