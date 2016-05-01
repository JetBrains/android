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

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;

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
                 LayoutTestUtilities.toTree(model.getComponents(), true));


    // Same hierarchy; preserveXmlTags means that all the XmlTags in the view hiearchy
    // will be different than in the existing model (simulating a completely separate
    // PSI parse)
    boolean preserveXmlTags = false;
    modelBuilder.updateModel(model, preserveXmlTags);

    // Everything should be identical
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));
  }

  public void testRemoveFirstChild() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));

    // Remove first child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    parent.removeChild(modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW));
    modelBuilder.updateModel(model, false);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));
  }

  public void testRemoveLastChild() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));

    // Remove last child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    parent.removeChild(modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON));
    modelBuilder.updateModel(model, false);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));
  }

  public void testTransposeChildren() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));

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
                 LayoutTestUtilities.toTree(model.getComponents(), true));
  }

  public void testAddChild() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));

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
                 LayoutTestUtilities.toTree(model.getComponents(), true));
  }

  public void testMoveInHierarchy() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));

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
                 LayoutTestUtilities.toTree(model.getComponents(), true));
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
                 LayoutTestUtilities.toTree(model.getComponents(), true));

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
                 LayoutTestUtilities.toTree(model.getComponents(), true));
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
                 LayoutTestUtilities.toTree(model.getComponents(), true));

    // Change a single attribute in an element without id's.
    ComponentDescriptor layout = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(layout).isNotNull();
    layout.withAttribute("style", "@style/Foo");

    modelBuilder.updateModel(model, preserveXmlTags);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));
  }

  public void testAddRemove() throws Exception {
    // Test removing one child and adding another one. Check that we don't
    // preserve component identity across two separate tag names.
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 LayoutTestUtilities.toTree(model.getComponents(), true));

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
                 LayoutTestUtilities.toTree(model.getComponents(), true));
  }

  private ModelBuilder createDefaultModelBuilder(boolean includeIds) {
    LayoutTestUtilities.resetComponentTestIds();
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
