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
package com.android.tools.idea.gradle.dsl.parser;

import com.android.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.parser.PsiElements.getUnquotedText;
import static com.google.common.base.Strings.emptyToNull;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;

/**
 * A Gradle external dependency. There are two notations supported for declaring a dependency on an external module. One is a string
 * notation formatted this way:
 * <pre>
 * configurationName "group:name:version:classifier@extension"
 * </pre>
 * The other is a map notation:
 * <pre>
 * configurationName group: group:, name: name, version: version, classifier: classifier, ext: extension
 * </pre>
 * For more details, visit:
 * <ol>
 * <li><a href="https://docs.gradle.org/2.4/userguide/dependency_management.html">Gradle Dependency Management</a></li>
 * <li><a href="https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.DependencyHandler.html">Gradle
 * DependencyHandler</a></li>
 * </ol>
 */
public class ExternalDependencyModel extends AbstractDependencyModel {
  @NotNull private final Notation myNotation;

  @Nullable private String myNewVersion;

  @Nullable
  static ExternalDependencyModel withCompactNotation(@NotNull DependenciesModel parent,
                                                     @NotNull String configurationName,
                                                     @NotNull GrLiteral literal) {
    Notation notation = CompactNotation.parse(literal);
    if (notation != null) {
      return new ExternalDependencyModel(parent, configurationName, notation);
    }
    return null;
  }

  @Nullable
  static ExternalDependencyModel withMapNotation(@NotNull DependenciesModel parent,
                                                 @NotNull String configurationName,
                                                 @NotNull GrNamedArgument[] namedArguments) {
    Notation notation = MapNotation.parse(namedArguments);
    if (notation != null) {
      return new ExternalDependencyModel(parent, configurationName, notation);
    }
    return null;
  }

  private ExternalDependencyModel(@NotNull DependenciesModel parent, @NotNull String configurationName, @NotNull Notation notation) {
    super(parent, configurationName);
    myNotation = notation;
  }

  @Nullable
  public String getGroup() {
    return myNotation.getSpec().group;
  }

  @NotNull
  public String getName() {
    return myNotation.getSpec().name;
  }

  @Nullable
  public String getVersion() {
    return myNotation.getSpec().version;
  }

  public void setVersion(@NotNull String version) {
    myNewVersion = version;
    setModified(true);
  }

  @Nullable
  public String getClassifier() {
    return myNotation.getSpec().classifier;
  }

  @Nullable
  public String getExtension() {
    return myNotation.getSpec().extension;
  }

  @Override
  public String toString() {
    return "ExternalDependencyElement{" +
           "configurationName='" + getConfigurationName() + '\'' +
           ", spec='" + myNotation.getSpec() + '\'' +
           '}';
  }

  @Override
  protected void apply() {
    applyVersionChange();
  }

  private void applyVersionChange() {
    if (myNewVersion == null) {
      return;
    }
    myNotation.setVersion(myNewVersion);
  }

  private interface Notation {
    @NotNull
    DependencySpec getSpec();

    void setVersion(@NotNull String version);
  }

  @VisibleForTesting
  static class CompactNotation implements Notation {
    @NotNull private final GrLiteral myLiteral;
    @NotNull private final DependencySpec mySpec;

    @Nullable
    static CompactNotation parse(@NotNull GrLiteral literal) {
      String text = getUnquotedText(literal);
      if (text != null) {
        DependencySpec spec = parse(text);
        if (spec != null) {
          return new CompactNotation(literal, spec);
        }
      }
      return null;
    }

    @VisibleForTesting
    @Nullable
    static DependencySpec parse(@NotNull String text) {
      // Example: org.gradle.test.classifiers:service:1.0:jdk15@jar where
      //   group: org.gradle.test.classifiers
      //   name: service
      //   version: 1.0
      //   classifier: jdk15
      //   extension: jar
      List<String> segments = Splitter.on(':').trimResults().omitEmptyStrings().splitToList(text);
      int segmentCount = segments.size();
      if (segmentCount > 0) {
        segments = Lists.newArrayList(segments);
        String lastSegment = segments.remove(segmentCount - 1);
        String extension = null;
        int indexOfAt = lastSegment.indexOf('@');
        if (indexOfAt != -1) {
          extension = lastSegment.substring(indexOfAt + 1, lastSegment.length());
          lastSegment = lastSegment.substring(0, indexOfAt);
        }
        segments.add(lastSegment);
        segmentCount = segments.size();

        String group = null;
        String name = null;
        String version = null;
        String classifier = null;

        if (segmentCount == 1) {
          name = segments.get(0);
        }
        else if (segmentCount == 2) {
          if (!lastSegment.isEmpty() && Character.isDigit(lastSegment.charAt(0))) {
            name = segments.get(0);
            version = lastSegment;
          }
          else {
            group = segments.get(0);
            name = segments.get(1);
          }
        }
        else if (segmentCount == 3 || segmentCount == 4) {
          group = segments.get(0);
          name = segments.get(1);
          version = segments.get(2);
          if (segmentCount == 4) {
            classifier = segments.get(3);
          }
        }
        if (isNotEmpty(name)) {
          return new DependencySpec(name, group, version, classifier, extension);
        }
      }
      return null;
    }

