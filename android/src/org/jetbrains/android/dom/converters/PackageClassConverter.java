// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom.converters;

import static com.android.tools.idea.projectsystem.SourceProvidersKt.isTestFile;

import com.android.tools.idea.AndroidTextUtils;
import com.android.tools.idea.imports.MavenClassRegistryManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.ide.hierarchy.JavaHierarchyUtil;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.FilteredQuery;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jetbrains.android.dom.inspections.MavenClassResolverUtils;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PackageClassConverter extends Converter<PsiClass> implements CustomReferenceConverter<PsiClass> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.dom.converters.PackageClassConverter");

  @Retention(RetentionPolicy.RUNTIME)
  public @interface Options {
    String[] inheriting() default {};
    boolean completeLibraryClasses() default false;
    boolean includeDynamicFeatures() default false;
  }

  /**
   * Use this {@link Builder} to construct a {@link PackageClassConverter}.
   */
  public static class Builder {
    private boolean myUseManifestBasePackage;
    private String[] myExtendClassesNames = ArrayUtil.EMPTY_STRING_ARRAY;
    private String[] myExtraBasePackages = ArrayUtil.EMPTY_STRING_ARRAY;
    private boolean myCompleteLibraryClasses;

    /**
     * @param doUseManifestBasePackage if true, even when the attribute it's not defined within the manifest,
     *                                 the resolution will use the manifest package for completion.
     */
    public Builder useManifestBasePackage(boolean doUseManifestBasePackage) {
      myUseManifestBasePackage = doUseManifestBasePackage;
      return this;
    }

    /**
     * @param doCompleteLibraryClasses if true, offer library classes in code completion
     */
    public Builder completeLibraryClasses(boolean doCompleteLibraryClasses) {
      myCompleteLibraryClasses = doCompleteLibraryClasses;
      return this;
    }

    /**
     * @param classNames list of the classes that the searched class can extend
     */
    public Builder withExtendClassNames(String... classNames) {
      // Names coming from AndroidX will be use $ for inner classes whereas IntelliJ works with dots.
      myExtendClassesNames = Stream.of(classNames).map(jvmName -> jvmName.replace('$', '.')).toArray(String[]::new);
      return this;
    }

    /**
     * @param basePackages  list of package names that will be used for class resolution if the class name is specified without
     *                      a package name. A preceding "." in the class name indicates that the manifest package name should be
     *                      used instead of these package names. The specified package names must end with a "."
     */
    public Builder withExtraBasePackages(String... basePackages) {
      myExtraBasePackages = basePackages;
      return this;
    }

    public PackageClassConverter build() {
      return new PackageClassConverter(myUseManifestBasePackage, myExtraBasePackages, myCompleteLibraryClasses, myExtendClassesNames);
    }
  }

  private final boolean myUseManifestBasePackage;
  private final String[] myExtendClassesNames;
  private final String[] myExtraBasePackages;
  private final boolean myCompleteLibraryClasses;

  /**
   * Constructs a new {@link PackageClassConverter}.
   */
  protected PackageClassConverter(boolean useManifestBasePackage,
                                  String[] extraBasePackages,
                                  boolean completeLibraryClasses,
                                  String[] extendClassesNames) {
    myUseManifestBasePackage = useManifestBasePackage;
    myExtraBasePackages = extraBasePackages;
    myCompleteLibraryClasses = completeLibraryClasses;
    myExtendClassesNames = extendClassesNames;
  }

  public PackageClassConverter(String... extendClassesNames) {
    this(false, ArrayUtil.EMPTY_STRING_ARRAY, false, extendClassesNames);
  }

  public PackageClassConverter() {
    this(false, ArrayUtil.EMPTY_STRING_ARRAY, false, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Nullable
  private String getManifestPackage(@NotNull ConvertContext context) {
    DomElement domElement = context.getInvocationElement();
    Manifest manifest = domElement.getParentOfType(Manifest.class, false);
    String manifestPackage = manifest != null ? manifest.getPackage().getValue() : null;

    if ((manifest != null && manifestPackage == null) || myUseManifestBasePackage) {
      Module module = context.getModule();
      if (module != null) {
        manifestPackage = ProjectSystemUtil.getModuleSystem(module).getPackageName();
      }
    }
    return manifestPackage;
  }

  @Override
  @Nullable
  public PsiClass fromString(@Nullable @NonNls String nameValue, @NotNull ConvertContext context) {
    String manifestPackage = getManifestPackage(context);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getPsiManager().getProject());
    Module module = context.getModule();
    GlobalSearchScope scope = module != null
                              ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
                              : context.getInvocationElement().getResolveScope();
    return findClassFromString(nameValue, facade, scope, manifestPackage, getExtraBasePackages(context));
  }

  @NotNull
  protected String[] getExtraBasePackages(ConvertContext context) {
    return myExtraBasePackages;
  }

  @Nullable
  private static PsiClass findClassFromString(@Nullable String s,
                                              @NotNull JavaPsiFacade facade,
                                              @NotNull GlobalSearchScope scope,
                                              @Nullable String manifestPackage,
                                              @NotNull String[] extraBasePackages) {
    if (s == null) {
      return null;
    }
    s = s.replace('$', '.');
    List<String> prefixes = new ArrayList<>();

    if (s.startsWith(".")) {
      if (manifestPackage != null) {
        prefixes.add(manifestPackage);
      }
      else {
        return null;
      }
    }
    else {
      if (extraBasePackages.length > 0) {
        prefixes.addAll(Arrays.asList(extraBasePackages));
      }
      if (manifestPackage != null) {
        prefixes.add(manifestPackage + ".");
      }
      prefixes.add("");
    }
    String className = s;
    return prefixes.stream()
                   .map(prefix -> facade.findClass(prefix + className, scope))
                   .filter(Objects::nonNull)
                   .findFirst()
                   .orElse(null);
  }

  @Nullable
  private static PsiPackage findPackageFromString(@Nullable String s, @NotNull JavaPsiFacade facade, @Nullable String manifestPackage) {
    if (s == null) {
      return null;
    }
    List<String> prefixes = new ArrayList<>();

    if (s.startsWith(".")) {
      if (manifestPackage != null) {
        prefixes.add(manifestPackage);
      }
      else {
        return null;
      }
    }
    else {
      if (manifestPackage != null) {
        prefixes.add(manifestPackage + ".");
      }
      prefixes.add("");
    }
    return prefixes.stream()
                   .map(prefix -> facade.findPackage(prefix + s))
                   .filter(Objects::nonNull)
                   .findFirst()
                   .orElse(null);
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<PsiClass> value, final PsiElement element, ConvertContext context) {
    assert element instanceof XmlAttributeValue;
    final XmlAttributeValue attrValue = (XmlAttributeValue)element;
    final String strValue = StringUtil.notNullize(attrValue.getValue());

    final boolean startsWithPoint = strValue.startsWith(".");
    final int start = attrValue.getValueTextRange().getStartOffset() - attrValue.getTextRange().getStartOffset();

    final DomElement domElement = context.getInvocationElement();
    final String manifestPackage = getManifestPackage(context);
    final String[] extendClassesNames = getClassNames(domElement);
    final boolean completeLibraryClasses = getCompleteLibraryClasses(domElement);
    final boolean includeDynamicFeatures = getIncludeDynamicFeatures(domElement);

    AndroidFacet facet = AndroidFacet.getInstance(context);
    // If the source XML file is contained within the test folders, we'll also allow to resolve test classes
    VirtualFile file = element.getContainingFile().getVirtualFile();
    final boolean isTestFile = facet != null && file != null && isTestFile(facet, file);

    if (strValue.isEmpty()) {
      return PsiReference.EMPTY_ARRAY;
    }

    final List<PsiReference> result = new ArrayList<>();
    final Module module = context.getModule();

    // Using inner class here as opposed to anonymous one as with anonymous class it wouldn't be possible to access {@code myPartStart}
    // later.
    class CustomConsumer implements Consumer<Integer> {
      int myPartStart = 0;
      private boolean myIsPackage = true;

      @Override
      public void consume(Integer index) {
        if (index > myPartStart) {
          final TextRange range = new TextRange(start + myPartStart, start + index);
          final MyReference reference =
            new MyReference(element, range, manifestPackage, getExtraBasePackages(context), startsWithPoint, start, myIsPackage, module,
                            extendClassesNames, completeLibraryClasses, isTestFile, includeDynamicFeatures);
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

    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  private boolean getIncludeDynamicFeatures(DomElement domElement) {
    Options options = domElement.getAnnotation(Options.class);
    return options != null && options.includeDynamicFeatures();
  }

  public boolean getCompleteLibraryClasses(@NotNull DomElement domElement) {
    Options options = domElement.getAnnotation(Options.class);
    return options != null ? options.completeLibraryClasses() : myCompleteLibraryClasses;
  }

  @NotNull
  private String[] getClassNames(@NotNull DomElement domElement) {
    Options options = domElement.getAnnotation(Options.class);
    if (options == null || options.inheriting().length == 0) {
      return myExtendClassesNames;
    }
    else {
      return options.inheriting();
    }
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
    Manifest manifest = domElement.getParentOfType(Manifest.class, false);
    final String packageName = manifest == null ? null : manifest.getPackage().getValue();
    return classToString(psiClass, packageName, "");
  }

  @Nullable
  public static String getPackageName(@NotNull PsiClass psiClass) {
    return JavaHierarchyUtil.getPackageName(psiClass);
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
      if (Objects.equals(psiFile.getPackageName(), basePackageName)) {
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

  private static class MyReference extends PsiReferenceBase<PsiElement> implements EmptyResolveMessageProvider, LocalQuickFixProvider {
    private final int myStart;
    private final String myManifestPackage;
    private final String[] myExtraBasePackages;
    private final boolean myStartsWithPoint;
    private final boolean myIsPackage;
    @Nullable private final Module myModule;
    private final String[] myExtendsClasses;
    private final boolean myCompleteLibraryClasses;
    private final boolean myIncludeTests;
    private final boolean myIncludeDynamicFeatures;
    private final MavenClassRegistryManager myMavenClassRegistryManager = MavenClassRegistryManager.getInstance();

    private MyReference(PsiElement element,
                       TextRange range,
                       String manifestPackage,
                       String[] extraBasePackages,
                       boolean startsWithPoint,
                       int start,
                       boolean isPackage,
                       @Nullable Module module,
                       String[] extendsClasses,
                       boolean completeLibraryClasses,
                       boolean includeTests, boolean includeDynamicFeatures) {
      super(element, range, true);
      myManifestPackage = manifestPackage;
      myExtraBasePackages = extraBasePackages;
      myStartsWithPoint = startsWithPoint;
      myStart = start;
      myIsPackage = isPackage;
      myModule = module;
      myExtendsClasses = extendsClasses;
      myCompleteLibraryClasses = completeLibraryClasses;
      myIncludeTests = includeTests;
      myIncludeDynamicFeatures = includeDynamicFeatures;
    }

    @Override
    public PsiElement resolve() {
      return ResolveCache.getInstance(myElement.getProject())
                         .resolveWithCaching(this,
                                             (ResolveCache.Resolver)(reference, incompleteCode) -> resolveInner(), false, false);
    }

    @Nullable
    private PsiElement resolveInner() {
      String value = getCurrentValue();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myElement.getProject());
      GlobalSearchScope scope = myModule != null
                                ? myModule.getModuleWithDependenciesAndLibrariesScope(myIncludeTests)
                                : myElement.getResolveScope();
      if (myIsPackage) {
        return findPackageFromString(value, facade, myManifestPackage);
      }
      return findClassFromString(value, facade, expandSearchScope(myModule, scope), myManifestPackage, myExtraBasePackages);
    }

    private GlobalSearchScope expandSearchScope(@Nullable Module module, @NotNull GlobalSearchScope currentScope) {
      if (module == null || !myIncludeDynamicFeatures) {
        return currentScope;
      }
      List<Module> dynamicFeatureModules = ProjectSystemUtil.getModuleSystem(module).getDynamicFeatureModules();
      if (dynamicFeatureModules.isEmpty()) {
        return currentScope;
      }
      GlobalSearchScope dynamicFeatureScope =
        GlobalSearchScope.union(ContainerUtil.map(dynamicFeatureModules, it -> it.getModuleContentScope()));
      return currentScope.union(dynamicFeatureScope);
    }

    @NotNull
    private String getCurrentValue() {
      final int end = getRangeInElement().getEndOffset();
      return myElement.getText().substring(myStart, end).replace('$', '.');
    }

    @Nullable
    private String getAbsoluteName(String value) {
      if (myManifestPackage == null) {
        return null;
      }
      return myManifestPackage + (myStartsWithPoint ? "" : ".") + value;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
      if (myExtendsClasses != null) {
        final List<PsiClass> classes = new ArrayList<>();
        for (String extendsClass : myExtendsClasses) {
          classes.addAll(findInheritors(extendsClass));
        }
        final List<Object> result = new ArrayList<>(classes.size());

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, n = classes.size(); i < n; i++) {
          final PsiClass psiClass = classes.get(i);
          final String prefix = myElement.getText().substring(myStart, getRangeInElement().getStartOffset());
          String name = classToString(psiClass, myManifestPackage, prefix);

          if (name != null && name.startsWith(prefix)) {
            name = name.substring(prefix.length());
            result.add(JavaLookupElementBuilder.forClass(psiClass, name, true));
          }
        }
        return ArrayUtil.toObjectArray(result);
      }
      return EMPTY_ARRAY;
    }

    @NotNull
    private Collection<PsiClass> findInheritors(@NotNull final String className) {
      Project project = myElement.getProject();
      PsiClass base = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
      if (base == null) {
        return new ArrayList<>();
      }

      GlobalSearchScope scope;
      if (myModule == null) {
        scope = GlobalSearchScope.allScope(project);
      }
      else if (myCompleteLibraryClasses) {
        scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule, false);
      } else {
        scope = GlobalSearchScope.moduleWithDependenciesScope(myModule);
      }
      Query<PsiClass> query = new FilteredQuery<>(ClassInheritorsSearch.search(base, expandSearchScope(myModule, scope), true),
                                                  psiClass -> psiClass.hasModifier(JvmModifier.PUBLIC));
      return query.findAll();
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiClass || element instanceof PsiPackage) {
        if (myIsPackage && myManifestPackage != null &&
            AndroidUtils.isPackagePrefix(getCurrentValue(), myManifestPackage)) {
          // in such case reference updating is performed by AndroidPackageConverter.MyPsiPackageReference#bindToElement()
          return super.bindToElement(element);
        }
        String newName;
        if (element instanceof PsiClass) {
          newName = classToString((PsiClass)element, myManifestPackage, "");
          // Check if the class has a full-qualified name. In this case, as classToString(...) will return a shortened version, add the base
          // package as a prefix of the new name.
          if (myManifestPackage != null && AndroidUtils.isPackagePrefix(myManifestPackage, getCurrentValue()) &&
              newName != null && !newName.startsWith(myManifestPackage)) {
            newName = myManifestPackage + (newName.startsWith(".") ? "" : ".") + newName;
          }
        } else {
          // Check if the current package has a full-qualified name and, in case it has, make sure the renamed package
          // has also a full-qualified name. resolveInner() is used to check if the package has full-qualified name because it returns null
          // if the package is named using shortened notation.
          newName = packageToString((PsiPackage)element, myManifestPackage, resolve() != null);
        }

        assert newName != null;

        final TextRange range = new TextRange(myStart, getRangeInElement().getEndOffset());
        return ElementManipulators.handleContentChange(myElement, range, newName);
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

    @Override
    public @NotNull LocalQuickFix @Nullable [] getQuickFixes() {
      if (myModule == null) {
        return LocalQuickFix.EMPTY_ARRAY;
      }

      if (myIsPackage) {
        // This is to suggest fixes if users are pointing in the middle of the fully qualified name - package
        // segment.
        String value = ((XmlAttributeValue)myElement).getValue();
        return collectFixesFromMavenClassRegistry(value, myModule.getProject());
      }

      String value = getCurrentValue();
      if (myStartsWithPoint && myManifestPackage != null) {
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
        return collectFixesFromMavenClassRegistry(value, myModule.getProject());
      }
      final String baseClassFqcn = myExtendsClasses.length == 0 ? null : myExtendsClasses[0];
      return new LocalQuickFix[]{new CreateMissingClassQuickFix(aPackage, value.substring(dot + 1), myModule, baseClassFqcn)};
    }

    @NotNull
    private LocalQuickFix[] collectFixesFromMavenClassRegistry(@NotNull String className, @NotNull Project project) {
      Collection<LocalQuickFix> fixes =
        MavenClassResolverUtils.collectFixesFromMavenClassRegistry(
          myMavenClassRegistryManager,
          className,
          project
        );

      return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
    }
  }
}
