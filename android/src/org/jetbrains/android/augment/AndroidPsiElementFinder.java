package org.jetbrains.android.augment;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.AndroidSdkResolveScopeProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPsiElementFinder extends PsiElementFinder {
  public static final String INTERNAL_PACKAGE_QNAME = "com.android.internal";
  public static final String INTERNAL_R_CLASS_QNAME = INTERNAL_PACKAGE_QNAME + ".R";
  private final Object myLock = new Object();

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final Project project = scope.getProject();

    if (project == null ||
        !ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return null;
    }
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    if (scope instanceof AndroidSdkResolveScopeProvider.MyJdkScope && INTERNAL_R_CLASS_QNAME.equals(qualifiedName)) {
      synchronized (myLock) {
        return getAndroidInternalRClass(project, (AndroidSdkResolveScopeProvider.MyJdkScope)scope);
      }
    }
    final int lastDot = qualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return null;
    }
    final String shortName = qualifiedName.substring(lastDot + 1);
    final String parentName = qualifiedName.substring(0, lastDot);

    if (shortName.length() == 0 || !parentName.endsWith(".R")) {
      return null;
    }
    final PsiClass rClass = facade.findClass(parentName, scope);

    if (rClass == null) {
      return null;
    }
    return rClass.findInnerClassByName(shortName, false);
  }

  private static PsiClass getAndroidInternalRClass(Project project, AndroidSdkResolveScopeProvider.MyJdkScope scope) {
    final Sdk sdk = scope.getJdkOrderEntry().getJdk();

    if (sdk == null) {
      return null;
    }
    final AndroidPlatform platform = AndroidPlatform.getInstance(sdk);

    if (platform == null) {
      return null;
    }
    final AndroidSdkData sdkData = platform.getSdkData();
    PsiClass internalRClass = sdkData.getInternalRClass();

    if (internalRClass == null) {
      internalRClass = new AndroidInternalRClass(PsiManager.getInstance(project), platform);
      sdkData.setInternalRClass(internalRClass);
    }
    return internalRClass;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final PsiClass aClass = findClass(qualifiedName, scope);
    return aClass != null ? new PsiClass[] {aClass} : PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    if (INTERNAL_PACKAGE_QNAME.equals(psiPackage.getQualifiedName())) {
      return Collections.singleton("R");
    }
    return Collections.emptySet();
  }
}
