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
package com.android.tools.idea.uibuilder.palette;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.FileResourceRepository;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ActionMenuViewHandler;
import com.android.tools.idea.uibuilder.handlers.CustomViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.CustomViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.menu.MenuHandler;
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceCategoryHandler;
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlLayoutType;
import com.google.common.base.Charsets;
import com.intellij.openapi.module.Module;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

public class NlPaletteModel {
  @VisibleForTesting
  static final String THIRD_PARTY_GROUP = "3rd Party";

  private final Map<NlLayoutType, Palette> myTypeToPalette;
  private final Module myModule;

  public static NlPaletteModel get(@NotNull AndroidFacet facet) {
    return facet.getModule().getComponent(NlPaletteModel.class);
  }

  private NlPaletteModel(@NotNull Module module) {
    myTypeToPalette = new EnumMap<>(NlLayoutType.class);
    myModule = module;
  }

  @NotNull
  Palette getPalette(@NotNull NlLayoutType type) {
    assert type.isSupportedByDesigner();
    Palette palette = myTypeToPalette.get(type);

    if (palette == null) {
      loadPalette(type);
      return myTypeToPalette.get(type);
    }
    else {
      return palette;
    }
  }

  private void loadPalette(@NotNull NlLayoutType type) {
    try {
      String metadata = type.getPaletteFileName();
      URL url = NlPaletteModel.class.getResource(metadata);
      URLConnection connection = url.openConnection();
      Palette palette;

      try (Reader reader = new InputStreamReader(connection.getInputStream(), Charsets.UTF_8)) {
        palette = loadPalette(reader, type);
      }
      loadThirdPartyLibraryComponents(type, palette);
    }
    catch (IOException | JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  @VisibleForTesting
  @NotNull
  Palette loadPalette(@NotNull Reader reader, @NotNull NlLayoutType type) throws JAXBException {
    Palette palette = Palette.parse(reader, ViewHandlerManager.get(myModule.getProject()));
    myTypeToPalette.put(type, palette);
    return palette;
  }

  private void loadThirdPartyLibraryComponents(@NotNull NlLayoutType type, @NotNull Palette palette) {
    for (FileResourceRepository fileResource : AppResourceRepository.getOrCreateInstance(myModule).getLibraries()) {
      // TODO: Add all palette components here:
      //for (PaletteComponent component : fileResource.getPaletteComponents()) {
      //  addThirdPartyComponent(...);
      //}
    }
    // TODO: Remove this. Use this line to test this feature.
    //addThirdPartyComponent(type, palette, AndroidIcons.Android, AndroidIcons.Android24,
    //                       "com.google.android.exoplayer2.ui.SimpleExoPlayerView", null, null, "com.google.android.exoplayer:exoplayer",
    //                       null, Collections.singletonList("tag"), Collections.emptyList());
  }

  @VisibleForTesting
  boolean addThirdPartyComponent(@NotNull NlLayoutType type,
                                 @NotNull Palette palette,
                                 @Nullable Icon icon16,
                                 @Nullable Icon icon24,
                                 @NotNull String tagName,
                                 @Nullable @Language("XML") String xml,
                                 @Nullable @Language("XML") String previewXml,
                                 @NotNull String libraryCoordinate,
                                 @Nullable String preferredProperty,
                                 @NotNull List<String> properties,
                                 @NotNull List<String> layoutProperties) {
    if (tagName.indexOf('.') < 0 || !NlComponent.viewClassToTag(tagName).equals(tagName) || tagName.equals(CONSTRAINT_LAYOUT)) {
      // Do NOT allow third parties to overwrite predefined Google handlers
      return false;
    }

    ViewHandlerManager manager = ViewHandlerManager.get(myModule.getProject());
    ViewHandler handler = manager.getHandler(tagName);
    if (handler == null) {
      getLogger().warning(String.format("Could not add %1s to palette", tagName));
      return false;
    }

    // For now only support layouts
    if (type != NlLayoutType.LAYOUT ||
        handler instanceof PreferenceHandler ||
        handler instanceof PreferenceCategoryHandler ||
        handler instanceof MenuHandler ||
        handler instanceof ActionMenuViewHandler) {
      return false;
    }

    if (handler instanceof ViewGroupHandler) {
      handler = new CustomViewGroupHandler((ViewGroupHandler)handler, icon16, icon24, tagName, xml, previewXml, libraryCoordinate,
                                           preferredProperty, properties, layoutProperties);
    }
    else {
      handler = new CustomViewHandler(handler, icon16, icon24, tagName, xml, previewXml, libraryCoordinate, preferredProperty, properties);
    }

    List<Palette.BaseItem> groups = palette.getItems();
    assert groups.size() > 0 && groups.get(groups.size() - 1) instanceof Palette.Group;
    Palette.Group group = (Palette.Group)groups.get(groups.size() - 1);
    if (!group.getName().equals(THIRD_PARTY_GROUP)) {
      group = new Palette.Group(THIRD_PARTY_GROUP);
      groups.add(group);
    }
    group.getItems().add(new Palette.Item(tagName, handler));
    manager.registerHandler(tagName, handler);
    return true;
  }

  private static Logger getLogger() {
    return Logger.getLogger(NlPaletteModel.class.getSimpleName());
  }
}
