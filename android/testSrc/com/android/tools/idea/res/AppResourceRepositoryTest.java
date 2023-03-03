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

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.res.ResourcesTestsUtil.addBinaryAarDependency;
import static com.android.tools.idea.res.ResourcesTestsUtil.getSingleItem;
import static com.android.tools.idea.testing.AndroidTestUtils.waitForUpdates;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.projectmodel.DynamicResourceValue;
import com.android.resources.AarTestUtils;
import com.android.resources.ResourceType;
import com.android.resources.aar.AarSourceResourceRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link AppResourceRepository}.
 */
public class AppResourceRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT = "resourceRepository/layout.xml";
  private static final String VALUES = "resourceRepository/values.xml";
  private static final String VALUES_OVERLAY1 = "resourceRepository/valuesOverlay1.xml";
  private static final String VALUES_OVERLAY2 = "resourceRepository/valuesOverlay2.xml";
  private static final String VALUES_WITH_NAMESPACE = "resourceRepository/values_with_namespace_reference.xml";
  private static final String VALUES_OVERLAY2_NO = "resourceRepository/valuesOverlay2No.xml";

  public void testStable() {
    assertSame(StudioResourceRepositoryManager.getAppResources(myFacet), StudioResourceRepositoryManager.getAppResources(myFacet));
    assertSame(StudioResourceRepositoryManager.getAppResources(myFacet), StudioResourceRepositoryManager.getAppResources(myModule));
  }

  public void testStringOrder() {
    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();

    ModuleResourceRepository moduleRepository =
        ModuleResourceRepository.createForTest(myFacet, Collections.singletonList(res1), RES_AUTO, null);
    ProjectResourceRepository projectResources =
        ProjectResourceRepository.createForTest(myFacet, Collections.singletonList(moduleRepository));
    AppResourceRepository appResources =
        AppResourceRepository.createForTest(myFacet, Collections.singletonList(projectResources), Collections.emptyList());

    assertOrderedEquals(appResources.getResources(RES_AUTO, ResourceType.STRING).keySet(),
                        ImmutableList.of("app_name", "title_crossfade", "title_card_flip", "title_screen_slide", "title_zoom",
                                         "title_layout_changes", "title_template_step", "ellipsis"));
  }

  /**
   * Like {@link ModuleResourceRepositoryTest#testOverlayUpdates1}, but rather than testing changes to layout
   * resources (file-based resource) performs document edits in value-documents.
   */
  public void testMerging() throws Exception {
    VirtualFile layoutFile = myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");

    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile res3 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res3/values/nameDoesNotMatter.xml").getParent().getParent();
    myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res3/values-no/values.xml");

    assertNotSame(res1, res2);
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);

    // res3 is not used as an overlay here; instead we use it to simulate an AAR library below
    ModuleResourceRepository moduleRepository =
        ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res1, res2), RES_AUTO, null);
    ProjectResourceRepository projectResources = ProjectResourceRepository.createForTest(
        myFacet, Collections.singletonList(moduleRepository));

    AppResourceRepository appResources = AppResourceRepository.createForTest(
        myFacet, Collections.singletonList(projectResources), Collections.emptyList());

    assertTrue(appResources.hasResources(RES_AUTO, ResourceType.STRING, "title_card_flip"));
    assertFalse(appResources.hasResources(RES_AUTO, ResourceType.STRING, "non_existent_title_card_flip"));

    assertTrue(projectResources.hasResources(RES_AUTO, ResourceType.STRING, "title_card_flip"));
    assertFalse(projectResources.hasResources(RES_AUTO, ResourceType.STRING, "non_existent_title_card_flip"));

    assertTrue(moduleRepository.hasResources(RES_AUTO, ResourceType.STRING, "title_card_flip"));
    assertFalse(moduleRepository.hasResources(RES_AUTO, ResourceType.STRING, "non_existent_title_card_flip"));

    AarSourceResourceRepository aar1 = AarSourceResourceRepository.create(VfsUtilCore.virtualToIoFile(res3).toPath(), "aar1");
    appResources.updateRoots(ImmutableList.of(projectResources), ImmutableList.of(aar1));

    assertTrue(appResources.hasResources(RES_AUTO, ResourceType.STRING, "another_unique_string"));
    assertTrue(aar1.hasResources(RES_AUTO, ResourceType.STRING, "another_unique_string"));
    assertFalse(projectResources.hasResources(RES_AUTO, ResourceType.STRING, "another_unique_string"));
    assertFalse(moduleRepository.hasResources(RES_AUTO, ResourceType.STRING, "another_unique_string"));
    assertTrue(appResources.hasResources(RES_AUTO, ResourceType.STRING, "title_card_flip"));
    assertFalse(appResources.hasResources(RES_AUTO, ResourceType.STRING, "non_existent_title_card_flip"));

    // Update module resource repository and assert that changes make it all the way up
    PsiFile layoutPsiFile = PsiManager.getInstance(getProject()).findFile(layoutFile);
    assertNotNull(layoutPsiFile);
    assertTrue(moduleRepository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh"));
    ResourceItem item = getSingleItem(moduleRepository, ResourceType.ID, "btn_title_refresh");

    long generation = moduleRepository.getModificationCount();
    int rescans = moduleRepository.getFileRescans();
    long projectGeneration = projectResources.getModificationCount();
    long appGeneration = appResources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(layoutPsiFile);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String string = "<ImageView style=\"@style/TitleBarSeparator\" />";
      int offset = document.getText().indexOf(string);
      document.deleteString(offset, offset + string.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(moduleRepository);
    assertTrue(generation < moduleRepository.getModificationCount());
    assertTrue(projectGeneration < projectResources.getModificationCount());
    assertTrue(appGeneration < appResources.getModificationCount());
    assertThat(moduleRepository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    // Should still be defined:
    assertTrue(moduleRepository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh"));
    assertTrue(appResources.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh"));
    assertTrue(projectResources.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh"));
    ResourceItem newItem = getSingleItem(appResources, ResourceType.ID, "btn_title_refresh");
    assertNotNull(newItem.getSource());
    // However, should be a different item.
    assertNotSame(item, newItem);
  }

  public void testGetResourceDirs() {
    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();

    assertNotSame(res1, res2);

    // res2 is not used as an overlay here; instead we use it to simulate an AAR library below
    ModuleResourceRepository moduleRepository1 =
        ModuleResourceRepository.createForTest(myFacet, Collections.singletonList(res1), RES_AUTO, null);
    ModuleResourceRepository moduleRepository2 =
        ModuleResourceRepository.createForTest(myFacet, Collections.singletonList(res2), RES_AUTO, null);
    ProjectResourceRepository projectResources =
        ProjectResourceRepository.createForTest(myFacet, ImmutableList.of(moduleRepository1, moduleRepository2));
    AppResourceRepository appResources =
        AppResourceRepository.createForTest(myFacet, Collections.singletonList(projectResources), Collections.emptyList());

    Set<VirtualFile> folders = appResources.getResourceDirs();
    assertSameElements(folders, res1, res2);
  }

  public void testGetItemsOfTypeIdIncludeAar() {
    VirtualFile res1 = myFixture.copyFileToProject(LAYOUT, "res/layout/some_layout.xml").getParent().getParent();
    LocalResourceRepository moduleRepository = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res1), RES_AUTO, null);
    LocalResourceRepository projectResources = ProjectResourceRepository.createForTest(myFacet, ImmutableList.of(moduleRepository));

    AarSourceResourceRepository aar = AarTestUtils.getTestAarRepositoryFromExplodedAar();
    AppResourceRepository appResources =
        AppResourceRepository.createForTest(myFacet, ImmutableList.of(projectResources), ImmutableList.of(aar));

    Collection<String> idResources = appResources.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertContainsElements(idResources, "btn_title_refresh");
  }

  public void testNamespaces() {
    // This is necessary to use DynamicResourceValueRepository.
    myFacet.getProperties().ALLOW_USER_CONFIGURATION = false;

    BiFunction<ResourceNamespace, String, DynamicValueResourceRepository> makeDynamicRepo = (namespace, value) -> {
      DynamicResourceValue field = new DynamicResourceValue(ResourceType.STRING, value);
      return DynamicValueResourceRepository.createForTest(myFacet, namespace, Collections.singletonMap("model_value", field));
    };
    ResourceNamespace appNamespace = ResourceNamespace.fromPackageName("com.example.app");

    ModuleResourceRepository appModuleResources = ModuleResourceRepository.createForTest(
        myFacet,
        ImmutableSet.of(
            myFixture.copyFileToProject(VALUES, "app/res/values/values.xml").getParent().getParent(),
            myFixture.copyFileToProject(VALUES_WITH_NAMESPACE, "app/res/values/values_with_namespace.xml").getParent().getParent()),
       appNamespace,
       makeDynamicRepo.apply(appNamespace, "appValue"));

    ResourceNamespace localLibNamespace = ResourceNamespace.fromPackageName("com.localLib");
    ModuleResourceRepository localLibResources = ModuleResourceRepository.createForTest(
        myFacet,
        ImmutableSet.of(
            myFixture.copyFileToProject(VALUES_OVERLAY1, "localLib/res/values/values.xml").getParent().getParent(),
            myFixture.copyFileToProject(VALUES_WITH_NAMESPACE, "localLib/res/values/values_with_namespace.xml").getParent().getParent()),
      localLibNamespace,
      makeDynamicRepo.apply(localLibNamespace, "localLibValue"));

    ProjectResourceRepository projectResources =
        ProjectResourceRepository.createForTest(myFacet, ImmutableList.of(appModuleResources, localLibResources));

    VirtualFile aarLibResDir = myFixture.copyFileToProject(VALUES_OVERLAY2, "aarLib/res/values/values.xml").getParent().getParent();
    ResourceNamespace aarLibNamespace = ResourceNamespace.fromPackageName("com.aarLib");
    AarSourceResourceRepository aarLib =
        AarSourceResourceRepository.createForTest(Paths.get(aarLibResDir.getPath()), aarLibNamespace, "aarlib");

    AppResourceRepository appResources =
        AppResourceRepository.createForTest(myFacet, ImmutableList.of(projectResources), ImmutableList.of(aarLib));

    assertRepositorySelfConsistent(appResources);
    assertRepositorySelfConsistent(appModuleResources);
    assertRepositorySelfConsistent(localLibResources);
    assertRepositorySelfConsistent(aarLib);
    assertRepositorySelfConsistent(projectResources);

    assertThat(appResources.getResources(appNamespace, ResourceType.ID).keySet()).containsExactly("action_flip", "action_next");

    assertThat(projectResources.getNamespaces()).containsExactly(appNamespace, localLibNamespace);
    assertThat(appResources.getNamespaces()).containsExactly(appNamespace, localLibNamespace, aarLibNamespace, ResourceNamespace.TOOLS);

    assertOnlyValue(appResources, appNamespace, "app_name", "Animations Demo");
    assertOnlyValue(appResources, localLibNamespace, "app_name", "Different App Name");
    assertOnlyValue(appResources, aarLibNamespace, "app_name", "Very Different App Name");

    assertOnlyValue(appResources, appNamespace, "model_value", "appValue");
    assertOnlyValue(appResources, localLibNamespace, "model_value", "localLibValue");

    assertThat(appResources.getResources(aarLibNamespace, ResourceType.STRING, "model_value")).isEmpty();

    assertThat(appResources.getResources(appNamespace, ResourceType.ID, "action_next")).hasSize(1);
    assertThat(appResources.getResources(localLibNamespace, ResourceType.ID, "action_next")).isEmpty();
    assertThat(appResources.getResources(aarLibNamespace, ResourceType.ID, "action_next")).isEmpty();

    assertThat(appResources.getResources(appNamespace, ResourceType.STRING, "unique_string")).isEmpty();
    assertThat(appResources.getResources(localLibNamespace, ResourceType.STRING, "unique_string")).hasSize(1);
    assertThat(appResources.getResources(aarLibNamespace, ResourceType.STRING, "unique_string")).isEmpty();

    assertThat(appResources.getResources(appNamespace, ResourceType.STRING, "another_unique_string")).isEmpty();
    assertThat(appResources.getResources(localLibNamespace, ResourceType.STRING, "another_unique_string")).isEmpty();
    assertThat(appResources.getResources(aarLibNamespace, ResourceType.STRING, "another_unique_string")).hasSize(1);

    checkCrossNamespaceReference(appResources,
                                 new ResourceReference(appNamespace, ResourceType.STRING, "using_alias"),
                                 new ResourceReference(aarLibNamespace, ResourceType.STRING, "app_name"),
                                 true);
    checkCrossNamespaceReference(appResources,
                                 new ResourceReference(appNamespace, ResourceType.STRING, "using_package_name"),
                                 new ResourceReference(aarLibNamespace, ResourceType.STRING, "app_name"),
                                 true);
    checkCrossNamespaceReference(appResources,
                                 new ResourceReference(appNamespace, ResourceType.STRING, "this_namespace"),
                                 new ResourceReference(appNamespace, ResourceType.STRING, "app_name"),
                                 true);
    checkCrossNamespaceReference(localLibResources,
                                 new ResourceReference(localLibNamespace, ResourceType.STRING, "using_alias"),
                                 new ResourceReference(aarLibNamespace, ResourceType.STRING, "app_name"),
                                 false);
    checkCrossNamespaceReference(localLibResources,
                                 new ResourceReference(localLibNamespace, ResourceType.STRING, "using_package_name"),
                                 new ResourceReference(aarLibNamespace, ResourceType.STRING, "app_name"),
                                 false);
    checkCrossNamespaceReference(localLibResources,
                                 new ResourceReference(localLibNamespace, ResourceType.STRING, "this_namespace"),
                                 new ResourceReference(localLibNamespace, ResourceType.STRING, "app_name"),
                                 true);
  }

  public void testLibraryResources() {
    addBinaryAarDependency(myModule);
    enableNamespacing("p1.p2");

    LocalResourceRepository appResources = StudioResourceRepositoryManager.getAppResources(myFacet);
    Collection<SingleNamespaceResourceRepository> repositories = appResources.getLeafResourceRepositories();
    assertThat(repositories).hasSize(4);

    List<ResourceItem> items = appResources.getResources(ResourceNamespace.fromPackageName("com.example.mylibrary"),
                                                         ResourceType.STRING, "my_aar_string");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).getResourceValue().getValue()).isEqualTo("This string came from an AARv2");
  }

  private static void checkCrossNamespaceReference(LocalResourceRepository repo,
                                                   ResourceReference toCheck,
                                                   ResourceReference expected,
                                                   boolean shouldExist) {
    ResourceValue referencingValue = getOnlyValue(repo, toCheck.getNamespace(), toCheck.getResourceType(), toCheck.getName());
    assertNotNull(referencingValue);
    ResourceReference reference = referencingValue.getReference();
    assertEquals(expected, reference);

    if (shouldExist) {
      List<ResourceItem> matches = repo.getResources(reference.getNamespace(), reference.getResourceType(), reference.getName());
      assertThat(matches).hasSize(1);
      ResourceItem target = matches.get(0);
      assertNotNull(target);
    }
    else {
      assertThat(repo.getResources(reference.getNamespace(), reference.getResourceType(), reference.getName())).isEmpty();
    }
  }

  private static void assertRepositorySelfConsistent(@NotNull ResourceRepository repository) {
    eachItemInRepo:
    for (ResourceItem item : repository.getAllResources()) {
      ListMultimap<String, ResourceItem> multimap = repository.getResources(item.getNamespace(), item.getType());
      assertThat(multimap).named("Multimap for " + item).isNotNull();

      List<ResourceItem> itemsWithSameName = multimap.get(item.getName());
      assertThat(itemsWithSameName).named("List for " + item).isNotEmpty();

      for (ResourceItem candidate : itemsWithSameName) {
        if (candidate == item) {
          continue eachItemInRepo;
        }
      }

      fail(item + " is not stored correctly.");
    }
  }

  private static void assertOnlyValue(MultiResourceRepository repository,
                                      ResourceNamespace namespace,
                                      String name,
                                      String value) {
    ResourceValue resourceValue = getOnlyValue(repository, namespace, ResourceType.STRING, name);
    assertThat(resourceValue.getValue()).named(resourceValue.toString()).isEqualTo(value);
  }

  @NotNull
  private static ResourceValue getOnlyValue(LocalResourceRepository repository,
                                            ResourceNamespace namespace,
                                            ResourceType resourceType,
                                            String name) {
    String fullName = String.format("@%s:%s/%s", namespace, resourceType, name);

    ListMultimap<String, ResourceItem> multimap = repository.getResources(namespace, resourceType);
    assertThat(multimap).named(String.format("@%s:%s", namespace, resourceType)).isNotNull();

    List<ResourceItem> items = multimap.get(name);
    assertThat(items).named(fullName).hasSize(1);

    ResourceValue resourceValue = Iterables.getOnlyElement(items).getResourceValue();
    assertThat((resourceValue)).named(fullName).isNotNull();
    return resourceValue;
  }
}
