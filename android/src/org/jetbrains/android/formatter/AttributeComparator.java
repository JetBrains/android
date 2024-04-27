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
package org.jetbrains.android.formatter;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * An Android XML attribute comparator.
 *
 * @param <A> an arbitrary attribute type. The local parts of the attribute names are retrieved with the Function argument passed to the
 *            constructor.
 */
public final class AttributeComparator<A> implements Comparator<A> {
  private final Function<A, String> myGetLocalPart;

  /**
   * @param getLocalPart returns the local part of a qualified attribute name
   */
  public AttributeComparator(@NotNull Function<A, String> getLocalPart) {
    myGetLocalPart = getLocalPart;
  }

  @Override
  public int compare(A attribute1, A attribute2) {
    return new Attribute(myGetLocalPart.apply(attribute1)).compareTo(new Attribute(myGetLocalPart.apply(attribute2)));
  }

  private static final class Attribute implements Comparable<Attribute> {
    private static final Comparator<Attribute> COMPARATOR = Comparator.<Attribute, Type>comparing(attribute -> attribute.myType)
      .thenComparing(attribute -> attribute.myLocalPartWithoutSpecifier)
      .thenComparing(attribute -> attribute.mySpecifier);

    private final Type myType;
    private final String myLocalPartWithoutSpecifier;
    private final Specifier mySpecifier;

    private Attribute(@NotNull String localPart) {
      myType = Type.get(localPart);

      Specifier specifier = Specifier.get(localPart);
      myLocalPartWithoutSpecifier = specifier.removeFrom(localPart);
      mySpecifier = specifier;
    }

    @Override
    public int compareTo(@NotNull Attribute attribute) {
      return COMPARATOR.compare(this, attribute);
    }
  }

  private enum Type {
    LAYOUT_WIDTH {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.equals("layout_width");
      }
    },

    LAYOUT_HEIGHT {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.equals("layout_height");
      }
    },

    LAYOUT_ROW {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.startsWith("layout_row");
      }
    },

    LAYOUT_COLUMN {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.startsWith("layout_column");
      }
    },

    LAYOUT_ALIGN_WITH_PARENT_IF_MISSING {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.equals("layout_alignWithParentIfMissing");
      }
    },

    LAYOUT_ABOVE {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.equals("layout_above");
      }
    },

    LAYOUT_BELOW {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.equals("layout_below");
      }
    },

    LAYOUT {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.startsWith("layout_");
      }
    },

    WIDTH {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.equals("width");
      }
    },

    HEIGHT {
      @Override
      boolean matches(@NotNull String localPart) {
        return localPart.equals("height");
      }
    },

    NON_LAYOUT {
      @Override
      boolean matches(@NotNull String localPart) {
        throw new UnsupportedOperationException();
      }
    };

    private static final Collection<Type> TYPES = Sets.immutableEnumSet(EnumSet.complementOf(EnumSet.of(NON_LAYOUT)));

    @NotNull
    private static Type get(@NotNull String localPart) {
      Optional<Type> optionalType = TYPES.stream()
        .filter(type -> type.matches(localPart))
        .findFirst();

      return optionalType.orElse(NON_LAYOUT);
    }

    abstract boolean matches(@NotNull String localPart);
  }

  private enum Specifier {
    NULL("") {
      @NotNull
      @Override
      String removeFrom(@NotNull String localPart) {
        return localPart;
      }
    },

    WIDTH("Width"),
    HEIGHT("Height"),
    IN_PARENT("InParent"),
    HORIZONTAL("Horizontal"),
    VERTICAL("Vertical"),
    BASELINE("Baseline"),
    START("Start"),
    LEFT("Left"),
    TOP("Top"),
    END("End"),
    RIGHT("Right"),
    BOTTOM("Bottom"),
    UP("Up"),
    DOWN("Down");

    private static final Collection<Specifier> SPECIFIERS = Sets.immutableEnumSet(EnumSet.complementOf(EnumSet.of(NULL)));
    private final Pattern myPattern;

    Specifier(@NotNull String name) {
      myPattern = Pattern.compile(name, Pattern.LITERAL);
    }

    @NotNull
    private static Specifier get(@NotNull String localPart) {
      Optional<Specifier> optionalSpecifier = SPECIFIERS.stream()
        .filter(specifier -> localPart.contains(specifier.myPattern.toString()))
        .findFirst();

      return optionalSpecifier.orElse(NULL);
    }

    @NotNull
    String removeFrom(@NotNull String localPart) {
      return myPattern.matcher(localPart).replaceFirst("");
    }
  }
}
