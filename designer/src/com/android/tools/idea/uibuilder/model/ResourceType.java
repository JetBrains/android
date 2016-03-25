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
package com.android.tools.idea.uibuilder.model;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.menu.MenuDomFileDescription;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum ResourceType {
  LAYOUT {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return LayoutDomFileDescription.isLayoutFile(file);
    }
  },
  MENU {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return MenuDomFileDescription.isMenuFile(file);
    }
  },
  PREFERENCE_SCREEN {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      XmlTag tag = file.getRootTag();
      return tag != null && tag.getName().equals("PreferenceScreen");
    }
  };

  public abstract boolean isResourceTypeOf(@NotNull XmlFile file);

  @NotNull
  public static ResourceType valueOf(@NotNull XmlFile file) {
    for (ResourceType type : values()) {
      if (type.isResourceTypeOf(file)) {
        return type;
      }
    }

    throw new IllegalArgumentException(file.toString());
  }

  @NotNull
  public final String getPaletteFileName() {
    return toString().toLowerCase(Locale.ROOT) + "_palette.xml";
  }
}
