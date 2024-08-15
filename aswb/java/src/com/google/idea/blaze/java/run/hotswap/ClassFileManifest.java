/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.hotswap;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.filecache.FilesDiff;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import javax.annotation.Nullable;

/** A manifest of .class file hashes for jars needed at runtime. Used for HotSwapping. */
public class ClassFileManifest {

  private static final Logger logger = Logger.getInstance(ClassFileManifest.class);

  /** A per-jar map of .class files changed between manifests */
  public static class Diff {
    public final ImmutableMultimap<File, String> perJarModifiedClasses;

    public Diff(ImmutableMultimap<File, String> perJarModifiedClasses) {
      this.perJarModifiedClasses = perJarModifiedClasses;
    }
  }

  // jar file timestamps
  private final ImmutableMap<File, Long> jarFileState;
  // per-jar manifest of .class file hashes
  private final ImmutableMap<File, JarManifest> jarManifests;

  private ClassFileManifest(
      ImmutableMap<File, Long> jarFileState, ImmutableMap<File, JarManifest> jarManifests) {
    this.jarFileState = jarFileState;
    this.jarManifests = jarManifests;
  }

  /** Returns a per-jar map of .class files changed in the new manifest */
  public static Diff modifiedClasses(ClassFileManifest oldManifest, ClassFileManifest newManifest) {
    ImmutableMultimap.Builder<File, String> map = ImmutableMultimap.builder();
    for (Map.Entry<File, JarManifest> entry : newManifest.jarManifests.entrySet()) {
      // quick test for object equality -- jars are often not rebuilt
      JarManifest old = oldManifest.jarManifests.get(entry.getKey());
      if (old == entry.getValue()) {
        continue;
      }
      ImmutableList<String> changedClasses = JarManifest.diff(old, entry.getValue());
      if (!changedClasses.isEmpty()) {
        map.putAll(entry.getKey(), changedClasses);
      }
    }
    return new Diff(map.build());
  }

  @Nullable
  public static ClassFileManifest build(
      Collection<File> jars, @Nullable ClassFileManifest previousManifest)
      throws ExecutionException {
    try {
      FilesDiff<File, File> diff =
          FilesDiff.diffFileTimestamps(
              previousManifest != null ? previousManifest.jarFileState : null, jars);

      ImmutableMap.Builder<File, JarManifest> jarManifests = ImmutableMap.builder();
      jars.forEach(
          f -> {
            if (!diff.getUpdatedFiles().contains(f) && previousManifest != null) {
              jarManifests.put(f, previousManifest.jarManifests.get(f));
            }
          });
      buildJarManifests(diff.getUpdatedFiles()).stream()
          .filter(Objects::nonNull)
          .forEach(m -> jarManifests.put(m.jar, m));
      return new ClassFileManifest(diff.getNewFileState(), jarManifests.build());
    } catch (InterruptedException e) {
      throw new RunCanceledByUserException();
    } catch (java.util.concurrent.ExecutionException e) {
      throw new ExecutionException("Error parsing runtime jars", e);
    }
  }

  private static List<JarManifest> buildJarManifests(Collection<File> jars)
      throws java.util.concurrent.ExecutionException, InterruptedException {
    BlazeExecutor executor = BlazeExecutor.getInstance();
    return Futures.allAsList(
            jars.stream()
                .map(f -> executor.submit(() -> JarManifest.build(f)))
                .collect(Collectors.toList()))
        .get();
  }

  /** .class file manifest for a single jar. */
  private static class JarManifest {
    private final File jar;
    private final ImmutableMap<String, Long> nameToHash;

    @Nullable
    static JarManifest build(File file) {
      try {
        JarFile jar = new JarFile(file);
        return new JarManifest(
            file,
            jar.stream()
                .filter(e -> e.getName().endsWith(".class"))
                .collect(
                    toImmutableMap(JarEntry::getName, ZipEntry::getCrc, (first, second) -> first)));
      } catch (IOException e) {
        logger.warn("Error reading jar file: " + file, e);
        return null;
      }
    }

    private JarManifest(File jar, ImmutableMap<String, Long> nameToHash) {
      this.jar = jar;
      this.nameToHash = nameToHash;
    }

    /** Returns the list of classes changed in the new manifest. */
    static ImmutableList<String> diff(JarManifest oldManifest, JarManifest newManifest) {
      return newManifest
          .nameToHash
          .entrySet()
          .stream()
          .filter(e -> !Objects.equals(e.getValue(), oldManifest.nameToHash.get(e.getKey())))
          .map(Entry::getKey)
          .collect(toImmutableList());
    }
  }
}
