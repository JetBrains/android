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
package org.jetbrains.android.refactoring;

import com.android.annotations.NonNull;
import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.SmartHashSet;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ChangeXmlAttrUsageInfo;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ChangeXmlAttrValueUsageInfo;
import org.jetbrains.android.refactoring.MigrateToAppCompatUsageInfo.ChangeXmlTagUsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * An entry encapsulating a single migration to AppCompat.
 * For example for an attribute migration, one would use the {@link AttributeMigrationEntry}
 * which contains the from and to attribute names along with the namespaces if any to be migrated.
 * Similarly, {@link ClassMigrationEntry} contains the old and the new classnames for the migration
 * to be deemed complete.
 *
 * @see MigrateToAppCompatProcessor
 */
public class AppCompatMigrationEntry {
  /**
   * AppCompatMigrationEntry for denoting a class change.
   */
  public static final int CHANGE_CLASS = 1;
  /**
   * MigrationMapEntry denoting a method change
   */
  public static final int CHANGE_METHOD = 2;
  /**
   * MigrationMapEntry denoting a theme change to AppCompat.
   */
  public static final int CHANGE_THEME_AND_STYLE = 3;
  /**
   * MigrationMapEntry denoting a custom view superclass change to the AppCompat version.
   */
  public static final int CHANGE_CUSTOM_VIEW_SUPERCLASS = 4;
  /**
   * Entry denoting a method replacement.
   * For example replace item.getActionProvider() => MenuItemCompat.getActionProvider(item)
   */
  public static final int REPLACE_METHOD = 5;
  /**
   * Entry denoting a layout tag name change
   * for e.g: android.widget.Toolbar => android.support.v7.widget.Toolbar
   */
  public static final int CHANGE_TAG = 6;
  /**
   * Entry denoting an Xml attribute name change.
   */
  public static final int CHANGE_ATTR = 7;
  /**
   * Entry denoting an Xml attribute value change.
   */
  public static final int CHANGE_ATTR_VALUE = 8;

  /**
   * Entry denoting a package name change
   */
  public static final int CHANGE_PACKAGE = 9;

  public static final int CHANGE_GRADLE_DEPENDENCY = 10;

  /**
   * Entry denoting a package version update (no group or artifact name change)
   */
  public static final int UPGRADE_GRADLE_DEPENDENCY_VERSION = 11;

  @MagicConstant(valuesFromClass = AppCompatMigrationEntry.class)
  protected final int myType;

  public AppCompatMigrationEntry(@MagicConstant(valuesFromClass = AppCompatMigrationEntry.class) int type) {
    myType = type;
  }

  @MagicConstant(valuesFromClass = AppCompatMigrationEntry.class)
  public int getType() {
    return myType;
  }

  /**
   * A base class for all Xml specific migrations such as tag, attribute
   * and attribute value migrations.
   */
  static abstract class XmlElementMigration extends AppCompatMigrationEntry {
    // Flags for attribute
    public static final int FLAG_LAYOUT = 0x01;
    public static final int FLAG_MENU = 0x02;
    public static final int FLAG_STYLE = 0x04;

    public XmlElementMigration(@MagicConstant(valuesFromClass = AppCompatMigrationEntry.class) int type) {
      super(type);
    }

    public abstract int getFlags();
    public abstract Set<String> applicableTagNames();
    public abstract UsageInfo apply(@NonNull XmlTag tag);

    public boolean isMenuOperation() {
      return (getFlags() & FLAG_MENU) == FLAG_MENU;
    }

    public boolean isLayoutOperation() {
      return (getFlags() & FLAG_LAYOUT) == FLAG_LAYOUT;
    }

    public boolean isStyleOperation() {
      return (getFlags() & FLAG_STYLE) == FLAG_STYLE;
    }
  }

  static class XmlTagMigrationEntry extends XmlElementMigration {
    final String myOldTagName;
    final String myOldNamespace;
    final String myNewTagName;
    final String myNewNamespace;
    final int myFlags;

    public XmlTagMigrationEntry(@NonNull String oldTagName,
                                @Nullable String oldNamespace,
                                @NonNull String newTagName,
                                @Nullable String newNamespace,
                                int flags) {
      super(CHANGE_TAG);
      myOldTagName = oldTagName;
      myOldNamespace = oldNamespace;
      myNewTagName = newTagName;
      myNewNamespace = newNamespace;
      myFlags = flags;
    }

    @Override
    public int getFlags() {
      return myFlags;
    }

