/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.formatter;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.formatter.FormatUtils.FileContentsProvider;
import com.google.idea.blaze.base.formatter.FormatUtils.Replacements;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.concurrent.ConcurrentMap;
import org.jetbrains.ide.PooledThreadExecutor;

/** A helper class for synchronizing multiple formatting tasks on a per-file basis. */
public final class FileBasedFormattingSynchronizer {

  private static final ListeningExecutorService executor =
      MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE);

  private static final ConcurrentMap<File, Object> perFileLocks =
      ContainerUtil.createConcurrentWeakValueMap();

  /** A formatter that reads contents from a psi file and computes replacements. */
  public interface Formatter<T> {
    /**
     * A result from your formatter. The T can be used to communicate arbitrary information back to
     * the caller.
     */
    class Result<T> {
      T result;
      Replacements replacements;

      public Result(T result, Replacements replacements) {
        this.result = result;
        this.replacements = replacements;
      }
    }

    /**
     * Calculate formatting replacements for the given {@link PsiFile}. Not called with a read lock.
     */
    Result<T> format(PsiFile psiFile);
  }

  /**
   * Synchronously obtains a per-file lock, then applies the changes under a write action.
   *
   * <p>If the file contents have changed since the input {@link FileContentsProvider} was
   * instantiated, no changes are made.
   */
  public static <T> ListenableFuture<T> applyReplacements(PsiFile psiFile, Formatter<T> formatter) {
    File file = new File(psiFile.getViewProvider().getVirtualFile().getPath());
    return executor.submit(
        () -> {
          Object lock = perFileLocks.computeIfAbsent(file, f -> new Object());
          synchronized (lock) {
            Formatter.Result<T> result = formatter.format(psiFile);
            if (result == null) {
              return null;
            }
            FormatUtils.performReplacements(
                FileContentsProvider.fromPsiFile(psiFile), result.replacements);
            return result.result;
          }
        });
  }
}
