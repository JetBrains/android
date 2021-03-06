/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.inspectors.common.api.ide.stacktrace;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.inspectors.common.api.TraceSignatureConverter;
import com.android.tools.inspectors.common.api.stacktrace.CodeLocation;
import com.android.tools.inspectors.common.api.stacktrace.CodeNavigator;
import com.android.tools.nativeSymbolizer.NativeSymbolizer;
import com.android.tools.nativeSymbolizer.Symbol;
import com.google.common.base.Strings;
import com.intellij.build.FileNavigatable;
import com.intellij.build.FilePosition;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.ClassUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link CodeNavigator} with logic to jump to code inside of an IntelliJ code editor.
 */
public class IntellijCodeNavigator extends CodeNavigator {
  @NotNull
  private final Project myProject;
  @NotNull
  private final NativeSymbolizer myNativeSymbolizer;
  @NotNull
  private final Map<String, String> myApkSrcDirMap;

  /**
   * Supplier of the target CPU architecture (e.g. arm64, x86, etc) used to build the process currently being profiled. Can be set by
   * {@link #setCpuAbiArchSupplier(Supplier)}. Although it can't be null, it can return null so the result should be handled accordingly.
   */
  @NotNull private Supplier<String> myCpuAbiArchSupplier;

  public IntellijCodeNavigator(@NotNull Project project,
                               @NotNull NativeSymbolizer nativeSymbolizer) {
    myProject = project;
    myNativeSymbolizer = nativeSymbolizer;
    myApkSrcDirMap = getApkSourceDirMap();
    myCpuAbiArchSupplier = () -> null;
  }

  public void setCpuAbiArchSupplier(@NotNull Supplier<String> cpuAbiArch) {
    myCpuAbiArchSupplier = cpuAbiArch;
  }

  /**
   * Fetches the CPU architecture from {@link #myCpuAbiArchSupplier}.
   */
  @Nullable
  public String fetchCpuAbiArch() {
    return myCpuAbiArchSupplier.get();
  }

  @Override
  protected void handleNavigate(@NotNull CodeLocation location) {
    // Gets the navigatable in another thread, so we don't block the UI while potentially performing heavy operations, such as searching for
    // the java class/method in the PSI tree or using llvm-symbolizer to get a native function name.
    //
    // Note: due to IntelliJ PSI threading rules, read operations performed on a non-UI thread need to wrap the action in a ReadAction.
    // Hence all PSI-reading code inside getNavigatable() will need to wrapped in a ReadAction.
    // See http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html
    CompletableFuture.supplyAsync(
      () -> ReadAction.compute(() -> getNavigatable(location)), ApplicationManager.getApplication()::executeOnPooledThread)
      .thenAcceptAsync(nav -> {
        if (nav != null) {
          nav.navigate(true);
        }
      }, ApplicationManager.getApplication()::invokeLater);
  }

  @Override
  public boolean isNavigatable(@NotNull CodeLocation location) {
    // TODO(b/119398387): consider caching the call to getNavigatable to reuse it in the following call to handleNavigate
    return getNavigatable(location) != null;
  }

