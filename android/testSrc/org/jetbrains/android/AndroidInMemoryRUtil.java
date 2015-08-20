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
package org.jetbrains.android;

import com.google.common.base.Strings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.augment.ResourceTypeClass;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This is a utility class that registers example in-memory R support for other unit tests.
 * Call setup during test setup and teardown during test teardown.
 */
public class AndroidInMemoryRUtil {
  private AndroidFacet myFacet = null;
  private AndroidResourceClassFinder myRFinder = null;
  private ExtensionPoint<PsiElementFinder> myPsiElementFinderExtensionPoint;

  public  AndroidInMemoryRUtil(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  public void setup() {
    myFacet.setLightRClass(new AndroidPackageRClass(
      PsiManager.getInstance(myFacet.getModule().getProject()),
      myFacet.getManifest().getPackage().getStringValue(), myFacet.getModule()));

    myPsiElementFinderExtensionPoint = Extensions
      .getArea(myFacet.getModule().getProject())
      .getExtensionPoint(PsiElementFinder.EP_NAME);

    myRFinder = new AndroidResourceClassFinder(myFacet.getModule().getProject());
    myPsiElementFinderExtensionPoint.registerExtension(myRFinder);
    ((JavaPsiFacadeImpl)JavaPsiFacade.getInstance(myFacet.getModule().getProject())).clearFindersCache();
  }

  public void tearDown() throws Exception {
    myPsiElementFinderExtensionPoint.unregisterExtension(myRFinder);
    ((JavaPsiFacadeImpl)JavaPsiFacade.getInstance(myFacet.getModule().getProject())).clearFindersCache();
  }

  public class AndroidPackageRClass extends AndroidLightClassBase {
    private final PsiFile myFile;
    private String myFullyQualifiedName;
    private Module myModule;

    public AndroidPackageRClass(@NotNull PsiManager psiManager,
                                @NotNull String packageName,
                                @NotNull Module module) {
      super(psiManager);

      myModule = module;
      myFullyQualifiedName = packageName + ".R";
      myFile = PsiFileFactory.getInstance(myManager.getProject())
        .createFileFromText("R.java", JavaFileType.INSTANCE, "package " + packageName + ";");

      this.putUserData(ModuleUtilCore.KEY_MODULE, module);
      myFile.putUserData(ModuleUtilCore.KEY_MODULE, module);
    }

    @Override
    public String toString() {
      return "AndroidPackageRClass";
    }

    @Nullable
    @Override
    public String getQualifiedName() {
      return myFullyQualifiedName;
    }

    @Override
    public String getName() {
      return "R";
    }

    @Nullable
    @Override
    public PsiClass getContainingClass() {
      return null;
    }

    @Nullable
    @Override
    public PsiFile getContainingFile() {
      return myFile;
    }

    @NotNull
    @Override
    public PsiClass[] getInnerClasses() {
      if (DumbService.isDumb(getProject())) {
        return new PsiClass[0];
      }

      final AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet == null) {
        return new PsiClass[0];
      }

      final Set<String> types = ResourceReferenceConverter.getResourceTypesInCurrentModule(facet);
      final List<PsiClass> result = new ArrayList<PsiClass>();

      for (String resType : types) {
        result.add(new ResourceTypeClass(facet, resType, this));
      }
      return result.toArray(new PsiClass[result.size()]);
    }

    @Override
    public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
      for (PsiClass aClass : getInnerClasses()) {
        if (name.equals(aClass.getName())) {
          return aClass;
        }
      }
      return null;
    }
  }

  public class AndroidResourceClassFinder extends PsiElementFinder {
    static final String INTERNAL_R_CLASS_SHORTNAME = ".R";
    private Project myProject;

    public AndroidResourceClassFinder(@NotNull Project project) {
      myProject = project;
    }

    @Nullable
    @Override
    public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
      if (qualifiedName.endsWith(INTERNAL_R_CLASS_SHORTNAME)) {
        PsiClass[] result = getClasses(
          qualifiedName.substring(0, qualifiedName.lastIndexOf(INTERNAL_R_CLASS_SHORTNAME)),
          scope);
        return result.length > 0 ? result[0] : null;
      }
      return null;
    }

    @NotNull
    @Override
    public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
      if (qualifiedName.endsWith(INTERNAL_R_CLASS_SHORTNAME)) {
        return getClasses(
          qualifiedName.substring(0, qualifiedName.lastIndexOf(INTERNAL_R_CLASS_SHORTNAME)),
          scope);
      }
      return new PsiClass[0];
    }

    @NotNull
    @Override
    public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
      String targetPackageName = psiPackage.getQualifiedName();
      if (Strings.isNullOrEmpty(targetPackageName)) {
        return new PsiClass[0];
      }

      return getClasses(targetPackageName, scope);
    }

    private PsiClass[] getClasses(@NotNull String targetPackageName,
                                  @NotNull GlobalSearchScope scope) {
      List<PsiClass> result = new ArrayList<PsiClass>();

      for(Module module : ModuleManager.getInstance(myProject).getModules()) {
        if (!scope.isSearchInModuleContent(module)) {
          continue;
        }
        AndroidFacet facet = AndroidFacet.getInstance(module);
        // If we cannot find a facet or manifest, we cannot have resources.
        if (facet == null || facet.getManifest() == null) {
          continue;
        }

        // If our target package isn't associated with this module, continue.
        String thisPackageName = facet.getManifest().getPackage().getStringValue();
        if (Strings.isNullOrEmpty(targetPackageName)
            || !targetPackageName.equals(thisPackageName)) {
          continue;
        }

        PsiClass cachedResult = facet.getLightRClass();
        if (cachedResult != null) {
          result.add(cachedResult);
        }
      }

      return result.toArray(new PsiClass[result.size()]);
    }
  }
}
