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
package org.jetbrains.android.maven;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMavenUtil {
  @NonNls public static final String APKSOURCES_DEPENDENCY_TYPE = "apksources";
  @NonNls public static final String APKLIB_DEPENDENCY_AND_PACKAGING_TYPE = "apklib";
  @NonNls public static final String SO_PACKAGING_AND_DEPENDENCY_TYPE = "so";
  @NonNls public static final String APK_PACKAGING_TYPE = "apk";
  @NonNls public static final String AAR_DEPENDENCY_AND_PACKAGING_TYPE = "aar";

  @NonNls public static final String APK_LIB_ARTIFACT_SOURCE_ROOT = "src";
  @NonNls public static final String APK_LIB_ARTIFACT_RES_DIR = "res";
  public static final String APK_LIB_ARTIFACT_NATIVE_LIBS_DIR = "libs";
  public static final String APK_LIB_ARTIFACT_MANIFEST_FILE = "AndroidManifest.xml";
  @NonNls private static final String APKLIB_MODULE_PREFIX = "~apklib-";
  @NonNls private static final String GEN_EXTERNAL_APKLIBS_DIRNAME = "gen-external-apklibs";

  private AndroidMavenUtil() {
  }

  @Nullable
  public static String computePathForGenExternalApklibsDir(@NotNull MavenId mavenId,
                                                           @NotNull MavenProject project,
                                                           @NotNull Collection<MavenProject> allProjects) {
    String path = null;
    boolean resultUnderApp = false;

    for (MavenProject p : allProjects) {
      List<MavenArtifact> dependencies = p.findDependencies(mavenId);

      if (dependencies.isEmpty()) {
        dependencies = p.findDependencies(mavenId.getGroupId(), mavenId.getArtifactId());
      }

      if (!dependencies.isEmpty() && containsCompileDependency(dependencies)) {
        final VirtualFile projectDir = p.getDirectoryFile();
        final boolean app = APK_PACKAGING_TYPE.equals(p.getPackaging());
        if (path == null || !resultUnderApp && app) {
          path = projectDir.getPath() + '/' + GEN_EXTERNAL_APKLIBS_DIRNAME;
          resultUnderApp = app;
        }
      }
    }
    
    if (path == null) {
      path = getGenExternalApklibDirInProject(project);
    }
    return path;
  }

  @NotNull
  public static String getGenExternalApklibDirInProject(@NotNull MavenProject project) {
    return project.getDirectoryFile().getPath() + '/' + GEN_EXTERNAL_APKLIBS_DIRNAME;
  }

  private static boolean containsCompileDependency(Collection<MavenArtifact> dependencies) {
    for (MavenArtifact dependency : dependencies) {
      if (MavenConstants.SCOPE_COMPILE.equals(dependency.getScope())) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static String getModuleNameForExtApklibArtifact(MavenId mavenId) {
    return APKLIB_MODULE_PREFIX + getMavenIdStringForFileName(mavenId);
  }
  
  @Nullable
  public static String getMavenIdStringByExtApklibModule(@NotNull Module module) {
    final String moduleName = module.getName();
    
    if (!moduleName.startsWith(APKLIB_MODULE_PREFIX)) {
      return null;
    }
    
    return moduleName.substring(APKLIB_MODULE_PREFIX.length());
  }

  public static boolean isExtApklibModule(@NotNull Module module) {
    return module.getName().startsWith(APKLIB_MODULE_PREFIX);
  }

  @NotNull
  public static String getMavenIdStringForFileName(@NotNull MavenId mavenId) {
    final String artifactId = mavenId.getKey().replace(':', '_');
    return artifactId != null ? artifactId : "null";
  }

  public static boolean isMavenizedModule(@NotNull Module module) {
    AndroidMavenProvider mavenProxy = getMavenProvider();
    return mavenProxy != null && mavenProxy.isMavenizedModule(module);
  }

  public static boolean isMavenAarDependency(@NotNull Module module, @NotNull OrderEntry entry) {
    if (ApplicationManager.getApplication().isUnitTestMode() && entry.getPresentableName().equals("maven_aar_dependency")) {
      return true;
    }
    if (!(entry instanceof LibraryOrderEntry) || !isMavenizedModule(module)) {
      return false;
    }
    final Library library = ((LibraryOrderEntry)entry).getLibrary();

    if (library == null) {
      return false;
    }
    final MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);

    if (mavenProject == null) {
      return false;
    }
    final MavenArtifact artifact = MavenRootModelAdapter.findArtifact(mavenProject, library);
    return artifact != null && AAR_DEPENDENCY_AND_PACKAGING_TYPE.equals(artifact.getType());
  }

  @Nullable
  public static AndroidMavenProvider getMavenProvider() {
    return ArrayUtil.getFirstElement(AndroidMavenProvider.EP_NAME.getExtensions());
  }
}