    @Override
    public Set<String> applicableTagNames() {
      return Sets.newHashSet(myOldTagName);
    }

    @Override
    public UsageInfo apply(@NonNull XmlTag tag) {
      if (StringUtil.equals(tag.getLocalName(), myOldTagName)
          && StringUtil.equals(tag.getNamespace(), myOldNamespace)) {
        return new ChangeXmlTagUsageInfo(tag, this);
      }
      return null;
    }
  }

  static class AttributeMigrationEntry extends XmlElementMigration {
    @NonNull final String myOldAttributeName;
    @NonNull final String myNewAttributeName;
    @NonNull final String myNewNamespace;
    @NonNull final String myOldNamespace;
    private final int myFlags;
    @NonNull private final Set<String> myTagNames;

    public AttributeMigrationEntry(@NonNull String oldAttributeName,
                                   @NonNull String oldNamespace,
                                   @NonNull String newAttributeName,
                                   @NonNull String newNamespace,
                                   int flags,
                                   @NonNull String... tagNames) {
      super(CHANGE_ATTR);
      myOldAttributeName = oldAttributeName;
      myOldNamespace = oldNamespace;
      myNewAttributeName = newAttributeName;
      myNewNamespace = newNamespace;
      myFlags = flags;
      myTagNames = new SmartHashSet<>(Arrays.asList(tagNames));
    }

    @Override
    public int getFlags() {
      return myFlags;
    }

    @Override
    public Set<String> applicableTagNames() {
      return myTagNames;
    }

    @Override
    public UsageInfo apply(@NonNull XmlTag tag) {
      if (myTagNames.contains(tag.getName())) {
        XmlAttribute attr = tag.getAttribute(myOldAttributeName, myOldNamespace);
        if (attr != null) {
          return new ChangeXmlAttrUsageInfo(attr.getNameElement(), this);
        }
      }
      return null;
    }
  }

  static class AttributeValueMigrationEntry extends XmlElementMigration {

    final String myOldAttrValue;
    final String myNewAttrValue;
    final String myAttributeName;
    final String myNamespace;
    final int myFlags;
    final Set<String> myTagNames;

    public AttributeValueMigrationEntry(@NonNull String oldAttrValue,
                                        @NonNull String newAttrValue,
                                        @NonNull String attributeName,
                                        @NonNull String namespace,
                                        int flags,
                                        @NonNull String... tagNames) {
      super(CHANGE_ATTR_VALUE);
      myOldAttrValue = oldAttrValue;
      myNewAttrValue = newAttrValue;
      myAttributeName = attributeName;
      myNamespace = namespace;
      myFlags = flags;
      myTagNames = new SmartHashSet<>(Arrays.asList(tagNames));
    }

    @Override
    public int getFlags() {
      return myFlags;
    }

    @Override
    public Set<String> applicableTagNames() {
      return myTagNames;
    }

    @Override
    public UsageInfo apply(@NonNull XmlTag tag) {
      if (myTagNames.contains(tag.getName())) {
        XmlAttribute attr = tag.getAttribute(myAttributeName, myNamespace);
        if (attr != null && StringUtil.equals(myOldAttrValue, attr.getValue())) {
          //noinspection ConstantConditions
          return new ChangeXmlAttrValueUsageInfo(attr.getValueElement(), this);
        }
      }
      return null;
    }
  }

  static class ClassMigrationEntry extends AppCompatMigrationEntry {
    @NonNull final String myOldName;
    @NonNull final String myNewName;

    public ClassMigrationEntry(@NotNull String oldName, @NotNull String newName) {
      super(CHANGE_CLASS);
      myOldName = oldName;
      myNewName = newName;
    }
  }

  static class PackageMigrationEntry extends AppCompatMigrationEntry {
    @NonNull final String myOldName;
    @NonNull final String myNewName;

    public PackageMigrationEntry(@NotNull String oldName, @NotNull String newName) {
      super(CHANGE_PACKAGE);
      myOldName = oldName;
      myNewName = newName;
    }
  }

  static class MethodMigrationEntry extends AppCompatMigrationEntry {

    @NonNull final String myOldClassName;
    @NonNull final String myOldMethodName;

    @NonNull final String myNewMethodName;
    @NonNull final String myNewClassName;

    MethodMigrationEntry(@NonNull String oldClassName,
                         @NonNull String oldMethodName,
                         @NonNull String newClassName,
                         @NonNull String newMethodName) {
      this(CHANGE_METHOD, oldClassName, oldMethodName, newClassName, newMethodName);
    }

