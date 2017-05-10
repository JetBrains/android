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
package com.android.tools.idea.navigator.nodes.apk.ndk;

import com.google.common.base.Splitter;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.util.ArrayUtil.toStringArray;

public class SourceFolderNode extends PsiDirectoryNode {
  @NotNull private final File myFolderPath;
  @NotNull private final String myPathToShow;

  public SourceFolderNode(@NotNull Project project,
                          @NotNull PsiDirectory value,
                          @NotNull ViewSettings viewSettings) {
    super(project, value, viewSettings, null);

    myFolderPath = toSystemDependentPath(value.getVirtualFile().getPath());
    String pathToShow = myFolderPath.getPath();
    List<String> segments = Splitter.on(File.separator).omitEmptyStrings().splitToList(pathToShow);

    int segmentCount = segments.size();
    if (segmentCount > 4) {
      segments = segments.subList(segmentCount - 5, segmentCount - 1);
      pathToShow = "..." + File.separatorChar + join(toStringArray(segments));
    }
    myPathToShow = pathToShow;
  }

  @Override
  protected void updateImpl(PresentationData data) {
    PsiDirectory folder = getValue();
    assert folder != null;
    data.addText(folder.getName() + " ", REGULAR_ATTRIBUTES);
    data.addText("(" + myPathToShow + ")", GRAY_ATTRIBUTES);
    data.setTooltip(myFolderPath.getPath());
  }
}
