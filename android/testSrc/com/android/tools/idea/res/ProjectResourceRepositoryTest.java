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

import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.res.ModuleResourceRepositoryTest.assertHasExactResourceTypes;
import static com.android.tools.idea.res.ResourcesTestsUtil.getSingleItem;
import static com.android.tools.idea.testing.AndroidTestUtils.waitForUpdates;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link ProjectResourceRepository}.
 */
public class ProjectResourceRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT = "resourceRepository/layout.xml";
  private static final String VALUES = "resourceRepository/values.xml";
  private static final String VALUES_OVERLAY1 = "resourceRepository/valuesOverlay1.xml";
  private static final String VALUES_OVERLAY2 = "resourceRepository/valuesOverlay2.xml";
  private static final String VALUES_OVERLAY2_NO = "resourceRepository/valuesOverlay2No.xml";

  public void testStable() {
    assertSame(ResourceRepositoryManager.getProjectResources(myFacet), ResourceRepositoryManager.getProjectResources(myFacet));
    assertSame(ResourceRepositoryManager.getProjectResources(myFacet), ResourceRepositoryManager.getProjectResources(myModule));
  }

  /**
   * Like {@link ModuleResourceRepositoryTest#testOverlayUpdates1}, but rather than testing changes to layout
   * resources (file-based resource) perform document edits in value-documents.
   * <p>
   * Ensure that we invalidate the id cache when the file is rescanned but ids don't change.
   */
  public void testInvalidateIds() throws Exception {
    VirtualFile layoutFile = myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");

    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile res3 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res3/values/nameDoesNotMatter.xml").getParent().getParent();
    myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res3/values-no/values.xml");

    assertNotSame(res1, res2);
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);

    // Just need an empty repository to make it a real module -set-; otherwise with a single
    // module we just get a module repository, not a module set repository.
    LocalResourceRepository other = new TestLocalResourceRepository(RES_AUTO);

    ModuleResourceRepository module = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res1, res2, res3), RES_AUTO, null);
    ProjectResourceRepository resources = ProjectResourceRepository.createForTest(myFacet, ImmutableList.of(module, other));

    PsiFile layoutPsiFile = PsiManager.getInstance(getProject()).findFile(layoutFile);
    assertNotNull(layoutPsiFile);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh"));
    ResourceItem item = getSingleItem(resources, ResourceType.ID, "btn_title_refresh");

    long generation = resources.getModificationCount();
    int rescans = resources.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(layoutPsiFile);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "<ImageView style=\"@style/TitleBarSeparator\" />";
      int offset = document.getText().indexOf(string);
      document.deleteString(offset, offset + string.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(resources);
    // Should still be defined:
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh"));
    ResourceItem newItem = getSingleItem(resources, ResourceType.ID, "btn_title_refresh");
    assertNotNull(newItem.getSource());
    // However, should be a different item
    assertNotSame(item, newItem);
    assertThat(resources.getModificationCount()).isGreaterThan(generation);
    assertThat(resources.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
  }

  // Regression test for https://code.google.com/p/android/issues/detail?id=57090
  public void testParents() {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    List<AndroidFacet> libraries = AndroidDependenciesCache.getAllAndroidDependencies(myModule, true);
    assertEquals(2, libraries.size());
    ModuleRootModificationUtil.addDependency(libraries.get(0).getModule(), libraries.get(1).getModule());

    ProjectResourceRepository repository = ProjectResourceRepository.create(myFacet);
    Disposer.register(getTestRootDisposable(), repository);
    assertEquals(3, repository.getChildren().size());
    Collection<String> items = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertTrue(items.isEmpty());

    for (AndroidFacet facet : libraries) {
      LocalResourceRepository moduleRepository = ResourceRepositoryManager.getModuleResources(facet);
      assertNotNull(moduleRepository);
      LocalResourceRepository moduleSetRepository = ResourceRepositoryManager.getProjectResources(facet);
      assertNotNull(moduleSetRepository);
      LocalResourceRepository librarySetRepository = ResourceRepositoryManager.getAppResources(facet);
      assertNotNull(librarySetRepository);
    }
    ResourceRepositoryManager.getModuleResources(myFacet);
    ResourceRepositoryManager.getProjectResources(myFacet);
    ResourceRepositoryManager.getAppResources(myFacet);
  }

  public void testGetResourceDirsAndUpdateRoots() {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    List<VirtualFile> flavorDirs = Lists.newArrayList(ResourceFolderManager.getInstance(myFacet).getFolders());
    ProjectResourceRepository repository = ProjectResourceRepository.create(myFacet);
    Disposer.register(getTestRootDisposable(), repository);
    List<LocalResourceRepository> originalChildren = repository.getLocalResources();
    // Should have a bunch repository directories from the various flavors.
    Set<VirtualFile> resourceDirs = repository.getResourceDirs();
    assertNotEmpty(resourceDirs);
    assertNotEmpty(flavorDirs);
    assertSameElements(resourceDirs, flavorDirs);

    // Now delete a directory, notify the folder manager and updateRoots.
    VirtualFile firstFlavor = flavorDirs.remove(0);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        try {
          firstFlavor.delete(this);
        }
        catch (IOException e) {
          assertFalse("delete failed " + e, false);
        }
        ResourceFolderManager.getInstance(myFacet).checkForChanges();
        repository.updateRoots();
      }
    });
    // The child repositories should be the same since the module structure didn't change.
    List<LocalResourceRepository> newChildren = repository.getLocalResources();
    Set<VirtualFile> newResourceDirs = repository.getResourceDirs();
    assertEquals(newChildren, originalChildren);
    // However, the resourceDirs should now be different, missing the first flavor directory.
    assertSameElements(newResourceDirs, flavorDirs);
  }

  public void testRootChangeListener() {
    MultiResourceRepository resources = (MultiResourceRepository)ResourceRepositoryManager.getProjectResources(myFacet);
    List<LocalResourceRepository> originalChildren = resources.getLocalResources();
    assertNotEmpty(originalChildren);
    Collection<VirtualFile> originalDirs = resources.getResourceDirs();
    assertNotEmpty(originalDirs);

    // Now remove one of the modules, which should automatically cause the repo to have different roots.
    WriteCommandAction.runWriteCommandAction(
      getProject(), () -> removeModuleDependency(myModule, TestModuleUtil.findModule(getProject(), "plib2").getName()));
    assertEquals(originalChildren.size() - 1, resources.getChildren().size());
    assertEquals(originalDirs.size() - 1, resources.getResourceDirs().size());
  }

  public void testHasResourcesOfType() throws Exception {
    // Test hasResourcesOfType merging (which may be optimized to be lighter-weight than map merging).
    VirtualFile res1 = myFixture.copyFileToProject(LAYOUT, "res/layout/layout.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile values3 = myFixture.copyFileToProject(VALUES, "res3/values/many_more_values.xml");
    VirtualFile res3 = values3.getParent().getParent();

    assertNotSame(res1, res2);
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);
    // Test having some overlap between the modules.
    LocalResourceRepository module1 = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res1, res2), RES_AUTO, null);
    LocalResourceRepository module2 = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res2, res3), RES_AUTO, null);
    ProjectResourceRepository resources = ProjectResourceRepository.createForTest(myFacet, ImmutableList.of(module1, module2));

    // Create a repo with res1, res2, res3 and check types.
    // After that, delete a file in res3 and check types again.
    EnumSet<ResourceType> typesWithoutRes3 = EnumSet.of(ResourceType.ARRAY, ResourceType.ID, ResourceType.LAYOUT,
                                                        ResourceType.STRING, ResourceType.STYLE);
    EnumSet<ResourceType> allTypes = EnumSet.copyOf(typesWithoutRes3);
    allTypes.addAll(ImmutableList.of(ResourceType.ATTR, ResourceType.INTEGER, ResourceType.STYLEABLE, ResourceType.PLURALS));


    assertHasExactResourceTypes(resources, allTypes);
    // Now delete the values file and check again.
    PsiFile psiValues3 = PsiManager.getInstance(getProject()).findFile(values3);
    assertNotNull(psiValues3);
    WriteCommandAction.runWriteCommandAction(null, psiValues3::delete);
    waitForUpdates(resources);
    assertHasExactResourceTypes(resources, typesWithoutRes3);
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    String testName = getTestName(true);
    if (testName.equals("parents")) { // for unit test testDependencies
      addModuleWithAndroidFacet(projectBuilder, modules, "plib1", PROJECT_TYPE_LIBRARY);
      addModuleWithAndroidFacet(projectBuilder, modules, "plib2", PROJECT_TYPE_LIBRARY);
    }
    else if (testName.equals("dependencies")) { // for unit test testDependencies
      addModuleWithAndroidFacet(projectBuilder, modules, "sharedlib", PROJECT_TYPE_LIBRARY);
      addModuleWithAndroidFacet(projectBuilder, modules, "lib1", PROJECT_TYPE_LIBRARY);
      addModuleWithAndroidFacet(projectBuilder, modules, "lib2", PROJECT_TYPE_LIBRARY);
    }
    else if (testName.equals("rootChangeListener")) {
      addModuleWithAndroidFacet(projectBuilder, modules, "plib1", PROJECT_TYPE_LIBRARY);
      addModuleWithAndroidFacet(projectBuilder, modules, "plib2", PROJECT_TYPE_LIBRARY);
    }
    else if (testName.equals("resourceOverride")) {
      addModuleWithAndroidFacet(projectBuilder, modules, "level1", PROJECT_TYPE_LIBRARY, true);
      addModuleWithAndroidFacet(projectBuilder, modules, "level2", PROJECT_TYPE_LIBRARY, false);
    }
  }

  // Regression test for https://code.google.com/p/android/issues/detail?id=65140
  public void testDependencies() {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");

    Module lib1 = TestModuleUtil.findModule(getProject(), "lib1");
    Module lib2 = TestModuleUtil.findModule(getProject(), "lib2");
    Module sharedLib = TestModuleUtil.findModule(getProject(), "sharedlib");
    Module app = TestModuleUtil.findAppModule(getProject());

    assertNotNull(lib1);
    assertNotNull(lib2);
    assertNotNull(sharedLib);
    assertNotNull(app);

    AndroidFacet lib1Facet = AndroidFacet.getInstance(lib1);
    AndroidFacet lib2Facet = AndroidFacet.getInstance(lib2);
    AndroidFacet sharedLibFacet = AndroidFacet.getInstance(sharedLib);
    AndroidFacet appFacet = AndroidFacet.getInstance(app);

    assertNotNull(lib1Facet);
    assertNotNull(lib2Facet);
    assertNotNull(sharedLibFacet);
    assertNotNull(appFacet);

    // Set up project dependencies.
    addModuleDependency(lib1, sharedLib);
    addModuleDependency(lib2, sharedLib);
    addModuleDependency(app, lib1);
    addModuleDependency(app, lib2);

    assertTrue(ModuleRootManager.getInstance(app).isDependsOn(lib1));
    assertTrue(ModuleRootManager.getInstance(lib1).isDependsOn(sharedLib));
    assertFalse(ModuleRootManager.getInstance(sharedLib).isDependsOn(lib1));
    assertFalse(ModuleRootManager.getInstance(lib2).isDependsOn(lib1));
    // Note that these are currently direct dependencies only, so app.isDependsOn(sharedLib) is false

    // Test AndroidUtils#getallAndroidDependencies
    List<AndroidFacet> appDependsOn = AndroidDependenciesCache.getAllAndroidDependencies(app, true);
    assertTrue(appDependsOn.contains(lib1Facet));
    assertTrue(appDependsOn.contains(lib2Facet));
    assertTrue(appDependsOn.contains(sharedLibFacet));
    assertFalse(appDependsOn.contains(appFacet));

    List<AndroidFacet> lib1DependsOn = AndroidDependenciesCache.getAllAndroidDependencies(lib1, true);
    assertTrue(lib1DependsOn.contains(sharedLibFacet));
    assertFalse(lib1DependsOn.contains(appFacet));
    assertFalse(lib1DependsOn.contains(lib1Facet));
    assertFalse(lib1DependsOn.contains(lib2Facet));

    // Set up resources so we can check which values are inherited through module dependencies.
    VirtualFile v1 = myFixture.copyFileToProject(VALUES, "additionalModules/sharedlib/res/values/sharedvalues.xml");
    VirtualFile v2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "additionalModules/lib2/res/values/lib2values.xml");
    assertNotNull(v1);
    assertNotNull(v2);

    PsiManager manager = PsiManager.getInstance(getProject());
    PsiFile sharedLibValues = manager.findFile(v1);
    PsiFile lib2Values = manager.findFile(v2);
    assertNotNull(sharedLibValues);
    assertNotNull(lib2Values);

    LocalResourceRepository lib1Resources = ResourceRepositoryManager.getAppResources(lib1Facet);
    LocalResourceRepository lib2Resources = ResourceRepositoryManager.getAppResources(lib2Facet);
    assertNotNull(lib1Resources);
    assertNotNull(lib2Resources);
    assertNotSame(lib1Resources, lib2Resources);

    assertTrue(lib1Resources.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural"));
    assertTrue(lib1Resources.hasResources(RES_AUTO, ResourceType.STRING, "ellipsis"));
    assertTrue(lib1Resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    List<ResourceItem> items = lib1Resources.getResources(RES_AUTO, ResourceType.STRING, "ellipsis");
    assertNotNull(items);
    ResourceValue firstValue = items.get(0).getResourceValue();
    assertNotNull(firstValue);
    assertEquals("Here it is: \u2026!", firstValue.getValue());

    assertTrue(lib2Resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    assertTrue(lib2Resources.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural"));
    assertTrue(lib2Resources.hasResources(RES_AUTO, ResourceType.STRING, "ellipsis"));

    // ONLY defined in lib2: should not be visible from lib1
    assertTrue(lib2Resources.hasResources(RES_AUTO, ResourceType.STRING, "unique_string"));
    items = lib2Resources.getResources(RES_AUTO, ResourceType.STRING, "unique_string");
    assertNotNull(items);
    firstValue = items.get(0).getResourceValue();
    assertNotNull(firstValue);
    assertEquals("Unique", firstValue.getValue());

    assertFalse(lib1Resources.hasResources(RES_AUTO, ResourceType.STRING, "unique_string"));
  }

  // Regression test for https://issuetracker.google.com/issues/68799367
  public void testResourceOverride() {
    Module lib1 = TestModuleUtil.findModule(getProject(), "level1");
    Module lib2 = TestModuleUtil.findModule(getProject(), "level2");
    Module app = TestModuleUtil.findAppModule(getProject());

    assertNotNull(lib1);
    assertNotNull(lib2);
    assertNotNull(app);

    AndroidFacet appFacet = AndroidFacet.getInstance(app);

    // Set up project dependencies
    addModuleDependency(lib1, lib2);

    // Dependencies: app -> lib1 -> lib2
    assertTrue(ModuleRootManager.getInstance(lib1).isDependsOn(lib2));
    assertTrue(ModuleRootManager.getInstance(app).isDependsOn(lib1));
    assertFalse(ModuleRootManager.getInstance(app).isDependsOn(lib2));
    assertFalse(ModuleRootManager.getInstance(lib2).isDependsOn(lib1));

    @Language("XML")
    String level1Strings = "<resources>\n" +
                           "    <string name=\"test_string\">LEVEL 1</string>\n" +
                           "</resources>";
    @Language("XML")
    String level2Strings = "<resources>\n" +
                           "    <string name=\"test_string\">LEVEL 2</string>\n" +
                           "</resources>";

    // Set up string override
    myFixture.addFileToProject("additionalModules/level1/res/values/strings.xml", level1Strings).getVirtualFile();
    myFixture.addFileToProject("additionalModules/level2/res/values/strings.xml", level2Strings).getVirtualFile();

    ResourceRepository appResources = ResourceRepositoryManager.getAppResources(appFacet);
    List<ResourceItem> resolved = appResources.getResources(RES_AUTO, ResourceType.STRING, "test_string");
    assertEquals(1, resolved.size());
    assertEquals("LEVEL 1", resolved.get(0).getResourceValue().getValue());

    // Retry reversing the library dependency to ensure the order does not depend on anything other than the
    // dependency order (like for example, alphabetical order of the modules).
    removeModuleDependency(lib1, lib2.getName());
    removeModuleDependency(app, lib1.getName());

    addModuleDependency(app, lib2);
    addModuleDependency(lib2, lib1);

    // app -> lib2 -> lib1
    assertTrue(ModuleRootManager.getInstance(lib2).isDependsOn(lib1));
    assertTrue(ModuleRootManager.getInstance(app).isDependsOn(lib2));
    assertFalse(ModuleRootManager.getInstance(app).isDependsOn(lib1));
    assertFalse(ModuleRootManager.getInstance(lib1).isDependsOn(lib2));

    appResources = ResourceRepositoryManager.getAppResources(appFacet);
    resolved = appResources.getResources(RES_AUTO, ResourceType.STRING, "test_string");
    assertEquals(1, resolved.size());
    assertEquals("LEVEL 2", resolved.get(0).getResourceValue().getValue());
  }

  private static void addModuleDependency(Module from, Module to) {
    ModifiableRootModel model = ModuleRootManager.getInstance(from).getModifiableModel();
    model.addModuleOrderEntry(to);
    ApplicationManager.getApplication().runWriteAction(model::commit);
  }

  private static void removeModuleDependency(Module from, String name) {
    boolean found = false;
    ModifiableRootModel model = ModuleRootManager.getInstance(from).getModifiableModel();
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
        if (moduleOrderEntry.getModuleName().equals(name)) {
          assertFalse(found);
          model.removeOrderEntry(orderEntry);
          found = true;
        }
      }
    }
    assertTrue(found);
    ApplicationManager.getApplication().runWriteAction(model::commit);
  }

  // Note that the project resource repository is also tested in the app resource repository test,
  // which of course merges project resources with libraries.
}
