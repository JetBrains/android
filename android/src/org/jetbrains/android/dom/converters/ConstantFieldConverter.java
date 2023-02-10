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
package org.jetbrains.android.dom.converters;

import com.android.sdklib.IAndroidTarget;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.LookupClass;
import org.jetbrains.android.dom.LookupPrefix;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConstantFieldConverter extends Converter<String> implements CustomReferenceConverter<String> {
  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return s;
  }

  @Override
  public String toString(@Nullable String value, ConvertContext context) {
    return value;
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    final DomElement domElement = context.getInvocationElement();
    final LookupClass lookupClass = domElement.getAnnotation(LookupClass.class);
    final LookupPrefix lookupPrefix = domElement.getAnnotation(LookupPrefix.class);

    if (lookupClass == null || lookupPrefix == null) {
      return PsiReference.EMPTY_ARRAY;
    }

    final Module module = context.getModule();
    final GlobalSearchScope scope = module != null ? GlobalSearchScope.allScope(module.getProject()) : domElement.getResolveScope();
    final JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(context.getPsiManager().getProject());

    final String[] classNames = lookupClass.value();
    final List<PsiReference> result = Lists.newArrayListWithCapacity(classNames.length);
    final Set<String> filteringSet = getFilteringSet(context);
    for (String className : classNames) {
      final PsiClass psiClass = javaFacade.findClass(className, scope);

      if (psiClass != null) {
        result.add(new MyReference(element, psiClass, lookupPrefix.value(), filteringSet));
      }
    }

    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  @Nullable
  private static Set<String> getFilteringSet(@NotNull ConvertContext context) {
    final Module module = context.getModule();

    if (module == null) {
      return null;
    }
    final AndroidPlatform platform = AndroidPlatform.getInstance(module);

    if (platform == null) {
      return null;
    }
    final AndroidTargetData targetData = AndroidTargetData.get(platform.getSdkData(), platform.getTarget());
    DomElement element = context.getInvocationElement().getParent();

    if (element instanceof Category) {
      return targetData.getStaticConstantsData().getCategories();
    }
    else if (element instanceof Action) {
      element = element.getParent();

      if (element instanceof IntentFilter) {
        element = element.getParent();

        if (element instanceof Activity) {
          return targetData.getStaticConstantsData().getActivityActions();
        }
        else if (element instanceof Service) {
          return targetData.getStaticConstantsData().getServiceActions();
        }
        else if (element instanceof Receiver) {
          return targetData.getStaticConstantsData().getReceiverActions();
        }
      }
    }
    return null;
  }

  private static class MyReference extends PsiReferenceBase<PsiElement> {

    private final PsiClass myClass;
    private final String myLookupPrefix;
    private final Set<String> myFilteringSet;

    public MyReference(@NotNull PsiElement element,
                       @NotNull PsiClass aClass,
                       @NotNull String lookupPrefix,
                       @Nullable Set<String> filteringSet) {
      super(element, true);
      myClass = aClass;
      myLookupPrefix = lookupPrefix;
      myFilteringSet = filteringSet;
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
      final String value = getValue();

      if (value.isEmpty()) {
        return null;
      }
      final Ref<PsiElement> ref = Ref.create();

      processFields(new Processor<Pair<PsiField, String>>() {
        @Override
        public boolean process(Pair<PsiField, String> pair) {
          if (value.equals(pair.getSecond())) {
            ref.set(pair.getFirst());
            return false;
          }
          return true;
        }
      });
      return ref.get();
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      final List<Object> result = new ArrayList<>();
      final Set<String> added = new HashSet<>();

      processFields(new Processor<Pair<PsiField, String>>() {
        @Override
        public boolean process(Pair<PsiField, String> pair) {
          final String s = pair.getSecond();

          if (myFilteringSet != null && !myFilteringSet.contains(s)) {
            return true;
          }
          if (added.add(s)) {
            LookupElementBuilder builder = LookupElementBuilder.create(pair.getFirst(), s).withCaseSensitivity(true);

            // ConstantFieldConverter provides autocompletion for permission, action and category names which consist
            // of period-separated parts. We want to provide case-insensitive completion by the last part of the name
            // (after the last dot), for example, to provide "android.permission.INTERNET" as completion for "INT".
            // Usage of LookupElementBuilder#withLookupString adds an additional string that would be used for
            // looking up completion by entered text.
            //
            // Bugs: http://b.android.com/184877, http://b.android.com/154004
            final String unqualifiedName = AndroidUtils.getUnqualifiedName(s);
            if (unqualifiedName != null) {
              builder = builder.withLookupString(unqualifiedName);
            }
            result.add(builder);
          }
          return true;
        }
      });
      return ArrayUtil.toObjectArray(result);
    }

    private void processFields(@NotNull Processor<Pair<PsiField, String>> processor) {
      final PsiField[] psiFields = myClass.getFields();

      for (PsiField field : psiFields) {
        if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.PUBLIC)) {
          final PsiExpression initializer = field.getInitializer();

          if (initializer instanceof PsiLiteralExpression) {
            final PsiLiteralExpression literalExpression = (PsiLiteralExpression)initializer;
            final Object o = literalExpression.getValue();

            if (o instanceof String && o.toString().startsWith(myLookupPrefix)) {
              if (!processor.process(Pair.create(field, o.toString()))) {
                return;
              }
            }
          }
        }
      }
    }
  }
}