    protected MethodMigrationEntry(@MagicConstant(valuesFromClass = AppCompatMigrationEntry.class) int type,
                                   @NonNull String oldClassName,
                                   @NonNull String oldMethodName,
                                   @NonNull String newClassName,
                                   @NonNull String newMethodName) {
      super(type);
      myOldClassName = oldClassName;
      myOldMethodName = oldMethodName;
      myNewClassName = newClassName;
      myNewMethodName = newMethodName;
    }
  }

  static class ReplaceMethodCallMigrationEntry extends MethodMigrationEntry {
    // where to place the qualifier of the method call in
    // the resulting method call.
    final int myQualifierParamIndex;

    ReplaceMethodCallMigrationEntry(@NonNull String oldClassName,
                                    @NonNull String oldMethodName,
                                    @NonNull String newClassName,
                                    @NonNull String newMethodName,
                                    int qualifierParamIndex) {
      super(REPLACE_METHOD, oldClassName, oldMethodName, newClassName, newMethodName);
      myQualifierParamIndex = qualifierParamIndex;
    }
  }

  static abstract class GradleMigrationEntry extends AppCompatMigrationEntry {
    @NotNull private final String myNewGroupName;
    @NotNull private final String myNewArtifactName;
    @NotNull private final String myNewBaseVersion;

    public GradleMigrationEntry(@MagicConstant(valuesFromClass = AppCompatMigrationEntry.class) int type,
                                @NotNull String newGroupName,
                                @NotNull String newArtifactName,
                                @NotNull String newBaseVersion) {
      super(type);

      myNewGroupName = newGroupName;
      myNewArtifactName = newArtifactName;
      myNewBaseVersion = newBaseVersion;
    }

    @NotNull
    public abstract Pair<String, String> compactKey();

    /**
     * Returns the gradle coordinates compact notation but allows replacing the pre-defined version for this artifact with a different one.
     * The given withVersion will only be used if it's higher than this entry base version
     */
    @NotNull
    public String toCompactNotation(@NotNull String withVersion) {
      String newVersionString = getNewBaseVersion();
      GradleCoordinate newVersion = GradleCoordinate.parseVersionOnly(withVersion);
      GradleCoordinate baseVersion = GradleCoordinate.parseVersionOnly(newVersionString);

      String useVersion;
      if (GradleCoordinate.COMPARE_PLUS_HIGHER.compare(newVersion, baseVersion) < 0) {
        // The given version is lower than the base version, use the baseVersion
        useVersion = newVersionString;
      }
      else {
        useVersion = withVersion;
      }

      return new GradleCoordinate(getNewGroupName(), getNewArtifactName(), useVersion).toString();
    }

    @NotNull
    public String getNewGroupName() {
      return myNewGroupName;
    }

    @NotNull
    public String getNewArtifactName() {
      return myNewArtifactName;
    }

    @NotNull
    public String getNewBaseVersion() {
      return myNewBaseVersion;
    }
  }

  static class GradleDependencyMigrationEntry extends GradleMigrationEntry {

    @NotNull private final String myOldGroupName;
    @NotNull private final String myOldArtifactName;

    public GradleDependencyMigrationEntry(@NotNull String oldGroupName, @NotNull String oldArtifactName,
                                          @NotNull String newGroupName, @NotNull String newArtifactName, @NotNull String newBaseVersion) {
      super(CHANGE_GRADLE_DEPENDENCY, newGroupName, newArtifactName, newBaseVersion);
      myOldGroupName = oldGroupName;
      myOldArtifactName = oldArtifactName;
    }

    @Override
    @NotNull
    public Pair<String, String> compactKey() {
      return Pair.create(myOldGroupName, myOldArtifactName);
    }

    @NotNull
    public String getOldArtifactName() {
      return myOldArtifactName;
    }

    @NotNull
    public String getOldGroupName() {
      return myOldGroupName;
    }
  }

  static class UpdateGradleDepedencyVersionMigrationEntry extends GradleMigrationEntry {
    public UpdateGradleDepedencyVersionMigrationEntry(@NotNull String groupName, @NotNull String artifactName, @NotNull String newBaseVersion) {
      super(UPGRADE_GRADLE_DEPENDENCY_VERSION, groupName, artifactName, newBaseVersion);
    }

    @NotNull
    @Override
    public Pair<String, String> compactKey() {
      return Pair.create(getNewGroupName(), getNewArtifactName());
    }
  }
}
