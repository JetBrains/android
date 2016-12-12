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
package com.android.tools.idea.uibuilder.model;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.TagSnapshot;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.util.NlTreeDumper;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlTag;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;

/**
 * Model tests. This checks that when a model is updated, we correctly
 * handle bounds and hierarchy changes.
 * <p>
 * Most tests start with a current model hierarchy (which is pretty printed.)
 * The important thing to note is the {@code instance=} identity listed at the
 * end. The identity is tracked in a static map. This lets us check that
 * an {@link NlComponent} is really a specific instanceof an object, not just
 * something recreated with the same values.
 * <p>
 * Most tests then perform some sort of mutation on the tag/view info hierarchy,
 * then they update the model, and then pretty print the model again to verify
 * that it has been updated as expected.
 */
public class NlModelTest extends LayoutTestCase {
  private final NlTreeDumper myTreeDumper = new NlTreeDumper();

  @SuppressWarnings("ConstantConditions")
  public void testSync() throws Exception {
    // Whether we include id's in the components or not. Id's help
    // associate id's with before/after versions; they're conditionally
    // added such that we can test the model handler with and without this
    // aid.
    boolean includeIds = false;
    ModelBuilder modelBuilder = createDefaultModelBuilder(includeIds);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));


    // Same hierarchy; preserveXmlTags means that all the XmlTags in the view hiearchy
    // will be different than in the existing model (simulating a completely separate
    // PSI parse)
    boolean preserveXmlTags = false;
    modelBuilder.updateModel(model, preserveXmlTags);

    // Everything should be identical
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testRemoveFirstChild() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Remove first child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    parent.removeChild(modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW));
    modelBuilder.updateModel(model, false);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testRemoveLastChild() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Remove last child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    parent.removeChild(modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON));
    modelBuilder.updateModel(model, false);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testTransposeChildren() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Remove last child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    ComponentDescriptor button = modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON);
    ComponentDescriptor textView = modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW);
    assertThat(parent).isNotNull();
    parent.children(button, textView);
    modelBuilder.updateModel(model, false);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testAddChild() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Add child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    parent.addChild(component(EDIT_TEXT)
      .withBounds(100, 100, 100, 100)
      .width("100dp")
      .height("100dp"), null);
    modelBuilder.updateModel(model, false);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}\n" +
                 "    NlComponent{tag=<EditText>, bounds=[100,100:100x100, instance=3}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testMoveInHierarchy() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Move button to be child of the text view instead
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    ComponentDescriptor textView = modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW);
    assertThat(textView).isNotNull();
    ComponentDescriptor button = modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON);
    assertThat(button).isNotNull();
    parent.removeChild(button);
    textView.addChild(button, null);
    modelBuilder.updateModel(model, false);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "        NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  @SuppressWarnings("ConstantConditions")
  public void testChangedPropertiesWithIds() throws Exception {
    boolean preserveXmlTags = false;
    // We include id's in the tags here since (due to attribute
    // changes between the two elements
    boolean includeIds = true;
    ModelBuilder modelBuilder = createDefaultModelBuilder(includeIds);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Change some attributes; this means that our finger print comparison (which
    // hashes all tag names and attributes) won't work
    ComponentDescriptor button = modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON);
    assertThat(button).isNotNull();
    ComponentDescriptor textView = modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW);
    assertThat(textView).isNotNull();
    button.withAttribute("style", "@style/Foo");
    textView.withAttribute("style", "@style/Foo");

    modelBuilder.updateModel(model, preserveXmlTags);

    // Everything should be identical
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  @SuppressWarnings("ConstantConditions")
  public void testChangeSingleProperty() throws Exception {
    boolean preserveXmlTags = false;
    boolean includeIds = false;

    ModelBuilder modelBuilder = createDefaultModelBuilder(includeIds);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Change a single attribute in an element without id's.
    ComponentDescriptor layout = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(layout).isNotNull();
    layout.withAttribute("style", "@style/Foo");

    modelBuilder.updateModel(model, preserveXmlTags);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testAddRemove() throws Exception {
    // Test removing one child and adding another one. Check that we don't
    // preserve component identity across two separate tag names.
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Add child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    ComponentDescriptor button = modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON);
    assertThat(button).isNotNull();
    ComponentDescriptor textView = modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW);
    assertThat(textView).isNotNull();
    parent.addChild(component(EDIT_TEXT)
                      .withBounds(100, 100, 100, 100)
                      .width("100dp")
                      .height("100dp"), textView);
    parent.removeChild(button);
    modelBuilder.updateModel(model, false);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<EditText>, bounds=[100,100:100x100, instance=3}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testCanAdd() throws Exception {
    NlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(FRAME_LAYOUT)
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp"),
        component(BUTTON)
          .withBounds(100, 200, 100, 100)
          .width("100dp")
          .height("100dp")
      )).build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<FrameLayout>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    NlComponent linearLayout = model.getComponents().get(0);
    NlComponent frameLayout = linearLayout.getChild(0);
    NlComponent button = linearLayout.getChild(1);
    assertThat(linearLayout).isNotNull();
    assertThat(button).isNotNull();
    assertThat(frameLayout).isNotNull();

    assertThat(model.canAddComponents(Collections.singletonList(frameLayout), linearLayout, frameLayout)).isTrue();
    assertThat(model.canAddComponents(Collections.singletonList(button), frameLayout, null)).isTrue();
    assertThat(model.canAddComponents(Arrays.asList(frameLayout, button), linearLayout, frameLayout)).isTrue();

    assertThat(model.canAddComponents(Collections.singletonList(linearLayout), frameLayout, null)).isFalse();
    assertThat(model.canAddComponents(Collections.singletonList(linearLayout), linearLayout, null)).isFalse();
  }

  public void testMoveInHierarchyWithWrongXmlTags() throws Exception {
    ModelBuilder modelBuilder = model("linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .withAttribute(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL)
      .children(
        component(FRAME_LAYOUT)
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
          .children(
            component(BUTTON)
              .withBounds(100, 100, 100, 100)
              .width("100dp")
              .height("100dp")
      )));

    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<FrameLayout>, bounds=[100,100:100x100, instance=1}\n" +
                 "        NlComponent{tag=<Button>, bounds=[100,100:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    XmlTag originalRoot = model.getFile().getRootTag();
    assertThat(originalRoot).isNotNull();
    XmlTag originalFrameLayout = originalRoot.getSubTags()[0];

    final Project project = model.getProject();
    WriteCommandAction.runWriteCommandAction(project, () -> {
      PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
      Document document = manager.getDocument(model.getFile());
      assertThat(document).isNotNull();
      document.setText("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "    android:layout_width=\"match_parent\"\n" +
                       "    android:layout_height=\"match_parent\"\n" +
                       "    android:orientation=\"vertical\">\n" +
                       "    <Button\n" +
                       "        android:layout_width=\"100dp\"\n" +
                       "        android:layout_height=\"100dp\"\n" +
                       "        android:text=\"Button\" />\n" +
                       "    <FrameLayout\n" +
                       "        android:layout_width=\"100dp\"\n" +
                       "        android:layout_height=\"100dp\">\n" +
                       "\n" +
                       "    </FrameLayout>\n" +
                       "\n" +
                       "</LinearLayout>");
      manager.commitAllDocuments();
    });

    // Manually construct the view hierarchy
    // Assert that component identity is preserved
    List<ViewInfo> views = Lists.newArrayList();
    XmlTag newRoot = model.getFile().getRootTag();
    assertThat(newRoot).isNotNull();
    XmlTag[] newRootSubTags = newRoot.getSubTags();
    XmlTag newButton = newRootSubTags[0];

    assertThat(originalRoot).isSameAs(newRoot);

    assertThat(originalFrameLayout).isSameAs(newButton);

    TagSnapshot snapshot = TagSnapshot.createTagSnapshot(newRoot, null);
    ViewInfo viewInfo = new ViewInfo("android.widget.LinearLayout", snapshot, 0, 0, 500, 500);
    views.add(viewInfo);

    ViewInfo buttonInfo = new ViewInfo("android.widget.Button", snapshot.children.get(0), 0, 0, 500, 500);
    buttonInfo.setChildren(Collections.emptyList());
    ViewInfo frameViewInfo = new ViewInfo("android.widget.TextView", snapshot.children.get(1), 0, 0, 300, 300);
    frameViewInfo.setChildren(Collections.emptyList());
    viewInfo.setChildren(Arrays.asList(buttonInfo, frameViewInfo));

    model.updateHierarchy(newRoot, views);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:500x500, instance=3}\n" +
                 // Make sure these instances are NOT reusing instances from before that
                 // mismatch, e.g. we should not get <Button> with instance=1 here
                 // since before the reparse instance=1 was associated with a <FrameLayout> !
                 "    NlComponent{tag=<Button>, bounds=[0,0:500x500, instance=4}\n" +
                 "    NlComponent{tag=<FrameLayout>, bounds=[0,0:300x300, instance=5}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testThemeSelection() {
    myFixture.addFileToProject("res/values/styles.xml",
                               "<resources>" +
                               "  <style name=\"Theme.MyTheme\"></style>" +
                               "<resources>");
    NlModel model = createDefaultModelBuilder(true)
      .build();
    Configuration configuration = model.getConfiguration();
    String defaultTheme = configuration.getTheme();
    assertNotNull(defaultTheme);

    // Set a valid theme
    configuration.setTheme("@style/Theme.MyTheme");
    model.deactivate();
    model.activate();
    assertEquals("@style/Theme.MyTheme", configuration.getTheme());

    // Set a valid framework theme
    configuration.setTheme("@android:style/Theme.Material");
    model.deactivate();
    model.activate();
    assertEquals("@android:style/Theme.Material", configuration.getTheme());

    // Check that if we try to select an invalid theme, NlModel will set back the default theme
    configuration.setTheme("@style/InvalidTheme");
    model.deactivate();
    model.activate();
    assertEquals(defaultTheme, configuration.getTheme());
    configuration.setTheme("@android:style/InvalidTheme");
    model.deactivate();
    model.activate();
    assertEquals(defaultTheme, configuration.getTheme());
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private ModelBuilder createDefaultModelBuilder(boolean includeIds) {
    return model("linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(TEXT_VIEW)
          .withBounds(100, 100, 100, 100)
          .id(includeIds ? "@id/myText1" : "")
          .width("100dp")
          .height("100dp"),
        component(BUTTON)
          .withBounds(100, 200, 100, 100)
          .id(includeIds ? "@id/myText2" : "")
          .width("100dp")
          .height("100dp")
          .withAttribute("android:layout_weight", "1.0")
      ));
  }
}
