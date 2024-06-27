/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.facet;

import static com.android.tools.idea.AndroidPsiUtils.getModuleSafely;

import com.android.tools.idea.util.CommonAndroidUtil;
import com.android.tools.idea.util.LinkedAndroidModuleGroup;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidFacet extends Facet<AndroidFacetConfiguration> {
  public static final FacetTypeId<AndroidFacet> ID = new FacetTypeId<>("android");
  public static final String NAME = "Android";

  @Nullable
  public static AndroidFacet getInstance(@NotNull VirtualFile file, @NotNull Project project) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);

    if (module == null) {
      return null;
    }

    return getInstance(module);
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull ConvertContext context) {
    return findAndroidFacet(context.getModule());
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull PsiElement element) {
    return findAndroidFacet(getModuleSafely(element));
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull DomElement element) {
    return findAndroidFacet(element.getModule());
  }

  @Nullable
  private static AndroidFacet findAndroidFacet(@Nullable Module module) {
    return module != null ? getInstance(module) : null;
  }

  @Nullable
  public static AndroidFacet getInstance(@NotNull Module module) {
    return !module.isDisposed() ? FacetManager.getInstance(module).getFacetByType(ID) : null;
  }

  public AndroidFacet(@NotNull Module module, @NotNull String name, @NotNull AndroidFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static AndroidFacetType getFacetType() {
    return (AndroidFacetType)FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  @NotNull
  public AndroidFacetProperties getProperties() {
    return getConfiguration().getState();
  }

  /**
   * Obtains the module that contains the main production sources.
   *
   * For Gradle, If module per source set is enabled then this will return the module that contains sources and dependencies
   * from the main artifact ONLY.
   * Or, If module per source set is disabled then this will return the combined module with all sources, tests and dependencies
   * for the combined main, unit test and android test artifacts.
   */
  @NotNull
  public Module getMainModule() {
    Module mainModule = getModuleByMethod(LinkedAndroidModuleGroup::getMain);
    if (mainModule == null) throw new IllegalStateException("Main modules shouldn't be null, something has gone wrong!");
    return mainModule;
  }

  /**
   * Obtains the empty module which should be used when sources or dependencies aren't needed, or to get information to display to the user.
   *
   * For Gradle, If module per source set is enabled then this will return the module that has as children all the modules from this
   * Android Gradle project, this module will contain no sources or dependencies and is just used to group the other modules.
   * Or, If module per source set is disabled then this will return the combined module with all sources, tests and dependencies
   * for the combined main, unit test and android test artifacts.
   */
  @NotNull
  public Module getHolderModule() {
    Module holderModule = getModuleByMethod(LinkedAndroidModuleGroup::getHolder);
    if (holderModule == null) throw new IllegalStateException("Holder modules shouldn't be null, something has gone wrong!");
    return holderModule;
  }

  /**
   * Helper method to find different modules related to this Android facet.
   *
   * @param func the getter from the {@link LinkedAndroidModuleGroup} for the module needed
   * @return the desired module
   */
  @Nullable
  private Module getModuleByMethod(@NotNull Function<LinkedAndroidModuleGroup, Module> func) {
      // We can't use the utilities in ModuleUtil in this module as such we access the LINKED_ANDROID_MODULE_GROUP directly
      LinkedAndroidModuleGroup moduleGroup = getModule().getUserData(CommonAndroidUtil.LINKED_ANDROID_MODULE_GROUP);
      return (moduleGroup != null) ? func.apply(moduleGroup) : getModule();
  }
}
