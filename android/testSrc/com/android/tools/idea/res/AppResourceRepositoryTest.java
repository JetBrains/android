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

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceTable;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.stubs.android.ClassFieldStub;
import com.android.util.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;

import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotEquals;

public class AppResourceRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT = "resourceRepository/layout.xml";
  private static final String VALUES = "resourceRepository/values.xml";
  private static final String VALUES_OVERLAY1 = "resourceRepository/valuesOverlay1.xml";
  private static final String VALUES_OVERLAY2 = "resourceRepository/valuesOverlay2.xml";
  private static final String VALUES_WITH_NAMESPACE = "resourceRepository/values_with_namespace_reference.xml";
  private static final String VALUES_OVERLAY2_NO = "resourceRepository/valuesOverlay2No.xml";

  @Override
  public void tearDown() throws Exception {
    try {
      ResourceFolderRegistry.reset();
    }
    finally {
      super.tearDown();
    }
  }

  public void testStable() {
    assertSame(AppResourceRepository.getOrCreateInstance(myFacet), AppResourceRepository.getOrCreateInstance(myFacet));
    assertSame(AppResourceRepository.getOrCreateInstance(myFacet), AppResourceRepository.getOrCreateInstance(myModule));
  }

  public void testStringOrder() {
    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();

    final ModuleResourceRepository moduleRepository = ModuleResourceRepository.createForTest(
      myFacet, Collections.singletonList(res1));
    final ProjectResourceRepository projectResources = ProjectResourceRepository.createForTest(
      myFacet, Collections.singletonList(moduleRepository));
    final AppResourceRepository appResources = AppResourceRepository.createForTest(
      myFacet, Collections.singletonList(projectResources), Collections.emptyList());

    assertOrderedEquals(appResources.getItemsOfType(ResourceType.STRING),
                        Arrays.asList("app_name", "title_crossfade", "title_card_flip", "title_screen_slide", "title_zoom",
                                      "title_layout_changes", "title_template_step", "ellipsis"));
  }

  public void testMerging() {
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

    // res3 is not used as an overlay here; instead we use it to simulate an AAR library below
    final ModuleResourceRepository moduleRepository = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2));
    final ProjectResourceRepository projectResources = ProjectResourceRepository.createForTest(
      myFacet, Collections.singletonList(moduleRepository));

    final AppResourceRepository appResources = AppResourceRepository.createForTest(
      myFacet, Collections.singletonList(projectResources), Collections.emptyList());

    assertTrue(appResources.hasResourceItem(ResourceType.STRING, "title_card_flip"));
    assertFalse(appResources.hasResourceItem(ResourceType.STRING, "non_existent_title_card_flip"));

    assertTrue(projectResources.hasResourceItem(ResourceType.STRING, "title_card_flip"));
    assertFalse(projectResources.hasResourceItem(ResourceType.STRING, "non_existent_title_card_flip"));

    assertTrue(moduleRepository.hasResourceItem(ResourceType.STRING, "title_card_flip"));
    assertFalse(moduleRepository.hasResourceItem(ResourceType.STRING, "non_existent_title_card_flip"));

    FileResourceRepository aar1 = FileResourceRepository.get(VfsUtilCore.virtualToIoFile(res3), null);
    appResources.updateRoots(Arrays.asList(projectResources, aar1), Collections.singletonList(aar1));

    assertTrue(appResources.hasResourceItem(ResourceType.STRING, "another_unique_string"));
    assertTrue(aar1.hasResourceItem(ResourceType.STRING, "another_unique_string"));
    assertFalse(projectResources.hasResourceItem(ResourceType.STRING, "another_unique_string"));
    assertFalse(moduleRepository.hasResourceItem(ResourceType.STRING, "another_unique_string"));
    assertTrue(appResources.hasResourceItem(ResourceType.STRING, "title_card_flip"));
    assertFalse(appResources.hasResourceItem(ResourceType.STRING, "non_existent_title_card_flip"));

    // Update module resource repository and assert that changes make it all the way up
    PsiFile layoutPsiFile = PsiManager.getInstance(getProject()).findFile(layoutFile);
    assertNotNull(layoutPsiFile);
    assertTrue(moduleRepository.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
    final ResourceItem item = ModuleResourceRepositoryTest.getFirstItem(moduleRepository, ResourceType.ID, "btn_title_refresh");

    final long generation = moduleRepository.getModificationCount();
    final long projectGeneration = projectResources.getModificationCount();
    final long appGeneration = appResources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(layoutPsiFile);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String string = "<ImageView style=\"@style/TitleBarSeparator\" />";
      int offset = document.getText().indexOf(string);
      document.deleteString(offset, offset + string.length());
      documentManager.commitDocument(document);
    });

    assertTrue(moduleRepository.isScanPending(layoutPsiFile));
    ApplicationManager.getApplication().invokeLater(() -> {
      assertTrue(generation < moduleRepository.getModificationCount());
      assertTrue(projectGeneration < projectResources.getModificationCount());
      assertTrue(appGeneration < appResources.getModificationCount());
      // Should still be defined:
      assertTrue(moduleRepository.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
      assertTrue(appResources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
      assertTrue(projectResources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
      ResourceItem newItem = ModuleResourceRepositoryTest.getFirstItem(appResources, ResourceType.ID, "btn_title_refresh");
      assertNotNull(newItem.getSource());
      // However, should be a different item
      assertNotSame(item, newItem);
    });
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testGetDeclaredArrayValues() throws IOException {
    final AppResourceRepository appResources = createTestAppResourceRepository(myFacet);
    ImmutableList.Builder<AttrResourceValue> builder = ImmutableList.builder();
    // simple styleable test.
    ImmutableList<AttrResourceValue> attrList = builder.add(
      new AttrResourceValue(new ResourceReference(RES_AUTO, ResourceType.ATTR, "some-attr"), null)).build();
    Integer[] foundValues = appResources.getDeclaredArrayValues(attrList, "Styleable1");
    assertOrderedEquals(foundValues, 0x7f010000);

    // Declared styleables mismatch
    attrList = builder.add(
      new AttrResourceValue(new ResourceReference(RES_AUTO, ResourceType.ATTR, "some-attr"), null),
      new AttrResourceValue(new ResourceReference(RES_AUTO, ResourceType.ATTR, "other-attr"), null)).build();
    assertNull(appResources.getDeclaredArrayValues(attrList, "Styleable1"));

    // slightly complex test.
    builder = ImmutableList.builder();
    attrList = builder
      .add(new AttrResourceValue(new ResourceReference(RES_AUTO, ResourceType.ATTR, "app_attr1"), null),
           new AttrResourceValue(new ResourceReference(RES_AUTO, ResourceType.ATTR, "app_attr2"), null),
           new AttrResourceValue(new ResourceReference(ANDROID, ResourceType.ATTR, "framework-attr1"), null),
           new AttrResourceValue(new ResourceReference(RES_AUTO, ResourceType.ATTR, "app_attr3"), null),
           new AttrResourceValue(new ResourceReference(ANDROID, ResourceType.ATTR, "framework_attr2"), null)).build();
    foundValues = appResources.getDeclaredArrayValues(attrList, "Styleable_with_underscore");
    assertOrderedEquals(foundValues, 0x7f010000, 0x7f010068, 0x01010125, 0x7f010069, 0x01010142);
  }

  public void testGetResourceDirs() throws IOException {
    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();

    assertNotSame(res1, res2);

    // res2 is not used as an overlay here; instead we use it to simulate an AAR library below
    final ModuleResourceRepository moduleRepository = ModuleResourceRepository.createForTest(
      myFacet, Collections.singletonList(res1));
    final ProjectResourceRepository projectResources = ProjectResourceRepository.createForTest(
      myFacet, Collections.singletonList(moduleRepository));

    final AppResourceRepository appResources = AppResourceRepository.createForTest(
      myFacet, Collections.singletonList(projectResources), Collections.emptyList());

    Set<VirtualFile> folders = appResources.getResourceDirs();
    assertSameElements(folders, res1);

    FileResourceRepository aar1 = FileResourceRepository.get(VfsUtilCore.virtualToIoFile(res2), null);
    appResources.updateRoots(Arrays.asList(projectResources, aar1), Collections.singletonList(aar1));

    Set<VirtualFile> foldersWithAar = appResources.getResourceDirs();
    assertSameElements(foldersWithAar, res1, res2);
  }

  public void testGetItemsOfTypeIdIncludeAAR() throws IOException {
    VirtualFile res1 = myFixture.copyFileToProject(LAYOUT, "res/layout/some_layout.xml").getParent().getParent();
    final LocalResourceRepository moduleRepository = ModuleResourceRepository.createForTest(
      myFacet, ImmutableList.of(res1));
    final LocalResourceRepository projectResources = ProjectResourceRepository.createForTest(
      myFacet, ImmutableList.of(moduleRepository));

    FileResourceRepository aar = FileResourceRepositoryTest.getTestRepository();
    final AppResourceRepository appResources = AppResourceRepository.createForTest(
      myFacet, ImmutableList.of(projectResources, aar), ImmutableList.of(aar));

    Collection<String> idResources = appResources.getItemsOfType(ResourceType.ID);
    Map<String, Integer> aarIds = aar.getAllDeclaredIds();
    assertNotNull(aarIds);
    assertFalse(aarIds.isEmpty());
    assertContainsElements(idResources, aarIds.keySet());
    assertFalse(aarIds.keySet().contains("btn_title_refresh"));
    assertContainsElements(idResources, "btn_title_refresh");
  }

  @SuppressWarnings("deprecation")  // For Pair
  public void testDynamicIds() {
    AppResourceRepository repository = AppResourceRepository.getOrCreateInstance(myFacet);
    Integer stringId = repository.getResourceId(ResourceType.STRING, "string");
    assertNotNull(stringId);
    Integer styleId = repository.getResourceId(ResourceType.STYLE, "style");
    assertNotNull(styleId);
    Integer layoutId = repository.getResourceId(ResourceType.LAYOUT, "layout");
    assertNotNull(layoutId);
    assertEquals(stringId, repository.getResourceId(ResourceType.STRING, "string"));
    assertEquals(Pair.of(ResourceType.STRING, "string"), repository.resolveResourceId(stringId));
    assertEquals(styleId, repository.getResourceId(ResourceType.STYLE, "style"));
    assertEquals(Pair.of(ResourceType.STYLE, "style"), repository.resolveResourceId(styleId));
    assertEquals(layoutId, repository.getResourceId(ResourceType.LAYOUT, "layout"));
    assertEquals(Pair.of(ResourceType.LAYOUT, "layout"), repository.resolveResourceId(layoutId));
  }

  public void testResetDynamicIds() {
    AppResourceRepository repository = AppResourceRepository.getOrCreateInstance(myFacet);
    Integer stringId = repository.getResourceId(ResourceType.STRING, "string");
    Integer styleId = repository.getResourceId(ResourceType.STYLE, "style");
    Integer layoutId = repository.getResourceId(ResourceType.LAYOUT, "layout");
    repository.resetDynamicIds(false);
    // They should be all gone now.
    assertNull(repository.resolveResourceId(stringId));
    assertNull(repository.resolveResourceId(styleId));
    assertNull(repository.resolveResourceId(layoutId));
    // Check in different order. These should be new IDs.
    assertNotEquals(layoutId, repository.getResourceId(ResourceType.LAYOUT, "layout"));
    assertNotEquals(stringId, repository.getResourceId(ResourceType.STRING, "string"));
    assertNotEquals(styleId, repository.getResourceId(ResourceType.STYLE, "style"));
  }

  @SuppressWarnings("deprecation")  // For Pair
  public void testSetCompiledResources() {
    AppResourceRepository repository = AppResourceRepository.getOrCreateInstance(myFacet);
    Integer stringId = repository.getResourceId(ResourceType.STRING, "string");
    Integer styleId = repository.getResourceId(ResourceType.STYLE, "style");
    Integer layoutId = repository.getResourceId(ResourceType.LAYOUT, "layout");

    TIntObjectHashMap<Pair<ResourceType, String>> id2res = new TIntObjectHashMap<>();
    id2res.put(0x7F000000, Pair.of(ResourceType.STRING, "string"));
    id2res.put(0x7F010000, Pair.of(ResourceType.STYLE, "style"));
    id2res.put(0x7F020000, Pair.of(ResourceType.LAYOUT, "layout"));

    Map<ResourceType, TObjectIntHashMap<String>> res2id = Maps.newHashMap();
    TObjectIntHashMap<String> stringIdMap = new TObjectIntHashMap<>();
    stringIdMap.put("string", 0x7F000000);
    res2id.put(ResourceType.STRING, stringIdMap);
    TObjectIntHashMap<String> styleIdMap = new TObjectIntHashMap<>();
    styleIdMap.put("style", 0x7F010000);
    res2id.put(ResourceType.STYLE, styleIdMap);
    TObjectIntHashMap<String> layoutIdMap = new TObjectIntHashMap<>();
    layoutIdMap.put("layout", 0x7F020000);
    res2id.put(ResourceType.LAYOUT, layoutIdMap);

    repository.setCompiledResources(id2res, Collections.emptyMap(), res2id);

    // Compiled resources should replace the dynamic IDs.
    assertNotEquals(stringId, repository.getResourceId(ResourceType.STRING, "string"));
    assertEquals(Integer.valueOf(0x7F000000), repository.getResourceId(ResourceType.STRING, "string"));
    assertNotEquals(styleId, repository.getResourceId(ResourceType.STYLE, "style"));
    assertEquals(Integer.valueOf(0x7F010000), repository.getResourceId(ResourceType.STYLE, "style"));
    assertNotEquals(layoutId, repository.getResourceId(ResourceType.LAYOUT, "layout"));
    assertEquals(Integer.valueOf(0x7F020000), repository.getResourceId(ResourceType.LAYOUT, "layout"));

    // Dynamic IDs should still resolve though.
    assertEquals(Pair.of(ResourceType.STRING, "string"), repository.resolveResourceId(stringId));
    assertEquals(Pair.of(ResourceType.STYLE, "style"), repository.resolveResourceId(styleId));
    assertEquals(Pair.of(ResourceType.LAYOUT, "layout"), repository.resolveResourceId(layoutId));

    // But not after reset.
    repository.resetDynamicIds(false);
    assertNull(repository.resolveResourceId(stringId));
    assertNull(repository.resolveResourceId(styleId));
    assertNull(repository.resolveResourceId(layoutId));
  }

  public void testNamespaces() {
    //// This is necessary to use DynamicResourceValueRepository.
    myFacet.getProperties().ALLOW_USER_CONFIGURATION = false;

    BiFunction<ResourceNamespace, String, DynamicResourceValueRepository> makeDynamicRepo = (namespace, value) -> {
      ClassFieldStub field = new ClassFieldStub("string",
                                                "model_value",
                                                value,
                                                "value from model",
                                                Collections.emptySet());
      return DynamicResourceValueRepository.createForTest(myFacet, namespace, Collections.singletonMap("model_value", field));
    };

    ModuleResourceRepository app =
      ModuleResourceRepository.createForTest(myFacet,
                                             ImmutableList.of(
                                               myFixture.copyFileToProject(VALUES,
                                                                           "app/res/values/values.xml").getParent().getParent(),
                                               myFixture.copyFileToProject(VALUES_WITH_NAMESPACE,
                                                                           "app/res/values/values_with_namespace.xml").getParent().getParent()),
                                             RES_AUTO,
                                             makeDynamicRepo.apply(RES_AUTO, "appValue"));

    ResourceNamespace localLibNamespace = ResourceNamespace.fromPackageName("com.localLib");
    ModuleResourceRepository localLib =
      ModuleResourceRepository.createForTest(myFacet,
                                             ImmutableList.of(
                                               myFixture.copyFileToProject(VALUES_OVERLAY1, "localLib/res/values/values.xml").getParent().getParent(),
                                               myFixture.copyFileToProject(VALUES_WITH_NAMESPACE, "localLib/res/values/values_with_namespace.xml").getParent().getParent()),
                                             localLibNamespace,
                                             makeDynamicRepo.apply(localLibNamespace, "localLibValue"));

    ProjectResourceRepository projectResourceRepository = ProjectResourceRepository.createForTest(myFacet, Arrays.asList(app, localLib));

    VirtualFile aarLibResources = myFixture.copyFileToProject(VALUES_OVERLAY2, "aarLib/res/values/values.xml").getParent().getParent();
    ResourceNamespace aarLibNamespace = ResourceNamespace.fromPackageName("com.aarLib");
    FileResourceRepository aarLib = FileResourceRepository.createForTest(new File(aarLibResources.getPath()), aarLibNamespace, null);

    AppResourceRepository appResourceRepository = AppResourceRepository.createForTest(myFacet,
                                                                                      Arrays.asList(projectResourceRepository, aarLib),
                                                                                      Collections.singletonList(aarLib));

    assertRepositorySelfConsistent(appResourceRepository);
    assertRepositorySelfConsistent(app);
    assertRepositorySelfConsistent(localLib);
    assertRepositorySelfConsistent(aarLib);
    assertRepositorySelfConsistent(projectResourceRepository);

    ResourceTable resourceTable = appResourceRepository.getItems();

    assertThat(projectResourceRepository.getNamespaces()).containsExactly(RES_AUTO, localLibNamespace);
    assertThat(appResourceRepository.getNamespaces()).containsExactly(RES_AUTO, localLibNamespace, aarLibNamespace);

    assertOnlyValue(appResourceRepository, RES_AUTO, ResourceType.STRING, "app_name", "Animations Demo");
    assertOnlyValue(appResourceRepository, localLibNamespace, ResourceType.STRING, "app_name", "Different App Name");
    assertOnlyValue(appResourceRepository, aarLibNamespace, ResourceType.STRING, "app_name", "Very Different App Name");

    assertOnlyValue(appResourceRepository, RES_AUTO, ResourceType.STRING, "model_value", "appValue");
    assertOnlyValue(appResourceRepository, localLibNamespace, ResourceType.STRING, "model_value", "localLibValue");
    assertThat(resourceTable.get(aarLibNamespace, ResourceType.STRING).get("model_value")).isEmpty();

    assertThat(resourceTable.get(RES_AUTO, ResourceType.ID).get("action_next")).hasSize(1);
    assertThat(resourceTable.get(localLibNamespace, ResourceType.ID).get("action_next")).isEmpty();
    assertThat(resourceTable.get(aarLibNamespace, ResourceType.ID).get("action_next")).isEmpty();

    assertThat(resourceTable.get(RES_AUTO, ResourceType.STRING).get("unique_string")).isEmpty();
    assertThat(resourceTable.get(localLibNamespace, ResourceType.STRING).get("unique_string")).hasSize(1);
    assertThat(resourceTable.get(aarLibNamespace, ResourceType.STRING).get("unique_string")).isEmpty();

    assertThat(resourceTable.get(RES_AUTO, ResourceType.STRING).get("another_unique_string")).isEmpty();
    assertThat(resourceTable.get(localLibNamespace, ResourceType.STRING).get("another_unique_string")).isEmpty();
    assertThat(resourceTable.get(aarLibNamespace, ResourceType.STRING).get("another_unique_string")).hasSize(1);

    checkCrossNamespaceReference(appResourceRepository,
                                 new ResourceReference(RES_AUTO, ResourceType.STRING, "using_alias"),
                                 new ResourceReference(aarLibNamespace, ResourceType.STRING, "app_name"),
                                 true);
    checkCrossNamespaceReference(appResourceRepository,
                                 new ResourceReference(RES_AUTO, ResourceType.STRING, "using_package_name"),
                                 new ResourceReference(aarLibNamespace, ResourceType.STRING, "app_name"),
                                 true);
    checkCrossNamespaceReference(appResourceRepository,
                                 new ResourceReference(RES_AUTO, ResourceType.STRING, "this_namespace"),
                                 new ResourceReference(RES_AUTO, ResourceType.STRING, "app_name"),
                                 true);
    checkCrossNamespaceReference(localLib,
                                 new ResourceReference(localLibNamespace, ResourceType.STRING, "using_alias"),
                                 new ResourceReference(aarLibNamespace, ResourceType.STRING, "app_name"),
                                 false);
    checkCrossNamespaceReference(localLib,
                                 new ResourceReference(localLibNamespace, ResourceType.STRING, "using_package_name"),
                                 new ResourceReference(aarLibNamespace, ResourceType.STRING, "app_name"),
                                 false);
    checkCrossNamespaceReference(localLib,
                                 new ResourceReference(localLibNamespace, ResourceType.STRING, "this_namespace"),
                                 new ResourceReference(localLibNamespace, ResourceType.STRING, "app_name"),
                                 true);
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
      List<ResourceItem> matches = repo.getItems().get(reference);
      assertSize(1, matches);
      ResourceItem target = matches.get(0);
      assertNotNull(target);
    }
    else {
      assertNull(repo.getItems().get(reference));
    }
  }

  private static void assertRepositorySelfConsistent(AbstractResourceRepository repository) {
    ResourceTable resourceTable = repository.getItems();

    eachItemInRepo:
    for (ResourceItem item : repository.getAllResourceItems()) {

      ListMultimap<String, ResourceItem> multimap = resourceTable.get(item.getNamespace(), item.getType());
      assertThat(multimap).named("Multimap for " + item).isNotNull();

      List<ResourceItem> itemsWithSameName = multimap.get(item.getName());
      assertThat(itemsWithSameName).named("List for " + item).isNotEmpty();

      for (ResourceItem candidate : itemsWithSameName) {
        if (candidate == item) {
          continue eachItemInRepo;
        }
      }

      Assert.fail(item + " is not stored correctly.");
    }
  }

  private static void assertOnlyValue(MultiResourceRepository repository,
                                      ResourceNamespace namespace,
                                      ResourceType resourceType,
                                      String name,
                                      String value) {
    ResourceValue resourceValue = getOnlyValue(repository, namespace, resourceType, name);
    assertThat(resourceValue.getValue()).named(resourceValue.toString()).isEqualTo(value);
  }

  @NotNull
  private static ResourceValue getOnlyValue(LocalResourceRepository repository,
                                            ResourceNamespace namespace,
                                            ResourceType resourceType,
                                            String name) {
    String fullName = String.format("@%s:%s/%s", namespace, resourceType, name);

    ListMultimap<String, ResourceItem> multimap = repository.getItems().get(namespace, resourceType);
    assertThat(multimap).named(String.format("@%s:%s", namespace, resourceType)).isNotNull();

    List<ResourceItem> items = multimap.get(name);
    assertThat(items).named(fullName).hasSize(1);

    ResourceValue resourceValue = Iterables.getOnlyElement(items).getResourceValue(false);
    assertThat((resourceValue)).named(fullName).isNotNull();
    return resourceValue;
  }

  static AppResourceRepository createTestAppResourceRepository(AndroidFacet facet) throws IOException {
    final ModuleResourceRepository moduleRepository = ModuleResourceRepository.createForTest(facet, Collections.emptyList());
    final ProjectResourceRepository projectResources = ProjectResourceRepository.createForTest(
      facet, Collections.singletonList(moduleRepository));

    final AppResourceRepository appResources = AppResourceRepository.createForTest(
      facet, Collections.singletonList(projectResources), Collections.emptyList());
    FileResourceRepository aar = FileResourceRepositoryTest.getTestRepository();
    appResources.updateRoots(Arrays.asList(projectResources, aar), Collections.singletonList(aar));
    return appResources;
  }

  // TODO: When we can load gradle projects from unit tests, test that we properly override
  // library resources (from AARs) with resources in the main app (e.g. that computeRepositories()
  // places the libraries *before* the module resources)
}
