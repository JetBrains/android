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
package org.jetbrains.android.dom.converters;

import com.android.ide.common.res2.DataBindingResourceType;
import com.android.tools.idea.lang.databinding.DataBindingXmlReferenceContributor;
import com.android.tools.idea.res.DataBindingInfo;
import com.android.tools.idea.res.PsiDataBindingResourceItem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.dom.layout.AndroidLayoutUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.lang.databinding.DataBindingCompletionUtil.JAVA_LANG;

/**
 * The converter for "type" attribute in databinding layouts.
 */
public class DataBindingConverter extends ResolvingConverter<PsiClass> implements CustomReferenceConverter<PsiClass> {

  /**
   * Get the fully qualified name of the class referenced by {@code nameOrAlias}.
   * <p/>
   * It is not guaranteed that the class will exist. Also, the name returned here uses '.' for inner classes (like import declarations) and
   * not '$' as used by JVM.
   *
   * @param nameOrAlias     a fully qualified name, or an alias as declared in an {@code <import>} or an inner class of the alias.
   * @param dataBindingInfo for getting the list of {@code <import>} tags.
   * @return the fully qualified name. This does not guarantee that the class will exist.
   */
  public static String getQualifiedType(@Nullable String nameOrAlias, @Nullable final DataBindingInfo dataBindingInfo) {
    if (nameOrAlias == null) {
      return null;
    }
    nameOrAlias = nameOrAlias.replace('$', '.');
    if (dataBindingInfo == null) {
      return nameOrAlias;
    }
    final int i = nameOrAlias.indexOf('.');
    final String alias = i >= 0 ? nameOrAlias.substring(0, i) : nameOrAlias;
    PsiDataBindingResourceItem imp = getImport(alias, dataBindingInfo);
    if (imp != null) {
      final String type = imp.getTypeDeclaration();
      if (type != null) {
        return nameOrAlias.equals(alias) ? type : type + nameOrAlias.substring(alias.length());
      }
    }
    return nameOrAlias;
  }

  private static PsiDataBindingResourceItem getImport(@NotNull String alias, @NotNull DataBindingInfo dataBindingInfo) {
    for (final PsiDataBindingResourceItem anImport : dataBindingInfo.getItems(DataBindingResourceType.IMPORT)) {
      if (alias.equals(AndroidLayoutUtil.getAlias(anImport))) {
        return anImport;
      }
    }
    return null;
  }

