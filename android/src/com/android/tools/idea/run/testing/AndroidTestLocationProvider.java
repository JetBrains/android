package com.android.tools.idea.run.testing;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestLocationProvider implements SMTestLocator {
  public static final String PROTOCOL_ID = "android";

  public static final AndroidTestLocationProvider INSTANCE = new AndroidTestLocationProvider();

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    if (PROTOCOL_ID.equals(protocol)) {
      PsiElement element = findElement(path, project, scope);
      if (element != null) {
        return Collections.singletonList((Location)new PsiLocation<PsiElement>(project, element));
      }
    }

    return Collections.emptyList();
  }

  @Nullable
  private static PsiElement findElement(String link, Project project, GlobalSearchScope scope) {
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(link, scope);
    if (aClass != null) {
      return aClass;
    }

    if (link.contains(".") && link.endsWith("()")) {
      final int idx = link.lastIndexOf('.');
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
