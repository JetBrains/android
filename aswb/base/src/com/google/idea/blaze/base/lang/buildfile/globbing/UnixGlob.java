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
package com.google.idea.blaze.base.lang.buildfile.globbing;

import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.lang.buildfile.validation.GlobPatternValidator;
import com.intellij.openapi.progress.ProgressManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.Nullable;

/**
 * Implementation of a subset of UNIX-style file globbing, expanding "*" and "?" as wildcards, but
 * not [a-z] ranges.
 *
 * <p><code>**</code> gets special treatment in include patterns. If it is used as a complete path
 * segment it matches the filenames in subdirectories recursively.
 *
 * <p>Largely copied from {@link com.google.devtools.build.lib.vfs.UnixGlob}
 */
public final class UnixGlob {
  private UnixGlob() {}

  private static Set<File> globInternal(
      File base,
      Collection<String> patterns,
      boolean excludeDirectories,
      Predicate<File> dirPred,
      ThreadPoolExecutor threadPool)
      throws IOException, InterruptedException {

    GlobVisitor visitor = (threadPool == null) ? new GlobVisitor() : new GlobVisitor(threadPool);
    return visitor.glob(base, patterns, excludeDirectories, dirPred);
  }

  /**
   * Checks that each pattern is valid, splits it into segments and checks that each segment
   * contains only valid wildcards.
   *
   * @return list of segment arrays
   */
  private static List<String[]> checkAndSplitPatterns(Collection<String> patterns) {
    List<String[]> list = Lists.newArrayListWithCapacity(patterns.size());
    for (String pattern : patterns) {
      String error = GlobPatternValidator.validate(pattern);
      if (error != null) {
        throw new IllegalArgumentException(error);
      }
      Iterable<String> segments = Splitter.on('/').split(pattern);
      list.add(Iterables.toArray(segments, String.class));
    }
    return list;
  }

