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

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.res.ResourceNotificationManager.Reason;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceVersion;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class ResourceNotificationManagerTest extends AndroidTestCase {
  public void test() {
    @Language("XML") String xml;

    // Setup sample project: a strings file, and a couple of layout file

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
          "    android:layout_width=\"match_parent\"\n" +
          "    android:layout_height=\"match_parent\">\n" +
          "    <!-- My comment -->\n" +
          "    <TextView " +
          "        android:layout_width=\"match_parent\"\n" +
          "        android:layout_height=\"match_parent\"\n" +
          "        android:text=\"@string/hello\" />\n" +
          "</FrameLayout>";
    final XmlFile layout1 = (XmlFile)myFixture.addFileToProject("res/layout/my_layout1.xml", xml);

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
          "    android:layout_width=\"match_parent\"\n" +
          "    android:layout_height=\"match_parent\" />\n";
    final XmlFile layout2 = (XmlFile)myFixture.addFileToProject("res/layout/my_layout2.xml", xml);

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
    final XmlFile values1 = (XmlFile)myFixture.addFileToProject("res/values/my_values1.xml", xml);

    xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
          "<resources>\n" +
          "    \n" +
          "</resources>";
    myFixture.addFileToProject("res/values/colors.xml", xml);

    final Configuration configuration1 = myFacet.getConfigurationManager().getConfiguration(layout1.getVirtualFile());
    final ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getProject());

    // Listener 1: Listens for changes in layout 1
    final Ref<Boolean> called1 = new Ref<>(false);
    final Ref<Set<Reason>> calledValue1 = new Ref<>();
    ResourceChangeListener listener1 = new ResourceChangeListener() {
      @Override
      public void resourcesChanged(@NotNull Set<Reason> reason) {
        called1.set(true);
        calledValue1.set(reason);
      }
    };

    // Listener 2: Only listens for general changes in the module
    final Ref<Boolean> called2 = new Ref<>(false);
    final Ref<Set<Reason>> calledValue2 = new Ref<>();
    ResourceChangeListener listener2 = new ResourceChangeListener() {
      @Override
      public void resourcesChanged(@NotNull Set<Reason> reason) {
        called2.set(true);
        calledValue2.set(reason);
      }
    };

    manager.addListener(listener1, myFacet, layout1, configuration1);
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
    assertEquals("#ff0000", configuration1.getResourceResolver().getStyle("AppTheme", false).getItem("colorBackground", true).getValue());
    AndroidResourceUtil.createValueResource(myModule, "color2", ResourceType.COLOR, "colors.xml", Collections.singletonList("values"),
                                            "#fa2395");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);
    clear(called1, calledValue1, called2, calledValue2);
    @SuppressWarnings("ConstantConditions")
    final XmlTag tag = values1.getDocument().getRootTag().getSubTags()[1].getSubTags()[0];
    assertEquals("item", tag.getName());
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        tag.getValue().setEscapedText("@color/color2");
      }
    });
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);

    // First check: Modify the layout by changing @string/hello to @string/hello_world
    // and verify that our listeners are called.
    ResourceVersion version1 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    addText(layout1, "@string/hello^", "_world");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.EDIT);
    ResourceVersion version2 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertFalse(version1.toString(), version1.equals(version2));

    // Next check: Modify a <string> value definition in a values file
    // and check that those changes are flagged too
    clear(called1, calledValue1, called2, calledValue2);
    ResourceVersion version3 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    addText(values1, "name=\"hello^\"", "_world");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);
    ResourceVersion version4 = manager.getCurrentVersion(myFacet, layout1, configuration1);
    assertFalse(version4.toString(), version3.equals(version4));

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
    // resource resolver lookup)
    //noinspection ConstantConditions
    assertEquals("Hello", configuration1.getResourceResolver().findResValue("@string/hello_world", false).getValue());
    addText(values1, "Hello^</string>", " World");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);

    // Check that recreating AppResourceRepository object doesn't affect the ResourceNotificationManager
    clear(called1, calledValue1, called2, calledValue2);
    myFacet.refreshResources();
    AndroidResourceUtil.createValueResource(myModule, "color4", ResourceType.COLOR, "colors.xml", Collections.singletonList("values"),
                                            "#ff2300");
    ensureCalled(called1, calledValue1, called2, calledValue2, Reason.RESOURCE_EDIT);

    // Finally check that once we remove the listeners there are no more notifications
    manager.removeListener(listener1, myFacet, layout1, configuration1);
    manager.removeListener(listener2, myFacet, layout2, configuration1);
    clear(called1, calledValue1, called2, calledValue2);
    addText(layout1, "@string/hello_world^", "2");
    ensureNotCalled(called1, called2);

    // TODO: Check that editing a partial URL doesn't re-render
    // Check module dependency triggers!
    // TODO: Test that remove and replace editing also works as expected
  }

  private static void ensureCalled(final Ref<Boolean> called1,
                                   final Ref<Set<Reason>> calledValue1,
                                   final Ref<Boolean> called2,
                                   final Ref<Set<Reason>> calledValue2,
                                   final Reason reason) {
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        assertTrue(called1.get());
        assertEquals(EnumSet.of(reason), calledValue1.get());

        assertTrue(called2.get());
        assertEquals(EnumSet.of(reason), calledValue2.get());
      }
    });
  }

  private static void clear(Ref<Boolean> called1, Ref<Set<Reason>> calledValue1, Ref<Boolean> called2, Ref<Set<Reason>> calledValue2) {
    called1.set(false);
    called2.set(false);
    calledValue1.set(null);
    calledValue2.set(null);
  }

  private static void ensureNotCalled(final Ref<Boolean> called1, final Ref<Boolean> called2) {
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        assertFalse(called1.get());
        assertFalse(called2.get());
      }
    });
  }

  private void addText(@NotNull PsiFile file, final String location, final String insertedText) {
    editText(file, location, 0, insertedText);
  }

  private void removeText(@NotNull PsiFile file, final String location, final int length) {
    editText(file, location, length, null);
  }

  private void replaceText(@NotNull PsiFile file, final String location, final int length, final String replaceText) {
    editText(file, location, length, replaceText);
  }

  private void editText(@NotNull PsiFile file, final String location, final int length, @Nullable final String text) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(file);
    assertNotNull(document);

    // Insert a comment at the beginning
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final String documentText = document.getText();

        int delta = location.indexOf('^');
        assertTrue("Missing ^ describing caret offset in text window " + location, delta != -1);
        String target = location.substring(0, delta) + location.substring(delta + 1);
        int offset = documentText.indexOf(target);
        assertTrue("Could not find " + target + " in " + documentText, offset != -1);

        if (text != null) {
          if (length == 0) {
            document.insertString(offset + delta, text);
          } else {
            document.replaceString(offset + delta, offset + delta + length, text);
          }
        } else {
          document.deleteString(offset + delta, offset + delta + length);
        }
        documentManager.commitDocument(document);
      }
    });
  }
}
