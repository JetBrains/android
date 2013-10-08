package org.jetbrains.android.augment;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.AndroidSdkResolveScopeProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPsiElementFinder extends PsiElementFinder {
  public static final String INTERNAL_PACKAGE_QNAME = "com.android.internal";
  public static final String INTERNAL_R_CLASS_QNAME = INTERNAL_PACKAGE_QNAME + ".R";
  private final Object myLock = new Object();

  private final Map<Sdk, SoftReference<PsiClass>> myInternalRClasses = new HashMap<Sdk, SoftReference<PsiClass>>();

  public AndroidPsiElementFinder(@NotNull Project project) {
    ApplicationManager.getApplication().getMessageBus().connect(project)
      .subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
      @Override
      public void jdkAdded(final Sdk sdk) {
      }

      @Override
      public void jdkRemoved(final Sdk sdk) {
        synchronized (myLock) {
          myInternalRClasses.remove(sdk);
        }
      }

      @Override
      public void jdkNameChanged(final Sdk sdk, final String previousName) {
      }
    });
  }

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

  private PsiClass getAndroidInternalRClass(Project project, AndroidSdkResolveScopeProvider.MyJdkScope scope) {
    final Sdk sdk = scope.getJdkOrderEntry().getJdk();

    if (sdk == null) {
      return null;
    }
    final AndroidPlatform platform = AndroidPlatform.getInstance(sdk);

    if (platform == null) {
      return null;
    }
    PsiClass internalRClass = SoftReference.dereference(myInternalRClasses.get(sdk));

    if (internalRClass == null) {
      internalRClass = new AndroidInternalRClass(PsiManager.getInstance(project), platform);
      myInternalRClasses.put(sdk, new SoftReference<PsiClass>(internalRClass));
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
