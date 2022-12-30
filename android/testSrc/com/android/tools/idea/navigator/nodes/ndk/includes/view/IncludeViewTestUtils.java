/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk.includes.view;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import java.io.File;
import org.jetbrains.annotations.NotNull;

public final class IncludeViewTestUtils {

  @NotNull
  private static String presentationDataToString(@NotNull PresentationData presentationData) {
    StringBuilder sb = new StringBuilder();
    for (PresentableNodeDescriptor.ColoredFragment fragment : presentationData.getColoredText()) {
      sb.append(fragment.getText());
    }
    return sb.toString();
  }

  @NotNull
  private static String renderOsSpecificSlashes(@NotNull String target) {
    return target.replace("{os-slash}",  Character.toString(File.separatorChar));
  }

  static void checkPresentationDataHasOsSpecificSlashes(@NotNull SimpleIncludeViewNode node, @NotNull String expected) {
    PresentationData presentationData = new PresentationData();
    node.update(presentationData);
    checkPresentationDataHasOsSpecificSlashes(presentationData, expected);
  }

  static void checkPresentationDataHasOsSpecificSlashes(@NotNull PsiFileNode node, @NotNull String expected) {
    PresentationData presentationData = new PresentationData();
    node.update(presentationData);
    checkPresentationDataHasOsSpecificSlashes(presentationData, expected);
  }

  static void checkPresentationDataHasOsSpecificSlashes(@NotNull PackagingFamilyViewNode node, @NotNull String expected) {
    PresentationData presentationData = new PresentationData();
    node.update(presentationData);
    checkPresentationDataHasOsSpecificSlashes(presentationData, expected);
  }

  static void checkPresentationDataHasOsSpecificSlashes(@NotNull PsiIncludeDirectoryView node, @NotNull String expected) {
    PresentationData presentationData = new PresentationData();
    node.update(presentationData);
    checkPresentationDataHasOsSpecificSlashes(presentationData, expected);
  }


  private static void checkPresentationDataHasOsSpecificSlashes(@NotNull PresentationData presentationData, @NotNull String expected) {
    assertThat(expected).doesNotContain("/");
    assertThat(expected).doesNotContain("\\");
    String presentationString = presentationDataToString(presentationData);
    checkNoSlashesFromOtherOs(presentationString);
    String osSpecificSlashes = renderOsSpecificSlashes(expected);
    assertThat(presentationString).isEqualTo(osSpecificSlashes);
  }

  public static void checkPresentationDataContainsOsSpecificSlashes(PackagingFamilyViewNode node, String expected) {
    PresentationData presentationData = new PresentationData();
    node.update(presentationData);
    assertThat(expected).doesNotContain("/");
    assertThat(expected).doesNotContain("\\");
    String presentationString = presentationDataToString(presentationData);
    checkNoSlashesFromOtherOs(presentationString);
    String osSpecificSlashes = renderOsSpecificSlashes(expected);
    assertThat(presentationString).contains(osSpecificSlashes);
  }

  private static void checkNoSlashesFromOtherOs(String text) {
    if (File.separatorChar == '/') {
      assertThat(text).doesNotContain("\\");
    } else {
      assertThat(text).doesNotContain("/");

    }
  }
}
