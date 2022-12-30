// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.augment;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.ScopeType;
import com.google.common.base.MoreObjects;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.ResolveState;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightEmptyImplementsList;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.kotlin.analyzer.ModuleInfo;
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinProjectStructureCustomizationUtils;
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger;

public abstract class AndroidLightClassBase extends LightElement implements PsiClass, SyntheticElement {
  private static final Key<Library> LIBRARY = Key.create(AndroidLightClassBase.class.getName() + ".LIBRARY");

  private final LightModifierList myPsiModifierList;

  protected AndroidLightClassBase(@NotNull PsiManager psiManager, @NotNull Collection<String> modifiers) {
    super(psiManager, JavaLanguage.INSTANCE);
    myPsiModifierList = new LightModifierList(psiManager);
    for (String modifier : modifiers) {
      myPsiModifierList.addModifier(modifier);
    }
  }

  /**
   * Sets the forced {@link ModuleInfo} of the containing {@link PsiFile} to point to the given {@link Module}, so that the Kotlin IDE
   * plugin knows how to handle this light class.
   */
  protected void setModuleInfo(@NotNull Module module, boolean isTest) {
    this.putUserData(ModuleUtilCore.KEY_MODULE, module);
    // Some scenarios move up to the file level and then attempt to get the module from the file.
    PsiFile containingFile = getContainingFile();
    if (containingFile != null) {
      containingFile.putUserData(ModuleUtilCore.KEY_MODULE, module);
      KotlinRegistrationHelper.setModuleInfo(containingFile, isTest);
    }
  }

  /**
   * Sets the forced {@link ModuleInfo} of the containing {@link PsiFile} to point to the given {@link Library}, so that the Kotlin IDE
   * plugin knows how to handle this light class.
   */
  protected void setModuleInfo(@NotNull Library library) {
    putUserData(LIBRARY, library);

    PsiFile containingFile = getContainingFile();
    if (containingFile != null) {
      KotlinRegistrationHelper.setModuleInfo(containingFile, library);
    }
  }

  /**
   * Sets the forced {@link ModuleInfo} of the containing {@link PsiFile} to point to the given {@link Sdk}, so that the Kotlin IDE
   * plugin knows how to handle this light class.
   */
  protected void setModuleInfo(@NotNull Sdk sdk) {
    PsiFile containingFile = getContainingFile();
    if (containingFile != null) {
      KotlinRegistrationHelper.setModelInfo(containingFile, sdk);
    }
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot add elements to R class");
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public PsiReferenceList getExtendsList() {
    return new LightEmptyImplementsList(myManager);
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return new LightEmptyImplementsList(myManager);
  }

  @NotNull
  @Override
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiClass getSuperClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiClass[] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClass[] getSupers() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClassType[] getSuperTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiField[] getAllFields() {
    return getFields();
  }

  @NotNull
  @Override
  public PsiMethod[] getAllMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClass[] getAllInnerClasses() {
    return getInnerClasses();
  }

  @Override
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    final PsiField[] fields = getFields();
    for (final PsiField field : fields) {
      if (name.equals(field.getName())) return field;
    }
    return null;
  }

  @Override
  public PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return null;
  }

  @NotNull
  @Override
  public PsiMethod[] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    List<PsiMethod> methods = new ArrayList<>();
    for (PsiMethod method : getMethods()) {
      if (method.getName().equals(name)) {
        methods.add(method);
      }
    }
    return methods.isEmpty() ? PsiMethod.EMPTY_ARRAY : methods.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls @NotNull String name, boolean checkBases) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return Collections.emptyList();
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

  @Override
  public PsiElement getLBrace() {
    return null;
  }

  @Override
  public PsiElement getRBrace() {
    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Nullable
  @Override
  public PsiElement getScope() {
    return null;
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(@NotNull PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @NotNull
  @Override
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptyList();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot change the name of " + getQualifiedName() + " class");
  }

  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return new LightTypeParameterListBuilder(myManager, getLanguage());
  }

  @NotNull
  @Override
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Override
  public final PsiModifierList getModifierList() {
    return myPsiModifierList;
  }

  @Override
  public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NonNls @NotNull String name) {
    final PsiModifierList list = getModifierList();
    return list != null && list.hasModifierProperty(name);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  protected Icon getElementIcon(@Iconable.IconFlags int flags) {
    return PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    // For the common case of a public light class, getMemberUseScope below cannot determine the owning module and falls back to using the
    // entire project. Here we compute a more accurate scope, see ResolveScopeManagerImpl#getUseScope.
    PsiModifierList modifierList = getModifierList();
    if (modifierList != null) {
      if (PsiUtil.getAccessLevel(modifierList) == PsiUtil.ACCESS_LEVEL_PUBLIC) {
        Module module = ModuleUtilCore.findModuleForPsiElement(this); // see setModuleInfo.
        if (module != null) {
          if (getScopeType() == ScopeType.MAIN) {
            return GlobalSearchScope.moduleWithDependentsScope(module);
          }
          else {
            return GlobalSearchScope.moduleTestsWithDependentsScope(module);
          }
        }

        Library library= getUserData(LIBRARY);
        if (library != null) {
          VirtualFile root = ArrayUtil.getFirstElement(library.getFiles(OrderRootType.CLASSES));
          if (root != null) {
            List<OrderEntry> orderEntries = ProjectFileIndex.getInstance(getProject()).getOrderEntriesForFile(root);
            return LibraryScopeCache.getInstance(getProject()).getLibraryUseScope(orderEntries);
          }
        }
      }
    }

    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    final PsiClass containingClass = getContainingClass();
    return containingClass == null ? null : containingClass.getContainingFile();
  }

  @Override
  public TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, PsiUtil.getLanguageLevel(place), false);
  }

  @Override
  @NotNull
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(getQualifiedName()).toString();
  }

  /**
   * Encapsulates calls to Kotlin IDE plugin to prevent {@link NoClassDefFoundError} when Kotlin is not installed.
   */
  private static final class KotlinRegistrationHelper {
    static void setModuleInfo(@NotNull PsiFile file, boolean isTest) {
      KotlinProjectStructureCustomizationUtils.setCustomSourceRootType(file, isTest ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
    }

    static void setModuleInfo(@NotNull PsiFile file, @NotNull Library library) {
      KotlinProjectStructureCustomizationUtils.setCustomLibrary(file, library);
    }

    static void setModelInfo(@NotNull PsiFile file, @NotNull Sdk sdk) {
      KotlinProjectStructureCustomizationUtils.setCustomSdk(file, sdk);
    }
  }

  protected ScopeType getScopeType() {
    return ScopeType.MAIN;
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    Module module = ModuleUtilCore.findModuleForPsiElement(this);
    if (module == null) {
      // Some light classes come from libraries not modules.
      return super.getResolveScope();
    }

    ScopeType scopeType = getScopeType();
    GlobalSearchScope moduleResolveScope = ProjectSystemUtil.getModuleSystem(module).getResolveScope(scopeType);
    Ref<GlobalSearchScope> result = Ref.create(moduleResolveScope);
    KotlinResolveScopeEnlarger.Companion.getEP_NAME().forEachExtensionSafe(enlarger -> {
      SearchScope additionalResolveScope = enlarger.getAdditionalResolveScope(module, scopeType.isForTest());
      if (additionalResolveScope != null) {
        result.set(result.get().union(additionalResolveScope));
      }
    });
    return result.get();
  }
}
