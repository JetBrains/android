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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetAttributeFix extends WriteCommandAction<Void> {
  private final XmlTag myTag;
  private final String myNamespace;
  private final String myAttribute;
  private final String myValue;

  public SetAttributeFix(@NotNull Project project, @NotNull XmlTag tag, @NotNull String attribute, @Nullable String namespace,
                         @Nullable String value) {
    super(project, String.format("Set %1$s Attribute", StringUtil.capitalize(attribute)), tag.getContainingFile());
    myTag = tag;
    myNamespace = namespace;
    myAttribute = attribute;
    myValue = value;
  }

  @Override
  protected void run(@NotNull Result<Void> result) throws Throwable {
    if (myNamespace != null && myValue != null) {
      AndroidResourceUtil.ensureNamespaceImported((XmlFile)myTag.getContainingFile(), myNamespace, null);
    }
    myTag.setAttribute(myAttribute, myNamespace, myValue);
  }
}
