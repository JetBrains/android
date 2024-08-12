/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.npw.project;

import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.google.common.collect.Streams;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Project paths for a Blaze Android project.
 *
 * <p>We mostly just take whatever directory the user specified and put the new component there.
 * Unlike Gradle, Blaze has no strict requirements regarding the structure of an Android project,
 * but there are some common conventions:
 *
 * <pre>
 * google3/
 * |-java/com/google/foo/bar/... (module root)
 * | |-BUILD
 * | |-AndroidManifest.xml (manifest directory)
 * | |-Baz.java (source directory of com.google.foo.bar.Baz)
 * | |-Baz.aidl (aidl directory, option 1)
 * | |-aidl/
 * | | `-com/google/foo/bar/Baz.aidl (aidl directory, option 2)
 * | `-res/... (res directory, one of the few things required by the build system)
 * `-javatest/com/google/foo/bar/...
 *   |-BUILD
 *   `-BazTest.java (test directory of com.google.foo.bar.BazTest)
 * </pre>
 *
 * However, this is also possible (package name unrelated to directory structure):
 *
 * <pre>
 * google3/experimental/users/foo/my/own/project/
 * |-Baz.java (com.google.foo.bar.Baz)
 * `-BazTest.java (com.google.foo.bar.BazTest)
 * </pre>
 *
 * So is this (versioned paths that aren't reflected by the package name):
 *
 * <pre>
 * google3/third_party/com/google/foo/bar/
 * |-v1/Baz.java (com.google.foo.bar.Baz)
 * `-v2/Baz.java (com.google.foo.bar.Baz)
 * </pre>
 */
public class BlazeAndroidModuleTemplate implements AndroidModulePaths {
  @Nullable private File moduleRoot;
  @Nullable private File srcDirectory;
  private List<File> resDirectories = Collections.emptyList();

  @Nullable
  @Override
  public File getModuleRoot() {
    return moduleRoot;
  }

  @Nullable
  @Override
  public File getSrcDirectory(@Nullable String packageName) {
    return srcDirectory;
  }

  @Nullable
  @Override
  public File getTestDirectory(@Nullable String packageName) {
    return srcDirectory;
  }

  @Nullable
  @Override
  public File getUnitTestDirectory(@Nullable String packageName) {
    return srcDirectory;
  }

  @Override
  public List<File> getResDirectories() {
    return resDirectories;
  }

  @Nullable
  @Override
  public File getAidlDirectory(@Nullable String packageName) {
    return srcDirectory;
  }

  @Nullable
  @Override
  public File getManifestDirectory() {
    return srcDirectory;
  }
  /**
   * The new component wizard uses {@link NamedModuleTemplate#getName()} for the default package
   * name of the new component. If we can figure it out from the target directory here, then we can
   * pass it to the new component wizard.
   */
  private static String getPackageName(Project project, VirtualFile targetDirectory) {
    PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(targetDirectory);
    if (psiDirectory == null) {
      return null;
    }
    PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    if (psiPackage == null) {
      return null;
    }
    return psiPackage.getQualifiedName();
  }

  public static List<NamedModuleTemplate> getTemplates(
      Module module, @Nullable VirtualFile targetDirectory) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      return Collections.emptyList();
    }
    return getTemplates(androidFacet, targetDirectory);
  }

  public static List<NamedModuleTemplate> getTemplates(
      AndroidFacet androidFacet, @Nullable VirtualFile targetDirectory) {
    Module module = androidFacet.getModule();
    BlazeAndroidModuleTemplate paths = new BlazeAndroidModuleTemplate();
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      paths.moduleRoot = VfsUtilCore.virtualToIoFile(roots[0]);
    }
    IdeaSourceProvider sourceProvider =
        SourceProviderManager.getInstance(androidFacet).getSources();
    // If this happens to be a resource package,
    // the module name (resource package) would be more descriptive than the facet name (Android).
    // Otherwise, .workspace is still better than (Android).
    String name = androidFacet.getModule().getName();
    if (targetDirectory != null) {
      String packageName = getPackageName(module.getProject(), targetDirectory);
      if (packageName != null) {
        name = packageName;
      }
      paths.srcDirectory = VfsUtilCore.virtualToIoFile(targetDirectory);
    } else {
      // People usually put the manifest file with their sources.
      //noinspection OptionalGetWithoutIsPresent
      paths.srcDirectory =
          Streams.stream(sourceProvider.getManifestDirectoryUrls())
              .map(it -> new File(VfsUtilCore.urlToPath(it)))
              .findFirst()
              .get();
    }
    // We have a res dir if this happens to be a resource module.
    paths.resDirectories =
        Streams.stream(sourceProvider.getResDirectoryUrls())
            .map(it -> new File(VfsUtilCore.urlToPath(it)))
            .collect(Collectors.toList());
    return Collections.singletonList(new NamedModuleTemplate(name, paths));
  }
}
