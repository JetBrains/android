package org.jetbrains.android.uipreview;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.Variant;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.RenderSecurityManager;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.rendering.*;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.DOT_AAR;
import static com.android.SdkConstants.EXT_JAR;
import static com.android.SdkConstants.FN_RESOURCE_CLASS;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;
import static org.jetbrains.android.facet.ResourceFolderManager.EXPLODED_AAR;

/**
 * @author Eugene.Kudelevsky
 */
public final class ProjectClassLoader extends RenderClassLoader {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.ProjectClassLoader");

  private final Module myModule;
  private final RenderLogger myLogger;
  private final Object myCredential;

  public ProjectClassLoader(@Nullable ClassLoader parentClassLoader, @NotNull Module module, @Nullable RenderLogger logger,
                            @Nullable Object credential) {
    super(parentClassLoader);
    myModule = module;
    myLogger = logger;
    myCredential = credential;
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    try {
      return super.findClass(name);
    } catch (ClassNotFoundException e) {
      if (!myInsideJarClassLoader) {
        int index = name.lastIndexOf('.');
        if (index != -1 && name.charAt(index + 1) == 'R' && (index == name.length() - 2 || name.charAt(index + 2) == '$') && index > 1) {
          byte[] data = AarResourceClassRegistry.get().findClassDefinition(name);
          if (data != null) {
            data = convertClass(data);
            return defineClass(null, data, 0, data.length);
          }
        }
      }
      throw e;
    }
  }

  @Nullable
  public static ClassLoader create(IAndroidTarget target, Module module) throws Exception {
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(module);
    if (androidPlatform == null) {
      return null;
    }
    AndroidTargetData targetData = androidPlatform.getSdkData().getTargetData(target);
    LayoutLibrary library = targetData.getLayoutLibrary(module.getProject());
    if (library == null) {
      return null;
    }

    return new ProjectClassLoader(library.getClassLoader(), module, null, null);
  }

  @NotNull
  @Override
  protected Class<?> load(String name) throws ClassNotFoundException {
    final Class<?> aClass = loadClassFromModuleOrDependency(myModule, name, new HashSet<Module>());
    if (aClass != null) {
      return aClass;
    }

    throw new ClassNotFoundException(name);
  }

  @Nullable
  private Class<?> loadClassFromModuleOrDependency(Module module, String name, Set<Module> visited) {
    if (!visited.add(module)) {
      return null;
    }

    Class<?> aClass = loadClassFromModule(module, name);
    if (aClass != null) {
      return aClass;
    }

    aClass = loadClassFromJar(name);
    if (aClass != null) {
      return aClass;
    }

    for (Module depModule : ModuleRootManager.getInstance(module).getDependencies(false)) {
      aClass = loadClassFromModuleOrDependency(depModule, name, visited);
      if (aClass != null) {
        return aClass;
      }
    }
    return null;
  }

  @Nullable
  private Class<?> loadClassFromModule(Module module, String name) {
    final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
    if (extension == null) {
      return null;
    }

    VirtualFile vOutFolder = extension.getCompilerOutputPath();
    if (vOutFolder == null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.isGradleProject()) {
        // Try a bit harder; we don't have a compiler module extension or mechanism
        // to query this yet, so just hardcode it (ugh!)
        IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
        if (gradleProject != null) {
          Variant variant = gradleProject.getSelectedVariant();
          String variantName = variant.getName();
          AndroidArtifact mainArtifactInfo = variant.getMainArtifact();
          File classesFolder = mainArtifactInfo.getClassesFolder();

          // Older models may not supply it; in that case, we rely on looking relative
          // to the .APK file location:
          //noinspection ConstantConditions
          if (classesFolder == null) {
            AndroidArtifactOutput output = GradleUtil.getOutput(mainArtifactInfo);
            File file = output.getMainOutputFile().getOutputFile();
            File buildFolder = file.getParentFile().getParentFile();
            classesFolder = new File(buildFolder, "classes"); // See AndroidContentRoot
          }

          File outFolder = new File(classesFolder,
                                    // Change variant name variant-release into variant/release directories
                                    variantName.replace('-', File.separatorChar));
          if (outFolder.exists()) {
            vOutFolder = LocalFileSystem.getInstance().findFileByIoFile(outFolder);
            if (vOutFolder != null) {
              Class<?> localClass = loadClassFromClassPath(name, VfsUtilCore.virtualToIoFile(vOutFolder));
              if (localClass != null) {
                return localClass;
              }
            }
          }
        }
      }
      return null;
    }

