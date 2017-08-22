/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.fest.swing.core.KeyPressInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Test that applies random actions on the layout editor. This test is meant to be ran manually to discover issues that can be
 * found by random testing (performance, invalid states, etc).
 */
@RunWith(GuiTestRunner.class)
public class NlEditorMonkeyTest {
  private static final Random RND = new Random(101);

  private static final int RESIZE_RANGE_PX = 50;
  private static final int MOVE_RANGE_PX = 150;
  private static final String[][] WIDGETS_BY_LIKEHOOD = {
    {
      "Text/TextView",
      "Widgets/Button",
      "Widgets/CheckBox"
    },
    {
      "Widgets/Switch",
      "Widgets/ProgressBar",
    }
  };
  private static final List<String> WIDGETS = getProbabilityArray(WIDGETS_BY_LIKEHOOD);


  @SuppressWarnings("JUnitTestClassNamingConvention")
  private static class MonkeyActionContext {
    final IdeFrameFixture frame;
    final NlEditorFixture editor;
    final NlComponentFixture component;

    private MonkeyActionContext(@NotNull IdeFrameFixture frame,
                                @NotNull NlEditorFixture editor,
                                @NotNull NlComponentFixture component) {
      this.frame = frame;
      this.editor = editor;
      this.component = component;
    }
  }

  interface MonkeyAction {
    void run(@NotNull MonkeyActionContext context);
  }

  private static final MonkeyAction[][] ACTIONS_BY_LIKEHOOD = {
    // Very likely actions
    {
      // Wait
      (context) -> context.editor.waitForRenderToFinish(),

      // Add
      (context) -> {
        // Always leave 1 component
        if (context.editor.getAllComponents().size() < 2) {
          return;
        }

        String widgetPath = getRandomElement(WIDGETS);
        String widgetGroup = widgetPath.split("/", 2)[0];
        String widgetName = widgetPath.split("/", 2)[1];
        context.editor
          .dragComponentToSurface(widgetGroup, widgetName)
          .waitForRenderToFinish();
        System.out.println("Adding widget " + widgetName);
      },

      // Click
      (context) -> context.component.click(),

      // Resize
      (context) -> context.component.resizeBy(RND.nextInt(RESIZE_RANGE_PX * 2) - RESIZE_RANGE_PX, RND.nextInt(RESIZE_RANGE_PX * 2) -
                                                                                                  RESIZE_RANGE_PX),

      // Move
      (context) -> context.component.moveBy(RND.nextInt(MOVE_RANGE_PX * 2) - MOVE_RANGE_PX, RND.nextInt(MOVE_RANGE_PX * 2) - MOVE_RANGE_PX),
    },

    // Likely actions
    {
      // Remove
      (context) -> {
        // Always leave 1 component
        int elements = context.editor.getAllComponents().size();
        if (elements < 2 || context.editor.getSelection().size() == elements) {
          return;
        }
        context.frame.pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_DELETE));
      },

      // Constraint to layout
      (context) -> {
        switch (RND.nextInt(3)) {
          case 0:
            context.component.createConstraintFromLeftToLeftOfLayout();
            break;
          case 1:
            context.component.createConstraintFromRightToRightOfLayout();
            break;
          default:
            context.component.createConstraintFromTopToTopOfLayout();
            break;
        }
      },

      // Constraint to random widget
      (context) -> {
        List<NlComponentFixture> allComponents = context.editor.getAllComponents();
        if (allComponents.size() < 2) {
          return;
        }
        Set<NlComponentFixture> allComponentsSet = new HashSet<>(allComponents);
        allComponentsSet.remove(context.component);
        Consumer<NlComponentFixture> constraintMethod;
        switch (RND.nextInt(3)) {
          case 0:
            constraintMethod = context.component::createBaselineConstraintWith;
            break;
          case 1:
            constraintMethod = context.component::createConstraintFromBottomToLeftOf;
            break;
          default:
            constraintMethod = context.component::createConstraintFromBottomToTopOf;
            break;
        }

        constraintMethod.accept(Iterables.get(allComponentsSet, RND.nextInt(allComponentsSet.size())));
      },

      // Copy
      (context) -> context.frame.invokeMenuPath("Edit", "Copy"),

      // Cut
      (context) -> context.frame.invokeMenuPath("Edit", "Cut"),
    },

    // Less likely actions
    {
    },

    // Unlikely actions
    {
      // Multi-select
      (context) -> {
        context.frame.pressKey(KeyEvent.VK_SHIFT);
        List<NlComponentFixture> allComponents = context.editor.getAllComponents();
        for (int i = 0; i <= RND.nextInt(allComponents.size()); i++) {
          allComponents.get(RND.nextInt(allComponents.size())).click();
        }
        context.frame.releaseKey(KeyEvent.VK_SHIFT);
      },

      // Paste
      (context) -> context.frame.invokeMenuPath("Edit", "Paste"),
    }
  };
  private static List<MonkeyAction> ACTIONS = getProbabilityArray(ACTIONS_BY_LIKEHOOD);

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(2, TimeUnit.HOURS);

  /**
   * Takes as an input a 2D array of T and returns a 1D array with the items replicated depending on their position
   * in the initial 2D array.
   * This allows for the actions declared in T[0][] than the ones in T[1][] assuming they are accessed randomly
   * in the 1D array.
   */
  @NotNull
  private static <T> List<T> getProbabilityArray(@NotNull T[][] actionsByLikehood) {
    ImmutableList.Builder<T> builder = ImmutableList.builder();

    int likehoodFactor = actionsByLikehood.length * actionsByLikehood.length;
    for (T[] actions : actionsByLikehood) {
      for (T action : actions) {
        for (int i = 0; i < likehoodFactor; i++) {
          builder.add(action);
        }
      }
      likehoodFactor--;
    }

    return builder.build();
  }

  @NotNull
  private static <T> T getRandomElement(@NotNull List<T> list) {
    return list.get(RND.nextInt(list.size()));
  }

  @Test
  @Ignore // Run only manually
  public void monkeyTest() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    for (int i = 0; i < 500; i++) {
      List<NlComponentFixture> allComponents = layout.getAllComponents();
      NlComponentFixture component = allComponents.get(RND.nextInt(allComponents.size()));

      MonkeyActionContext context = new MonkeyActionContext(guiTest.ideFrame(), layout, component);
      try {
        getRandomElement(ACTIONS).run(context);
      } catch(Throwable t) {
        t.printStackTrace();
      }
    }
  }
}
