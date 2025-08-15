/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

package com.android.tools.idea.rendering.tokens;

import com.android.tools.idea.projectsystem.ClassContent;
import com.android.tools.idea.projectsystem.ClassFileFinder;
import com.android.tools.idea.projectsystem.ClassFileFinderUtil;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.jetbrains.annotations.NotNull;

final class BazelClassFileFinder implements ClassFileFinder {
  private final Map<String, List<Jar>> classToJarMultimap;

  BazelClassFileFinder(Collection<Path> jars) {
    classToJarMultimap = jars.stream()
      .map(Jar::new)
      .flatMap(Jar::entries)
      .collect(Collectors.groupingBy(Object::toString, Collectors.mapping(Entry::getJar, Collectors.toList())));
  }

  @Override
  public ClassContent findClassFile(@NotNull String c) {
    c = ClassFileFinderUtil.getPathFromFqcn(c);
    return classToJarMultimap.get(c).get(0).getContent(c);
  }

  private static final class Jar {
    private final File jar;
    private final Collection<Entry> entries;

    private Jar(Path jar) {
      this.jar = jar.toFile();
      entries = initEntries();
    }

    private Collection<Entry> initEntries() {
      try (var jar = new JarFile(this.jar)) {
        return jar.stream()
          .map(entry -> new Entry(entry, this))
          .filter(Entry::isNotDirectory)
          .filter(Entry::isNotInMetaInfDirectory)
          .toList();
      }
      catch (IOException exception) {
        Logger.getInstance(BazelClassFileFinder.class).warn(exception);
        return List.of();
      }
    }

    private Stream<Entry> entries() {
      return entries.stream();
    }

    private ClassContent getContent(String c) {
      try (var jar = new JarFile(this.jar)) {
        return ClassContent.fromJarEntryContent(this.jar, jar.getInputStream(jar.getEntry(c)).readAllBytes());
      }
      catch (IOException exception) {
        Logger.getInstance(BazelClassFileFinder.class).warn(exception);
        return null;
      }
    }
  }

  @SuppressWarnings("ClassCanBeRecord")
  private static final class Entry {
    private final ZipEntry entry;
    private final Jar jar;

    private Entry(ZipEntry entry, Jar jar) {
      this.entry = entry;
      this.jar = jar;
    }

    private boolean isNotDirectory() {
      return !entry.isDirectory();
    }

    private boolean isNotInMetaInfDirectory() {
      return !entry.toString().startsWith("META-INF/");
    }

    private Jar getJar() {
      return jar;
    }

    @Override
    public String toString() {
      return entry.toString();
    }
  }
}
