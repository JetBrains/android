/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.rendering;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/** Replaces all tags in the current file named {@code wrongTag} into {@code rightTag} */
public class ReplaceTagFix extends WriteCommandAction<Void> {
  @NotNull private final XmlFile myFile;
  @NotNull private final String myWrongTag;
  @NotNull private final String myRightTag;

  public ReplaceTagFix(@NotNull Project project, @NotNull XmlFile file, @NotNull final String wrongTag, @NotNull final String rightTag) {
    super(project, String.format("Replace <%1$s> with <%2$s>", wrongTag, rightTag), file);
    myFile = file;
    myWrongTag = wrongTag;
    myRightTag = rightTag;
  }

  @Override
  protected void run(@NotNull Result<Void> result) throws Throwable {
    Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(myFile, XmlTag.class);
    if (!xmlTags.isEmpty()) {
      List<XmlTag> matching = Lists.newArrayListWithExpectedSize(xmlTags.size());
      for (XmlTag tag : xmlTags) {
        if (tag.getName().equals(myWrongTag)) {
          matching.add(tag);
        }
      }
      if (!matching.isEmpty()) {
        for (XmlTag tag : matching) {
          tag.setName(myRightTag);
        }
      }
    }
  }
}
