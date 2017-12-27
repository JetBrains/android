package org.jetbrains.android.augment;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.reference.SoftReference;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * An element finder which finds the Android internal R class, and inner classes within R classes.
 */
public class AndroidPsiElementFinder extends PsiElementFinder {
  public static final String INTERNAL_PACKAGE_QNAME = "com.android.internal";
  public static final String INTERNAL_R_CLASS_QNAME = INTERNAL_PACKAGE_QNAME + ".R";
  private final Object myLock = new Object();

  private final Map<Sdk, SoftReference<PsiClass>> myInternalRClasses = new HashMap<Sdk, SoftReference<PsiClass>>();

  public AndroidPsiElementFinder(@NotNull Project project) {
    ApplicationManager.getApplication().getMessageBus().connect(project).subscribe(
      ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Adapter() {
        @Override
        public void jdkRemoved(@NotNull final Sdk sdk) {
          synchronized (myLock) {
            myInternalRClasses.remove(sdk);
          }
        }
      });
  }

  private boolean processInternalRClasses(@NotNull Project project, @NotNull GlobalSearchScope scope, Processor<PsiClass> processor) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      AndroidPlatform platform = sdk == null ? null : AndroidPlatform.getInstance(sdk);
      PsiClass internalRClass = platform == null ? null : getOrCreateInternalRClass(project, sdk, platform);
      if (internalRClass != null && scope.contains(internalRClass.getContainingFile().getViewProvider().getVirtualFile())) {
        if (!processor.process(internalRClass)) {
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiClass[] classes = findClasses(qualifiedName, scope);
    return classes.length == 0 ? null : classes[0];
  }

  private PsiClass getOrCreateInternalRClass(Project project, Sdk sdk, AndroidPlatform platform) {
    synchronized (myLock) {
      PsiClass internalRClass = SoftReference.dereference(myInternalRClasses.get(sdk));

      if (internalRClass == null) {
        internalRClass = new AndroidInternalRClass(PsiManager.getInstance(project), platform, sdk);
        myInternalRClasses.put(sdk, new SoftReference<PsiClass>(internalRClass));
      }
      return internalRClass;
    }
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    Project project = scope.getProject();
    if (project == null || !ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return PsiClass.EMPTY_ARRAY;
    }

    if (INTERNAL_R_CLASS_QNAME.equals(qualifiedName)) {
      CommonProcessors.CollectUniquesProcessor<PsiClass> processor = new CommonProcessors.CollectUniquesProcessor<PsiClass>();
      processInternalRClasses(project, scope, processor);
      Collection<PsiClass> results = processor.getResults();
      return results.isEmpty() ? PsiClass.EMPTY_ARRAY : results.toArray(new PsiClass[results.size()]);
    }

    final int lastDot = qualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return PsiClass.EMPTY_ARRAY;
    }
    final String shortName = qualifiedName.substring(lastDot + 1);
    final String parentName = qualifiedName.substring(0, lastDot);

    if (shortName.isEmpty() || !parentName.endsWith(".R")) {
      return PsiClass.EMPTY_ARRAY;
    }
    List<PsiClass> result = new SmartList<PsiClass>();
    for (PsiClass parentClass : JavaPsiFacade.getInstance(project).findClasses(parentName, scope)) {
      ContainerUtil.addIfNotNull(result, parentClass.findInnerClassByName(shortName, false));
    }
    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
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