    return loadClassFromClassPath(name, VfsUtilCore.virtualToIoFile(vOutFolder));
  }

  @Override
  @Nullable
  protected Class<?> loadClassFile(final String fqcn, File classFile) {
    // Make sure the class file is up to date and if not, log an error
    if (myLogger != null) {
      // Allow creating class loaders during rendering; may be prevented by the RenderSecurityManager
      boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
      try {
        long classFileModified = classFile.lastModified();
        if (classFileModified > 0L) {
          VirtualFile virtualFile = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
            @Nullable
            @Override
            public VirtualFile compute() {
              Project project = myModule.getProject();
              GlobalSearchScope scope = myModule.getModuleScope();
              PsiManager psiManager = PsiManager.getInstance(project);
              JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(psiManager.getProject());
              PsiClass source = psiFacade.findClass(fqcn, scope);
              if (source != null) {
                PsiFile containingFile = source.getContainingFile();
                if (containingFile != null) {
                  return containingFile.getVirtualFile();
                }
              }

              return null;
            }
          });

          if (virtualFile != null && !FN_RESOURCE_CLASS.equals(virtualFile.getName())) { // Don't flag R.java edits; not done by user
            // Edited but not yet saved?
            boolean modified = FileDocumentManager.getInstance().isFileModified(virtualFile);
            if (!modified) {
              // Check timestamp
              File sourceFile = VfsUtilCore.virtualToIoFile(virtualFile);
              long sourceFileModified = sourceFile.lastModified();

              AndroidFacet facet = AndroidFacet.getInstance(myModule);
              // User modifications on the source file might not always result on a new .class file.
              // If it's a gradle project, we use the project modification time instead to display the warning
              // more reliably.
              long lastBuildTimestamp = facet != null && facet.isGradleProject()
                                        ? PostProjectBuildTasksExecutor.getInstance(myModule.getProject()).getLastBuildTimestamp()
                                        : classFileModified;
              if (sourceFileModified > lastBuildTimestamp) {
                modified = true;
              }
            }

            if (modified) {
              RenderProblem.Html problem = RenderProblem.create(WARNING);
              HtmlBuilder builder = problem.getHtmlBuilder();
              String className = fqcn.substring(fqcn.lastIndexOf('.') + 1);
              builder.addLink("The " + className + " custom view has been edited more recently than the last build: ",
                              "Build", " the project.",
                              myLogger.getLinkManager().createCompileModuleUrl());
              myLogger.addMessage(problem);
            }
          }
        }
      } finally {
        RenderSecurityManager.exitSafeRegion(token);
      }
    }

    return super.loadClassFile(fqcn, classFile);
  }

  @Override
  protected URL[] getExternalJars() {
    final List<URL> result = new ArrayList<URL>();

    for (VirtualFile libFile : AndroidRootUtil.getExternalLibraries(myModule)) {
      if (EXT_JAR.equals(libFile.getExtension())) {
        final File file = new File(libFile.getPath());
        if (file.exists()) {
          try {
            result.add(SdkUtils.fileToUrl(file));

            File parentFile = file.getParentFile();
            if (parentFile != null && (parentFile.getPath().endsWith(DOT_AAR) ||
              parentFile.getPath().contains(EXPLODED_AAR))) {
              AppResourceRepository appResources = AppResourceRepository.getAppResources(myModule, true);
              if (appResources != null) {
                AarResourceClassRegistry.get().addLibrary(appResources, parentFile);
              }
            }
          }
          catch (MalformedURLException e) {
            LOG.error(e);
          }
        }
      }
    }
    return result.toArray(new URL[result.size()]);
  }
}
