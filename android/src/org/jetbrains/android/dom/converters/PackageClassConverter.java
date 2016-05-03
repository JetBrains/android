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

import com.android.tools.idea.AndroidTextUtils;
import com.android.tools.idea.model.MergedManifest;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PackageClassConverter extends ResolvingConverter<PsiClass> implements CustomReferenceConverter<PsiClass> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.dom.converters.PackageClassConverter");

  private final boolean myUseManifestBasePackage;
  private final String[] myExtendClassesNames;

  /**
   * @param useManifestBasePackage if true, even when the attribute it's not defined within the manifest, the resolution will use the
   *                               manifest package for completion.
   * @param extendClassesNames     list of the classes that the searched class can extend
   */
  public PackageClassConverter(boolean useManifestBasePackage, String... extendClassesNames) {
    myUseManifestBasePackage = useManifestBasePackage;
    myExtendClassesNames = extendClassesNames;
  }

  public PackageClassConverter(String... extendClassesNames) {
    this(false, extendClassesNames);
  }

  public PackageClassConverter() {
    this(false, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Override
  @NotNull
  public Collection<? extends PsiClass> getVariants(ConvertContext context) {
    return Collections.emptyList();
  }

  @Nullable
  private String getManifestPackage(@NotNull ConvertContext context) {
    DomElement domElement = context.getInvocationElement();
    Manifest manifest = domElement.getParentOfType(Manifest.class, true);
    String manifestPackage = manifest != null ? manifest.getPackage().getValue() : null;

    if (manifestPackage == null && myUseManifestBasePackage) {
      manifestPackage = MergedManifest.get(context.getModule()).getPackage();
    }
    return manifestPackage;
  }

  @Override
  public PsiClass fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    if (s == null) {
      return null;
    }
    String manifestPackage = getManifestPackage(context);
    s = s.replace('$', '.');
    String className = null;

    if (manifestPackage != null) {
      if (s.startsWith(".")) {
        className = manifestPackage + s;
      }
      else {
        className = manifestPackage + "." + s;
      }
    }
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getPsiManager().getProject());
    final Module module = context.getModule();
    GlobalSearchScope scope = module != null
                              ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
                              : context.getInvocationElement().getResolveScope();
    PsiClass psiClass = className != null ? facade.findClass(className, scope) : null;
    if (psiClass == null) {
      psiClass = facade.findClass(s, scope);
    }
    return psiClass;
  }

  /**
   * @return whether the given file is contained within the test sources
   */
  private static boolean isTestFile(@NotNull AndroidFacet facet, @Nullable VirtualFile file) {
    if (file != null) {
      for (IdeaSourceProvider sourceProvider : IdeaSourceProvider.getCurrentTestSourceProviders(facet)) {
        if (sourceProvider.containsFile(file)) {
          return true;
        }
      }
    }

    return false;
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<PsiClass> value, final PsiElement element, ConvertContext context) {
    assert element instanceof XmlAttributeValue;
    final XmlAttributeValue attrValue = (XmlAttributeValue)element;
    final String strValue = attrValue.getValue();

    final boolean startsWithPoint = strValue.startsWith(".");
    final int start = attrValue.getValueTextRange().getStartOffset() - attrValue.getTextRange().getStartOffset();

    final DomElement domElement = context.getInvocationElement();
    final String manifestPackage = getManifestPackage(context);
    final ExtendClass extendClassAnnotation = domElement.getAnnotation(ExtendClass.class);

    final String[] extendClassesNames = extendClassAnnotation != null
                                        ? new String[]{extendClassAnnotation.value()}
                                        : myExtendClassesNames;
    final boolean inModuleOnly = domElement.getAnnotation(CompleteNonModuleClass.class) == null;

    AndroidFacet facet = AndroidFacet.getInstance(context);
    // If the source XML file is contained within the test folders, we'll also allow to resolve test classes
    final boolean isTestFile = facet != null && isTestFile(facet, element.getContainingFile().getVirtualFile());

    if (strValue.isEmpty()) {
      return PsiReference.EMPTY_ARRAY;
    }

    final List<PsiReference> result = new ArrayList<>();
    final Module module = context.getModule();

    /**
     * Using inner class here as opposed to anonymous one as with anonymous class it wouldn't be possible to access {@code myPartStart} later
     */
    class CustomConsumer implements Consumer<Integer> {
      int myPartStart = 0;
      private boolean myIsPackage = true;

      @Override
      public void consume(Integer index) {
        if (index > myPartStart) {
          final TextRange range = new TextRange(start + myPartStart, start + index);
          final MyReference reference =
            new MyReference(element, range, manifestPackage, startsWithPoint, start, myIsPackage, module, extendClassesNames, inModuleOnly,
                            isTestFile);
          result.add(reference);
        }

        myPartStart = index + 1;
      }
    }

    final CustomConsumer consumer = new CustomConsumer();

    AndroidTextUtils.forEachOccurrence(strValue, '.', consumer);
    consumer.myIsPackage = false;
    AndroidTextUtils.forEachOccurrence(strValue, '$', consumer.myPartStart, consumer);
    consumer.consume(strValue.length());

    return result.toArray(new PsiReference[result.size()]);
  }

  @Nullable
  public static String getQualifiedName(@NotNull PsiClass aClass) {
    PsiElement parent = aClass.getParent();
    if (parent instanceof PsiClass) {
      String parentQName = getQualifiedName((PsiClass)parent);
      if (parentQName == null) {
        return null;
      }
      return parentQName + "$" + aClass.getName();
    }
    return aClass.getQualifiedName();
  }

  @Nullable
  private static String getName(@NotNull PsiClass aClass) {
    PsiElement parent = aClass.getParent();
    if (parent instanceof PsiClass) {
      String parentName = getName((PsiClass)parent);
      if (parentName == null) {
        return null;
      }
      return parentName + "$" + aClass.getName();
    }
    return aClass.getName();
  }

  @Override
  @Nullable
  public String toString(@Nullable PsiClass psiClass, ConvertContext context) {
    DomElement domElement = context.getInvocationElement();
    Manifest manifest = domElement.getParentOfType(Manifest.class, true);
    final String packageName = manifest == null ? null : manifest.getPackage().getValue();
    return classToString(psiClass, packageName, "");
  }

  @Nullable
  public static String getPackageName(@NotNull PsiClass psiClass) {
    final PsiFile file = psiClass.getContainingFile();
    return file instanceof PsiClassOwner ? ((PsiClassOwner)file).getPackageName() : null;
  }

  @Nullable
  private static String classToString(PsiClass psiClass, String basePackageName, String prefix) {
    if (psiClass == null) {
      return null;
    }
    String qName = getQualifiedName(psiClass);
    if (qName == null) {
      return null;
    }
    PsiFile file = psiClass.getContainingFile();
    if (file instanceof PsiClassOwner) {
      PsiClassOwner psiFile = (PsiClassOwner)file;
      if (Comparing.equal(psiFile.getPackageName(), basePackageName)) {
        String name = getName(psiClass);
        if (name != null) {
          final String dottedName = '.' + name;
          if (dottedName.startsWith(prefix)) {
            return dottedName;
          }
          else if (name.startsWith(prefix)) {
            return name;
          }
        }
      }
      else if (basePackageName != null && qName.startsWith(basePackageName + ".")) {
        final String name = qName.substring(basePackageName.length());
        if (name.startsWith(prefix)) {
          return name;
        }
      }
    }
    return qName;
  }

  @NotNull
  public static Collection<PsiClass> findInheritors(@NotNull Project project, @Nullable final Module module,
                                                    @NotNull final String className, boolean inModuleOnly) {
    PsiClass base = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
    if (base != null) {
      GlobalSearchScope scope = inModuleOnly && module != null
                                ? GlobalSearchScope.moduleWithDependenciesScope(module)
                                : GlobalSearchScope.allScope(project);
      Query<PsiClass> query = ClassInheritorsSearch.search(base, scope, true);
      return query.findAll();
    }
    return new ArrayList<>();
  }

  private static class MyReference extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider, LocalQuickFixProvider {
    private final int myStart;
    private final String myBasePackage;
    private final boolean myStartsWithPoint;
    private final boolean myIsPackage;
    private final Module myModule;
    private final String[] myExtendsClasses;
    private final boolean myCompleteOnlyModuleClasses;
    private final boolean myIncludeTests;

    public MyReference(PsiElement element,
                       TextRange range,
                       String basePackage,
                       boolean startsWithPoint,
                       int start,
                       boolean isPackage,
                       Module module,
                       String[] extendsClasses,
                       boolean completeOnlyModuleClasses,
                       boolean includeTests) {
      super(element, range, true);
      myBasePackage = basePackage;
      myStartsWithPoint = startsWithPoint;
      myStart = start;
      myIsPackage = isPackage;
      myModule = module;
      myExtendsClasses = extendsClasses;
      myCompleteOnlyModuleClasses = completeOnlyModuleClasses;
      myIncludeTests = includeTests;
    }

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
      final String value = getCurrentValue();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(myElement.getProject());

      if (!myStartsWithPoint) {
        final PsiElement element = myIsPackage ?
                                   facade.findPackage(value) :
                                   facade.findClass(value, myModule != null
                                                           ? myModule.getModuleWithDependenciesAndLibrariesScope(myIncludeTests)
                                                           : myElement.getResolveScope());

        if (element != null) {
          return element;
        }
      }

      final String absName = getAbsoluteName(value);
      if (absName != null) {
        return myIsPackage ?
               facade.findPackage(absName) :
               facade.findClass(absName, myModule != null
                                         ? myModule.getModuleWithDependenciesAndLibrariesScope(myIncludeTests)
                                         : myElement.getResolveScope());
      }
      return null;
    }

    @NotNull
    private String getCurrentValue() {
      final int end = getRangeInElement().getEndOffset();
      return myElement.getText().substring(myStart, end).replace('$', '.');
    }

    @Nullable
    private String getAbsoluteName(String value) {
      if (myBasePackage == null) {
        return null;
      }
      return myBasePackage + (myStartsWithPoint ? "" : ".") + value;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      if (myExtendsClasses != null) {
        final List<PsiClass> classes = new ArrayList<>();
        for (String extendsClass : myExtendsClasses) {
          classes.addAll(findInheritors(myElement.getProject(), myModule, extendsClass, myCompleteOnlyModuleClasses));
        }
        final List<Object> result = new ArrayList<>(classes.size());

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, n = classes.size(); i < n; i++) {
          final PsiClass psiClass = classes.get(i);
          final String prefix = myElement.getText().substring(myStart, getRangeInElement().getStartOffset());
          String name = classToString(psiClass, myBasePackage, prefix);

          if (name != null && name.startsWith(prefix)) {
            name = name.substring(prefix.length());
            result.add(JavaLookupElementBuilder.forClass(psiClass, name, true));
          }
        }
        return ArrayUtil.toObjectArray(result);
      }
      return EMPTY_ARRAY;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiClass || element instanceof PsiPackage) {
        if (myIsPackage && myBasePackage != null &&
            AndroidUtils.isPackagePrefix(getCurrentValue(), myBasePackage)) {
          // in such case reference updating is performed by AndroidPackageConverter.MyPsiPackageReference#bindToElement()
          return super.bindToElement(element);
        }
        String newName;
        if (element instanceof PsiClass) {
          newName = classToString((PsiClass)element, myBasePackage, "");
          // Check if the class has a full-qualified name. In this case, as classToString(...) will return a shortened version, add the base
          // package as a prefix of the new name.
          if (myBasePackage != null && AndroidUtils.isPackagePrefix(myBasePackage, getCurrentValue()) &&
              newName != null && !newName.startsWith(myBasePackage)) {
            newName = myBasePackage + (newName.startsWith(".") ? "" : ".") + newName;
          }
        } else {
          // Check if the current package has a full-qualified name and, in case it has, make sure the renamed package
          // has also a full-qualified name. resolveInner() is used to check if the package has full-qualified name because it returns null
          // if the package is named using shortened notation.
          newName = packageToString((PsiPackage)element, myBasePackage, resolve() != null);
        }

        assert newName != null;

        final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(myElement);
        final TextRange range = new TextRange(myStart, getRangeInElement().getEndOffset());

        if (manipulator != null) {
          return manipulator.handleContentChange(myElement, range, newName);
        }
        return element;
      }
      LOG.error("PackageClassConverter resolved to " + element.getClass());
      return super.bindToElement(element);
    }

    private static String packageToString(PsiPackage psiPackage, String basePackageName, boolean isFullQualified) {
      final String qName = psiPackage.getQualifiedName();
      return basePackageName != null && AndroidUtils.isPackagePrefix(basePackageName, qName) && !isFullQualified ?
             qName.substring(basePackageName.length()) :
             qName;
    }

    @NotNull
    @Override
    public String getUnresolvedMessagePattern() {
      return myIsPackage ? "Unresolved package ''{0}''" : "Unresolved class ''{0}''";
    }

    @NotNull
    @Override
    public LocalQuickFix[] getQuickFixes() {
      if (myIsPackage) {
        return LocalQuickFix.EMPTY_ARRAY;
      }

      String value = getCurrentValue();
      if (myStartsWithPoint && myBasePackage != null) {
        value = getAbsoluteName(value);
      }

      if (value == null) {
        return LocalQuickFix.EMPTY_ARRAY;
      }
      final int dot = value.lastIndexOf('.');
      if (dot == -1) {
        return LocalQuickFix.EMPTY_ARRAY;
      }

      final PsiPackage aPackage = JavaPsiFacade.getInstance(myModule.getProject()).findPackage(value.substring(0, dot));
      if (aPackage == null) {
        return LocalQuickFix.EMPTY_ARRAY;
      }
      final String baseClassFqcn = myExtendsClasses.length == 0 ? null : myExtendsClasses[0];
      return new LocalQuickFix[]{new CreateMissingClassQuickFix(aPackage, value.substring(dot + 1), myModule, baseClassFqcn)};
    }
  }

}
