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
import com.android.annotations.Nullable;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.AndroidDependencies;
import com.android.tools.idea.gradle.stubs.android.AndroidLibraryStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.android.tools.idea.rendering.ModuleResourceRepositoryTest.getFirstItem;
import static org.easymock.EasyMock.createMock;

public class ModuleSetResourceRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT = "resourceRepository/layout.xml";
  private static final String VALUES = "resourceRepository/values.xml";
  private static final String VALUES_OVERLAY1 = "resourceRepository/valuesOverlay1.xml";
  private static final String VALUES_OVERLAY2 = "resourceRepository/valuesOverlay2.xml";
  private static final String VALUES_OVERLAY2_NO = "resourceRepository/valuesOverlay2No.xml";

  // Ensure that we invalidate the id cache when the file is rescanned but ids don't change
  // (this was broken)
  public void testInvalidateIds() {
    // Like testOverlayUpdates1, but rather than testing changes to layout resources (file-based resource)
    // perform document edits in value-documents

    VirtualFile layoutFile = myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");

    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile res3 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res3/values/nameDoesNotMatter.xml").getParent().getParent();
    myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res3/values-no/values.xml");

    assertNotSame(res1, res2);
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);

    // Just need an empty repository to make it a real module -set-; otherwise with a single
    // module we just get a module repository, not a module set repository
    ProjectResources other = new ProjectResources("unit test") {
      @NonNull
      @Override
      protected Map<ResourceType, ListMultimap<String, ResourceItem>> getMap() {
        return Collections.emptyMap();
      }

      @Nullable
      @Override
      protected ListMultimap<String, ResourceItem> getMap(ResourceType type, boolean create) {
        return ArrayListMultimap.create();
      }
    };

    ModuleResourceRepository module = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2, res3));
    final ProjectResources r = ModuleSetResourceRepository.create(myFacet, Arrays.asList(module, other));
    assertTrue(r instanceof ModuleSetResourceRepository);
    final ModuleSetResourceRepository resources = (ModuleSetResourceRepository)r;

    PsiFile layoutPsiFile = PsiManager.getInstance(getProject()).findFile(layoutFile);
    assertNotNull(layoutPsiFile);
    assertTrue(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
    final PsiResourceItem item = getFirstItem(resources, ResourceType.ID, "btn_title_refresh");

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(layoutPsiFile);
    assertNotNull(document);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        String string = "<ImageView style=\"@style/TitleBarSeparator\" />";
        int offset = document.getText().indexOf(string);
        document.deleteString(offset, offset + string.length());
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.isScanPending(layoutPsiFile));
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        assertTrue(generation < resources.getModificationCount());
        // Should still be defined:
        assertTrue(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
        PsiResourceItem newItem = getFirstItem(resources, ResourceType.ID, "btn_title_refresh");
        assertNotNull(newItem.getSource());
        // However, should be a different item
        assertNotSame(item, newItem);
      }
    });
  }

  // Regression test for https://code.google.com/p/android/issues/detail?id=57090
  public void testParents() {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    List<AndroidFacet> libraries = AndroidUtils.getAllAndroidDependencies(myModule, true);
    assertEquals(2, libraries.size());
    ModuleRootModificationUtil.addDependency(libraries.get(0).getModule(), libraries.get(1).getModule());


    addArchiveLibraries();

    ProjectResources r = ModuleSetResourceRepository.create(myFacet);
    assertTrue(r instanceof ModuleSetResourceRepository);
    ModuleSetResourceRepository repository = (ModuleSetResourceRepository)r;
    assertEquals(3, repository.getChildCount());
    Collection<String> items = repository.getItemsOfType(ResourceType.STRING);
    assertTrue(items.isEmpty());

    for (AndroidFacet facet : libraries) {
      ProjectResources moduleRepository = facet.getProjectResources(false, true);
      assertNotNull(moduleRepository);
      ProjectResources moduleSetRepository = facet.getProjectResources(true, true);
      assertNotNull(moduleSetRepository);
    }
    myFacet.getProjectResources(false, true);
    myFacet.getProjectResources(true, true);
  }

  private void addArchiveLibraries() {
    // Add in some Android projects too
    myFacet.getConfiguration().getState().ALLOW_USER_CONFIGURATION = false; // make it a Gradle project
    AndroidProjectStub androidProject = TestProjects.createFlavorsProject();
    VariantStub variant = androidProject.getFirstVariant();
    assertNotNull(variant);
    String rootDirPath = androidProject.getRootDir().getAbsolutePath();
    IdeaAndroidProject ideaAndroidProject =
      new IdeaAndroidProject(androidProject.getName(), rootDirPath, androidProject, variant.getName());
    myFacet.setIdeaAndroidProject(ideaAndroidProject);

    File libJar = new File(rootDirPath, "library.aar/library.jar");
    AndroidLibraryStub library = new AndroidLibraryStub(libJar);
    variant.getMainArtifactInfo().getDependencies().addLibrary(library);

    AndroidDependencies.DependencyFactory myDependencyFactory = createMock(AndroidDependencies.DependencyFactory.class);
    myDependencyFactory.addLibraryDependency(DependencyScope.COMPILE, "library.aar", libJar);
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    final String testName = getTestName(true);
    if (testName.equals("parents")) { // for unit test testParents
      addModuleWithAndroidFacet(projectBuilder, modules, "lib1", true);
      addModuleWithAndroidFacet(projectBuilder, modules, "lib2", true);
    }
  }
}
