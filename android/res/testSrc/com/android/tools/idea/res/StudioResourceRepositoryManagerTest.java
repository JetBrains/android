/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.idea.res.ResourcesTestsUtil.addBinaryAarDependency;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.AndroidProjectTypes;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.res.LocalResourceRepository;
import com.android.tools.res.MultiResourceRepository;
import com.google.common.collect.Iterables;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link StudioResourceRepositoryManager}.
 */
public class StudioResourceRepositoryManagerTest extends AndroidTestCase {
  /**
   * Checks that adding an AAR dependency is reflected in the result returned by {@link StudioResourceRepositoryManager#getLibraryResources()}.
   */
  public void testLibraryResources() {
    enableNamespacing("p1.p2");

    StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(myFacet);
    assertThat(repositoryManager.getLibraryResources()).isEmpty();
    ResourceNamespace libraryNamespace = ResourceNamespace.fromPackageName("com.example.mylibrary");
    addBinaryAarDependency(myModule);
    assertThat(repositoryManager.getLibraryResources()).hasSize(1);
    assertThat(Iterables.getOnlyElement(repositoryManager.getLibraryResources()).getNamespace()).isEqualTo(libraryNamespace);
  }

  public void testDisposal() {
    myFacet.getProperties().ALLOW_USER_CONFIGURATION = false;
    assertThat(AndroidModel.isRequired(myFacet)).named("module uses a model").isTrue();

    StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(myFacet);
    repositoryManager.getAppResources();
    repositoryManager.resetResources(); // This can be triggered from the layout editor UI.
    for (SingleNamespaceResourceRepository repository : repositoryManager.getAppResources().getLeafResourceRepositories()) {
      if (repository instanceof Disposable) {
        assertWithMessage("%s has already been disposed but is returned from ResourceRepositoryManager", repository)
          .that(Disposer.isDisposed((Disposable)repository))
          .isFalse();
      }
    }
  }

  public static class AllRepositoriesDisposedTest extends AndroidTestCase {
    Set<ResourceRepository> repositoriesToDispose = new HashSet<>();

    public void testClosingProject() {
      StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(myFacet);
      repositoriesToDispose.add(repositoryManager.getAppResources());
      repositoriesToDispose.add(repositoryManager.getProjectResources());
      repositoriesToDispose.add(repositoryManager.getModuleResources());
      repositoriesToDispose.add(repositoryManager.getTestAppResources());
      repositoriesToDispose.addAll(repositoryManager.getTestAppResources().getLeafResourceRepositories());
      repositoriesToDispose.addAll(repositoryManager.getAppResources().getLeafResourceRepositories());
    }

    @Override
    protected void tearDown() throws Exception {
      // Copy the list of repositories before super.tearDown() nulls it out via UsefulTestCase.clearDeclaredFields().
      List<ResourceRepository> repositories = new ArrayList<>(repositoriesToDispose);

      Project project;
      try {
        project = getProject();
      } finally {
        super.tearDown();
      }

      assertThat(project.isDisposed()).isTrue();

      for (ResourceRepository repository : repositories) {
        if (repository instanceof Disposable) {
          assertThat(Disposer.isDisposed((Disposable)repository)).named(repository + " disposed").isTrue();
        }
      }
    }
  }

  public static class DisposingAcrossFacets extends AndroidTestCase {
    @Override
    protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                              @NotNull List<MyAdditionalModuleData> modules) {
      super.configureAdditionalModules(projectBuilder, modules);
      addModuleWithAndroidFacet(projectBuilder, modules, "myLibrary", AndroidProjectTypes.PROJECT_TYPE_LIBRARY);
    }

    public void testDependentModuleReset() throws Exception {
      // Regression test for b/313309846
      // StudioResourceRepositoryManager.resetResources() causes the contained repositories to be disposed.
      // Any other modules' StudioResourceRepositoryManagers that reference those disposed repositories need to
      // get rid of the references they're holding.
      StudioResourceRepositoryManager mainFacetManager = StudioResourceRepositoryManager.getInstance(myFacet);

      Module myLibraryModule = getAdditionalModuleByName("myLibrary");
      assertThat(myLibraryModule).isNotNull();
      AndroidFacet myLibraryFacet = AndroidFacet.getInstance(myLibraryModule);
      assertThat(myLibraryFacet).isNotNull();

      StudioResourceRepositoryManager libraryFacetManager = StudioResourceRepositoryManager.getInstance(myLibraryFacet);

      LocalResourceRepository<VirtualFile> mainModuleAppResources = mainFacetManager.getAppResources();
      LocalResourceRepository<VirtualFile> mainModuleProjectResources = mainFacetManager.getProjectResources();
      LocalResourceRepository<VirtualFile> mainModuleTestResources = mainFacetManager.getTestModuleResources();
      LocalResourceRepository<VirtualFile> mainModuleModuleResources = mainFacetManager.getModuleResources();

      assertChildrenNotDisposed(mainModuleAppResources);
      assertChildrenNotDisposed(mainModuleProjectResources);
      assertChildrenNotDisposed(mainModuleTestResources);
      assertChildrenNotDisposed(mainModuleModuleResources);

      // Get one of the library facet's repositories, so we can ensure it's been reset and disposed.
      LocalResourceRepository<VirtualFile> libraryModuleAppResources = libraryFacetManager.getAppResources();

      // Resetting the resources will cause the library's repositories to become disposed.
      libraryFacetManager.resetResources();
      assertThat(
        StudioResourceRepositoryManager.DisposeAndRefreshService.getInstance()
          .waitForRunningTasks(10000, TimeUnit.MILLISECONDS)).isTrue();

      // Verify that the library module's repositories have been updated, and the old ones disposed.
      LocalResourceRepository<VirtualFile> libraryModuleAppResourcesAfterReset = libraryFacetManager.getAppResources();
      assertThat(libraryModuleAppResourcesAfterReset).isNotSameAs(libraryModuleAppResources);
      assertThat(Disposer.isDisposed((Disposable)libraryModuleAppResources)).isTrue();

      // The main module's repositories should still be the same objects; but their children (including the
      // library module's repositories) should have changed and should not be disposed.
      assertThat(mainFacetManager.getAppResources()).isSameAs(mainModuleAppResources);
      assertChildrenNotDisposed(mainModuleAppResources);

      assertThat(mainFacetManager.getProjectResources()).isSameAs(mainModuleProjectResources);
      assertChildrenNotDisposed(mainModuleProjectResources);

      assertThat(mainFacetManager.getTestModuleResources()).isSameAs(mainModuleTestResources);
      assertChildrenNotDisposed(mainModuleTestResources);

      assertThat(mainFacetManager.getModuleResources()).isSameAs(mainModuleModuleResources);
      assertChildrenNotDisposed(mainModuleModuleResources);
    }

    private void assertChildrenNotDisposed(LocalResourceRepository<VirtualFile> repository) {
      MultiResourceRepository<VirtualFile> multiRepository = (MultiResourceRepository<VirtualFile>)repository;
      for (ResourceRepository child : multiRepository.getChildren()) {
        if (child instanceof Disposable disposableChild) {
          String displayName;
          if (child instanceof LocalResourceRepository<?> localRepo) {
            displayName = localRepo.getDisplayName();
          } else {
            displayName = child.toString();
          }

          assertThat(Disposer.isDisposed(disposableChild)).named(displayName + " disposed").isFalse();
        }
      }
    }
  }
}
