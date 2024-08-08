/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.io;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Reads file attributes from a list files in parallel. */
public class FileAttributeScanner {

  /** Reads an attribute from a file. */
  public interface AttributeReader<F, T> {
    @Nullable
    T getAttribute(F file);

    boolean isValid(T attribute);
  }

  public static <F, T> ImmutableMap<F, T> readAttributes(
      Iterable<F> files, AttributeReader<F, T> attributeReader, ListeningExecutorService executor)
      throws InterruptedException, ExecutionException {
    List<ListenableFuture<FilePair<F, T>>> futures = Lists.newArrayList();
    for (F file : files) {
      futures.add(
          executor.submit(
              () -> {
                T attribute = attributeReader.getAttribute(file);
                if (attribute != null && attributeReader.isValid(attribute)) {
                  return new FilePair<>(file, attribute);
                }
                return null;
              }));
    }

    Map<F, T> result = new HashMap<>();
    for (FilePair<F, T> filePair : Futures.allAsList(futures).get()) {
      if (filePair != null) {
        result.put(filePair.file, filePair.attribute);
      }
    }
    return ImmutableMap.copyOf(result);
  }

  private static class FilePair<F, T> {
    public final F file;
    public final T attribute;

    public FilePair(F file, T attribute) {
      this.file = file;
      this.attribute = attribute;
    }
  }
}