    private CompactNotation(@NotNull GrLiteral literal, @NotNull DependencySpec spec) {
      myLiteral = literal;
      mySpec = spec;
    }

    @Override
    @NotNull
    public DependencySpec getSpec() {
      return mySpec;
    }

    @Override
    public void setVersion(@NotNull String version) {
      Project project = myLiteral.getProject();
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      mySpec.version = version;
      GrLiteral newCoordinatePsiLiteral = factory.createLiteralFromValue(mySpec.toString());

      myLiteral.replace(newCoordinatePsiLiteral);
    }
  }

  @VisibleForTesting
  static class MapNotation implements Notation {
    @NonNls private static final String VERSION_PROPERTY = "version";

    @NotNull private final Map<String, GrLiteral> myArgumentsByName;
    @NotNull private final DependencySpec mySpec;

    @Nullable
    static MapNotation parse(@NotNull GrNamedArgument[] namedArguments) {
      Map<String, GrLiteral> argumentsByName = Maps.newHashMap();
      Map<String, String> argumentValuesByName = Maps.newHashMap();
      for (GrNamedArgument argument : namedArguments) {
        GrLiteral literal = getChildOfType(argument, GrLiteral.class);
        if (literal != null) {
          String name = argument.getLabelName();
          argumentsByName.put(name, literal);
          argumentValuesByName.put(name, getUnquotedText(literal));
        }
      }
      DependencySpec spec = parse(argumentValuesByName);
      if (spec != null) {
        return new MapNotation(argumentsByName, spec);
      }
      return null;
    }

    @VisibleForTesting
    @Nullable
    static DependencySpec parse(@NotNull Map<String, String> namedArguments) {
      String name = namedArguments.get("name");
      if (isNotEmpty(name)) {
        return new DependencySpec(name, namedArguments.get("group"), namedArguments.get(VERSION_PROPERTY), namedArguments.get("classifier"),
                                  namedArguments.get("ext"));
      }
      return null;
    }

    private MapNotation(@NotNull Map<String, GrLiteral> argumentsByName, @NotNull DependencySpec spec) {
      myArgumentsByName = argumentsByName;
      mySpec = spec;
    }

    @Override
    @NotNull
    public DependencySpec getSpec() {
      return mySpec;
    }

    @Override
    public void setVersion(@NotNull String version) {
      GrLiteral literal = myArgumentsByName.get(VERSION_PROPERTY);
      if (literal != null) {
        Project project = literal.getProject();
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

        mySpec.version = version;
        GrLiteral newCoordinatePsiLiteral = factory.createLiteralFromValue(version);

        literal.replace(newCoordinatePsiLiteral);
      }
      // TODO handle case where 'version' property is not defined, and needs to be added.
    }
  }

  @VisibleForTesting
  static class DependencySpec {
    @NotNull String name;

    @Nullable String group;
    @Nullable String version;
    @Nullable String classifier;
    @Nullable String extension;

    @VisibleForTesting
    DependencySpec(@NotNull String name,
                   @Nullable String group,
                   @Nullable String version,
                   @Nullable String classifier,
                   @Nullable String extension) {
      this.name = name;
      this.group = emptyToNull(group);
      this.version = emptyToNull(version);
      this.classifier = emptyToNull(classifier);
      this.extension = emptyToNull(extension);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      DependencySpec that = (DependencySpec)o;

      return Objects.equal(name, that.name) &&
             Objects.equal(group, that.group) &&
             Objects.equal(version, that.version) &&
             Objects.equal(classifier, that.classifier) &&
             Objects.equal(extension, that.extension);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name, group, version, classifier, extension);
    }

    @Override
    public String toString() {
      List<String> segments = Lists.newArrayList(group, name, version, classifier);
      String s = Joiner.on(':').skipNulls().join(segments);
      if (extension != null) {
        s += "@" + extension;
      }
      return s;
    }
  }
}
