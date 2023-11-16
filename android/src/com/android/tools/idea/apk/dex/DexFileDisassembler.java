/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.apk.dex;


import static com.android.tools.apk.analyzer.dex.DexFiles.getDexFile;
import static com.android.tools.smali.baksmali.Baksmali.disassembleDexFile;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static com.intellij.concurrency.JobSchedulerImpl.getCPUCoresCount;

import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

public class DexFileDisassembler {
  public boolean disassemble(@NotNull File dexFile, @NotNull File outputFolder) throws ExecutionException, InterruptedException {
    ListeningExecutorService executor = listeningDecorator(PooledThreadExecutor.INSTANCE);
    Future<DexBackedDexFile> dexFileFuture = executor.submit(() -> getDexFile(dexFile.toPath()));
    DexBackedDexFile dexBackedDexFile = dexFileFuture.get();
    return disassemble(dexBackedDexFile, outputFolder);
  }

  private static boolean disassemble(@NotNull DexFile dexFile, @NotNull File outputFolderPath) {
    return disassembleDexFile(dexFile, outputFolderPath, getCPUCoresCount(), new BaksmaliOptions());
  }
}
