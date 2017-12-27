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
package com.android.tools.idea.common.model;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.naveditor.editor.NavToolbarActionGroups;
import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.uibuilder.adaptiveicon.AdaptiveIconActionGroups;
import com.android.tools.idea.uibuilder.editor.DefaultNlToolbarActionGroups;
import com.android.tools.idea.common.editor.SetZoomActionGroups;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.uibuilder.statelist.StateListActionGroups;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.FileDescriptionUtils;
import org.jetbrains.android.dom.drawable.fileDescriptions.AdaptiveIconDomFileDescription;
import org.jetbrains.android.dom.font.FontFamilyDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.menu.MenuDomFileDescription;
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Locale;

/**
 * Describes the supported types of editors (where each editor type refers to the type of resource that the editor can handle
 *
 * TODO: refactor so types can be registered in some way rather than needing to be statically defined here.
 */
public enum NlLayoutType {
  ADAPTIVE_ICON(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return AdaptiveIconDomFileDescription.isAdaptiveIcon(file);
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new AdaptiveIconActionGroups(surface);
    }
  },

  FONT(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return FontFamilyDomFileDescription.isFontFamilyFile(file);
    }
  },

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

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new SetZoomActionGroups(surface);
    }
  },

  NAV(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return NavSceneManager.enableNavigationEditor() && NavigationDomFileDescription.isNavFile(file);
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new NavToolbarActionGroups(surface);
    }
  },

  PREFERENCE_SCREEN(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return FileDescriptionUtils.isResourceOfType(file, ResourceFolderType.XML, Collections.singleton(SdkConstants.TAG_PREFERENCE_SCREEN));
    }
  },

  STATE_LIST(true) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return FileDescriptionUtils.isResourceOfType(file, ResourceFolderType.DRAWABLE, Collections.singleton(SdkConstants.TAG_SELECTOR));
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new StateListActionGroups(surface);
    }
  },

  UNKNOWN(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return false;
    }

    @NotNull
    @Override
    public String getPaletteFileName() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new ToolbarActionGroups(surface);
    }
  },

  VECTOR(false) {
    @Override
    public boolean isResourceTypeOf(@NotNull XmlFile file) {
      return FileDescriptionUtils.isResourceOfType(file, ResourceFolderType.DRAWABLE, Collections.singleton(SdkConstants.TAG_VECTOR));
    }

    @NotNull
    @Override
    public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
      return new SetZoomActionGroups(surface);
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
  public String getPaletteFileName() {
    return toString().toLowerCase(Locale.ROOT) + "_palette.xml";
  }

  @NotNull
  public ToolbarActionGroups getToolbarActionGroups(@NotNull DesignSurface surface) {
    return new DefaultNlToolbarActionGroups((NlDesignSurface)surface);
  }
}
