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
package org.jetbrains.android.dom.structure.layout;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.xml.XmlStructureViewBuilderProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see LayoutStructureViewBuilder
 */
public class LayoutStructureViewBuilderProvider implements XmlStructureViewBuilderProvider {

  @Nullable
  @Override
  public StructureViewBuilder createStructureViewBuilder(@NotNull XmlFile file) {
    final DomFileElement<LayoutViewElement> fileElement = DomManager.getDomManager(file.getProject()).getFileElement(file, LayoutViewElement.class);
    if (fileElement == null) {
      return null;
    }

    return new LayoutStructureViewBuilder(fileElement);
  }
}
