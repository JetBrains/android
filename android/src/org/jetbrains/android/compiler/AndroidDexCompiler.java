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
package org.jetbrains.android.compiler;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.ClassPostProcessingCompiler;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.compiler.tools.AndroidDxWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Android Dex compiler.
 */
public class AndroidDexCompiler implements ClassPostProcessingCompiler {

  @Override
  @NotNull
  public ProcessingItem[] getProcessingItems(@NotNull CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new PrepareAction(context));
  }

  @Override
  public ProcessingItem[] process(@NotNull CompileContext context, @NotNull ProcessingItem[] items) {
    if (!AndroidCompileUtil.isFullBuild(context)) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    if (items.length > 0) {
      context.getProgressIndicator().setText("Generating " + AndroidBuildCommonUtils.CLASSES_FILE_NAME + "...");
      return new ProcessAction(context, items).compute();
    }
    return ProcessingItem.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String getDescription() {
    return FileUtil.getNameWithoutExtension(SdkConstants.FN_DX);
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return new MyValidityState(in);
  }

  public static VirtualFile getOutputDirectoryForDex(@NotNull Module module) {
    return CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
  }

  static void addModuleOutputDir(Collection<VirtualFile> files, VirtualFile dir) {
    // only include files inside packages
    for (VirtualFile child : dir.getChildren()) {
      if (child.isDirectory()) {
        files.add(child);
      }
    }
  }

  @SuppressWarnings("deprecation")
  private static final class PrepareAction implements Computable<ProcessingItem[]> {
    private final CompileContext myContext;

    public PrepareAction(CompileContext context) {
      myContext = context;
    }

    @Override
    public ProcessingItem[] compute() {
      final AndroidDexCompilerConfiguration dexConfig =
        AndroidDexCompilerConfiguration.getInstance(myContext.getProject());

      Module[] modules = ModuleManager.getInstance(myContext.getProject()).getModules();
      List<ProcessingItem> items = new ArrayList<ProcessingItem>();
      for (Module module : modules) {
        AndroidFacet facet = FacetManager.getInstance(module).getFacetByType(AndroidFacet.ID);
        if (facet != null && facet.getConfiguration().isAppProject()) {

          final VirtualFile dexOutputDir = getOutputDirectoryForDex(module);

          Collection<VirtualFile> files;

          final boolean shouldRunProguard = AndroidCompileUtil.getProguardConfigFilePathIfShouldRun(facet, myContext) != null;

          if (shouldRunProguard) {
            final VirtualFile obfuscatedSourcesJar = dexOutputDir.findChild(AndroidBuildCommonUtils.PROGUARD_OUTPUT_JAR_NAME);
            if (obfuscatedSourcesJar == null) {
              myContext.addMessage(CompilerMessageCategory.INFORMATION, "Dex won't be launched for module " +
                                                                        module.getName() +
                                                                        " because file " +
                                                                        AndroidBuildCommonUtils.PROGUARD_OUTPUT_JAR_NAME +
                                                                        " doesn't exist", null, -1, -1);
              continue;
            }

            files = Collections.singleton(obfuscatedSourcesJar);
          }
          else {
            CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
            VirtualFile outputDir = extension.getCompilerOutputPath();

            if (outputDir == null) {
              myContext.addMessage(CompilerMessageCategory.INFORMATION,
                                   "Dex won't be launched for module " + module.getName() + " because it doesn't contain compiled files",
                                   null, -1, -1);
              continue;
            }

            files = new HashSet<VirtualFile>();
            addModuleOutputDir(files, outputDir);
            files.addAll(AndroidRootUtil.getExternalLibraries(module));

            for (VirtualFile file : AndroidRootUtil.getDependentModules(module, outputDir)) {
              if (file.isDirectory()) {
                addModuleOutputDir(files, file);
              }
              else {
                files.add(file);
              }
            }

            if (facet.getProperties().PACK_TEST_CODE) {
              VirtualFile outputDirForTests = extension.getCompilerOutputPathForTests();

              if (outputDirForTests != null) {
                addModuleOutputDir(files, outputDirForTests);
              }
            }
          }

          final AndroidPlatform platform = AndroidPlatform.getInstance(module);

          if (platform == null) {
            myContext.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
            continue;
          }

          StringBuilder options = new StringBuilder(dexConfig.VM_OPTIONS);
          //JpsAndroidModuleProperties state = configuration.getState();
          //if (state != null ) {
          //  if (state.ENABLE_MULTI_DEX) {
          //    options.append(" --multi-dex");
          //  }
          //  if (!StringUtil.isEmpty(state.MAIN_DEX_LIST)) {
          //    options.append(" --main-dex-list ").append(state.MAIN_DEX_LIST);
          //  }
          //  if (state.MINIMAL_MAIN_DEX) {
          //    options.append(" --minimal-main-dex");
          //  }
          //}

          items.add(new DexItem(module, dexOutputDir, platform.getTarget(), files, options.toString(), dexConfig.MAX_HEAP_SIZE,
                                dexConfig.OPTIMIZE));
        }
      }
      return items.toArray(ProcessingItem.EMPTY_ARRAY);
    }
  }

  private final static class ProcessAction implements Computable<ProcessingItem[]> {
    private final CompileContext myContext;
    private final ProcessingItem[] myItems;

    public ProcessAction(CompileContext context, ProcessingItem[] items) {
      myContext = context;
      myItems = items;
    }

    @Override
    public ProcessingItem[] compute() {
      List<ProcessingItem> results = new ArrayList<ProcessingItem>(myItems.length);
      for (ProcessingItem item : myItems) {
        if (item instanceof DexItem) {
          DexItem dexItem = (DexItem)item;

          if (!AndroidCompileUtil.isModuleAffected(myContext, dexItem.myModule)) {
            continue;
          }

          String outputDirPath = FileUtil.toSystemDependentName(dexItem.myClassDir.getPath());
          String[] files = new String[dexItem.myFiles.size()];
          int i = 0;
          for (VirtualFile file : dexItem.myFiles) {
            files[i++] = FileUtil.toSystemDependentName(file.getPath());
          }

          Map<CompilerMessageCategory, List<String>> messages = AndroidCompileUtil.toCompilerMessageCategoryKeys(AndroidDxWrapper.execute(
            dexItem.myModule, dexItem.myAndroidTarget, outputDirPath, files, dexItem.myAdditionalVmParams, dexItem.myMaxHeapSize,
            dexItem.myOptimize));

          addMessages(messages, dexItem.myModule);
          if (messages.get(CompilerMessageCategory.ERROR).isEmpty()) {
            results.add(dexItem);
          }
        }
      }
      return results.toArray(ProcessingItem.EMPTY_ARRAY);
    }

    private void addMessages(Map<CompilerMessageCategory, List<String>> messages, Module module) {
      for (CompilerMessageCategory category : messages.keySet()) {
        List<String> messageList = messages.get(category);
        for (String message : messageList) {
          myContext.addMessage(category, '[' + module.getName() + "] " + message, null, -1, -1);
        }
      }
    }
  }

  private final static class DexItem implements ProcessingItem {
    final Module myModule;
    final VirtualFile myClassDir;
    final IAndroidTarget myAndroidTarget;
    final Collection<VirtualFile> myFiles;
    final String myAdditionalVmParams;
    final int myMaxHeapSize;
    final boolean myOptimize;

    public DexItem(@NotNull Module module,
                   @NotNull VirtualFile classDir,
                   @NotNull IAndroidTarget target,
                   Collection<VirtualFile> files,
                   @NotNull String additionalVmParams,
                   int maxHeapSize,
                   boolean optimize) {
      myModule = module;
      myClassDir = classDir;
      myAndroidTarget = target;
      myFiles = files;
      myAdditionalVmParams = additionalVmParams;
      myMaxHeapSize = maxHeapSize;
      myOptimize = optimize;
    }

    @Override
    @NotNull
    public VirtualFile getFile() {
      return myClassDir;
    }

    @Override
    @Nullable
    public ValidityState getValidityState() {
      return new MyValidityState(myFiles, myAdditionalVmParams, myMaxHeapSize, myOptimize);
    }
  }

  private static class MyValidityState extends ClassesAndJarsValidityState {
    private final String myAdditionalVmParams;
    private final int myMaxHeapSize;
    private final boolean myOptimize;

    public MyValidityState(@NotNull Collection<VirtualFile> files, @NotNull String additionalVmParams, int maxHeapSize, boolean optimize) {
      super(files);
      myAdditionalVmParams = additionalVmParams;
      myMaxHeapSize = maxHeapSize;
      myOptimize = optimize;
    }

    public MyValidityState(@NotNull DataInput in) throws IOException {
      super(in);
      myAdditionalVmParams = in.readUTF();
      myMaxHeapSize = in.readInt();
      myOptimize = in.readBoolean();
    }

    @Override
    public void save(DataOutput out) throws IOException {
      super.save(out);
      out.writeUTF(myAdditionalVmParams);
      out.writeInt(myMaxHeapSize);
      out.writeBoolean(myOptimize);
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!super.equalsTo(otherState)) {
        return false;
      }
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }
      final MyValidityState state = (MyValidityState)otherState;
      return state.myAdditionalVmParams.equals(myAdditionalVmParams) &&
             state.myMaxHeapSize == myMaxHeapSize &&
             state.myOptimize == myOptimize;
    }
  }
}
