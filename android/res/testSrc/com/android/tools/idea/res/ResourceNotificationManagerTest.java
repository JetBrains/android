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

import static com.android.testutils.AsyncTestUtils.waitForCondition;
import static com.android.tools.idea.testing.AndroidTestUtils.waitForResourceRepositoryUpdates;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.res.ResourceNotificationManager.Reason;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceVersion;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.android.tools.idea.util.ReformatUtil;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.ui.UIUtil;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link ResourceNotificationManager}.
 */
@RunsInEdt
public class ResourceNotificationManagerTest {
  @Rule
  public EdtAndroidProjectRule projectRule = new EdtAndroidProjectRule(AndroidProjectRule.onDisk().initAndroid(true));
  private CodeInsightTestFixture myFixture;
  private Module myModule;
  private Project myProject;
  private AndroidFacet myFacet;

  @Before
  public void setUp() {
    myFixture = projectRule.getFixture();
    myModule = myFixture.getModule();
    myProject = projectRule.getProject();
    myFacet = AndroidFacet.getInstance(myModule);
  }

  @Test
  public void testEditNotifications() throws Exception {
    @Language("XML") String xml;

    // Setup sample project: a strings file, and a couple of layout file

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
          "    android:layout_width=\"match_parent\"\n" +
          "    android:layout_height=\"match_parent\">\n" +
          "    <!-- My comment -->\n" +
          "    <TextView\n" +
          "        android:layout_width=\"match_parent\"\n" +
          "        android:layout_height=\"match_parent\"\n" +
          "        android:text=\"@string/hello\" />\n" +
          "</FrameLayout>";
    XmlFile layout1 = (XmlFile)projectRule.getFixture().addFileToProject("res/layout/my_layout1.xml", xml);
    @SuppressWarnings("ConstantConditions")
    VirtualFile resourceDir = layout1.getParent().getParent().getVirtualFile();
    assertNotNull(resourceDir);

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
          "    android:layout_width=\"match_parent\"\n" +
          "    android:layout_height=\"match_parent\" />\n";
    XmlFile layout2 = (XmlFile)myFixture.addFileToProject("res/layout/my_layout2.xml", xml);

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<resources>\n" +
          "    <string name=\"hello\">Hello</string>\n" +
          "\n" +
          "    <!-- Base application theme. -->\n" +
          "    <style name=\"AppTheme\" parent=\"Theme.AppCompat.Light.DarkActionBar\">\n" +
          "        <!-- Customize your theme here. -->\n" +
          "        <item name=\"android:colorBackground\">#ff0000</item>\n" +
          "    </style>" +
          "</resources>";
    XmlFile values1 = (XmlFile)myFixture.addFileToProject("res/values/my_values1.xml", xml);

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<resources>\n" +
          "    \n" +
          "</resources>";
    myFixture.addFileToProject("res/values/colors.xml", xml);

    Configuration configuration1 = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout1.getVirtualFile());
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myProject);

    // Listener 1: Listens for changes in layout 1.
    Ref<Boolean> called1 = new Ref<>(false);
    Ref<Set<Reason>> calledValue1 = new Ref<>();
    ResourceChangeListener listener1 = reason -> {
      called1.set(true);
      calledValue1.set(reason);
    };

    // Listener 2: Only listens for general changes in the module.
    Ref<Boolean> called2 = new Ref<>(false);
    Ref<Set<Reason>> calledValue2 = new Ref<>();
    ResourceChangeListener listener2 = reason -> {
      called2.set(true);
      calledValue2.set(reason);
    };

    manager.addListener(listener1, myFacet, layout1.getVirtualFile(), configuration1);
    manager.addListener(listener2, myFacet, null, null);

    // Make sure that when we're modifying multiple files, with complicated
    // edits (that trigger full file rescans), we handle that scenario correctly.
    clear(called1, calledValue1, called2, calledValue2);
    // There's actually some special optimizations done via PsiResourceItem#recomputeValue
    // to only mark the resource repository changed if the value has actually been looked
    // up. This allows us to not recompute layout if you're editing some string that
    // hasn't actually been looked up and rendered in a layout. In order to make sure
    // that that optimization doesn't kick in here, we need to look up the value of
    // the resource item first:
    //noinspection ConstantConditions
    assertEquals("#ff0000",
                 configuration1.getResourceResolver()
                   .getStyle(new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "AppTheme"))
                   .getItem(ResourceNamespace.ANDROID, "colorBackground").getValue());
    IdeResourcesUtil.createValueResource(myProject, resourceDir, "color2", ResourceType.COLOR, "colors.xml",
                                         Collections.singletonList("values"), "#fa2395");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);
    clear(called1, calledValue1, called2, calledValue2);
    @SuppressWarnings("ConstantConditions")
    XmlTag tag = values1.getDocument().getRootTag().getSubTags()[1].getSubTags()[0];
    assertEquals("item", tag.getName());
    WriteCommandAction.runWriteCommandAction(myProject, () -> tag.getValue().setEscapedText("@color/color2"));
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT); ///

    // First check: Modify the layout by changing @string/hello to @string/hello_world
    // and verify that our listeners are called.
    ResourceVersion version1 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    addText(layout1, "@string/hello^", "_world");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.EDIT);
    ResourceVersion version2 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertNotEquals(version1.toString(), version1, version2);

    // Next check: Modify a <string> value definition in a values file
    // and check that those changes are flagged too
    clear(called1, calledValue1, called2, calledValue2);
    ResourceVersion version3 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    addText(values1, "name=\"hello^\"", "_world");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);
    ResourceVersion version4 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertNotEquals(version4.toString(), version3, version4);

    // Next check: Modify content in a comment and verify that no changes are fired
    clear(called1, calledValue1, called2, calledValue2);
    addText(layout1, "My ^comment", "new ");
    ensureNotCalled(called1, called2);

    // Check that editing text in a layout file has no effect
    clear(called1, calledValue1, called2, calledValue2);
    addText(layout1, " ^ <TextView", "abc");
    ensureNotCalled(called1, called2);

    // Make sure that's true for replacements too
    replaceText(layout1, "^abc", "abc".length(), "def");
    ensureNotCalled(called1, called2);

    // ...and for deletions
    removeText(layout1, "^def", "def".length());
    ensureNotCalled(called1, called2);

    // Check that editing text in a *values file* -does- have an effect
    // Read the value first to ensure that we trigger it as a read (see comment above for previous
    // resource resolver lookup).
    //noinspection ConstantConditions
    assertEquals("Hello",
                 configuration1.getResourceResolver().getResolvedResource(
                     new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "hello_world")).getValue());
    //getResolvedResource
    addText(values1, "Hello^</string>", " World");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);

    // Check that recreating AppResourceRepository object doesn't affect the ResourceNotificationManager.
    clear(called1, calledValue1, called2, calledValue2);
    StudioResourceRepositoryManager.getInstance(myFacet).resetAllCaches();
    IdeResourcesUtil.createValueResource(myProject, resourceDir, "color4", ResourceType.COLOR, "colors.xml",
                                         Collections.singletonList("values"), "#ff2300");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);

    // Next check: Mark the lines between <TextView .... /> as comments
    // and verify that our listeners are called.
    clear(called1, calledValue1, called2, calledValue2);
    ResourceVersion version5 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    replaceText(layout1, "^<TextView",  9, "<!--<TextView-->");
    replaceText(layout1, "        ^android:layout_width", 35, "<!--android:layout_width=\"match_parent\"-->");
    replaceText(layout1, "        ^android:layout_height", 36, "<!--android:layout_height=\"match_parent\"-->");
    replaceText(layout1, "^android:text=", 37,"<!--android:text=\"@string/hello_world\" />-->");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.EDIT);
    ResourceVersion version6 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertNotEquals(version6.toString(), version5, version6);

    // Next check: Un-mark the comments of the lines between <!--<TextView ... />--> (which we just commented in previous check)
    // and verify that our listeners are called.
    clear(called1, calledValue1, called2, calledValue2);
    ResourceVersion version7 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    replaceText(layout1, "^<!--<TextView-->",  15, "<TextView");
    replaceText(layout1, "^<!--android:layout_width=\"match_parent\"-->", 42, "android:layout_width=\"match_parent\"");
    replaceText(layout1, "^<!--android:layout_height=\"match_parent\"-->", 43, "android:layout_height=\"match_parent\"");
    replaceText(layout1, "^<!--android:text=\"@string/hello_world\" />-->", 44, "android:text=\"@string/hello_world\" />");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.EDIT);
    ResourceVersion version8 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertNotEquals(version8.toString(), version7, version8);

    // Finally check that once we remove the listeners there are no more notifications.
    manager.removeListener(listener1, myFacet, layout1.getVirtualFile(), configuration1);
    manager.removeListener(listener2, myFacet, layout2.getVirtualFile(), configuration1);
    clear(called1, calledValue1, called2, calledValue2);
    addText(layout1, "@string/hello_world^", "2");
    ensureNotCalled(called1, called2);

    // TODO: Check that editing a partial URL doesn't re-render.
    // Check module dependency triggers!
    // TODO: Test that remove and replace editing also works as expected.
  }

  @Test
  public void testNotifiedOnRename() throws Exception {
    // Setup sample project: a strings file, and a couple of layout file
    @Language("XML") String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                  "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                  "    android:layout_width=\"match_parent\"\n" +
                                  "    android:layout_height=\"match_parent\">\n" +
                                  "    <!-- My comment -->\n" +
                                  "    <TextView\n" +
                                  "        android:layout_width=\"match_parent\"\n" +
                                  "        android:layout_height=\"match_parent\"\n" +
                                  "        android:text=\"@string/hello\" />\n" +
                                  "</FrameLayout>";
    XmlFile layout1 = (XmlFile)myFixture.addFileToProject("res/layout/my_layout1.xml", xml);
    VirtualFile resourceDir = layout1.getParent().getParent().getVirtualFile();
    assertNotNull(resourceDir);


    Configuration configuration1 = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout1.getVirtualFile());
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myProject);

    // Listener 1: Listens for changes in layout 1.
    Ref<Boolean> called1 = new Ref<>(false);
    Ref<Set<Reason>> calledValue1 = new Ref<>();
    ResourceChangeListener listener1 = reason -> {
      called1.set(true);
      calledValue1.set(reason);
    };

    // Listener 2: Only listens for general changes in the module.
    Ref<Boolean> called2 = new Ref<>(false);
    Ref<Set<Reason>> calledValue2 = new Ref<>();
    ResourceChangeListener listener2 = reason -> {
      called2.set(true);
      calledValue2.set(reason);
    };
    manager.addListener(listener1, myFacet, layout1.getVirtualFile(), configuration1);
    manager.addListener(listener2, myFacet, null, null);
    ApplicationManager.getApplication().invokeAndWait(() -> new RenameDialog(myProject, layout1, null, null).performRename("newLayout"));
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);
  }

  @Test
  public void testNotNotifiedOnRenameNonResourceFile() throws Exception {
    // Setup sample project: a strings file, and a couple of layout file.
    @Language("JAVA") String java = "class Hello {}";
    PsiFile javaFile = myFixture.addFileToProject("src/hello.java", java);
    VirtualFile resourceDir = javaFile.getParent().getParent().getVirtualFile();
    assertNotNull(resourceDir);

    Configuration configuration1 = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(javaFile.getVirtualFile());
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myProject);

    // Listener 1: Listens for changes in layout 1.
    Ref<Boolean> called1 = new Ref<>(false);
    Ref<Set<Reason>> calledValue1 = new Ref<>();
    ResourceChangeListener listener1 = reason -> {
      called1.set(true);
      calledValue1.set(reason);
    };

    // Listener 2: Only listens for general changes in the module.
    Ref<Boolean> called2 = new Ref<>(false);
    Ref<Set<Reason>> calledValue2 = new Ref<>();
    ResourceChangeListener listener2 = reason -> {
      called2.set(true);
      calledValue2.set(reason);
    };
    manager.addListener(listener1, myFacet, javaFile.getVirtualFile(), configuration1);
    manager.addListener(listener2, myFacet, null, null);
    ApplicationManager.getApplication()
      .invokeAndWait(() -> new RenameDialog(myProject, javaFile, null, null).performRename("newFile.java"));
    ensureNotCalled(called1, called2);
  }

  @Test
  // Regression test for b/362961808
  public void testResourceImageChangedNotNotifiedWhenOtherFileIsReformatted() throws Exception {
    @Language("XML") String xml;

    // Setup sample project: a layout file and an animated vector file

    String layoutContents = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
          "    android:layout_width=\"match_parent\"\n" +
          "    android:layout_height=\"match_parent\">\n" +
          "    <!-- My comment -->\n" +
          "    <TextView\n" +
          "        android:layout_width=\"match_parent\"\n" +
          "        android:layout_height=\"match_parent\"\n" +
          "        android:text=\"@string/hello\" />\n" +
          "</FrameLayout>";
    XmlFile layout = (XmlFile)projectRule.getFixture().addFileToProject("res/layout/my_layout1.xml", layoutContents);

    String vectorXml = """
        <animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt" >
            <aapt:attr name="android:drawable">
                <vector
                    android:height="64dp"
                    android:width="64dp"
                    android:viewportHeight="600"
                    android:viewportWidth="600" >
                    <group
                        android:name="rotationGroup"
                        android:pivotX="300.0"
                        android:pivotY="300.0"
                        android:rotation="45.0" >
                        <path
                            android:name="v"
                            android:fillColor="#000000"
                            android:pathData="M300,70 l 0,-70 70,70 0,0 -70,70z" />
                    </group>
                </vector>
            </aapt:attr>
        </animated-vector>
    """;

    XmlFile animatedVector = (XmlFile)projectRule.getFixture().addFileToProject("res/drawable/my_animated_vector.xml", vectorXml);
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      ReformatUtil.reformatAndRearrange(myProject, animatedVector);
    });

    AtomicBoolean resourcesHaveChanged = new AtomicBoolean(false);
    Configuration layoutConfiguration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout.getVirtualFile());
    UIUtil.dispatchAllInvocationEvents(); // Dispatch any pending notifications
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(projectRule.getProject());
    manager.addListener(reasons -> resourcesHaveChanged.set(true), myFacet, layout.getVirtualFile(), layoutConfiguration);
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      ReformatUtil.reformatAndRearrange(myProject, animatedVector);
    });
    waitForResourceRepositoryUpdates(myModule, 4, TimeUnit.SECONDS);
    UIUtil.dispatchAllInvocationEvents(); // Dispatch notifications
    assertFalse("Reformat of the vector should not have triggered a change", resourcesHaveChanged.get());
  }

  private void ensureCalled(@NotNull Ref<Boolean> called1, @NotNull Ref<Set<Reason>> calledValue1,
                            @NotNull Ref<Boolean> called2, @NotNull Ref<Set<Reason>> calledValue2,
                            @NotNull Reason reason) throws InterruptedException, TimeoutException {
    waitForResourceRepositoryUpdates(myModule, 4, TimeUnit.SECONDS);
    UIUtil.dispatchAllInvocationEvents();
    waitForCondition(5, TimeUnit.SECONDS, () -> called1.get() && called2.get());
    assertEquals(EnumSet.of(reason), calledValue1.get());
    assertEquals(EnumSet.of(reason), calledValue2.get());
  }

  private static void clear(@NotNull Ref<Boolean> called1, @NotNull Ref<Set<Reason>> calledValue1,
                            @NotNull Ref<Boolean> called2, @NotNull Ref<Set<Reason>> calledValue2) {
    called1.set(false);
    called2.set(false);
    calledValue1.set(null);
    calledValue2.set(null);
  }

  private void ensureNotCalled(@NotNull Ref<Boolean> called1, @NotNull Ref<Boolean> called2) throws InterruptedException, TimeoutException {
    waitForResourceRepositoryUpdates(myModule);
    UIUtil.dispatchAllInvocationEvents();
    try {
      waitForCondition(5, TimeUnit.SECONDS, () -> called1.get() || called2.get());
      assertFalse(called1.get());
      assertFalse(called2.get());
    } catch (TimeoutException e) {}
  }

  private void addText(@NotNull PsiFile file, @NotNull String location, @NotNull String insertedText) {
    editText(file, location, 0, insertedText);
  }

  private void removeText(@NotNull PsiFile file, @NotNull String location, int length) {
    editText(file, location, length, "");
  }

  private void replaceText(@NotNull PsiFile file, @NotNull String location, int length, @NotNull String replaceText) {
    editText(file, location, length, replaceText);
  }

  private void editText(@NotNull PsiFile file, @NotNull String location, int length, @NotNull String text) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(file);
    assertNotNull(document);

    // Insert a comment at the beginning
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String documentText = document.getText();

      int delta = location.indexOf('^');
      assertTrue("Missing ^ describing caret offset in text window " + location, delta != -1);
      String target = location.substring(0, delta) + location.substring(delta + 1);
      int offset = documentText.indexOf(target);
      assertTrue("Could not find " + target + " in " + documentText, offset != -1);

      if (!text.isEmpty()) {
        if (length == 0) {
          document.insertString(offset + delta, text);
        } else {
          document.replaceString(offset + delta, offset + delta + length, text);
        }
      } else {
        document.deleteString(offset + delta, offset + delta + length);
      }
      documentManager.commitDocument(document);
    });
  }
}
