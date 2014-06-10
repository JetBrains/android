package org.jetbrains.android.uipreview;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.Variant;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.rendering.AarResourceClassRegistry;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.rendering.RenderClassLoader;
import com.android.utils.SdkUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
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
import static org.jetbrains.android.facet.ResourceFolderManager.EXPLODED_AAR;

/**
 * @author Eugene.Kudelevsky
 */
public final class ProjectClassLoader extends RenderClassLoader {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.ProjectClassLoader");

  private final Module myModule;

  public ProjectClassLoader(@Nullable ClassLoader parentClassLoader, Module module) {
    super(parentClassLoader);
    myModule = module;
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

    return new ProjectClassLoader(library.getClassLoader(), module);
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
            File file = output.getOutputFile();
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
              AarResourceClassRegistry.get().addLibrary(AppResourceRepository.getAppResources(myModule, true), parentFile);
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
