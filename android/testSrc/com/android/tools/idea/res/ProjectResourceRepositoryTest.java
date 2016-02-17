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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidLibraryStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
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
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.res.ModuleResourceRepositoryTest.getFirstItem;
import static com.android.tools.idea.res.ModuleResourceRepositoryTest.assertHasExactResourceTypes;

public class ProjectResourceRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT = "resourceRepository/layout.xml";
  private static final String VALUES = "resourceRepository/values.xml";
  private static final String VALUES_OVERLAY1 = "resourceRepository/valuesOverlay1.xml";
  private static final String VALUES_OVERLAY2 = "resourceRepository/valuesOverlay2.xml";
  private static final String VALUES_OVERLAY2_NO = "resourceRepository/valuesOverlay2No.xml";

  public void testStable() {
    assertSame(ProjectResourceRepository.getProjectResources(myFacet, true), ProjectResourceRepository.getProjectResources(myFacet, true));
    assertSame(ProjectResourceRepository.getProjectResources(myFacet, true), ProjectResourceRepository.getProjectResources(myModule, true));
  }

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
    LocalResourceRepository other = new LocalResourceRepository("unit test") {
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

      @NotNull
      @Override
      protected Set<VirtualFile> computeResourceDirs() {
        return ImmutableSet.of();
      }
    };

    ModuleResourceRepository module = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2, res3));
    final ProjectResourceRepository resources = ProjectResourceRepository.createForTest(myFacet, Arrays.asList(module, other));

    PsiFile layoutPsiFile = PsiManager.getInstance(getProject()).findFile(layoutFile);
    assertNotNull(layoutPsiFile);
    assertTrue(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
    final ResourceItem item = getFirstItem(resources, ResourceType.ID, "btn_title_refresh");

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(layoutPsiFile);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
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
        ResourceItem newItem = getFirstItem(resources, ResourceType.ID, "btn_title_refresh");
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

    ProjectResourceRepository repository = ProjectResourceRepository.create(myFacet);
    assertEquals(3, repository.getChildCount());
    Collection<String> items = repository.getItemsOfType(ResourceType.STRING);
    assertTrue(items.isEmpty());

    for (AndroidFacet facet : libraries) {
      LocalResourceRepository moduleRepository = facet.getModuleResources(true);
      assertNotNull(moduleRepository);
      LocalResourceRepository moduleSetRepository = facet.getProjectResources(true);
      assertNotNull(moduleSetRepository);
      LocalResourceRepository librarySetRepository = facet.getAppResources(true);
      assertNotNull(librarySetRepository);
    }
    myFacet.getModuleResources(true);
    myFacet.getProjectResources(true);
    myFacet.getAppResources(true);
  }

  public void testGetResourceDirsAndUpdateRoots() {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    addArchiveLibraries();
    List<VirtualFile> flavorDirs = Lists.newArrayList(myFacet.getAllResourceDirectories());
    final ProjectResourceRepository repository = ProjectResourceRepository.create(myFacet);
    List<? extends LocalResourceRepository> originalChildren = repository.getChildren();
    // Should have a bunch repository directories from the various flavors.
    Set<VirtualFile> resourceDirs = repository.getResourceDirs();
    assertNotEmpty(resourceDirs);
    assertNotEmpty(flavorDirs);
    assertSameElements(resourceDirs, flavorDirs);

    // Now delete a directory, notify the folder manager and updateRoots.
    final VirtualFile firstFlavor = flavorDirs.remove(0);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        try {
          firstFlavor.delete(this);
        }
        catch (IOException e) {
          assertFalse("delete failed " + e, false);
        }
        myFacet.getResourceFolderManager().invalidate();
        repository.updateRoots();
      }
    });
    // The child repositories should be the same since the module structure didn't change.
    List<? extends LocalResourceRepository> newChildren = repository.getChildren();
    assertTrue(newChildren.equals(originalChildren));
    // However, the resourceDirs should now be different, missing the first flavor directory.
    Set<VirtualFile> newResourceDirs = repository.getResourceDirs();
    assertSameElements(newResourceDirs, flavorDirs);
  }

  public void testHasResourcesOfType() {
    // Test hasResourcesOfType merging (which may be optimized to be lighter-weight than map merging).
    VirtualFile res1 = myFixture.copyFileToProject(LAYOUT, "res/layout/layout.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile values3 = myFixture.copyFileToProject(VALUES, "res3/values/many_more_values.xml");
    VirtualFile res3 = values3.getParent().getParent();

    assertNotSame(res1, res2);
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);
    // Test having some overlap between the modules.
    LocalResourceRepository module1 = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2));
    LocalResourceRepository module2 = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res2, res3));
    final ProjectResourceRepository resources = ProjectResourceRepository.createForTest(myFacet, Arrays.asList(module1, module2));

    // Create a repo with res1, res2, res3 and check types.
    // After that, delete a file in res3 and check types again.
    EnumSet<ResourceType> typesWithoutRes3 = EnumSet.of(ResourceType.ARRAY, ResourceType.ID, ResourceType.LAYOUT,
                                                        ResourceType.STRING, ResourceType.STYLE);
    EnumSet<ResourceType> allTypes = EnumSet.copyOf(typesWithoutRes3);
    allTypes.addAll(Arrays.asList(ResourceType.ATTR, ResourceType.INTEGER, ResourceType.DECLARE_STYLEABLE, ResourceType.PLURALS));


    assertHasExactResourceTypes(resources, allTypes);
    // Now delete the values file and check again.
    final PsiFile psiValues3 = PsiManager.getInstance(getProject()).findFile(values3);
    assertNotNull(psiValues3);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        psiValues3.delete();
      }
    });
    assertHasExactResourceTypes(resources, typesWithoutRes3);
  }

  private void addArchiveLibraries() {
    // Add in some Android projects too
    myFacet.getProperties().ALLOW_USER_CONFIGURATION = false; // make it a Gradle project
    AndroidProjectStub androidProject = TestProjects.createFlavorsProject();
    VariantStub variant = androidProject.getFirstVariant();
    assertNotNull(variant);
    File rootDir = androidProject.getRootDir();
    AndroidGradleModel androidModel = new AndroidGradleModel(GradleConstants.SYSTEM_ID, androidProject.getName(), rootDir,
                                                             androidProject, variant.getName(), AndroidProject.ARTIFACT_ANDROID_TEST);
    myFacet.setAndroidModel(androidModel);

    File bundle = new File(rootDir, "bundle.aar");
    File libJar = new File(rootDir, "bundle_aar" + File.separatorChar + "library.jar");
    AndroidLibraryStub library = new AndroidLibraryStub(bundle, libJar);
    variant.getMainArtifact().getDependencies().addLibrary(library);
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    final String testName = getTestName(true);
    if (testName.equals("parents")) { // for unit test testDependencies
      addModuleWithAndroidFacet(projectBuilder, modules, "plib1", true);
      addModuleWithAndroidFacet(projectBuilder, modules, "plib2", true);
    } else if (testName.equals("dependencies")) { // for unit test testDependencies
      addModuleWithAndroidFacet(projectBuilder, modules, "sharedlib", true);
      addModuleWithAndroidFacet(projectBuilder, modules, "lib1", true);
      addModuleWithAndroidFacet(projectBuilder, modules, "lib2", true);
      addModuleWithAndroidFacet(projectBuilder, modules, "app", true);
    }
  }

  // Regression test for https://code.google.com/p/android/issues/detail?id=65140
  public void testDependencies() throws Exception {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");

    Module lib1 = null;
    Module lib2 = null;
    Module sharedLib = null;
    Module app = null;

    for (Module module : ModuleManager.getInstance(getProject()).getModules()) {
      if (module != myModule) {
        VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
        assertEquals(1, contentRoots.length);

        String name = contentRoots[0].getName();
        if (name.equals("lib1")) {
          lib1 = module;
        }
        else if (name.equals("lib2")) {
          lib2 = module;
        }
        else if (name.equals("sharedlib")) {
          sharedLib = module;
        }
        else if (name.equals("app")) {
          app = module;
        } else {
          fail(name);
        }
      }
    }

    assertNotNull(lib1);
    assertNotNull(lib2);
    assertNotNull(sharedLib);
    assertNotNull(app);

    renameModule(lib1, "lib1");
    renameModule(lib2, "lib2");
    renameModule(sharedLib, "sharedLib");
    renameModule(app, "app");

    AndroidFacet lib1Facet = AndroidFacet.getInstance(lib1);
    AndroidFacet lib2Facet = AndroidFacet.getInstance(lib2);
    AndroidFacet sharedLibFacet = AndroidFacet.getInstance(sharedLib);
    AndroidFacet appFacet = AndroidFacet.getInstance(app);

    assertNotNull(lib1Facet);
    assertNotNull(lib2Facet);
    assertNotNull(sharedLibFacet);
    assertNotNull(appFacet);

    // Set up project dependencies
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
    List<AndroidFacet> appDependsOn = AndroidUtils.getAllAndroidDependencies(app, true);
    assertTrue(appDependsOn.contains(lib1Facet));
    assertTrue(appDependsOn.contains(lib2Facet));
    assertTrue(appDependsOn.contains(sharedLibFacet));
    assertFalse(appDependsOn.contains(appFacet));

    List<AndroidFacet> lib1DependsOn = AndroidUtils.getAllAndroidDependencies(lib1, true);
    assertTrue(lib1DependsOn.contains(sharedLibFacet));
    assertFalse(lib1DependsOn.contains(appFacet));
    assertFalse(lib1DependsOn.contains(lib1Facet));
    assertFalse(lib1DependsOn.contains(lib2Facet));

    // Set up resources so we can check which values are inherited through module dependencies
    VirtualFile v1 = myFixture.copyFileToProject(VALUES, "additionalModules/sharedlib/res/values/sharedvalues.xml");
    VirtualFile v2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "additionalModules/lib2/res/values/lib2values.xml");
    assertNotNull(v1);
    assertNotNull(v2);

    PsiManager manager = PsiManager.getInstance(getProject());
    PsiFile sharedLibValues = manager.findFile(v1);
    PsiFile lib2Values = manager.findFile(v2);
    assertNotNull(sharedLibValues);
    assertNotNull(lib2Values);

    final AppResourceRepository lib1Resources = lib1Facet.getAppResources(true);
    final AppResourceRepository lib2Resources = lib2Facet.getAppResources(true);
    assertNotNull(lib1Resources);
    assertNotNull(lib2Resources);
    assertNotSame(lib1Resources, lib2Resources);

    assertFalse(lib1Resources.isScanPending(sharedLibValues));
    assertFalse(lib1Resources.isScanPending(lib2Values));
    assertFalse(lib2Resources.isScanPending(sharedLibValues));
    assertFalse(lib2Resources.isScanPending(lib2Values));

    assertTrue(lib1Resources.hasResourceItem(ResourceType.PLURALS, "my_plural"));
    assertTrue(lib1Resources.hasResourceItem(ResourceType.STRING, "ellipsis"));
    assertTrue(lib1Resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    List<ResourceItem> items = lib1Resources.getResourceItem(ResourceType.STRING, "ellipsis");
    assertNotNull(items);
    ResourceValue firstValue = items.get(0).getResourceValue(false);
    assertNotNull(firstValue);
    assertEquals("Here it is: \u2026!", firstValue.getValue());

    assertTrue(lib2Resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    assertTrue(lib2Resources.hasResourceItem(ResourceType.PLURALS, "my_plural"));
    assertTrue(lib2Resources.hasResourceItem(ResourceType.STRING, "ellipsis"));

    // ONLY defined in lib2: should not be visible from lib1
    assertTrue(lib2Resources.hasResourceItem(ResourceType.STRING, "unique_string"));
    items = lib2Resources.getResourceItem(ResourceType.STRING, "unique_string");
    assertNotNull(items);
    firstValue = items.get(0).getResourceValue(false);
    assertNotNull(firstValue);
    assertEquals("Unique", firstValue.getValue());

    assertFalse(lib1Resources.hasResourceItem(ResourceType.STRING, "unique_string"));
  }

  private static void addModuleDependency(Module from, Module to) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(from).getModifiableModel();
    model.addModuleOrderEntry(to);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }

  private static void renameModule(Module from, String name) throws ModuleWithNameAlreadyExists {
    final ModifiableModuleModel model = ModuleManager.getInstance(from.getProject()).getModifiableModel();
    model.renameModule(from, name);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }

  // Note that the project resource repository is also tested in the app resource repository test, which of course merges
  // project resources with libraries
}
