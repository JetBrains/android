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
package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.util.Pair;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;


/**
 * Repository for Android application resources, e.g. those that show up in {@code R}, not {@code android.R}
 * (which are referred to as framework resources.)
 * <p>
 * For a given Android module, you can obtain either the resources for the module itself, or for a module and all
 * its libraries. Most clients should use the module with all its dependencies included; when a user is
 * using code completion for example, they expect to be offered not just the drawables in this module, but
 * all the drawables available in this module which includes the libraries.
 * </p>
 * <p>
 * The module repository is implemented using several layers. Consider a Gradle project where the main module has
 * two flavors, and depends on a library module. In this case, the {@linkplain ProjectResources} for the
 * module with dependencies will contain these components:
 * <ul>
 *   <li> A {@link ModuleSetResourceRepository} representing the collection of module repositories</li>
 *   <li> For each module (e.g. the main module and library module}, a {@link ModuleResourceRepository}</li>
 *   <li> For each resource directory in each module, a {@link ResourceFolderRepository}</li>
 * </ul>
 * These different repositories are merged together by the {@link MultiResourceRepository} class,
 * which represents a repository that just combines the resources from each of its children.
 * Both {@linkplain ModuleResourceRepository} and {@linkplain ModuleSetResourceRepository} are instances
 * of a {@linkplain MultiResourceRepository}.
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
 * The {@linkplain ModuleSetResourceRepository} is similar, but it combines {@link ModuleResourceRepository}
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
 * TODO: Rename this class to ModuleResources, or maybe LocalResources or ApplicationResources
 */
@SuppressWarnings("deprecation") // Deprecated com.android.util.Pair is required by ProjectCallback interface
public abstract class ProjectResources extends AbstractResourceRepository implements Disposable, ModificationTracker {
  protected static final Logger LOG = Logger.getInstance(ProjectResources.class);
  private final String myDisplayName;

  @Nullable private List<MultiResourceRepository> myParents;

  // Project resource ints are defined as 0x7FXX#### where XX is the resource type (layout, drawable,
  // etc...). Using FF as the type allows for 255 resource types before we get a collision
  // which should be fine.
  private static final int DYNAMIC_ID_SEED_START = 0x7fff0000;

  /** Map of (name, id) for resources of type {@link com.android.resources.ResourceType#ID} coming from R.java */
  private Map<ResourceType, TObjectIntHashMap<String>> myResourceValueMap;
  /** Map of (id, [name, resType]) for all resources coming from R.java */
  private TIntObjectHashMap<Pair<ResourceType, String>> myResIdValueToNameMap;
  /** Map of (int[], name) for styleable resources coming from R.java */
  private Map<IntArrayWrapper, String> myStyleableValueToNameMap;

  private final TObjectIntHashMap<String> myName2DynamicIdMap = new TObjectIntHashMap<String>();
  private final TIntObjectHashMap<Pair<ResourceType, String>> myDynamicId2ResourceMap =
    new TIntObjectHashMap<Pair<ResourceType, String>>();
  private int myDynamicSeed = DYNAMIC_ID_SEED_START;
  private final IntArrayWrapper myWrapper = new IntArrayWrapper(null);

  protected long myGeneration;

  protected ProjectResources(@NotNull String displayName) {
    super(false);
    myDisplayName = displayName;
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  @NotNull
  public static ProjectResources get(@NotNull Module module, boolean includeLibraries) {
    ProjectResources projectResources = get(module, includeLibraries, true);
    assert projectResources != null;
    return projectResources;
  }

  @Nullable
  public static ProjectResources get(@NotNull Module module, boolean includeLibraries, boolean createIfNecessary) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      return facet.getProjectResources(includeLibraries, createIfNecessary);
    }

    return null;
  }

  @NotNull
  public static ProjectResources create(@NotNull AndroidFacet facet, boolean includeLibraries) {
    if (includeLibraries) {
      return ModuleSetResourceRepository.create(facet);
    } else {
      return ModuleResourceRepository.create(facet);
    }
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

  // For ProjectCallback

  @Nullable
  public Pair<ResourceType, String> resolveResourceId(int id) {
    Pair<ResourceType, String> result = null;
    if (myResIdValueToNameMap != null) {
      result = myResIdValueToNameMap.get(id);
    }

    if (result == null) {
      final Pair<ResourceType, String> pair = myDynamicId2ResourceMap.get(id);
      if (pair != null) {
        result = pair;
      }
    }

    return result;
  }

  @Nullable
  public String resolveStyleable(int[] id) {
    if (myStyleableValueToNameMap != null) {
      myWrapper.set(id);
      // A normal map lookup on int[] would only consider object identity, but the IntArrayWrapper
      // will check all the individual elements for equality. We reuse an instance for all the lookups
      // since we don't need a new one each time.
      return myStyleableValueToNameMap.get(myWrapper);
    }

    return null;
  }

  @Nullable
  public Integer getResourceId(ResourceType type, String name) {
    final TObjectIntHashMap<String> map = myResourceValueMap != null ? myResourceValueMap.get(type) : null;

    if (map == null || !map.containsKey(name)) {
      return getDynamicId(type, name);
    }
    return map.get(name);
  }

  private int getDynamicId(ResourceType type, String name) {
    synchronized (myName2DynamicIdMap) {
      if (myName2DynamicIdMap.containsKey(name)) {
        return myName2DynamicIdMap.get(name);
      }
      final int value = ++myDynamicSeed;
      myName2DynamicIdMap.put(name, value);
      myDynamicId2ResourceMap.put(value, Pair.of(type, name));
      return value;
    }
  }

  public void setCompiledResources(TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                                   Map<IntArrayWrapper, String> styleableId2name,
                                   Map<ResourceType, TObjectIntHashMap<String>> res2id) {
    // Regularly clear dynamic seed such that we don't run out of numbers (we only have 255)
    synchronized (myName2DynamicIdMap) {
      myDynamicSeed = DYNAMIC_ID_SEED_START;
      myName2DynamicIdMap.clear();
      myDynamicId2ResourceMap.clear();
    }

    myResourceValueMap = res2id;
    myResIdValueToNameMap = id2res;
    myStyleableValueToNameMap = styleableId2name;
  }

  // ---- Implements ModificationCount ----

  /**
   * Returns the current generation of the project resources. Any time the project resources are updated,
   * the generation increases. This can be used to force refreshing of layouts etc (which will cache
   * configured project resources) when the project resources have changed since last render.
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
    @SuppressWarnings("deprecation")
    ResourceFile best = super.getMatchingFile(ResourceHelper.getResourceName(file), type, config);
    if (best != null) {
      assert best instanceof PsiResourceFile;
      PsiResourceFile prf = (PsiResourceFile)best;
      return prf.getPsiFile().getVirtualFile();
    }

    return null;
  }

  /** @deprecated Use {@link #getMatchingFile(VirtualFile, ResourceType, FolderConfiguration)} in the plugin code */
  @Nullable
  @Override
  @Deprecated
  public ResourceFile getMatchingFile(@NonNull String name, @NonNull ResourceType type, @NonNull FolderConfiguration config) {
    assert name.indexOf('.') == -1 : name;
    return super.getMatchingFile(name, type, config);
  }

  @VisibleForTesting
  boolean isScanPending(@NonNull PsiFile psiFile) {
    return false;
  }
}