  /**
   * Completion is handled by {@link com.android.tools.idea.lang.databinding.DataBindingCompletionContributor}. So, nothing to do here.
   */
  @NotNull
  @Override
  public Collection<? extends PsiClass> getVariants(final ConvertContext context) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public PsiClass fromString(@Nullable @NonNls final String s, final ConvertContext context) {
    final DataBindingInfo bindingInfo = getDataBindingInfo(context);
    final String qualifiedName = getQualifiedType(s, bindingInfo);
    if (qualifiedName == null) {
      return null;
    }
    final Module module = context.getModule();
    if (module == null) {
      return null;
    }
    Project project = context.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
    if (qualifiedName.length() > 0 && qualifiedName.indexOf('.') < 0) {
      if (Character.isLowerCase(qualifiedName.charAt(0))) {
        PsiPrimitiveType primitiveType = PsiJavaParserFacadeImpl.getPrimitiveType(qualifiedName);
        if (primitiveType != null) {
          PsiClassType boxedType = primitiveType.getBoxedType(PsiManager.getInstance(project), scope);
          if (boxedType != null) {
            return boxedType.resolve();
          }
          return null;
        }
      }
      else {
        PsiClass aClass = psiFacade.findClass(JAVA_LANG + qualifiedName, scope);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    return psiFacade.findClass(qualifiedName, scope);
  }

  @Nullable
  @Override
  public String toString(@Nullable final PsiClass psiClass, final ConvertContext context) {
    if (psiClass == null) {
      return null;
    }
    final String qualifiedName = psiClass.getName();
    // try and replace with import if possible.
    final DataBindingInfo bindingInfo = getDataBindingInfo(context);
    if (bindingInfo != null) {
      int longestPrefix = 0;
      PsiDataBindingResourceItem longestImport = null;
      for (final PsiDataBindingResourceItem anImport : bindingInfo.getItems(DataBindingResourceType.IMPORT)) {
        // Try and find the longest matching import. For inner classes, either the outer or the inner class may be imported.
        final String prefix = getLongestPrefix(anImport.getTypeDeclaration(), qualifiedName);
        if (prefix.length() > longestPrefix) {
          if (qualifiedName.length() == prefix.length()) {
            return AndroidLayoutUtil.getAlias(anImport);
          }
          final char c = qualifiedName.charAt(prefix.length());
          if (c == '.') {
            longestPrefix = prefix.length();
            longestImport = anImport;
          }
        }
      }
      if (longestImport != null) {
        return AndroidLayoutUtil.getAlias(longestImport) + qualifiedName.substring(longestPrefix);
      }
    }
    if (qualifiedName.startsWith(JAVA_LANG)) {
      return qualifiedName.substring(JAVA_LANG.length());
    }
    return qualifiedName;
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(final GenericDomValue<PsiClass> value, final PsiElement element, final ConvertContext context) {
    assert element instanceof XmlAttributeValue;
    final XmlAttributeValue attrValue = (XmlAttributeValue)element;
    final String strValue = attrValue.getValue();

    final int start = attrValue.getValueTextRange().getStartOffset() - attrValue.getTextRange().getStartOffset();

    List<PsiReference> result = new ArrayList<>();
    final String[] nameParts = strValue.split("[$.]");
    Module module = context.getModule();
    if (nameParts.length == 0 || module == null) {
      return PsiReference.EMPTY_ARRAY;
    }

    int offset = start;

    // Check if the first namePart is an alias.
    DataBindingInfo bindingInfo = getDataBindingInfo(context);
    int idx = 0;   // for iterating over the nameParts.
    int diff = 0;  // difference in lengths of the "type" and the "alias". Used in range computation later.
    String fullType = strValue.replace('$', '.');
    if (bindingInfo != null) {
      String alias = nameParts[idx];
      PsiDataBindingResourceItem anImport = getImport(alias, bindingInfo);
      if (anImport != null) {
        // Found an import matching the first namePart. Add a reference from this to the type.
        idx++;
        TextRange range = new TextRange(offset, offset += alias.length());
        offset++;  // Skip the next dot or dollar separator (if any)
        String type = anImport.getTypeDeclaration();
        result.add(new AliasedReference(element, range, type, module));
        fullType = type + fullType.substring(alias.length());
        diff = type.length() - alias.length();
      }
      else {
        //  Check java.lang and primitives
        if (nameParts.length == 1) {
          if (alias.length() > 0) {
            if (Character.isLowerCase(alias.charAt(0))) {
              final PsiPrimitiveType primitive = PsiJavaParserFacadeImpl.getPrimitiveType(alias);
              if (primitive != null) {
                result.add(new PsiReferenceBase<PsiElement>(element, true) {
                  @Nullable
                  @Override
                  public PsiElement resolve() {
                    return myElement;
                  }

                  @NotNull
                  @Override
                  public Object[] getVariants() {
                    return ArrayUtil.EMPTY_OBJECT_ARRAY;
                  }
                });
              }
            }
            else {
              // java.lang
              PsiClass aClass = JavaPsiFacade.getInstance(context.getProject())
                .findClass(JAVA_LANG + alias, GlobalSearchScope.moduleWithLibrariesScope(module));
              if (aClass != null) {
                final TextRange range = new TextRange(offset, offset += alias.length());
                result.add(new ClassReference(element, range, aClass));
              }
            }
            idx++;
          }
        }
      }
    }
    for (; idx < nameParts.length; idx++,offset++) {
      final String packageName = nameParts[idx];
      if (packageName.length() > 0) {
        final TextRange range = new TextRange(offset, offset += packageName.length());
        result.add(new AliasedReference(element, range, fullType.substring(0, diff + offset - start), module));
      }
    }

    return result.toArray(new PsiReference[result.size()]);

  }

  @Nullable
  private static DataBindingInfo getDataBindingInfo(@NotNull final ConvertContext context) {
    return DataBindingXmlReferenceContributor.getDataBindingInfo(context.getFile());
  }

  private static String getLongestPrefix(@Nullable final String a, @Nullable final String b) {
    if (a == null || b == null) {
      return "";
    }
    final String shorter = a.length() > b.length() ? b : a;
    final String longer = a.length() > b.length() ? a : b;
    int i = 0;
    while (i < shorter.length() && shorter.charAt(i) == longer.charAt(i)) {
      i++;
    }
    return shorter.substring(0, i);
  }


  private static class AliasedReference extends PsiReferenceBase<PsiElement> {
    private final String myReferenceTo;
    private final Module myModule;

    public AliasedReference(PsiElement referenceFrom, TextRange range, String referenceTo, Module module) {
      super(referenceFrom, range, true);
      myReferenceTo = referenceTo;
      myModule = module;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return ResolveCache.getInstance(myElement.getProject()).resolveWithCaching(this, new ResolveCache.Resolver() {

        @Override
        public PsiElement resolve(@NotNull PsiReference psiReference, boolean incompleteCode) {
          return resolveInner();
        }
      }, false, false);
    }

    private PsiElement resolveInner() {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myElement.getProject());
      PsiPackage aPackage = facade.findPackage(myReferenceTo);
      if (aPackage != null) {
        return aPackage;
      }
      else {
        PsiClass aClass = facade.findClass(myReferenceTo, myModule != null
                                                          ? myModule.getModuleWithDependenciesAndLibrariesScope(false)
                                                          : myElement.getResolveScope());
        if (aClass != null) {
          return aClass;
        }
        return null;
      }

    }

    /**
     * Don't care about variants here since completion by
     * {@link org.jetbrains.android.AndroidCompletionContributor#completeDataBindingTypeAttr}.
     */
    @NotNull
    @Override
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @NotNull
    @Override
    public String getCanonicalText() {
      return myReferenceTo;
    }
  }

  private static class ClassReference extends PsiReferenceBase<PsiElement> {

    @NotNull private final PsiElement myResolveTo;

    public ClassReference(@NotNull PsiElement element, @NotNull TextRange range, @NotNull PsiElement resolveTo) {
      super(element, range);
      myResolveTo = resolveTo;
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return ResolveCache.getInstance(myElement.getProject()).resolveWithCaching(this, new ResolveCache.Resolver() {

        @Override
        public PsiElement resolve(@NotNull PsiReference psiReference, boolean incompleteCode) {
          return resolveInner();
        }
      }, false, false);
    }


    private PsiElement resolveInner() {
      return myResolveTo;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }
}
