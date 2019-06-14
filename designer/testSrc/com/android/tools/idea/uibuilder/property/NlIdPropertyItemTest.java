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
package com.android.tools.idea.uibuilder.property;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT_BELOW;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.CHECK_BOX;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.uibuilder.property2.support.NeleIdRenameProcessor;
import com.android.tools.idea.uibuilder.property2.support.NeleIdRenameProcessor.RefactoringChoice;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public class NlIdPropertyItemTest extends PropertyTestCase {
  private NlIdPropertyItem myItem;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    clearSnapshots();
    NeleIdRenameProcessor.setChoiceForNextRename(RefactoringChoice.ASK);
    myItem = (NlIdPropertyItem)createFrom(myTextView, ATTR_ID);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      NeleIdRenameProcessor.setChoiceForNextRename(RefactoringChoice.ASK);
    }
    finally {
      super.tearDown();
    }
  }

  public void testSetValueChangeReferences() {
    NeleIdRenameProcessor.setChoiceForNextRename(RefactoringChoice.ASK);
    NeleIdRenameProcessor.setDialogProvider((project, id, hasUsages, otherDeclarations) -> RefactoringChoice.YES);
    BaseRefactoringProcessor.runWithDisabledPreview(() -> myItem.setValue("label"));

    assertThat(myTextView.getId()).isEqualTo("label");
    assertThat(myTextView.getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/label");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/label");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/label");
  }

  public void testSetAndroidValueChangeReferences() {
    NeleIdRenameProcessor.setDialogProvider((project, id, hasUsages, otherDeclarations) -> RefactoringChoice.YES);
    BaseRefactoringProcessor.runWithDisabledPreview(() -> myItem.setValue("@android:id/text2"));

    assertThat(myTextView.getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@android:id/text2");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@android:id/text2");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@android:id/text2");
  }

  public void testSetValueDoNotChangeReferences() {
    NeleIdRenameProcessor.setDialogProvider((project, id, hasUsages, otherDeclarations) -> RefactoringChoice.NO);
    BaseRefactoringProcessor.runWithDisabledPreview(() -> myItem.setValue("label"));

    assertThat(myTextView.getId()).isEqualTo("label");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/textView");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView");
  }

  public void testSetValueAndYesToChangeReferencesAndDoNotCheckAgain() {
    NeleIdRenameProcessor.setDialogProvider((project, id, hasUsages, otherDeclarations) -> RefactoringChoice.YES);
    BaseRefactoringProcessor.runWithDisabledPreview(() -> myItem.setValue("other"));

    UIUtil.dispatchAllInvocationEvents();
    assertThat(myTextView.getId()).isEqualTo("other");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/other");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/other");

    // Set id again, this time expect references to be changed without showing a dialog
    NeleIdRenameProcessor.setChoiceForNextRename(RefactoringChoice.YES);
    NeleIdRenameProcessor.setDialogProvider(
      (project, id, hasUsages, otherDeclarations) -> { throw new RuntimeException("Unexpected invocation"); });
    BaseRefactoringProcessor.runWithDisabledPreview(() -> myItem.setValue("last"));

    assertThat(myTextView.getId()).isEqualTo("last");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/last");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/last");
  }

  public void testSetValueAndYesWillNotEnablePreviewBeforeRun() {
    NeleIdRenameProcessor.setDialogProvider((project, id, hasUsages, otherDeclarations) -> RefactoringChoice.YES);
    BaseRefactoringProcessor.runWithDisabledPreview(() -> myItem.setValue("label"));

    assertThat(myTextView.getId()).isEqualTo("label");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/label");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/label");
  }

  public void testSetValueAndPreviewWillEnablePreviewBeforeRun() {
    NeleIdRenameProcessor.setDialogProvider((project, id, hasUsages, otherDeclarations) -> RefactoringChoice.PREVIEW);
    try {
      BaseRefactoringProcessor.runWithDisabledPreview(() -> myItem.setValue("label"));
      throw new RuntimeException("Preview was not shown as expected as is emulating a click on the preview button");
    }
    catch (RuntimeException ex) {
      assertThat(ex.getMessage()).startsWith("Unexpected preview in tests: @id/textView");
    }
  }

  public void testSetValueAndCancelNotExecuteRenameProcess() {
    NeleIdRenameProcessor.setDialogProvider((project, id, hasUsages, otherDeclarations) -> RefactoringChoice.CANCEL);
    BaseRefactoringProcessor.runWithDisabledPreview(() -> myItem.setValue("label"));

    assertThat(myTextView.getId()).isEqualTo("textView");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/textView");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView");
  }

  @Override
  @NotNull
  protected SyncNlModel createModel() {
    return model("relative.xml",
                 component(RELATIVE_LAYOUT)
                   .withBounds(0, 0, 1000, 1000)
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(TEXT_VIEW)
                       .withBounds(100, 100, 100, 100)
                       .id("@+id/textView")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute("android:layout_alignParentTop", "true")
                       .withAttribute("android:layout_alignParentLeft", "true")
                       .text("Text"),
                     component(CHECK_BOX)
                       .withBounds(100, 200, 100, 100)
                       .id("@id/checkBox1")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute("android:layout_below", "@id/textView")
                       .withAttribute("android:layout_toRightOf", "@id/button2")
                       .text("Button"),
                     component(CHECK_BOX)
                       .withBounds(100, 400, 100, 100)
                       .id("@id/checkBox2")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute("android:layout_below", "@id/button1")
                       .withAttribute("android:layout_toRightOf", "@id/textView")
                   )).build();
  }
}
