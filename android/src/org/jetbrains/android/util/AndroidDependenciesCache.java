package org.jetbrains.android.util;

import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDependenciesCache {
  private static final Key<AndroidDependenciesCache> KEY = Key.create("ANDROID_DEPENDENCIES_CACHE");
  private final Module myModule;

  private volatile List<WeakReference<AndroidFacet>> myAllDependencies;
  private volatile List<WeakReference<AndroidFacet>> myAllLibraryDependencies;

  private AndroidDependenciesCache(@NotNull Module module) {
    myModule = module;

    module.getProject().getMessageBus().connect(module).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        dropCache();
      }
    });
  }

  private synchronized void dropCache() {
    myAllDependencies = null;
    myAllLibraryDependencies = null;
  }

  @NotNull
  public static AndroidDependenciesCache getInstance(@NotNull Module module) {
    AndroidDependenciesCache cache = module.getUserData(KEY);

    if (cache == null) {
      cache = new AndroidDependenciesCache(module);
      module.putUserData(KEY, cache);
    }
    return cache;
  }

  @NotNull
  public synchronized List<AndroidFacet> getAllAndroidDependencies(boolean androidLibrariesOnly) {
    return androidLibrariesOnly
           ? getAllAndroidDependencies(myModule, true, Ref.create(myAllLibraryDependencies))
           : getAllAndroidDependencies(myModule, false, Ref.create(myAllDependencies));
  }

  @NotNull
  private static List<AndroidFacet> getAllAndroidDependencies(@NotNull Module module,
                                                              boolean androidLibrariesOnly,
                                                              Ref<List<WeakReference<AndroidFacet>>> listRef) {
    List<WeakReference<AndroidFacet>> refs = listRef.get();

    if (refs == null) {
      final List<AndroidFacet> facets = new ArrayList<AndroidFacet>();
      collectAllAndroidDependencies(module, androidLibrariesOnly, facets, new HashSet<AndroidFacet>());

      refs = ContainerUtil.map(facets, new Function<AndroidFacet, WeakReference<AndroidFacet>>() {
        @Override
        public WeakReference<AndroidFacet> fun(AndroidFacet facet) {
          return new WeakReference<AndroidFacet>(facet);
        }
      });
      listRef.set(refs);
    }
    return dereference(refs);
  }

  @NotNull
  private static List<AndroidFacet> dereference(List<WeakReference<AndroidFacet>> refs) {
    return ContainerUtil.mapNotNull(refs, new Function<WeakReference<AndroidFacet>, AndroidFacet>() {
      @Override
      public AndroidFacet fun(WeakReference<AndroidFacet> ref) {
        return ref.get();
      }
    });
  }

  private static void collectAllAndroidDependencies(Module module,
                                                    boolean androidLibrariesOnly,
                                                    List<AndroidFacet> result,
                                                    Set<AndroidFacet> visited) {
    final OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    // loop in the inverse order to resolve dependencies on the libraries, so that if a library
    // is required by two higher level libraries it can be inserted in the correct place

    for (int i = entries.length - 1; i >= 0; i--) {
      final OrderEntry orderEntry = entries[i];
      if (orderEntry instanceof ModuleOrderEntry) {
        final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;

        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          final Module depModule = moduleOrderEntry.getModule();

          if (depModule != null) {
            final AndroidFacet depFacet = AndroidFacet.getInstance(depModule);

            if (depFacet != null &&
                (!androidLibrariesOnly || depFacet.isLibraryProject()) &&
                visited.add(depFacet)) {
              collectAllAndroidDependencies(depModule, androidLibrariesOnly, result, visited);
              result.add(0, depFacet);
            }
          }
        }
      }
    }
  }
}
