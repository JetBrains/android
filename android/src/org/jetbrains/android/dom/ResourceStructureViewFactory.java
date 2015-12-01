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
package org.jetbrains.android.dom;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.annotations.Nullable;

/**
 * Factory class for structure view builder for Android resources XML files
 *
 * @see ResourceStructureViewBuilder
 */
public class ResourceStructureViewFactory implements PsiStructureViewFactory {
  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder(PsiFile psiFile) {
    if (!(psiFile instanceof XmlFile)) {
      return null;
    }
    final XmlFile xmlFile = (XmlFile)psiFile;
    final DomFileElement<Resources> fileElement = DomManager.getDomManager(psiFile.getProject()).getFileElement(xmlFile, Resources.class);
    if (fileElement == null) {
      return null;
    }
    return new ResourceStructureViewBuilder(fileElement);
  }
}
