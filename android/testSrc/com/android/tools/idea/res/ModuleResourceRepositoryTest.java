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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

@SuppressWarnings("SpellCheckingInspection")
public class ModuleResourceRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT = "resourceRepository/layout.xml";
  private static final String LAYOUT_OVERLAY = "resourceRepository/layoutOverlay.xml";
  private static final String LAYOUT_IDS_1 = "resourceRepository/layout_ids1.xml";
  private static final String LAYOUT_IDS_2 = "resourceRepository/layout_ids2.xml";
  private static final String VALUES = "resourceRepository/values.xml";
  private static final String VALUES_OVERLAY1 = "resourceRepository/valuesOverlay1.xml";
  private static final String VALUES_OVERLAY2 = "resourceRepository/valuesOverlay2.xml";
  private static final String VALUES_OVERLAY2_NO = "resourceRepository/valuesOverlay2No.xml";

  public void testStable() {
    assertSame(ModuleResourceRepository.getModuleResources(myFacet, true), ModuleResourceRepository.getModuleResources(myFacet, true));
    assertSame(ModuleResourceRepository.getModuleResources(myFacet, true), ModuleResourceRepository.getModuleResources(myModule, true));
  }

  public void testSingleResourceFolder() {
    LocalResourceRepository repository = ModuleResourceRepository.create(myFacet);
    assertTrue(repository instanceof ResourceFolderRepository);
  }

  public void testOverlays() {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT_OVERLAY, "res2/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT_IDS_1, "res2/layout/layout_ids1.xml");
    myFixture.copyFileToProject(LAYOUT_IDS_2, "res2/layout/layout_ids2.xml");
    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile res3 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res3/values/nameDoesNotMatter.xml").getParent().getParent();
    myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res3/values-no/values.xml");

    assertNotSame(res1, res2);
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);

    ModuleResourceRepository resources = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2, res3));

    // Check that values are handled correctly. First a plain value (not overridden anywhere).
    assertStringIs(resources, "title_layout_changes", "Layout Changes");

    // Check that an overridden key (overridden in just one flavor) is picked up
    assertStringIs(resources, "title_crossfade", "Complex Crossfade"); // Overridden in res2
    assertStringIs(resources, "title_zoom", "Zoom!"); // Overridden in res3

    // Make sure that new/unique strings from flavors are available
    assertStringIs(resources, "unique_string", "Unique"); // Overridden in res2
    assertStringIs(resources, "another_unique_string", "Another Unique", false); // Overridden in res3

    // Check that an overridden key (overridden in multiple flavors) picks the last one
    assertStringIs(resources, "app_name", "Very Different App Name", false); // res3 (not unique because we have a values-no item too)

    // Layouts: Should only be offered id's from the overriding layout (plus those defined in values.xml)
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_next")); // from values.xml
    assertTrue(resources.hasResourceItem(ResourceType.ID, "noteArea")); // from res2 layout1.xml

    // Layout masking does not currently work. I'm not 100% certain what the intended behavior is
    // here (e.g. res1's layout1 contains @+id/button1, res2's layout1 does not; should @+id/button1 be visible?)
    //assertFalse(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh")); // masked in res1 by res2's layout replacement

    // Check that localized lookup (qualifier matching works)
    List<ResourceItem> stringList = resources.getResourceItem(ResourceType.STRING, "another_unique_string");
    assertNotNull(stringList);
    assertSize(2, stringList);
    FolderConfiguration valueConfig = FolderConfiguration.getConfigForFolder("values-no");
    assertNotNull(valueConfig);
    ResourceValue stringValue = resources.getConfiguredResources(ResourceType.STRING, valueConfig).get("another_unique_string");
    assertNotNull(stringValue);
    assertEquals("En Annen", stringValue.getValue());

    // Change flavor order and make sure things are updated and work correctly
    resources.updateRoots(Arrays.asList(res1, res3, res2));

    // Should now be picking app_name from res2 rather than res3 since it's now last
    assertStringIs(resources, "app_name", "Different App Name", false); // res2

    // Sanity check other merging
    assertStringIs(resources, "title_layout_changes", "Layout Changes");
    assertStringIs(resources, "title_crossfade", "Complex Crossfade"); // Overridden in res2
    assertStringIs(resources, "title_zoom", "Zoom!"); // Overridden in res3
    assertStringIs(resources, "unique_string", "Unique"); // Overridden in res2
    assertStringIs(resources, "another_unique_string", "Another Unique", false); // Overridden in res3

    // Hide a resource root (res2)
    resources.updateRoots(Arrays.asList(res1, res3));

    // No longer aliasing the main layout
    assertTrue(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh")); // res1 layout1.xml
    assertTrue(resources.hasResourceItem(ResourceType.ID, "noteArea")); // from res1 layout1.xml
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_next")); // from values.xml

    assertStringIs(resources, "title_crossfade", "Simple Crossfade"); // No longer overridden in res2

    // Finally ensure that we can switch roots repeatedly (had some earlier bugs related to root unregistration)
    resources.updateRoots(Arrays.asList(res1, res3, res2));
    resources.updateRoots(Arrays.asList(res1));
    resources.updateRoots(Arrays.asList(res1, res3, res2));
    resources.updateRoots(Arrays.asList(res1));
    resources.updateRoots(Arrays.asList(res1, res3, res2));
    resources.updateRoots(Arrays.asList(res2));
    resources.updateRoots(Arrays.asList(res1));
    resources.updateRoots(Arrays.asList(res1, res2, res3));
    assertStringIs(resources, "title_layout_changes", "Layout Changes");

    // Make sure I get all the resource ids (there can be multiple; these are not replaced via overlays)
    List<ResourceItem> ids = resources.getResourceItem(ResourceType.ID, "my_id");
    assertNotNull(ids);
    assertSize(2, ids);
    Collections.sort(ids, new Comparator<ResourceItem>() {
      @SuppressWarnings("ConstantConditions")
      @Override
      public int compare(ResourceItem item1, ResourceItem item2) {
        return item1.getSource().getFile().getName().compareTo(item2.getSource().getFile().getName());
      }
    });
    //noinspection ConstantConditions
    assertEquals("layout_ids1.xml", ids.get(0).getSource().getFile().getName());
    //noinspection ConstantConditions
    assertEquals("layout_ids2.xml", ids.get(1).getSource().getFile().getName());
  }

  public void testOverlayUpdates1() {
    final VirtualFile layout = myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    final VirtualFile layoutOverlay = myFixture.copyFileToProject(LAYOUT_OVERLAY, "res2/layout/layout1.xml");
    VirtualFile res1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile res3 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res3/values/nameDoesNotMatter.xml").getParent().getParent();
    myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res3/values-no/values.xml");
    ModuleResourceRepository resources = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2, res3));
    assertStringIs(resources, "title_layout_changes", "Layout Changes"); // sanity check

    // Layout resource check:
    // Check that our @/layout/layout1 resource currently refers to res2 override,
    // then rename it to @layout/layout2, and verify that we have both, and then
    // rename base to @layout/layout2 and verify that we are back to overriding.

    assertTrue(resources.hasResourceItem(ResourceType.LAYOUT, "layout1"));
    assertFalse(resources.hasResourceItem(ResourceType.LAYOUT, "layout2"));
    ResourceItem layout1 = getSingleItem(resources, ResourceType.LAYOUT, "layout1");
    assertItemIsInDir(res2, layout1);

    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          layoutOverlay.rename(this, "layout2.xml");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    assertTrue(resources.getModificationCount() > generation);

    assertTrue(resources.hasResourceItem(ResourceType.LAYOUT, "layout2"));
    assertTrue(resources.hasResourceItem(ResourceType.LAYOUT, "layout1"));

    // Layout should now be coming through from res1 since res2 is no longer overriding it
    layout1 = getSingleItem(resources, ResourceType.LAYOUT, "layout1");
    assertItemIsInDir(res1, layout1);

    ResourceItem layout2 = getSingleItem(resources, ResourceType.LAYOUT, "layout2");
    assertItemIsInDir(res2, layout2);

    // Now rename layout1 to layout2 to hide it again
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          layout.rename(this, "layout2.xml");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    assertTrue(resources.getModificationCount() > generation);

    assertTrue(resources.hasResourceItem(ResourceType.LAYOUT, "layout2"));
    assertFalse(resources.hasResourceItem(ResourceType.LAYOUT, "layout1"));

    layout2 = getSingleItem(resources, ResourceType.LAYOUT, "layout2");
    assertItemIsInDir(res2, layout2);
  }

  public void testOverlayUpdates2() {
    // Like testOverlayUpdates1, but rather than testing changes to layout resources (file-based resource)
    // perform document edits in value-documents

    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT_OVERLAY, "res2/layout/layout1.xml");
    VirtualFile values1 = myFixture.copyFileToProject(VALUES, "res/values/values.xml");
    VirtualFile values2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml");
    VirtualFile values3 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res3/values/nameDoesNotMatter.xml");
    final VirtualFile values3No = myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res3/values-no/values.xml");
    VirtualFile res1 = values1.getParent().getParent();
    VirtualFile res2 = values2.getParent().getParent();
    VirtualFile res3 = values3.getParent().getParent();
    ModuleResourceRepository resources = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2, res3));
    PsiFile psiValues1 = PsiManager.getInstance(getProject()).findFile(values1);
    assertNotNull(psiValues1);
    PsiFile psiValues2 = PsiManager.getInstance(getProject()).findFile(values2);
    assertNotNull(psiValues2);
    PsiFile psiValues3 = PsiManager.getInstance(getProject()).findFile(values3);
    assertNotNull(psiValues3);
    PsiFile psiValues3No = PsiManager.getInstance(getProject()).findFile(values3No);
    assertNotNull(psiValues3No);

    // Initial state; sanity check from #testOverlays()
    assertStringIs(resources, "title_layout_changes", "Layout Changes");
    assertStringIs(resources, "title_crossfade", "Complex Crossfade"); // Overridden in res2
    assertStringIs(resources, "title_zoom", "Zoom!"); // Overridden in res3
    assertStringIs(resources, "unique_string", "Unique"); // Overridden in res2
    assertStringIs(resources, "another_unique_string", "Another Unique", false); // Overridden in res3
    assertStringIs(resources, "app_name", "Very Different App Name", false); // res3 (not unique because we have a values-no item too)

    // Value resource check:
    // Verify that an edit in a value file, both in a non-overridden and an overridden
    // value, is observed; and that an override in an overridden value is not observed.

    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "title_layout_changes"));
    ResourceItem appName = getFirstItem(resources, ResourceType.STRING, "app_name");
    assertItemIsInDir(res3, appName);
    assertStringIs(resources, "app_name", "Very Different App Name", false); // res3 (not unique because we have a values-no item too)

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiValues3);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        int offset = document.getText().indexOf("Very Different App Name");
        document.insertString(offset, "Not ");
        documentManager.commitDocument(document);
      }
    });
    // The first edit to psiValues3 causes ResourceFolderRepository to transition from non-Psi -> Psi which requires a rescan.
    assertTrue(resources.isScanPending(psiValues3));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.getModificationCount() > generation);

    // Should still be defined in res3 but have new value.
    // The order of items may have swapped if a full rescan is done.
    List<ResourceItem> list = resources.getResourceItem(ResourceType.STRING, "app_name");
    assertNotNull(list);
    assertSize(2, list);
    appName = ContainerUtil.find(list, new Condition<ResourceItem>() {
      @Override
      public boolean value(ResourceItem resourceItem) {
        return resourceItem.getQualifiers().isEmpty();
      }
    });
    assertNotNull(appName);
    assertItemIsInDir(res3, appName);
    ResourceValue appNameResourceValue = appName.getResourceValue(false);
    assertNotNull(appNameResourceValue);
    assertEquals("Not Very Different App Name", appNameResourceValue.getValue());

    // Try renaming the item name.
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        int offset = document.getText().indexOf("app_name");
        document.insertString(offset, "r");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(resources.getModificationCount() > generation);
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "rapp_name"));

    appName = getFirstItem(resources, ResourceType.STRING, "app_name");
    // The item is still under res3, but now it's in the Norwegian translation
    assertEquals("no", appName.getSource().getQualifiers());
    assertStringIs(resources, "app_name", "Forskjellig Navn", false);

    // Delete that file:
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          values3No.delete(this);
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    assertTrue(resources.getModificationCount() > generation);

    // Now the item is no longer available in res3; should fallback to res 2
    appName = getFirstItem(resources, ResourceType.STRING, "app_name");
    assertItemIsInDir(res2, appName);
    assertStringIs(resources, "app_name", "Different App Name", false);

    // Check that editing an overridden attribute does not count as a change
    final Document document2 = documentManager.getDocument(psiValues1);
    assertNotNull(document2);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        int offset = document2.getText().indexOf("Animations Demo");
        document2.insertString(offset, "Cool ");
        documentManager.commitDocument(document2);
      }
    });
    // The first edit to psiValues1 causes ResourceFolderRepository to transition from non-Psi -> Psi which requires a rescan.
    assertTrue(resources.isScanPending(psiValues1));
    UIUtil.dispatchAllInvocationEvents();
    // Unaffected by above change
    assertStringIs(resources, "app_name", "Different App Name", false);

    // Finally check that editing an non-overridden attribute also gets picked up as a change
    generation = resources.getModificationCount();
    // Observe after the rescan, so that an edit causes a generation bump.
    assertStringIs(resources, "title_layout_changes", "Layout Changes");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        int offset = document2.getText().indexOf("Layout Changes");
        document2.insertString(offset, "New ");
        documentManager.commitDocument(document2);
      }
    });
    assertTrue(resources.getModificationCount() > generation);
    assertStringIs(resources, "title_layout_changes", "New Layout Changes", false);
  }

  public void testHasResourcesOfType() {
    // Test hasResourcesOfType merging (which may be optimized to be lighter-weight than map merging).
    VirtualFile res1 = myFixture.copyFileToProject(LAYOUT, "res/layout/layout.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();

    assertNotSame(res1, res2);
    ModuleResourceRepository resources = ModuleResourceRepository.createForTest(myFacet, Arrays.asList(res1, res2));
    EnumSet<ResourceType> typesWithoutRes3 = EnumSet.of(ResourceType.ARRAY, ResourceType.ID, ResourceType.LAYOUT,
                                                        ResourceType.STRING, ResourceType.STYLE);

    assertHasExactResourceTypes(resources, typesWithoutRes3);
    // Now update the repo with additional files, to test that merging picks up the new types.
    VirtualFile values3 = myFixture.copyFileToProject(VALUES, "res3/values/many_more_values.xml");
    VirtualFile res3 = values3.getParent().getParent();
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);
    resources.updateRoots(Arrays.asList(res1, res2, res3));

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

  // Unit test support methods

  static void assertItemIsInDir(VirtualFile dir, ResourceItem item) {
    ResourceFile resourceFile = item.getSource();
    assertNotNull(resourceFile);
    VirtualFile parent = VfsUtil.findFileByIoFile(resourceFile.getFile(), false);
    assertNotNull(parent);
    assertEquals(dir, parent.getParent().getParent());
  }

  static void assertStringIs(LocalResourceRepository repository, String key, String expected) {
    assertStringIs(repository, key, expected, true);
  }

  @NotNull
  private static ResourceItem getSingleItem(LocalResourceRepository repository, ResourceType type, String key) {
    List<ResourceItem> list = repository.getResourceItem(type, key);
    assertNotNull(list);
    assertSize(1, list);
    ResourceItem item = list.get(0);
    assertNotNull(item);
    return item;
  }

  @NotNull
  static ResourceItem getFirstItem(LocalResourceRepository repository, ResourceType type, String key) {
    List<ResourceItem> list = repository.getResourceItem(type, key);
    assertNotNull(list);
    ResourceItem item = list.get(0);
    assertNotNull(item);
    return item;
  }

  static void assertStringIs(LocalResourceRepository repository, String key, String expected, boolean mustBeUnique) {
    assertTrue(repository.hasResourceItem(ResourceType.STRING, key));
    List<ResourceItem> list = repository.getResourceItem(ResourceType.STRING, key);
    assertNotNull(list);

    // generally we expect just one item (e.g. overlays should not visible, which is why we assert a single item, but for items
    // that for example have translations there could be multiple items, and we test this, so allow assertion to specify whether it's
    // expected)
    if (mustBeUnique) {
      assertSize(1, list);
    }

    ResourceItem item = list.get(0);
    ResourceValue resourceValue = item.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals(expected, resourceValue.getValue());
  }

  static void assertHasExactResourceTypes(LocalResourceRepository resources, EnumSet<ResourceType> types) {
    for (ResourceType type : ResourceType.values()) {
      if (types.contains(type)) {
        assertTrue(resources.hasResourcesOfType(type));
      }
      else {
        assertFalse(resources.hasResourcesOfType(type));
      }
    }
  }

  public void testAllowEmpty() {
    assertTrue(LintUtils.assertionsEnabled()); // this test should be run with assertions enabled!
    LocalResourceRepository repository = ModuleResourceRepository.createForTest(myFacet, Collections.<VirtualFile>emptyList());
    assertNotNull(repository);
    repository.getModificationCount();
    assertEmpty(repository.getItemsOfType(ResourceType.ID));
  }
}
