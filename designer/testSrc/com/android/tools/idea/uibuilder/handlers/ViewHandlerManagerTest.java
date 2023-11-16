/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import static com.android.AndroidXConstants.RECYCLER_VIEW;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_PARENT_TAG;
import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.BOTTOM_APP_BAR;
import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;
import static com.android.testutils.AsyncTestUtils.waitForCondition;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.flexbox.FlexboxLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.relative.RelativeLayoutHandler;
import com.android.tools.idea.uibuilder.util.MockNlComponent;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.ServiceContainerUtil;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ViewHandlerManagerTest extends LayoutTestCase {
  private static final Runnable NOOP = () -> { throw new RuntimeException("Unexpected index lookup"); };

  public void testBasicHandlers() {
    ViewHandlerManager viewManager = getProject().getService(ViewHandlerManager.class);
    assertSame(viewManager, getProject().getService(ViewHandlerManager.class));

    assertTrue(viewManager.getHandler("LinearLayout", NOOP) instanceof LinearLayoutHandler);
    assertTrue(viewManager.getHandler("android.widget.LinearLayout", NOOP) instanceof LinearLayoutHandler);
    assertTrue(viewManager.getHandler("RelativeLayout", NOOP) instanceof RelativeLayoutHandler);
    assertTrue(viewManager.getHandler("android.widget.RelativeLayout", NOOP) instanceof RelativeLayoutHandler);
    assertTrue(viewManager.getHandler("merge", NOOP) instanceof MergeHandler);
    assertTrue(viewManager.getHandler("layout", NOOP) instanceof LayoutHandler);

    assertSame(viewManager.getHandler("LinearLayout", NOOP), viewManager.getHandler("LinearLayout", NOOP));
    if (FlexboxLayoutHandler.FLEXBOX_ENABLE_FLAG) {
      assertTrue(viewManager.getHandler(SdkConstants.FLEXBOX_LAYOUT, NOOP) instanceof FlexboxLayoutHandler);
    }
  }

  public void testAndroidxHandler() {
    ViewHandlerManager viewManager = getProject().getService(ViewHandlerManager.class);
    assertTrue(viewManager.getHandler(RECYCLER_VIEW.newName(), NOOP) instanceof RecyclerViewHandler);
  }

  public void testMergeHandler() {
    String xml = "<merge/>\n";
    XmlFile file = (XmlFile)myFixture.addFileToProject("layout/merge.xml", xml);
    NlComponent root = MockNlComponent.create(checkNotNull(file.getRootTag()));
    ViewHandlerManager viewManager = getProject().getService(ViewHandlerManager.class);
    ViewHandler handler = viewManager.getHandler(root, NOOP);
    assertTrue(handler instanceof MergeHandler);
    assertThat(handler.getInspectorProperties()).containsExactly(
      TOOLS_NS_NAME_PREFIX + ATTR_SHOW_IN, TOOLS_NS_NAME_PREFIX + ATTR_PARENT_TAG);
  }

  public void testMergeHandlerWithLinearLayoutParentTag() {
    String xml = """
      <merge xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools"
          tools:parentTag="LinearLayout">

      </merge>""";
    XmlFile file = (XmlFile)myFixture.addFileToProject("layout/merge.xml", xml);
    NlComponent root = MockNlComponent.create(checkNotNull(file.getRootTag()));
    ViewHandlerManager viewManager = getProject().getService(ViewHandlerManager.class);
    ViewHandler handler = viewManager.getHandler(root, NOOP);
    assertTrue(handler instanceof MergeDelegateHandler);

    // This handler should have inspector properties from <merge> and <LinearLayout>
    assertThat(handler.getInspectorProperties()).containsExactly(
      TOOLS_NS_NAME_PREFIX + ATTR_SHOW_IN, TOOLS_NS_NAME_PREFIX + ATTR_PARENT_TAG, ATTR_ORIENTATION, ATTR_GRAVITY);
  }

  public void testViewHandlerProvider() throws Exception {
    ViewHandlerProvider provider = new TestHandler((tag) -> {
      if (BOTTOM_APP_BAR.equals(tag)) {
        fail("Built-in component should not call the ViewHandlerProvider");
      }
      else if ("TestTag".equals(tag)) {
        return new ViewStubHandler();
      }

      return null;
    });

    ViewHandlerManager viewManager = getProject().getService(ViewHandlerManager.class);
    assertThat(viewManager.getHandler("TestTag", null)).isNull();

    viewManager.clearCache();
    ServiceContainerUtil.registerExtension(
      getProject(),
      ViewHandlerManager.EP_NAME,
      provider,
      getTestRootDisposable()
    );
    assertTrue(viewManager.getHandler("TestTag", NOOP) instanceof ViewStubHandler);
    assertNotNull(viewManager.getHandler(BOTTOM_APP_BAR, NOOP));
  }

  public void testCustomTextViewHandler() throws Exception {
    myFixture.addFileToProject(
      "src/com/example/MyTextView.java",
      """
      package com.example;
            
      import android.content.Context;
      import android.util.AttributeSet;
      import android.widget.TextView;
            
      public class MyTextView extends TextView {
          public MyTextView(Context context, AttributeSet attrs) {
              super(context, attrs);
          }
      }
      """
    );
    int[] updates = new int[1];
    Runnable callback = () -> { updates[0]++; };
    ViewHandlerManager viewManager = getProject().getService(ViewHandlerManager.class);
    assertThat(viewManager.getHandler("com.example.MyTextView", callback)).isNull();
    waitForCondition(10, TimeUnit.SECONDS, () -> updates[0] >= 1);

    // Now that we got the update the real handler should be available in the handler cache:
    assertThat(viewManager.getHandler("com.example.MyTextView", callback)).isInstanceOf(TextViewHandler.class);

    // Check that we only got 1 handler update in this test:
    assertThat(updates[0]).isEqualTo(1);
  }

  private static class TestHandler implements ViewHandlerProvider {
    private final Function<String, ViewHandler> myProvider;

    private TestHandler(Function<String, ViewHandler> provider) {
      myProvider = provider;
    }

    @Nullable
    @Override
    public ViewHandler findHandler(@NotNull String viewTag) {
      return myProvider.apply(viewTag);
    }
  }
}