  /** Calls {@link #matches(String, String, Cache) matches(pattern, str, null)} */
  public static boolean matches(String pattern, String str) {
    try {
      return matches(pattern, str, null);
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  /**
   * Returns whether {@code str} matches the glob pattern {@code pattern}. This method may use the
   * {@code patternCache} to speed up the matching process.
   *
   * @param pattern a glob pattern
   * @param str the string to match
   * @param patternCache a cache from patterns to compiled Pattern objects, or {@code null} to skip
   *     caching
   */
  private static boolean matches(String pattern, String str, Cache<String, Pattern> patternCache) {
    if (pattern.length() == 0 || str.length() == 0) {
      return false;
    }

    // Common case: **
    if (pattern.equals("**")) {
      return true;
    }

    // Common case: *
    if (pattern.equals("*")) {
      return true;
    }

    // If a filename starts with '.', this char must be matched explicitly.
    if (str.charAt(0) == '.' && pattern.charAt(0) != '.') {
      return false;
    }

    // Common case: *.xyz
    if (pattern.charAt(0) == '*' && pattern.lastIndexOf('*') == 0) {
      return str.endsWith(pattern.substring(1));
    }
    // Common case: xyz*
    int lastIndex = pattern.length() - 1;
    // The first clause of this if statement is unnecessary, but is an
    // optimization--charAt runs faster than indexOf.
    if (pattern.charAt(lastIndex) == '*' && pattern.indexOf('*') == lastIndex) {
      return str.startsWith(pattern.substring(0, lastIndex));
    }

    Pattern regex = patternCache == null ? null : patternCache.getIfPresent(pattern);
    if (regex == null) {
      regex = makePatternFromWildcard(pattern);
      if (patternCache != null) {
        patternCache.put(pattern, regex);
      }
    }
    return regex.matcher(str).matches();
  }

  /**
   * Returns a regular expression implementing a matcher for "pattern", in which "*" and "?" are
   * wildcards.
   *
   * <p>e.g. "foo*bar?.java" -> "foo.*bar.\\.java"
   */
  private static Pattern makePatternFromWildcard(String pattern) {
    StringBuilder regexp = new StringBuilder();
    for (int i = 0, len = pattern.length(); i < len; i++) {
      char c = pattern.charAt(i);
      switch (c) {
        case '*':
          int toIncrement = 0;
          if (len > i + 1 && pattern.charAt(i + 1) == '*') {
            // The pattern '**' is interpreted to match 0 or more directory separators, not 1 or
            // more. We skip the next * and then find a trailing/leading '/' and get rid of it.
            toIncrement = 1;
            if (len > i + 2 && pattern.charAt(i + 2) == '/') {
              // We have '**/' -- skip the '/'.
              toIncrement = 2;
            } else if (len == i + 2 && i > 0 && pattern.charAt(i - 1) == '/') {
              // We have '/**' -- remove the '/'.
              regexp.delete(regexp.length() - 1, regexp.length());
            }
          }
          regexp.append(".*");
          i += toIncrement;
          break;
        case '?':
          regexp.append('.');
          break;
          // escape the regexp special characters that are allowed in wildcards
        case '^':
        case '$':
        case '|':
        case '+':
        case '{':
        case '}':
        case '[':
        case ']':
        case '\\':
        case '.':
          regexp.append('\\');
          regexp.append(c);
          break;
        default:
          regexp.append(c);
          break;
      }
    }
    return Pattern.compile(regexp.toString());
  }

  public static Builder forPath(File path) {
    return new Builder(path);
  }

  /** Builder class for UnixGlob. */
  public static class Builder {
    private File base;
    private final List<String> patterns = new ArrayList<>();
    private final List<String> excludes = new ArrayList<>();
    private boolean excludeDirectories;
    private Predicate<File> pathFilter;
    private ThreadPoolExecutor threadPool;

    /** Creates a glob builder with the given base path. */
    public Builder(File base) {
      this.base = base;
      this.excludeDirectories = false;
      this.pathFilter = file -> true;
    }

    /**
     * Adds a pattern to include to the glob builder.
     *
     * <p>For a description of the syntax of the patterns, see {@link UnixGlob}.
     */
    @CanIgnoreReturnValue
    public Builder addPattern(String pattern) {
      this.patterns.add(pattern);
      return this;
    }

    /**
     * Adds a pattern to include to the glob builder.
     *
     * <p>For a description of the syntax of the patterns, see {@link UnixGlob}.
     */
    @CanIgnoreReturnValue
    public Builder addPatterns(String... patterns) {
      Collections.addAll(this.patterns, patterns);
      return this;
    }

    /**
     * Adds a pattern to include to the glob builder.
     *
     * <p>For a description of the syntax of the patterns, see {@link UnixGlob}.
     */
    @CanIgnoreReturnValue
    public Builder addPatterns(Collection<String> patterns) {
      this.patterns.addAll(patterns);
      return this;
    }

    /**
     * Adds patterns to exclude from the results to the glob builder.
     *
     * <p>For a description of the syntax of the patterns, see {@link UnixGlob}.
     */
    @CanIgnoreReturnValue
    public Builder addExcludes(String... excludes) {
      Collections.addAll(this.excludes, excludes);
      return this;
    }

    /**
     * Adds patterns to exclude from the results to the glob builder.
     *
     * <p>For a description of the syntax of the patterns, see {@link UnixGlob}.
     */
    @CanIgnoreReturnValue
    public Builder addExcludes(Collection<String> excludes) {
      this.excludes.addAll(excludes);
      return this;
    }

    /** If set to true, directories are not returned in the glob result. */
    @CanIgnoreReturnValue
    public Builder setExcludeDirectories(boolean excludeDirectories) {
      this.excludeDirectories = excludeDirectories;
      return this;
    }

    /**
     * Sets the threadpool to use for parallel glob evaluation. If unset, evaluation is done
     * in-thread.
     */
    @CanIgnoreReturnValue
    public Builder setThreadPool(ThreadPoolExecutor pool) {
      this.threadPool = pool;
      return this;
    }

    /**
     * If set, the given predicate is called for every directory encountered. If it returns false,
     * the corresponding item is not returned in the output and directories are not traversed
     * either.
     */
    @CanIgnoreReturnValue
    public Builder setDirectoryFilter(Predicate<File> pathFilter) {
      this.pathFilter = pathFilter;
      return this;
    }

    /**
     * Executes the glob.
     *
     * @throws InterruptedException if the thread is interrupted.
     */
    public List<File> glob() throws IOException, InterruptedException {
      Set<File> included = globInternal(base, patterns, excludeDirectories, pathFilter, threadPool);
      Set<File> excluded = globInternal(base, excludes, excludeDirectories, pathFilter, threadPool);
      included.removeAll(excluded);
      return Ordering.<File>natural().immutableSortedCopy(included);
    }
  }

  /** Adapts the result of the glob visitation as a Future. */
  private static class GlobFuture extends ForwardingListenableFuture<Set<File>> {
    private final GlobVisitor visitor;
    private final SettableFuture<Set<File>> delegate = SettableFuture.create();

    private GlobFuture(GlobVisitor visitor) {
      this.visitor = visitor;
    }

    @Override
    public Set<File> get() throws InterruptedException, ExecutionException {
      return super.get();
    }

    @Override
    protected ListenableFuture<Set<File>> delegate() {
      return delegate;
    }

    public void setException(IOException exception) {
      delegate.setException(exception);
    }

    public void set(Set<File> paths) {
      delegate.set(paths);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      // Best-effort interrupt of the in-flight visitation.
      visitor.cancel();
      return true;
    }

    void markCanceled() {
      super.cancel(true);
    }
  }

  /**
   * GlobVisitor executes a glob using parallelism, which is useful when the glob() requires many
   * readdir() calls on high latency filesystems.
   */
  private static final class GlobVisitor {
    // These collections are used across workers and must therefore be thread-safe.
    private final Set<File> results = Sets.newConcurrentHashSet();
    private final Cache<String, Pattern> cache =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, Pattern>() {
                  @Override
                  public Pattern load(String wildcard) {
                    return makePatternFromWildcard(wildcard);
                  }
                });

    private final GlobFuture result;
    private final ThreadPoolExecutor executor;
    private final AtomicLong pendingOps = new AtomicLong(0);
    private final AtomicReference<IOException> failure = new AtomicReference<>();
    private final FileOperationProvider fileOperationProvider = FileOperationProvider.getInstance();
    private volatile boolean canceled = false;

    private GlobVisitor(ThreadPoolExecutor executor) {
      this.executor = executor;
      this.result = new GlobFuture(this);
    }

    private GlobVisitor() {
      this(null);
    }

    /**
     * Performs wildcard globbing: returns the sorted list of filenames that match any of {@code
     * patterns} relative to {@code base}. Directories are traversed if and only if they match
     * {@code dirPred}. The predicate is also called for the root of the traversal.
     *
     * <p>Patterns may include "*" and "?", but not "[a-z]".
     *
     * <p><code>**</code> gets special treatment in include patterns. If it is used as a complete
     * path segment it matches the filenames in subdirectories recursively.
     *
     * @throws IllegalArgumentException if any glob or exclude pattern contains errors, or if any
     *     exclude pattern segment contains <code>**</code> or if any include pattern segment
     *     contains <code>**</code> but not equal to it.
     */
    private Set<File> glob(
        File base, Collection<String> patterns, boolean excludeDirectories, Predicate<File> dirPred)
        throws IOException, InterruptedException {
      try {
        return globAsync(base, patterns, excludeDirectories, dirPred).get();
      } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        Throwables.propagateIfPossible(cause, IOException.class);
        throw new RuntimeException(e);
      }
    }

    private Future<Set<File>> globAsync(
        File base, Collection<String> patterns, boolean excludeDirectories, Predicate<File> dirPred)
        throws IOException {

      if (!fileOperationProvider.exists(base) || patterns.isEmpty()) {
        return Futures.immediateFuture(Collections.emptySet());
      }
      boolean baseIsDirectory = fileOperationProvider.isDirectory(base);

      // We do a dumb loop, even though it will likely duplicate work
      // (e.g., readdir calls). In order to optimize, we would need
      // to keep track of which patterns shared sub-patterns and which did not
      // (for example consider the glob [*/*.java, sub/*.java, */*.txt]).
      pendingOps.incrementAndGet();
      try {
        for (String[] splitPattern : checkAndSplitPatterns(patterns)) {
          queueGlob(
              base, baseIsDirectory, splitPattern, 0, excludeDirectories, results, cache, dirPred);
        }
      } finally {
        decrementAndCheckDone();
      }

      return result;
    }

    private void queueGlob(
        File base,
        boolean baseIsDirectory,
        String[] patternParts,
        int idx,
        boolean excludeDirectories,
        Collection<File> results,
        Cache<String, Pattern> cache,
        Predicate<File> dirPred)
        throws IOException {
      enqueue(
          () -> {
            try {
              reallyGlob(
                  base,
                  baseIsDirectory,
                  patternParts,
                  idx,
                  excludeDirectories,
                  results,
                  cache,
                  dirPred);
            } catch (IOException e) {
              failure.set(e);
            }
          });
    }

    void enqueue(final Runnable r) {
      pendingOps.incrementAndGet();

      Runnable wrapped =
          () -> {
            try {
              if (!canceled && failure.get() == null) {
                r.run();
              }
            } finally {
              decrementAndCheckDone();
            }
          };

      if (executor == null) {
        wrapped.run();
      } else {
        executor.execute(wrapped);
      }
    }

    protected void cancel() {
      this.canceled = true;
    }

    private void decrementAndCheckDone() {
      if (pendingOps.decrementAndGet() == 0) {
        // We get to 0 iff we are done all the relevant work. This is because we always increment
        // the pending ops count as we're enqueuing, and don't decrement until the task is complete
        // (which includes accounting for any additional tasks that one enqueues).
        if (canceled) {
          result.markCanceled();
        } else if (failure.get() != null) {
          result.setException(failure.get());
        } else {
          result.set(results);
        }
      }
    }

    /**
     * Expressed in Haskell:
     *
     * <pre>
     *  reallyGlob base []     = { base }
     *  reallyGlob base [x:xs] = union { reallyGlob(f, xs) | f results "base/x" }
     * </pre>
     */
    private void reallyGlob(
        File base,
        boolean baseIsDirectory,
        String[] patternParts,
        int idx,
        boolean excludeDirectories,
        Collection<File> results,
        Cache<String, Pattern> cache,
        Predicate<File> dirPred)
        throws IOException {
      ProgressManager.checkCanceled();
      if (baseIsDirectory && !dirPred.test(base)) {
        return;
      }

      if (idx == patternParts.length) { // Base case.
        if (!(excludeDirectories && baseIsDirectory)) {
          results.add(base);
        }
        return;
      }

      if (!baseIsDirectory) {
        // Nothing to find here.
        return;
      }

      final String pattern = patternParts[idx];

      // ** is special: it can match nothing at all.
      // For example, x/** matches x, **/y matches y, and x/**/y matches x/y.
      if ("**".equals(pattern)) {
        queueGlob(
            base,
            baseIsDirectory,
            patternParts,
            idx + 1,
            excludeDirectories,
            results,
            cache,
            dirPred);
      }

      if (!pattern.contains("*") && !pattern.contains("?")) {
        // We do not need to do a readdir in this case, just a stat.
        File child = new File(base, pattern);
        boolean childIsDir = fileOperationProvider.isDirectory(child);
        if (!childIsDir && !fileOperationProvider.isFile(child)) {
          // The file is a dangling symlink, fifo, does not exist, etc.
          return;
        }

        queueGlob(
            child, childIsDir, patternParts, idx + 1, excludeDirectories, results, cache, dirPred);
        return;
      }

      File[] children = getChildren(base);
      if (children == null) {
        return;
      }
      for (File child : children) {
        boolean childIsDir = fileOperationProvider.isDirectory(child);

        if ("**".equals(pattern)) {
          // Recurse without shifting the pattern.
          if (childIsDir) {
            queueGlob(
                child, childIsDir, patternParts, idx, excludeDirectories, results, cache, dirPred);
          }
        }
        if (matches(pattern, child.getName(), cache)) {
          // Recurse and consume one segment of the pattern.
          if (childIsDir) {
            queueGlob(
                child,
                childIsDir,
                patternParts,
                idx + 1,
                excludeDirectories,
                results,
                cache,
                dirPred);
          } else {
            // Instead of using an async call, just repeat the base case above.
            if (idx + 1 == patternParts.length) {
              results.add(child);
            }
          }
        }
      }
    }

    @Nullable
    private File[] getChildren(File file) {
      return FileOperationProvider.getInstance().listFiles(file);
    }
  }
}
