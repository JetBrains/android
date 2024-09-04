/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.logging.utils;

import com.google.auto.value.AutoValue;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;

/** Highlight Information. See {@link HighlightStats} for more information */
@AutoValue
public abstract class HighlightInfo {
  /** Supported HighlightInfo Types */
  public enum Type {
    WRONG_REF,
    UNUSED_SYMBOL,
    DEPRECATED,
    MARKED_FOR_REMOVAL
  }

  /** Supported HighlightInfo Severities */
  public enum Severity {
    INFORMATION,
    WEAK_WARNING,
    GENERIC_SERVER_ERROR_OR_WARNING,
    WARNING,
    ERROR
  }

  public abstract String text();

  public abstract Severity severity();

  public abstract Type type();

  public abstract int startOffset();

  public abstract int endOffset();

  public static Builder builder() {
    return new AutoValue_HighlightInfo.Builder();
  }

  /** Builder for {@link HighlightInfo} */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setText(String value);

    public abstract Builder setSeverity(Severity value);

    public abstract Builder setType(Type type);

    public abstract Builder setStartOffset(int value);

    public abstract Builder setEndOffset(int value);

    public abstract HighlightInfo build();
  }

  public static Type convertHighlightInfoType(HighlightInfoType type) {
    if (HighlightInfoType.WRONG_REF.equals(type)) {
      return Type.WRONG_REF;
    } else if (HighlightInfoType.UNUSED_SYMBOL.equals(type)) {
      return Type.UNUSED_SYMBOL;
    } else if (HighlightInfoType.DEPRECATED.equals(type)) {
      return Type.DEPRECATED;
    } else if (HighlightInfoType.MARKED_FOR_REMOVAL.equals(type)) {
      return Type.MARKED_FOR_REMOVAL;
    }
    throw new IllegalArgumentException("Unhandled HighlightInfoType: " + type);
  }

  public static Severity convertHighlightSeverity(HighlightSeverity severity) {
    if (HighlightSeverity.INFORMATION.equals(severity)) {
      return Severity.INFORMATION;
    } else if (HighlightSeverity.WEAK_WARNING.equals(severity)) {
      return Severity.WEAK_WARNING;
    } else if (HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING.equals(severity)) {
      return Severity.GENERIC_SERVER_ERROR_OR_WARNING;
    } else if (HighlightSeverity.WARNING.equals(severity)) {
      return Severity.WARNING;
    } else if (HighlightSeverity.ERROR.equals(severity)) {
      return Severity.ERROR;
    }
    throw new IllegalArgumentException("Unhandled HighlightSeverity: " + severity);
  }
}
