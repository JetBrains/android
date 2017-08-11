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
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.FileResourceRepository;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ActionMenuViewHandler;
import com.android.tools.idea.uibuilder.handlers.CustomViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.CustomViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceCategoryHandler;
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceHandler;
import com.android.tools.idea.uibuilder.menu.MenuHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelper;
import com.android.tools.idea.common.model.NlLayoutType;
import com.google.common.base.Charsets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import icons.AndroidIcons;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

public class NlPaletteModel implements Disposable {
  @VisibleForTesting
  static final String THIRD_PARTY_GROUP = "3rd Party";
  static final String PROJECT_GROUP = "Project";

  /**
   * {@link Function} that returns all the classes within the project scope that inherit from android.view.View
   */
  private static final Function<Project, Query<PsiClass>> VIEW_CLASSES_QUERY = (project) -> {
    PsiClass viewClass = JavaPsiFacade.getInstance(project).findClass("android.view.View", GlobalSearchScope.allScope(project));

    if (viewClass == null) {
      // There is probably no SDK
      return EmptyQuery.getEmptyQuery();
    }

    return ClassInheritorsSearch.search(viewClass, ProjectScope.getProjectScope(project), true);
  };

  private final Map<NlLayoutType, Palette> myTypeToPalette;
  private final Module myModule;
  private UpdateListener myListener;

  @Override
  public void dispose() {
    myListener = null;
  }

  public interface UpdateListener {
    void update();
  }

  public static NlPaletteModel get(@NotNull AndroidFacet facet) {
    return facet.getModule().getComponent(NlPaletteModel.class);
  }

  private NlPaletteModel(@NotNull Module module) {
    myTypeToPalette = new EnumMap<>(NlLayoutType.class);
    myModule = module;
    Disposer.register(module, this);
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

  public void setUpdateListener(@NotNull UpdateListener updateListener) {
    myListener = updateListener;
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

      loadAdditionalComponents(type, palette, VIEW_CLASSES_QUERY);
      // Reload the additional components after every build to find new custom components
      AndroidProjectBuildNotifications
        .subscribe(myModule.getProject(), this, context -> loadAdditionalComponents(type, palette, VIEW_CLASSES_QUERY));
    }
    catch (IOException | JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Loads all components that are not part of default palette description. This includes components from third party libraries and
   * components from the project.
   */
  @VisibleForTesting
  void loadAdditionalComponents(@NotNull NlLayoutType type,
                                @NotNull Palette palette,
                                @NotNull Function<Project, Query<PsiClass>> viewClasses) {
    Project project = myModule.getProject();
    DumbService dumbService = DumbService.getInstance(project);
    if (dumbService.isDumb()) {
      dumbService.runWhenSmart(() -> loadAdditionalComponents(type, palette, viewClasses));
    }
    else {
      // Clean-up the existing items
      palette.getItems().stream()
        .filter(Palette.Group.class::isInstance)
        .map(Palette.Group.class::cast)
        .filter(g -> PROJECT_GROUP.equals(g.getName()) || THIRD_PARTY_GROUP.equals(g.getName()))
        .map(Palette.Group::getItems)
        .forEach(List::clear);

      loadThirdPartyLibraryComponents(type, palette);
      loadProjectComponents(type, palette, viewClasses);
    }

    UpdateListener listener = myListener;
    if (listener != null) {
      ApplicationManager.getApplication().invokeLater(listener::update);
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
    AppResourceRepository appResourceRepository = AppResourceRepository.getOrCreateInstance(myModule);
    if (appResourceRepository == null) {
      return;
    }
    for (FileResourceRepository fileResource : appResourceRepository.getLibraries()) {
      // TODO: Add all palette components here:
      //for (PaletteComponent component : fileResource.getPaletteComponents()) {
      //  addThirdPartyComponent(...);
      //}
    }
    // TODO: Remove this. Use this line to test this feature.
    //addThirdPartyComponent(type, THIRD_PARTY_GROUP, palette, AndroidIcons.Android, AndroidIcons.Android24,
    //                       "com.google.android.exoplayer2.ui.SimpleExoPlayerView", null, null, "com.google.android.exoplayer:exoplayer",
    //                       null, Collections.singletonList("tag"), Collections.emptyList());
  }

  private void loadProjectComponents(@NotNull NlLayoutType type,
                                     @NotNull Palette palette,
                                     @NotNull Function<Project, Query<PsiClass>> viewClasses) {
    Project project = myModule.getProject();
    viewClasses.apply(project).forEach(psiClass -> {
      String description = psiClass.getName(); // We use the "simple" name as description on the preview.
      String className = psiClass.getQualifiedName();

      if (description == null || className == null) {
        // Currently we ignore anonymous views
        return false;
      }

      addAdditionalComponent(type, PROJECT_GROUP, palette, AndroidIcons.Android, AndroidIcons.Android24,
                             className, null, null, "",
                             null, Collections.emptyList(), Collections.emptyList());

      return true;
    });
  }

  /**
   * Adds a new component to the palette with the given properties. This method is used to add 3rd party and project components
   */
  @VisibleForTesting
  boolean addAdditionalComponent(@NotNull NlLayoutType type,
                                 @NotNull String groupName,
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
    if (tagName.indexOf('.') < 0 ||
        !NlComponentHelper.INSTANCE.viewClassToTag(tagName).equals(tagName) ||
        tagName.equals(CONSTRAINT_LAYOUT)) {
      // Do NOT allow third parties to overwrite predefined Google handlers
      return false;
    }

    ViewHandlerManager manager = ViewHandlerManager.get(myModule.getProject());
    ViewHandler handler = manager.getHandlerOrDefault(tagName);

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
    Palette.Group group = groups.stream()
      .filter(Palette.Group.class::isInstance)
      .map(Palette.Group.class::cast)
      .filter(g -> groupName.equals(g.getName()))
      .findFirst()
      .orElse(null);
    if (group == null) {
      group = new Palette.Group(groupName);
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
