/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ui.validation.validators;

import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.ui.validation.Validator;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import net.jcip.annotations.Immutable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A class which contains validation logic that should be run on a path to ensure that there won't
 * be any problems with using it. If there is an issue, we offer a readable string which we can
 * show to the user.
 * <p/>
 * Use {@link #validate(File)} to test a path and check {@link Result#getSeverity()} to see if
 * there was a warning or error.
 * <p/>
 * The convenience method {@link #createDefault(String)} is provided for creating a validator for
 * the most common cases.
 * <p/>
 * This class is immutable.
 */
@Immutable
public final class PathValidator implements Validator<File> {

  public static final Rule IS_EMPTY = new SimpleRule() {
    @Override
    public boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return file.getName().isEmpty();
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("Please specify a %1$s.", fieldName);
    }
  };

  public static final Rule INVALID_SLASHES = new SimpleRule() {
    @Override
    public boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      String path = file.getPath();
      return (File.separatorChar == '/' && path.contains("\\")) || (File.separatorChar == '\\' && path.contains("/"));
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      if (File.separatorChar == '\\') {
        return String.format("Your %1$s contains incorrect slashes ('/').", fieldName);
      }
      else {
        return String.format("Your %1$s contains incorrect slashes ('\\').", fieldName);
      }
    }
  };

  public static final Rule WHITESPACE = new RecursiveRule() {
    @Override
    public boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return CharMatcher.WHITESPACE.matchesAnyOf(file.getName());
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("%1$s should not contain whitespace, as this can cause problems with the NDK tools.", fieldName);
    }
  };

  public static final Rule NON_ASCII_CHARS = new RecursiveRule() {
    @Override
    public boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return !CharMatcher.ASCII.matchesAllOf(file.getName());
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("Your %1$s contains non-ASCII characters.", fieldName);
    }
  };

  public static final Rule PARENT_DIRECTORY_NOT_WRITABLE = new RecursiveRule() {
    @Override
    protected boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      File parent = file.getParentFile();
      return !fileOp.exists(file) && parent != null && fileOp.exists(parent) && !fileOp.canWrite(parent);
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("The path '%1$s' is not writable. Please choose a new location.", file.getParentFile().getPath());
    }
  };

  public static final Rule LOCATION_IS_A_FILE = new SimpleRule() {
    @Override
    protected boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return fileOp.isFile(file);
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("The %1$s specified already exists.", fieldName);
    }
  };

  public static final Rule LOCATION_IS_ROOT = new SimpleRule() {
    @Override
    protected boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return file.getParent() == null;
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("The %1$s cannot be at the filesystem root.", fieldName);
    }
  };

  public static final Rule PARENT_IS_NOT_A_DIRECTORY = new SimpleRule() {
    @Override
    protected boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      File parent = file.getParentFile();
      return parent != null && fileOp.exists(parent) && !fileOp.isDirectory(parent);
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("The %1$s's parent must be a directory, not a plain file.", fieldName);
    }
  };

  public static final Rule PATH_NOT_WRITABLE = new SimpleRule() {
    @Override
    protected boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return fileOp.exists(file) && !fileOp.canWrite(file);
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("The path '%1$s' is not writable. Please choose a new location.", file.getPath());
    }
  };

  public static final Rule PATH_INSIDE_ANDROID_STUDIO = new SimpleRule() {
    @Override
    protected boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      String installLocation = PathManager.getHomePathFor(Application.class);
      return installLocation != null && FileUtil.isAncestor(new File(installLocation), file, false);
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      String applicationName = ApplicationNamesInfo.getInstance().getProductName();
      return String.format("The %1$s is inside %2$s's install location.", fieldName, applicationName);
    }
  };

  public static final Rule NON_EMPTY_DIRECTORY = new SimpleRule() {
    @Override
    protected boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return fileOp.listFiles(file).length > 0;
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("A non-empty directory already exists at the specified %1$s.", fieldName);
    }
  };

  public static final Rule ILLEGAL_FILENAME = new RecursiveRule() {
    @SuppressWarnings("SpellCheckingInspection")
    private final Set<String> RESERVED_WINDOWS_FILENAMES = ImmutableSet
      .of("con", "prn", "aux", "clock$", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2",
          "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "$mft", "$mftmirr", "$logfile", "$volume", "$attrdef", "$bitmap", "$boot",
          "$badclus", "$secure", "$upcase", "$extend", "$quota", "$objid", "$reparse");

    @Override
    public boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return fileOp.isWindows() && RESERVED_WINDOWS_FILENAMES.contains(file.getName().toLowerCase(Locale.US));
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("Illegal filename in %1$s path: %2$s.", fieldName, file.getName());
    }
  };

  public static final Rule PATH_TOO_LONG = new SimpleRule() {
    private static final int WINDOWS_PATH_LENGTH_LIMIT = 100;

    @Override
    protected boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return file.getAbsolutePath().length() > WINDOWS_PATH_LENGTH_LIMIT;
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      return String.format("The length of the %1$s exceeds the limit of %2$d characters.", fieldName, WINDOWS_PATH_LENGTH_LIMIT);
    }
  };

  public static final Rule ILLEGAL_CHARACTER = new RecursiveRule() {
    private final CharMatcher ILLEGAL_CHARACTER_MATCHER = CharMatcher.anyOf("[/\\\\?%*:|\"<>!;]");

    @Override
    public boolean matches(@NotNull FileOp fileOp, @NotNull File file) {
      return ILLEGAL_CHARACTER_MATCHER.matchesAnyOf(file.getName());
    }

    @NotNull
    @Override
    public String getMessage(@NotNull File file, @NotNull String fieldName) {
      String name = file.getName();
      char illegalChar = name.charAt(ILLEGAL_CHARACTER_MATCHER.indexIn(name));
      return String.format("Illegal character in %1$s path: '%2$c' in filename %3$s.", fieldName, illegalChar, name);
    }
  };

  @NotNull private final String myPathName;
  @NotNull private final Iterable<Rule> myErrors;
  @NotNull private final Iterable<Rule> myWarnings;
  @NotNull private final FileOp myFileOp;

  /**
   * Constructs a class that will validate a path against the various passed in rules, returning
   * a readable message if something goes wrong. A name describing the purpose of the path should
   * be included as it will be used in the error messages when applicable.
   */
  private PathValidator(@NotNull String pathName, @NotNull Iterable<Rule> errors, @NotNull Iterable<Rule> warnings, @NotNull FileOp fileOp) {
    myPathName = pathName;
    myErrors = errors;
    myWarnings = warnings;
    myFileOp = fileOp;
  }

  /**
   * A validator that provides reasonable defaults for checking a path's validity.
   */
  public static PathValidator createDefault(@NotNull String pathName) {
    return new Builder().withAllRules(Severity.ERROR).build(pathName);
  }

  /**
   * Validate that the target location passes all tests.
   *
   * @return {@link Result#OK} or the first error or warning it encounters.
   */
  @NotNull
  @Override
  public Result validate(@NotNull File file) {
    Result result = validate(file, Severity.ERROR);
    if (result != Result.OK) {
      return result;
    }

    result = validate(file, Severity.WARNING);
    if (result != Result.OK) {
      return result;
    }

    return Result.OK;
  }

  /**
   * Run only the validations whose level match the passed in {@link Severity}.
   */
  @NotNull
  private Result validate(@NotNull File projectFile, @NotNull Severity severity) {
    assert severity != Severity.OK;
    Iterable<Rule> rules = (severity == Severity.ERROR ? myErrors : myWarnings);

    for (Rule rule : rules) {
      File matchingFile = rule.getMatchingFile(myFileOp, projectFile);
      if (matchingFile != null) {
        return new Result(severity, rule.getMessage(matchingFile, myPathName));
      }
    }

    return Result.OK;
  }

  /**
   * A single validation rule which tests a target file to see if it violates it.
   */
  public interface Rule {
    /**
     * Returns a {@link File} which violates this rule or {@code null} if none.
     * <p/>
     * If a file is returned, it will usually be the same as {@code file} but not
     * always - for example, a rule may complain about a file's parent.
     */
    @Nullable
    File getMatchingFile(@NotNull FileOp fileOp, @NotNull File file);

    @NotNull
    String getMessage(@NotNull File file, @NotNull String fieldName);
  }

  public static class Builder {
    private final List<Rule> myErrors = Lists.newArrayList();
    private final List<Rule> myWarnings = Lists.newArrayList();

    /**
     * Useful for creating a {@link PathValidator} with all rules enforced.
     */
    @NotNull
    public Builder withAllRules(@NotNull Severity pathNotWritable) {
      withRule(IS_EMPTY, Severity.ERROR);
      withRule(PATH_NOT_WRITABLE, pathNotWritable);
      withRule(NON_EMPTY_DIRECTORY, Severity.WARNING);
      return withCommonRules();
    }

    /**
     * Useful for creating a {@link PathValidator} with most rules enforced.
     */
    @NotNull
    public Builder withCommonRules() {
      withRule(INVALID_SLASHES, Severity.ERROR);
      withRule(ILLEGAL_CHARACTER, Severity.ERROR);
      withRule(ILLEGAL_FILENAME, Severity.ERROR);
      withRule(WHITESPACE, Severity.WARNING);
      withRule(NON_ASCII_CHARS, SystemInfo.isWindows ? Severity.ERROR : Severity.WARNING);
      withRule(PARENT_DIRECTORY_NOT_WRITABLE, Severity.ERROR);
      withRule(PATH_TOO_LONG, Severity.ERROR);
      withRule(LOCATION_IS_A_FILE, Severity.ERROR);
      withRule(LOCATION_IS_ROOT, Severity.ERROR);
      withRule(PARENT_IS_NOT_A_DIRECTORY, Severity.ERROR);
      withRule(PATH_INSIDE_ANDROID_STUDIO, Severity.ERROR);
      return this;
    }

    /**
     * Add a {@link Rule} individually, in case {@link #withCommonRules()} is too aggressive for
     * your use-case, or if you want to add a custom one-off rule.
     */
    public Builder withRule(@NotNull Rule rule, @NotNull Severity severity) {
      switch (severity) {
        case ERROR:
          myErrors.add(rule);
          break;
        case WARNING:
          myWarnings.add(rule);
        break;
        default:
          throw new IllegalArgumentException(String.format("Can't create rule with invalid severity %1$s", severity));
      }
      return this;
    }

    @NotNull
    public PathValidator build(@NotNull String pathName) {
      return build(pathName, FileOpUtils.create());
    }

    @NotNull
    public PathValidator build(@NotNull String pathName, @NotNull FileOp fileOp) {
      return new PathValidator(pathName, myErrors, myWarnings, fileOp);
    }
  }

  /**
   * A rule which is run on the target file as is.
   */
  private static abstract class SimpleRule implements Rule {
    @Nullable
    @Override
    public final File getMatchingFile(@NotNull FileOp fileOp, @NotNull File file) {
      return matches(fileOp, file) ? file : null;
    }

    protected abstract boolean matches(@NotNull FileOp fileOp, @NotNull File file);
  }

  /**
   * A rule which is run on the target file and each of its ancestors, recursively.
   */
  private static abstract class RecursiveRule implements Rule {
    @Nullable
    @Override
    public final File getMatchingFile(@NotNull FileOp fileOp, @NotNull File file) {
      File currFile = file;
      while (currFile != null) {
        if (matches(fileOp, currFile)) {
          return currFile;
        }
        currFile = currFile.getParentFile();
      }

      return null;
    }

    protected abstract boolean matches(@NotNull FileOp fileOp, @NotNull File file);
  }
}

