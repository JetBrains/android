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
package org.jetbrains.android.dom.converters;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.lint.common.LintIdeClient;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.lint.checks.ApiLookup;
import com.android.tools.lint.checks.ApiMember;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.RemovedApiField;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Provides completion for app permission values.
 * Modeled after {@link ConstantFieldConverter}.
 */
public class AndroidPermissionConverter extends Converter<String> implements CustomReferenceConverter<String> {
  private static final String PERMISSION_CLASS_NAME = "android.Manifest.permission";
  private static final String PERMISSION_CLASS_NAME_FOR_API_LOOKUP = "android/Manifest$permission";
  private static final String PERMISSION_PREFIX = "android.permission.";

  @Override
  public String fromString(@Nullable @NonNls String str, ConvertContext context) {
    return str;
  }

  @Override
  public String toString(@Nullable String value, ConvertContext context) {
    return value;
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(@NotNull GenericDomValue<String> value, @NotNull PsiElement element,
                                         @NotNull ConvertContext context) {
    DomElement domElement = context.getInvocationElement();

    Module module = context.getModule();
    GlobalSearchScope scope = module != null ? GlobalSearchScope.allScope(module.getProject()) : domElement.getResolveScope();
    JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(context.getPsiManager().getProject());

    PsiClass permissionClass = javaFacade.findClass(PERMISSION_CLASS_NAME, scope);
    if (permissionClass == null) {
      return PsiReference.EMPTY_ARRAY;
    }

    int minVersion = 1;
    int maxVersion = Integer.MAX_VALUE;
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModel model = AndroidModel.get(facet);
        if (model != null) {
          AndroidVersion version = model.getMinSdkVersion();
          if (version != null) {
            minVersion = version.getApiLevel();
          }
          version = model.getTargetSdkVersion();
          if (version != null) {
            maxVersion = version.getApiLevel();
          }
        }
      }
    }

    return new PsiReference[] { new MyReference(element, permissionClass, minVersion, maxVersion) };
  }

  private static class MyReference extends PsiReferenceBase<PsiElement> {
    private final PsiClass myPermissionClass;
    private final int myMinVersion;
    private final int myMaxVersion;

    public MyReference(@NotNull PsiElement element, @NotNull PsiClass permissionClass, int minVersion, int maxVersion) {
      super(element, true);
      myPermissionClass = permissionClass;
      myMinVersion = minVersion;
      myMaxVersion = maxVersion;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return ResolveCache.getInstance(myElement.getProject()).resolveWithCaching(this, new ResolveCache.Resolver() {
        @Nullable
        @Override
        public PsiElement resolve(@NotNull PsiReference reference, boolean incompleteCode) {
          return resolveInner();
        }
      }, false, false);
    }

    @Nullable
    private PsiElement resolveInner() {
      String qualifiedName = getValue();
      if (!qualifiedName.startsWith(PERMISSION_PREFIX)) {
        return null;
      }

      String name = qualifiedName.substring(PERMISSION_PREFIX.length());
      Map<String, PsiElement> permissions = getPermissions(Collections.singleton(name));
      return permissions.get(name);
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      Map<String, PsiElement> permissions = getPermissions(null);
      List<LookupElementBuilder> result = new ArrayList<>(permissions.size());
      for (Map.Entry<String, PsiElement> entry : permissions.entrySet()) {
        String name = entry.getKey();
        String qualifiedName = PERMISSION_PREFIX + name;
        LookupElementBuilder builder = LookupElementBuilder.create(entry.getValue(), qualifiedName).withCaseSensitivity(true);

        // Provide case-insensitive completion by the last part of the name (after the last dot).
        // For example, to provide "android.permission.INTERNET" as completion for "INT".
        builder = builder.withLookupString(name);
        result.add(builder);
      }
      return ArrayUtil.toObjectArray(result);
    }

    /**
     * Calculates the set of app permissions applicable to the API level range between {@link #myMinVersion} and {@link #myMaxVersion},
     * inclusive.
     *
     * @param filter if not null, limits the set of returned permissions
     */
    @NotNull
    private Map<String, PsiElement> getPermissions(@Nullable Set<String> filter) {
      Project project = getElement().getProject();
      ApiLookup apiLookup = LintIdeClient.getApiLookup(project);
      Collection<ApiMember> removedFields =
          apiLookup == null ? Collections.emptyList() : apiLookup.getRemovedFields(PERMISSION_CLASS_NAME_FOR_API_LOOKUP);

      Map<String, ApiMember> removedFieldMap;
      if (removedFields == null || removedFields.isEmpty()) {
        removedFieldMap = Collections.emptyMap();
      } else {
        removedFieldMap = new HashMap<>();
        for (ApiMember field : removedFields) {
          removedFieldMap.put(field.getSignature(), field);
        }
      }

      Map<String, PsiElement> result = new TreeMap<>();
      // Add current android.Manifest$permission fields that existed between minVersion and targetVersion.
      for (PsiField field : myPermissionClass.getFields()) {
        if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.PUBLIC)) {
          String fieldName = field.getName();
          ApiMember removedField = removedFieldMap.remove(fieldName);
          if ((removedField == null ? isApplicable(field, apiLookup) : isApplicable(removedField))
              && (filter == null || filter.contains(fieldName))) {
            result.put(fieldName, field);
          }
        }
      }

      // Add removed android.Manifest$permission fields that existed between minVersion and targetVersion.
      for (ApiMember removedField : removedFieldMap.values()) {
        if (isApplicable(removedField)) {
          String fieldName = removedField.getSignature();
          if (filter == null || filter.contains(fieldName)) {
            RemovedApiField field = new RemovedApiField(fieldName, myPermissionClass, removedField.getSince(), removedField.getRemovedIn(),
                                                        removedField.getRemovedIn());
            result.put(fieldName, field);
          }
        }
      }

      return result;
    }

    private boolean isApplicable(@NotNull ApiMember removedField) {
      return removedField.getSince() <= myMaxVersion && removedField.getRemovedIn() > myMinVersion;
    }

    private boolean isApplicable(@NotNull PsiField field, @Nullable ApiLookup apiLookup) {
      return apiLookup == null || apiLookup.getFieldVersions(PERMISSION_CLASS_NAME_FOR_API_LOOKUP, field.getName()).min() <= myMaxVersion;
    }
  }
}
