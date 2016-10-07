/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.testing;

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SubjectFactory;

import javax.annotation.Nullable;
import java.io.File;

/** Propositions for {@link File} subjects. */
public class FileSubject extends ComparableSubject<FileSubject, File> {
  private static final SubjectFactory<FileSubject, File> FILE =
    new SubjectFactory<FileSubject, File>() {
      @Override
      public FileSubject getSubject(FailureStrategy fs, File file) {
        return new FileSubject(fs, file);
      }
    };

  public static SubjectFactory<FileSubject, File> file() {
    return FILE;
  }

  public FileSubject(FailureStrategy failureStrategy, @Nullable File file) {
    super(failureStrategy, file);
  }

  /** Fails if the subject {@link File#exists exists}. */
  public void doesNotExist() {
    if (getSubject().exists()) {
      fail("does not exist");
    }
  }

  /** Fails if the subject {@link File#isDirectory is not a directory}. */
  public void isDirectory() {
    if (!getSubject().isDirectory()) {
      fail("is a directory");
    }
  }

  /** Fails if the subject {@link File#isFile is not a normal file}. */
  public void isFile() {
    if (!getSubject().isFile()) {
      fail("is a normal file");
    }
  }
}
