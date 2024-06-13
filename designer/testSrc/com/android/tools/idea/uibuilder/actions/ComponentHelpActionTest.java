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
package com.android.tools.idea.uibuilder.actions;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.TestActionEvent;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.android.tools.idea.uibuilder.actions.ComponentHelpAction.ANDROIDX_CAST_ITEM;
import static com.android.tools.idea.uibuilder.actions.ComponentHelpAction.ANDROID_CAST_ITEM;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;

public class ComponentHelpActionTest extends AndroidTestCase {
  private DataContext myDataContext;
  @Mock
  private BrowserLauncher myBrowserLauncher;
  private AnActionEvent myEvent;
  private ComponentHelpAction myAction;
  private String myTagName;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    myDataContext = new DataContext() {
      @Override
      public @Nullable Object getData(@NotNull String dataId) {
        if (dataId.equals(CommonDataKeys.PROJECT.getName())) {
          return getProject();
        } else {
          return null;
        }
      }
    };
    myEvent = TestActionEvent.createTestEvent(myDataContext);
    myAction = new ComponentHelpAction(() -> myTagName);
    registerApplicationService(BrowserLauncher.class, myBrowserLauncher);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myDataContext = null;
      myBrowserLauncher = null;
      myEvent = null;
      myAction = null;
      myTagName = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testNullTagName() {
    myTagName = null;
    myAction.actionPerformed(myEvent);
    verifyNoMoreInteractions(myBrowserLauncher);
  }

  public void testUnknownTagName() {
    myTagName = "UnknownComponentTagName";
    myAction.actionPerformed(myEvent);
    verifyNoMoreInteractions(myBrowserLauncher);
  }

  public void testButton() {
    myTagName = "Button";
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/widget/Button.html"), isNull(), isNull());
  }

  public void testTextureView() {
    myTagName = "TextureView";
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/view/TextureView.html"), isNull(), isNull());
  }

  public void testWebView() {
    myTagName = "WebView";
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/webkit/WebView.html"), isNull(), isNull());
  }

  public void testFullyQualifiedButton() {
    myTagName = "android.widget.Button";
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/widget/Button.html"), isNull(), isNull());
  }

  public void testSupportLibraryTag() {
    myTagName = AndroidXConstants.CONSTRAINT_LAYOUT.defaultName();
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/support/constraint/ConstraintLayout.html"), isNull(), isNull());
  }

  public void testGoogleLibraryTag() {
    myTagName = SdkConstants.AD_VIEW;
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developers.google.com/android/reference/com/google/android/gms/ads/AdView"), isNull(), isNull());
  }

  public void testMenuItem() {
    myTagName = SdkConstants.TAG_ITEM;
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/view/MenuItem.html"), isNull(), isNull());
  }

  public void testGroup() {
    myTagName = SdkConstants.TAG_GROUP;
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/view/Menu.html"), isNull(), isNull());
  }

  public void testCastMenuItem() {
    myTagName = ANDROID_CAST_ITEM;
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/view/MenuItem.html"), isNull(), isNull());
  }

  public void testAndroidxCastMenuItem() {
    myTagName = ANDROIDX_CAST_ITEM;
    myAction.actionPerformed(myEvent);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/view/MenuItem.html"), isNull(), isNull());
  }
}
