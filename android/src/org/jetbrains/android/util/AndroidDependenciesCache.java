package org.jetbrains.android.util;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE;

import com.android.tools.idea.res.AndroidProjectRootListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDependenciesCache {
  private static final Key<SoftReference<AndroidDependenciesCache>> KEY = Key.create("ANDROID_DEPENDENCIES_CACHE");

  private final Module myModule;
  private final Ref<List<WeakReference<AndroidFacet>>> myAllDependencies = Ref.create();
  private final Ref<List<WeakReference<AndroidFacet>>> myAllLibraryDependencies = Ref.create();

  private AndroidDependenciesCache(@NotNull Module module) {
    myModule = module;

    AndroidProjectRootListener.ensureSubscribed(module.getProject());
  }

  /**
   * This method is called by {@link AndroidProjectRootListener} when module dependencies change.
   */
  public synchronized void dropCache() {
    myAllDependencies.set(null);
    myAllLibraryDependencies.set(null);
  }

  @NotNull
  public static AndroidDependenciesCache getInstance(@NotNull Module module) {
    AndroidDependenciesCache cache = SoftReference.dereference(module.getUserData(KEY));

    if (cache == null) {
      cache = new AndroidDependenciesCache(module);
      module.putUserData(KEY, new SoftReference<>(cache));
    }
    return cache;
  }

  @NotNull
  public synchronized List<AndroidFacet> getAllAndroidDependencies(boolean androidLibrariesOnly) {
    return getAllAndroidDependencies(myModule, androidLibrariesOnly, getListRef(androidLibrariesOnly));
  }

  @NotNull
  private Ref<List<WeakReference<AndroidFacet>>> getListRef(boolean androidLibrariesOnly) {
    return androidLibrariesOnly ? myAllLibraryDependencies : myAllDependencies;
  }

  @NotNull
  private static List<AndroidFacet> getAllAndroidDependencies(@NotNull Module module,
                                                              boolean androidLibrariesOnly,
                                                              @NotNull Ref<List<WeakReference<AndroidFacet>>> listRef) {
    List<WeakReference<AndroidFacet>> refs = listRef.get();

    if (refs == null) {
      List<AndroidFacet> facets = new ArrayList<>();
      collectAllAndroidDependencies(module, androidLibrariesOnly, facets, new HashSet<>());

      refs = ContainerUtil.map(ContainerUtil.reverse(facets), facet -> new WeakReference<>(facet));
      listRef.set(refs);
    }
    return dereference(refs);
  }

  @NotNull
  private static List<AndroidFacet> dereference(@NotNull List<WeakReference<AndroidFacet>> refs) {
    return ContainerUtil.mapNotNull(refs, ref -> ref.get());
  }

  private static void collectAllAndroidDependencies(@NotNull Module module,
                                                    boolean androidLibrariesOnly,
                                                    @NotNull List<AndroidFacet> result,
                                                    @NotNull Set<AndroidFacet> visited) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    boolean isDynamicFeature = facet != null && facet.getConfiguration().getProjectType() == PROJECT_TYPE_DYNAMIC_FEATURE;
    OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    // Loop in the reverse order to resolve dependencies on the libraries, so that if a library
    // is required by two higher level libraries it can be inserted in the correct place.

    for (int i = entries.length; --i >= 0;) {
      OrderEntry orderEntry = entries[i];
      if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;

        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          Module depModule = moduleOrderEntry.getModule();

          if (depModule != null) {
            AndroidFacet depFacet = AndroidFacet.getInstance(depModule);

            if (depFacet != null &&
                !(androidLibrariesOnly && depFacet.getConfiguration().isAppProject() && !isDynamicFeature) &&
                visited.add(depFacet)) {
              List<WeakReference<AndroidFacet>> cachedDepDeps = getInstance(depModule).getListRef(androidLibrariesOnly).get();

              if (cachedDepDeps != null) {
                List<AndroidFacet> depDeps = dereference(cachedDepDeps);

                for (AndroidFacet depDepFacet : depDeps) {
                  if (visited.add(depDepFacet)) {
                    result.add(depDepFacet);
                  }
                }
              }
              else {
                collectAllAndroidDependencies(depModule, androidLibrariesOnly, result, visited);
              }
              result.add(depFacet);
            }
          }
        }
      }
    }
  }
}
