/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.resources;

import static org.jetbrains.android.AndroidResolveScopeEnlarger.LIGHT_CLASS_KEY;
import static org.jetbrains.android.AndroidResolveScopeEnlarger.MODULE_POINTER_KEY;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.res.ResourceRepositoryRClass;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.res.LocalResourceRepository;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.augment.AndroidLightField;
import org.jetbrains.android.facet.AndroidFacet;

/** Blaze implementation of an R class based on resource repositories. */
public class BlazeRClass extends ResourceRepositoryRClass {

  private final AndroidFacet androidFacet;

  public BlazeRClass(PsiManager psiManager, AndroidFacet androidFacet, String packageName) {
    super(
        psiManager,
        new ResourcesSource() {
          @Override
          public String getPackageName() {
            return packageName;
          }

          // @Override #api4.1
          public Transitivity getTransitivity() {
            return Transitivity.TRANSITIVE;
          }

          @Override
          public StudioResourceRepositoryManager getResourceRepositoryManager() {
            return StudioResourceRepositoryManager.getInstance(androidFacet);
          }

          @Override
          public LocalResourceRepository getResourceRepository() {
            return StudioResourceRepositoryManager.getAppResources(androidFacet);
          }

          @Override
          public ResourceNamespace getResourceNamespace() {
            return ResourceNamespace.RES_AUTO;
          }

          @Override
          public AndroidLightField.FieldModifier getFieldModifier() {
            return AndroidLightField.FieldModifier.NON_FINAL;
          }
        });
    this.androidFacet = androidFacet;
    setModuleInfo(getModule(), false);
    VirtualFile virtualFile = myFile.getViewProvider().getVirtualFile();
    virtualFile.putUserData(
        MODULE_POINTER_KEY, ModulePointerManager.getInstance(getProject()).create(getModule()));
    virtualFile.putUserData(LIGHT_CLASS_KEY, ResourceRepositoryRClass.class);
  }

  public Module getModule() {
    return androidFacet.getModule();
  }
}
