// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.android.tools.idea.util.CommonAndroidUtil;
import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.augment.AndroidInternalRClass;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSdkResolveScopeProvider extends ResolveScopeProvider {

  @Nullable
  @Override
  public GlobalSearchScope getResolveScope(@NotNull VirtualFile file, @NotNull Project project) {
    if (!CommonAndroidUtil.getInstance().isAndroidProject(project)) return null;

    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    JdkOrderEntry entry = ContainerUtil.findInstance(index.getOrderEntriesForFile(file), JdkOrderEntry.class);
    final Sdk sdk = entry == null ? null : entry.getJdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      return null;
    }

    return new MyJdkScope(project, entry, index.isInLibrarySource(file));
  }

  public static class MyJdkScope extends JdkScope {
    private @Nullable final Sdk mySdk;
    private final boolean myIncludeSource;

    private MyJdkScope(Project project, @NotNull JdkOrderEntry entry, boolean includeSource) {
      super(project,
            entry.getRootFiles(OrderRootType.CLASSES),
            includeSource ? entry.getRootFiles(OrderRootType.SOURCES) : VirtualFile.EMPTY_ARRAY,
            entry.getJdkName());
      myIncludeSource = includeSource;
      mySdk = entry.getJdk();
    }

    @Override
    public boolean isForceSearchingInLibrarySources() {
      return myIncludeSource;
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      final boolean inSources1 = myIndex.isInLibrarySource(file1);

      if (inSources1 != myIndex.isInLibrarySource(file2)) {
        //Consider class A implements Runnable in project source.
        //Super class Object for class A is found in cls, super interface Runnable is found in cls as well (class A resolve scope is simple modules scope with dependencies).
        //Super class of cls Runnable isn't explicitly specified in the cls psi, so PsiClassImplUtil.getSuperClass/getSuperTypes return java.lang.Object
        //found via file's resolve scope (this one). So for the two hierarchies to meet in one place we should return cls Object here.
        //By default this resolve scope prefers sdk sources, so we need to override this behavior for Object.

        //The problem doesn't arise for other super class references inside Android sdk cls (e.g. A extends B implements C, where B implements C and both are inside android sdk),
        //because these references (C) are explicit and resolved via ClsJavaCodeReferenceElementImpl#resolveClassPreferringMyJar which returns cls classes despite custom Android sdk scope.
        if (!CommonClassNames.JAVA_LANG_OBJECT_SHORT.equals(file1.getNameWithoutExtension())) {
          return inSources1 ? 1 : -1;
        }
      }
      return super.compare(file1, file2);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return super.contains(file) || (mySdk != null && AndroidInternalRClass.isAndroidInternalR(file, mySdk));
    }
  }
}