  @Nullable
  private Navigatable getNavigatable(@NotNull CodeLocation location) {
    if (!Strings.isNullOrEmpty(location.getFileName()) &&
        location.getLineNumber() != CodeLocation.INVALID_LINE_NUMBER) {
      Navigatable navigatable = getExplicitLocationNavigable(location);
      if (navigatable != null) {
        return navigatable;
      }

      navigatable = getApkMappingNavigable(location);
      if (navigatable != null) {
        return navigatable;
      }
    }

    if (location.isNativeCode()) {
      return getNativeNavigatable(location);
    }

    PsiClass psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myProject), location.getClassName());
    if (psiClass == null) {
      if (location.getLineNumber() >= 0) {
        // There has been at least one case where the PsiManager could not find an inner class in
        // Kotlin code, which caused us to abort navigating. However, if we have the outer class
        // (which is easier for PsiManager to find) and a line number, that's enough information to
        // help us navigate. So, to be more robust against PsiManager error, we try one more time.
        psiClass = ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myProject), location.getOuterClassName());
      }
    }

    if (psiClass == null) {
      return null;
    }
    else if (location.getLineNumber() >= 0) {
      // If the specified CodeLocation has a line number, navigatable is that line
      return new OpenFileDescriptor(myProject, psiClass.getNavigationElement().getContainingFile().getVirtualFile(),
                                    location.getLineNumber(), 0);
    }
    else if (location.getMethodName() != null && location.getSignature() != null) {
      // If it has both method name and signature, navigatable is the corresponding method
      PsiMethod method = findMethod(psiClass, location.getMethodName(), location.getSignature());
      return method != null ? method : psiClass;
    }
    else {
      // Otherwise, navigatable is the class
      return psiClass;
    }
  }

  /**
   * Returns a navigation to a file and a line explicitly specified in the location
   * if it exists.
   */
  @Nullable
  private Navigatable getExplicitLocationNavigable(@NotNull CodeLocation location) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile sourceFile = fileSystem.findFileByPath(location.getFileName());
    if (sourceFile == null || !sourceFile.exists()) {
      return null;
    }
    return new OpenFileDescriptor(myProject, sourceFile, location.getLineNumber(), 0);
  }

  /**
   * Returns a navigation to a file and a line explicitly specified in the location
   * after applying APK source mapping to it.
   */
  @Nullable
  private Navigatable getApkMappingNavigable(@NotNull CodeLocation location) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (Map.Entry<String, String> entry : myApkSrcDirMap.entrySet()) {
      if (location.getFileName().startsWith(entry.getKey())) {
        String pathTailAfterPrefix = location.getFileName().substring(entry.getKey().length());
        String newFileName = Paths.get(entry.getValue(), pathTailAfterPrefix).toString();
        VirtualFile sourceFile = fileSystem.findFileByPath(newFileName);
        if (sourceFile != null && sourceFile.exists()) {
          return new OpenFileDescriptor(myProject, sourceFile, location.getLineNumber(), 0);
        }
      }
    }

    return null;
  }

  @NotNull
  private Map<String, String> getApkSourceDirMap() {
    // Using LinkedHashMap here to preserve order from getSymbolFolderPathMappings and imitate LLDB's behavior.
    Map<String, String> sourceMap = new LinkedHashMap<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      ApkFacet apkFacet = ApkFacet.getInstance(module);
      if (apkFacet != null) {
        for (Map.Entry<String, String> entry : apkFacet.getConfiguration().getSymbolFolderPathMappings().entrySet()) {
          // getSymbolFolderPathMappings() has a lot of path records which are not mapped, they need
          // to be filtered out.
          if (!entry.getValue().isEmpty() && !entry.getKey().equals(entry.getValue())) {
            sourceMap.put(entry.getKey(), entry.getValue());
          }
        }
      }
    }
    return sourceMap;
  }

  /**
   * Attempts to symbolize the code location to find and return the functions's corresponding {@link Navigatable} within the project.
   */
  @Nullable
  private Navigatable getNativeNavigatable(@NotNull CodeLocation location) {
    String arch = fetchCpuAbiArch();
    if (location.getFileName() == null || arch == null) {
      return null;
    }
    Symbol symbol;
    try {
      symbol = myNativeSymbolizer.symbolize(arch, location.getFileName(), location.getNativeVAddress());
    }
    catch (IOException e) {
      return null;
    }

    if (symbol == null) {
      return null;
    }
    return new FileNavigatable(myProject, new FilePosition(new File(symbol.getSourceFile()), symbol.getLineNumber() - 1, 0));
  }

  @Nullable
  private static PsiMethod findMethod(@NotNull PsiClass psiClass, @NotNull String methodName, @NotNull String signature) {
    for (PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
      if (signature.equals(TraceSignatureConverter.getTraceSignature(method))) {
        return method;
      }
    }
    return null;
  }
}
