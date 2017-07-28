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

import com.android.tools.idea.common.SyncNlModel;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;

import javax.swing.*;
import java.awt.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.NlIdPropertyItem.clearRefactoringChoice;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class NlIdPropertyItemTest extends PropertyTestCase {
  private NlIdPropertyItem myItem;
  private DialogBuilder myBuilder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    clearSnapshots();
    clearRefactoringChoice();

    DialogBuilder.CustomizableAction addAction = mock(DialogBuilder.CustomizableAction.class);
    myBuilder = mock(DialogBuilder.class);
    when(myBuilder.addOkAction()).thenReturn(addAction);
    myItem = (NlIdPropertyItem)createFrom(myTextView, ATTR_ID);
    myItem.setDialogSupplier(() -> myBuilder);
  }

  public void testSetValueChangeReferences() {
    when(myBuilder.show()).thenReturn(DialogWrapper.OK_EXIT_CODE);
    myItem.setValue("label");

    assertThat(myTextView.getId()).isEqualTo("label");
    assertThat(myTextView.getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@+id/label");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/label");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/label");
  }

  public void testSetAndroidValueChangeReferences() {
    when(myBuilder.show()).thenReturn(DialogWrapper.OK_EXIT_CODE);
    myItem.setValue("@android:id/text2");

    assertThat(myTextView.getAttribute(ANDROID_URI, ATTR_ID)).isEqualTo("@android:id/text2");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@android:id/text2");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@android:id/text2");
  }

  public void testSetValueDoNotChangeReferences() {
    when(myBuilder.show()).thenReturn(DialogWrapper.NEXT_USER_EXIT_CODE);
    myItem.setValue("label");

    assertThat(myTextView.getId()).isEqualTo("label");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/textView");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/textView");

    // Change id again (verify no dialog shown since there are no references)
    verify(myBuilder, times(1)).show();
    myItem.setValue("text");
    verify(myBuilder, times(1)).show();
  }

  public void testSetValueAndYesToChangeReferencesAndDoNotCheckAgain() {
    doAnswer(invocation -> {
      ArgumentCaptor<JPanel> panel = ArgumentCaptor.forClass(JPanel.class);
      verify(myBuilder).setCenterPanel(panel.capture());
      for (Component component : panel.getValue().getComponents()) {
        if (component instanceof JBCheckBox) {
          ((JBCheckBox)component).setSelected(true);
        }
      }
      return DialogWrapper.OK_EXIT_CODE;
    }).when(myBuilder).show();

    myItem.setValue("other");
    assertThat(myTextView.getId()).isEqualTo("other");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/other");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/other");

    // Set id again, this time expect references to be changed without showing a dialog
    verify(myBuilder, times(1)).show();
    myItem.setValue("last");
    verify(myBuilder, times(1)).show();

    assertThat(myTextView.getId()).isEqualTo("last");
    assertThat(myCheckBox1.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW)).isEqualTo("@id/last");
    assertThat(myCheckBox2.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)).isEqualTo("@id/last");
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
