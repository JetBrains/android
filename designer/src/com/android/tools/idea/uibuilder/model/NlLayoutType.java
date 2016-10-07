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

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.drawable.AndroidDrawableDomUtil;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.menu.MenuDomFileDescription;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Describes the supported types of editors (where each editor type refers to the type of resource that the editor can handle
 */
public enum NlLayoutType {
  LAYOUT(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return LayoutDomFileDescription.isLayoutFile(file);
    }
  },
  MENU(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return MenuDomFileDescription.isMenuFile(file);
    }
  },
  PREFERENCE_SCREEN(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
        if (file.getProject().isDisposed()) {
          return false;
        }
        if (AndroidResourceUtil.isInResourceSubdirectory(file, ResourceFolderType.XML.getName())) {
          XmlTag tag = file.getRootTag();
          return tag != null && tag.getName().equals(SdkConstants.TAG_PREFERENCE_SCREEN);
        }
        return false;
      });
    }
  },
  DRAWABLE(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return AndroidDrawableDomUtil.isDrawableResourceFile(file);
    }
  },
  UNKNOWN(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return false;
    }
  };

  private final boolean mySupportedByDesigner;

  NlLayoutType(boolean supportedByDesigner) {
    mySupportedByDesigner = supportedByDesigner;
  }

  public abstract boolean isResourceTypeOf(@NotNull XmlFile file);

  public boolean isLayout() {
    return this == LAYOUT;
  }

  public static boolean supports(@NotNull XmlFile file) {
    return typeOf(file).isSupportedByDesigner();
  }

  @NotNull
  public static NlLayoutType typeOf(@NotNull XmlFile file) {
    for (NlLayoutType type : values()) {
      if (type.isResourceTypeOf(file)) {
        return type;
      }
    }

    return UNKNOWN;
  }

  public boolean isSupportedByDesigner() {
    return mySupportedByDesigner;
  }

  @NotNull
  public final String getPaletteFileName() {
    assert isSupportedByDesigner();
    return toString().toLowerCase(Locale.ROOT) + "_palette.xml";
  }
}
