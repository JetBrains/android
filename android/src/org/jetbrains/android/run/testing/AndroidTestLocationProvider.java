package org.jetbrains.android.run.testing;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.TestLocationProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestLocationProvider implements TestLocationProvider {
  @NonNls public static final String PROTOCOL_ID = "android";

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull String protocolId, @NotNull String locationData, Project project) {
    if (PROTOCOL_ID.equals(protocolId)) {
      final PsiElement element = findElement(locationData, project);

      if (element != null) {
        return Collections.singletonList((Location)new PsiLocation<PsiElement>(project, element));
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  private static PsiElement findElement(String link, Project project) {
    int idx = link.indexOf(":");

    if (idx <= 0) {
      return null;
    }
    final String moduleName = link.substring(0, idx);
    final Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
    link = link.substring(idx + 1);

    final GlobalSearchScope scope = module != null ? module.getModuleWithDependenciesScope() : GlobalSearchScope.allScope(project);
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(link, scope);

    if (aClass != null) {
      return aClass;
    }
    if (link.contains(".") && link.endsWith("()")) {
      idx = link.lastIndexOf('.');
      final String className = link.substring(0, idx);
      aClass = JavaPsiFacade.getInstance(project).findClass(className, scope);

      if (aClass != null) {
        final String methodName = link.substring(idx + 1, link.length() - 2);
        final PsiMethod[] methods = aClass.findMethodsByName(methodName, false);

        if (methods.length == 0) {
          return null;
        }
        if (methods.length == 1) {
          return methods[0];
        }
        for (PsiMethod method : methods) {
          if (method.getParameterList().getParametersCount() == 0) {
            return method;
          }
        }
        return methods[0];
      }
    }
    return null;
  }
}
