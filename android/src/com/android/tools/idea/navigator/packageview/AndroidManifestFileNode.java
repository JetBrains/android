/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.navigator.packageview;

import com.android.SdkConstants;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidManifestFileNode extends PsiFileNode {
  @NotNull private final IdeaSourceProvider myProvider;

  public AndroidManifestFileNode(@NotNull Project project, @NotNull PsiFile value, @NotNull ViewSettings viewSettings, @NotNull IdeaSourceProvider provider) {
    super(project, value, viewSettings);
    myProvider = provider;
  }

  @Override
  public void update(PresentationData data) {
    super.update(data);

    // if it is not part of the main source set, then append the provider name
    if (!SdkConstants.FD_MAIN.equals(myProvider.getName())) {
      data.addText(SdkConstants.ANDROID_MANIFEST_XML, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      data.addText(" [" + myProvider.getName() + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return String.format("%1$s [%2$s]", SdkConstants.ANDROID_MANIFEST_XML, myProvider.getName());
  }
}
