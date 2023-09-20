/*
 * Copyright (C) 2019 The Android opening Source Project
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
package com.android.tools.idea.lang.proguardR8.parser

import com.android.tools.idea.lang.AndroidParsingTestCase
import com.android.tools.idea.lang.proguardR8.ProguardR8FileType
import com.android.tools.idea.lang.proguardR8.ProguardR8Language
import com.android.tools.idea.lang.proguardR8.ProguardR8PairedBraceMatcher
import com.intellij.lang.LanguageBraceMatching

class ProguardR8ParserTest : AndroidParsingTestCase(ProguardR8FileType.INSTANCE.defaultExtension, ProguardR8ParserDefinition()) {

  override fun setUp() {
    super.setUp()
    addExplicitExtension(LanguageBraceMatching.INSTANCE, ProguardR8Language.INSTANCE, ProguardR8PairedBraceMatcher())
  }

  fun testParse() {
    assertEquals(
      """
      FILE
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-printmapping')
          ProguardR8FlagArgumentImpl(FLAG_ARGUMENT)
            ProguardR8FileImpl(FILE)
              PsiElement(FILE_NAME)('out.map')
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassModifierImpl(CLASS_MODIFIER)
              PsiElement(public)('public')
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(asterisk)('*')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8FieldsSpecificationImpl(FIELDS_SPECIFICATION)
                ProguardR8FieldImpl(FIELD)
                  ProguardR8ModifierImpl(MODIFIER)
                    PsiElement(public)('public')
                  ProguardR8ModifierImpl(MODIFIER)
                    PsiElement(protected)('protected')
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(asterisk)('*')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keepparameternames')
          """.trimIndent(),
      toParseTreeText(
        """
          -printmapping out.map
          -keep public class * {
            public protected *;
          }
          -keepparameternames
        """.trimIndent()
      )
    )

    // few flags in the same line
    assertEquals(
      """
      FILE
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-printmapping')
          ProguardR8FlagArgumentImpl(FLAG_ARGUMENT)
            ProguardR8FileImpl(FILE)
              PsiElement(FILE_NAME)('out.map')
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-android')
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-dontpreverify')
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-repackageclasses')
      """.trimIndent(),
      toParseTreeText(
        """
          -printmapping out.map -android -dontpreverify -repackageclasses
        """.trimIndent()
      )
    )
  }

  fun testParseMethodSpecification() {
    assertEquals(
      """
      FILE
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassModifierImpl(CLASS_MODIFIER)
              PsiElement(public)('public')
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(asterisk)('*')
            PsiElement(extends)('extends')
            ProguardR8SuperClassNameImpl(SUPER_CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('android')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('view')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('View')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8ModifierImpl(MODIFIER)
                  PsiElement(public)('public')
                PsiElement(<init>)('<init>')
                ProguardR8ParametersImpl(PARAMETERS)
                  PsiElement(left parenthesis)('(')
                  ProguardR8TypeListImpl(TYPE_LIST)
                    ProguardR8TypeImpl(TYPE)
                      ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                        PsiElement(JAVA_IDENTIFIER)('android')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('content')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('Context')
                  PsiElement(right parenthesis)(')')
            PsiElement(semicolon)(';')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8ModifierImpl(MODIFIER)
                  PsiElement(public)('public')
                PsiElement(<init>)('<init>')
                ProguardR8ParametersImpl(PARAMETERS)
                  PsiElement(left parenthesis)('(')
                  ProguardR8TypeListImpl(TYPE_LIST)
                    ProguardR8TypeImpl(TYPE)
                      ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                        PsiElement(JAVA_IDENTIFIER)('android')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('content')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('Context')
                    PsiElement(comma)(',')
                    ProguardR8TypeImpl(TYPE)
                      ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                        PsiElement(JAVA_IDENTIFIER)('android')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('util')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('AttributeSet')
                    PsiElement(comma)(',')
                    ProguardR8TypeImpl(TYPE)
                      ProguardR8JavaPrimitiveImpl(JAVA_PRIMITIVE)
                        PsiElement(int)('int')
                  PsiElement(right parenthesis)(')')
            PsiElement(semicolon)(';')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8MethodImpl(METHOD)
                  ProguardR8ModifierImpl(MODIFIER)
                    PsiElement(public)('public')
                  ProguardR8TypeImpl(TYPE)
                    ProguardR8JavaPrimitiveImpl(JAVA_PRIMITIVE)
                      PsiElement(void)('void')
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(JAVA_IDENTIFIER_WITH_WILDCARDS)('set*')
                  ProguardR8ParametersImpl(PARAMETERS)
                    PsiElement(left parenthesis)('(')
                    PsiElement(...)('...')
                    PsiElement(right parenthesis)(')')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
          """.trimIndent(),
      toParseTreeText(
        """
          -keep public class * extends android.view.View {
            public <init>(android.content.Context);
            public <init>(android.content.Context, android.util.AttributeSet, int);
            public void set*(...);
          }
        """.trimIndent()
      )
    )
  }

  fun testParseKeepOptionWithModifier() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keepclasseswithmembers')
            PsiElement(comma)(',')
            ProguardR8KeepOptionModifierImpl(KEEP_OPTION_MODIFIER)
              PsiElement(allowobfuscation)('allowobfuscation')
            PsiElement(comma)(',')
            ProguardR8KeepOptionModifierImpl(KEEP_OPTION_MODIFIER)
              PsiElement(includedescriptorclasses)('includedescriptorclasses')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(asterisk)('*')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8FieldsSpecificationImpl(FIELDS_SPECIFICATION)
                  ProguardR8AnnotationNameImpl(ANNOTATION_NAME)
                    PsiElement(@)('@')
                    ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                      PsiElement(JAVA_IDENTIFIER)('com')
                      PsiElement(dot)('.')
                      PsiElement(JAVA_IDENTIFIER)('google')
                      PsiElement(dot)('.')
                      PsiElement(JAVA_IDENTIFIER)('gson')
                      PsiElement(dot)('.')
                      PsiElement(JAVA_IDENTIFIER)('annotations')
                      PsiElement(dot)('.')
                      PsiElement(JAVA_IDENTIFIER)('SerializedName')
                  PsiElement(<fields>)('<fields>')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
          """.trimIndent(),
      toParseTreeText(
        """
          -keepclasseswithmembers,allowobfuscation,includedescriptorclasses class * {
            @com.google.gson.annotations.SerializedName <fields>;
          }
        """.trimIndent()
      )
    )
  }

  fun testFileNamesAndFileFilters() {
    assertEquals(
      """
      FILE
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-injars')
          ProguardR8FlagArgumentImpl(FLAG_ARGUMENT)
            ProguardR8FileImpl(FILE)
              PsiElement(FILE_NAME)('in.jar')
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-outjars')
          ProguardR8FlagArgumentImpl(FLAG_ARGUMENT)
            ProguardR8FileImpl(FILE)
              PsiElement(FILE_NAME)('out.jar')
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-libraryjars')
          ProguardR8FlagArgumentImpl(FLAG_ARGUMENT)
            ProguardR8FileImpl(FILE)
              PsiElement(FILE_NAME)('<java.home>/jmods/java.base.jmod')
            PsiElement(left parenthesis)('(')
            ProguardR8FileFilterImpl(FILE_FILTER)
              ProguardR8FileImpl(FILE)
                PsiElement(!)('!')
                PsiElement(FILE_NAME)('**.jar')
              PsiElement(semicolon)(';')
              ProguardR8FileImpl(FILE)
                PsiElement(!)('!')
                PsiElement(FILE_NAME)('module-info.class')
            PsiElement(right parenthesis)(')')
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-libraryjars')
          ProguardR8FlagArgumentImpl(FLAG_ARGUMENT)
            ProguardR8FileImpl(FILE)
              PsiElement(FILE_NAME)('<java.home>/jmods/java.desktop.jmod')
            PsiElement(left parenthesis)('(')
            ProguardR8FileFilterImpl(FILE_FILTER)
              ProguardR8FileImpl(FILE)
                PsiElement(!)('!')
                PsiElement(FILE_NAME)('**.jar')
              PsiElement(semicolon)(';')
              ProguardR8FileImpl(FILE)
                PsiElement(!)('!')
                PsiElement(FILE_NAME)('module-info.class')
            PsiElement(right parenthesis)(')')
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-printseeds')
    """.trimIndent(),
      toParseTreeText(
        """
        -injars      in.jar
        -outjars     out.jar
        -libraryjars <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)
        -libraryjars <java.home>/jmods/java.desktop.jmod(!**.jar;!module-info.class)
        -printseeds
      """.trimIndent()
      )
    )
  }

  fun testFieldSpecification() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-assumenoexternalsideeffects')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER_WITH_WILDCARDS)('**java')
                  PsiElement(dot)('.')
                  PsiElement(JAVA_IDENTIFIER)('lang')
                  PsiElement(dot)('.')
                  PsiElement(JAVA_IDENTIFIER)('StringBuilder')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8FieldsSpecificationImpl(FIELDS_SPECIFICATION)
                  ProguardR8FieldImpl(FIELD)
                    ProguardR8ModifierImpl(MODIFIER)
                      PsiElement(static)('static')
                    ProguardR8TypeImpl(TYPE)
                      ProguardR8AnyTypeImpl(ANY_TYPE)
                        PsiElement(***)('***')
                    ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                      PsiElement(JAVA_IDENTIFIER)('fieldName')
              PsiElement(semicolon)(';')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  ProguardR8ModifierImpl(MODIFIER)
                    PsiElement(public)('public')
                  PsiElement(<methods>)('<methods>')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
          -assumenoexternalsideeffects class **java.lang.StringBuilder {
            static *** fieldName;
            public <methods>;
          }
        """.trimIndent()
      )
    )
  }

  fun testKeepOptionModifier() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keepclasseswithmembernames')
            PsiElement(comma)(',')
            ProguardR8KeepOptionModifierImpl(KEEP_OPTION_MODIFIER)
              PsiElement(includedescriptorclasses)('includedescriptorclasses')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(asterisk)('*')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  ProguardR8ModifierImpl(MODIFIER)
                    PsiElement(native)('native')
                  PsiElement(<methods>)('<methods>')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
          -keepclasseswithmembernames,includedescriptorclasses class * {
              native <methods>;
          }
        """.trimIndent()
      )
    )
  }

  fun testRecoveryClassSpecification() {
    assertEquals(
      """
      FILE
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keepclasseswithmembernames')
          ProguardR8FlagArgumentImpl(FLAG_ARGUMENT)
            ProguardR8FileImpl(FILE)
              PsiElement(FILE_NAME)('error')
        PsiErrorElement:colon, comma, left parenthesis or semicolon expected, got 'error'
          PsiElement(FILE_NAME)('error')
        PsiElement(DUMMY_BLOCK)
          PsiElement(opening brace)('{')
          PsiElement(DUMMY_BLOCK)
            PsiElement(JAVA_IDENTIFIER)('java')
            PsiElement(dot)('.')
            PsiElement(JAVA_IDENTIFIER)('lang')
            PsiElement(dot)('.')
            PsiElement(JAVA_IDENTIFIER)('StringBuilder')
            PsiElement(semicolon)(';')
            PsiElement(<methods>)('<methods>')
            PsiElement(semicolon)(';')
          PsiElement(closing brace)('}')
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keepclasseswithmembernames')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('MyClass')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8MethodImpl(METHOD)
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(JAVA_IDENTIFIER)('validOne')
                  ProguardR8ParametersImpl(PARAMETERS)
                    PsiElement(left parenthesis)('(')
                    ProguardR8TypeListImpl(TYPE_LIST)
                      <empty list>
                    PsiElement(right parenthesis)(')')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
          -keepclasseswithmembernames error error {
            java.lang.StringBuilder;
            <methods>;
          }

          -keepclasseswithmembernames class MyClass {
            validOne();
          }
        """.trimIndent()
      )
    )
  }

  fun testRecoveryJavaRule() {
    assertEquals(
      """
      FILE
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keepclasseswithmembernames')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(asterisk)('*')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8FullyQualifiedNameConstructorImpl(FULLY_QUALIFIED_NAME_CONSTRUCTOR)
                  ProguardR8ConstructorNameImpl(CONSTRUCTOR_NAME)
                    ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                      PsiElement(JAVA_IDENTIFIER)('java')
                      PsiElement(dot)('.')
                      PsiElement(JAVA_IDENTIFIER)('lang')
                      PsiElement(dot)('.')
                      PsiElement(JAVA_IDENTIFIER)('StringBuilder')
                  PsiErrorElement:<class member name>, '[', dot or left parenthesis expected, got ';'
                    <empty list>
            PsiElement(semicolon)(';')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                PsiElement(<methods>)('<methods>')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
          -keepclasseswithmembernames class * {
              java.lang.StringBuilder;
              <methods>;
          }
        """.trimIndent()
      )
    )
  }

  fun testRegularFlagBetweenFlagsWithClassSpecification() {
    assertEquals(
      """
      FILE
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassModifierImpl(CLASS_MODIFIER)
              PsiElement(public)('public')
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('com')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('example')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('MyApplication')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8ModifierImpl(MODIFIER)
                  PsiElement(public)('public')
                PsiElement(<methods>)('<methods>')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
        ProguardR8RuleImpl(RULE)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-flag')
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keepclassmembers')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(asterisk)('*')
            PsiElement(implements)('implements')
            ProguardR8SuperClassNameImpl(SUPER_CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('android')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('os')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('Parcelable')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8ModifierImpl(MODIFIER)
                  PsiElement(public)('public')
                PsiElement(<methods>)('<methods>')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
          -keep public class com.example.MyApplication {
              public <methods>;
          }

          -flag

          -keepclassmembers class * implements android.os.Parcelable {
              public <methods>;
          }
        """.trimIndent()
      )
    )
  }

  fun testAnyParametersSymbol() {
    assertEquals(
      """
      FILE
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('myClass')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8MethodImpl(METHOD)
                  ProguardR8TypeImpl(TYPE)
                    ProguardR8JavaPrimitiveImpl(JAVA_PRIMITIVE)
                      PsiElement(void)('void')
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(JAVA_IDENTIFIER)('anyType')
                  ProguardR8ParametersImpl(PARAMETERS)
                    PsiElement(left parenthesis)('(')
                    PsiElement(...)('...')
                    PsiElement(right parenthesis)(')')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class myClass {
            void anyType(...);
          }
        """.trimIndent()
      )
    )
  }

  fun testAnyParametersSymbolInTypeList() {
    assertEquals(
      """
      FILE
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('myClass')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8MethodImpl(METHOD)
                  ProguardR8TypeImpl(TYPE)
                    ProguardR8JavaPrimitiveImpl(JAVA_PRIMITIVE)
                      PsiElement(void)('void')
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(JAVA_IDENTIFIER)('mixedTypes')
                  ProguardR8ParametersImpl(PARAMETERS)
                    PsiElement(left parenthesis)('(')
                    ProguardR8TypeListImpl(TYPE_LIST)
                      ProguardR8TypeImpl(TYPE)
                        ProguardR8JavaPrimitiveImpl(JAVA_PRIMITIVE)
                          PsiElement(int)('int')
                      PsiElement(comma)(',')
                      PsiElement(...)('...')
                    PsiElement(right parenthesis)(')')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class myClass {
            void mixedTypes(int, ...);
          }
        """.trimIndent()
      )
    )
  }

  fun testAnyParametersSymbolInTypeListAtWrongPlace() {
    assertEquals(
      """
      FILE
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('myClass')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8MethodImpl(METHOD)
                  ProguardR8TypeImpl(TYPE)
                    ProguardR8JavaPrimitiveImpl(JAVA_PRIMITIVE)
                      PsiElement(void)('void')
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(JAVA_IDENTIFIER)('mixedTypes')
                  ProguardR8ParametersImpl(PARAMETERS)
                    PsiElement(left parenthesis)('(')
                    ProguardR8TypeListImpl(TYPE_LIST)
                      ProguardR8TypeImpl(TYPE)
                        ProguardR8JavaPrimitiveImpl(JAVA_PRIMITIVE)
                          PsiElement(int)('int')
                      PsiElement(comma)(',')
                      PsiElement(...)('...')
                      PsiErrorElement:',' unexpected
                        PsiElement(comma)(',')
                      PsiElement(int)('int')
                    PsiElement(right parenthesis)(')')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class myClass {
            void mixedTypes(int, ..., int);
          }
        """.trimIndent()
      )
    )
  }

  fun testTypeListRecovery() {
    assertEquals(
      """
      FILE
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('myClass')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8MethodImpl(METHOD)
                  ProguardR8TypeImpl(TYPE)
                    ProguardR8JavaPrimitiveImpl(JAVA_PRIMITIVE)
                      PsiElement(void)('void')
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(JAVA_IDENTIFIER)('badTypeList')
                  ProguardR8ParametersImpl(PARAMETERS)
                    PsiElement(left parenthesis)('(')
                    ProguardR8TypeListImpl(TYPE_LIST)
                      PsiErrorElement:'...' or <type> expected, got '2'
                        PsiElement(BAD_CHARACTER)('2')
                      PsiElement(comma)(',')
                      PsiElement(int)('int')
                    PsiElement(right parenthesis)(')')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class myClass {
            void badTypeList(2, int);
          }
        """.trimIndent()
      )
    )
  }

  fun testClassMemberWithoutType() {
    assertEquals(
      """
      FILE
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('myClass')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8FieldsSpecificationImpl(FIELDS_SPECIFICATION)
                ProguardR8FieldImpl(FIELD)
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(JAVA_IDENTIFIER)('field')
            PsiElement(semicolon)(';')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8MethodImpl(METHOD)
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(JAVA_IDENTIFIER)('method')
                  ProguardR8ParametersImpl(PARAMETERS)
                    PsiElement(left parenthesis)('(')
                    ProguardR8TypeListImpl(TYPE_LIST)
                      <empty list>
                    PsiElement(right parenthesis)(')')
            PsiElement(semicolon)(';')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                ProguardR8FullyQualifiedNameConstructorImpl(FULLY_QUALIFIED_NAME_CONSTRUCTOR)
                  ProguardR8ConstructorNameImpl(CONSTRUCTOR_NAME)
                    ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                      PsiElement(JAVA_IDENTIFIER)('not')
                      PsiElement(dot)('.')
                      PsiElement(JAVA_IDENTIFIER)('classMember')
                  PsiErrorElement:<class member name>, '[', dot or left parenthesis expected, got ';'
                    <empty list>
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class myClass {
            field;
            method();
            not.classMember;
          }
        """.trimIndent()
      )
    )
  }

  fun testFullyQualifiedNameConstructor() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('p1')
                  PsiElement(dot)('.')
                  PsiElement(JAVA_IDENTIFIER)('p2')
                  PsiElement(dot)('.')
                  PsiElement(JAVA_IDENTIFIER)('myClass')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  ProguardR8FullyQualifiedNameConstructorImpl(FULLY_QUALIFIED_NAME_CONSTRUCTOR)
                    ProguardR8ConstructorNameImpl(CONSTRUCTOR_NAME)
                      ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                        PsiElement(JAVA_IDENTIFIER)('p1')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('p2')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('myClass')
                    ProguardR8ParametersImpl(PARAMETERS)
                      PsiElement(left parenthesis)('(')
                      ProguardR8TypeListImpl(TYPE_LIST)
                        <empty list>
                      PsiElement(right parenthesis)(')')
              PsiErrorElement:semicolon expected, got '}'
                <empty list>
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class p1.p2.myClass {
            p1.p2.myClass()
          }
        """.trimIndent()
      )
    )
  }

  fun testClassPathWithKeyWord() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(class)('class')
                  PsiElement(dot)('.')
                  PsiElement(interface)('interface')
                  PsiElement(dot)('.')
                  PsiElement(JAVA_IDENTIFIER)('myClass')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  ProguardR8FullyQualifiedNameConstructorImpl(FULLY_QUALIFIED_NAME_CONSTRUCTOR)
                    ProguardR8ConstructorNameImpl(CONSTRUCTOR_NAME)
                      ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                        PsiElement(void)('void')
                        PsiElement(dot)('.')
                        PsiElement(int)('int')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('myClass')
                    ProguardR8ParametersImpl(PARAMETERS)
                      PsiElement(left parenthesis)('(')
                      ProguardR8TypeListImpl(TYPE_LIST)
                        <empty list>
                      PsiElement(right parenthesis)(')')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class class.interface.myClass {
            void.int.myClass();
          }
        """.trimIndent()
      )
    )
  }

  fun testDontParseModifierAsType() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('myClass')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  ProguardR8FullyQualifiedNameConstructorImpl(FULLY_QUALIFIED_NAME_CONSTRUCTOR)
                    ProguardR8ModifierImpl(MODIFIER)
                      PsiElement(strictfp)('strictfp')
                    ProguardR8ConstructorNameImpl(CONSTRUCTOR_NAME)
                      ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                        PsiElement(JAVA_IDENTIFIER)('my')
                    PsiErrorElement:<class member name>, '[', dot or left parenthesis expected, got '}'
                      <empty list>
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class myClass {
            strictfp my
          }
        """.trimIndent()
      )
    )

    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('myClass')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8FieldsSpecificationImpl(FIELDS_SPECIFICATION)
                  ProguardR8FieldImpl(FIELD)
                    ProguardR8ModifierImpl(MODIFIER)
                      PsiElement(public)('public')
                    ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                      PsiElement(JAVA_IDENTIFIER)('my')
              PsiErrorElement:<class member name>, '[', dot or semicolon expected, got '}'
                <empty list>
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class myClass {
            public my
          }
        """.trimIndent()
      )
    )


    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('myClass')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8FieldsSpecificationImpl(FIELDS_SPECIFICATION)
                  ProguardR8FieldImpl(FIELD)
                    ProguardR8ModifierImpl(MODIFIER)
                      PsiElement(volatile)('volatile')
                    ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                      PsiElement(JAVA_IDENTIFIER)('my')
              PsiErrorElement:<class member name>, '[', dot or semicolon expected, got '}'
                <empty list>
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class myClass {
            volatile my
          }
        """.trimIndent()
      )
    )
  }

  fun testParseModifierAsPartOfQualifiedName() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('myClass')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  ProguardR8FullyQualifiedNameConstructorImpl(FULLY_QUALIFIED_NAME_CONSTRUCTOR)
                    ProguardR8ModifierImpl(MODIFIER)
                      PsiElement(private)('private')
                    ProguardR8ConstructorNameImpl(CONSTRUCTOR_NAME)
                      ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                        PsiElement(private)('private')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('not')
                        PsiElement(dot)('.')
                        PsiElement(JAVA_IDENTIFIER)('modifier')
                    ProguardR8ParametersImpl(PARAMETERS)
                      PsiElement(left parenthesis)('(')
                      ProguardR8TypeListImpl(TYPE_LIST)
                        <empty list>
                      PsiElement(right parenthesis)(')')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class myClass {
            private private.not.modifier();
          }
        """.trimIndent()
      )
    )
  }

  fun testFileNameAfterAt() {
    assertEquals(
      """
        FILE
          ProguardR8IncludeFileImpl(INCLUDE_FILE)
            PsiElement(@)('@')
            ProguardR8FileImpl(FILE)
              PsiElement(FILE_NAME)('keep-rules.txt')
          ProguardR8RuleImpl(RULE)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-secondrule')
      """.trimIndent(),
      toParseTreeText(
        """
        @keep-rules.txt

        -secondrule
        """.trimIndent()
      )
    )
  }

  fun testParsingSingleAsterisk() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8AnnotationNameImpl(ANNOTATION_NAME)
                PsiElement(@)('@')
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(double asterisk)('**')
                  PsiElement(dot)('.')
                  PsiElement(JAVA_IDENTIFIER)('RunWith')
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(asterisk)('*')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8FieldsSpecificationImpl(FIELDS_SPECIFICATION)
                  ProguardR8FieldImpl(FIELD)
                    ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                      PsiElement(asterisk)('*')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep @**.RunWith class * { *; }
        """.trimIndent()
      )
    )
  }

  fun testAnnotationInSuperClass() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(double asterisk)('**')
              PsiElement(implements)('implements')
              ProguardR8AnnotationNameImpl(ANNOTATION_NAME)
                PsiElement(@)('@')
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('shaking3')
                  PsiElement(dot)('.')
                  PsiElement(JAVA_IDENTIFIER)('SubtypeUsedByReflection')
              ProguardR8SuperClassNameImpl(SUPER_CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(double asterisk)('**')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  PsiElement(<init>)('<init>')
                  ProguardR8ParametersImpl(PARAMETERS)
                    PsiElement(left parenthesis)('(')
                    PsiElement(...)('...')
                    PsiElement(right parenthesis)(')')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class ** implements @shaking3.SubtypeUsedByReflection ** {
          <init>(...);
        }
        """.trimIndent()
      )
    )
  }

  fun testBackReferenceWildcard() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER_WITH_WILDCARDS)('**${'$'}D<2>')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class **${'$'}D<2>
        """.trimIndent()
      )
    )
  }

  fun testMultipleClasses() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('a')
                  PsiElement(dot)('.')
                  PsiElement(JAVA_IDENTIFIER)('b')
                  PsiElement(dot)('.')
                  PsiElement(JAVA_IDENTIFIER)('c')
              PsiElement(comma)(',')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('a')
              PsiElement(comma)(',')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('g')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class a.b.c, a, g {}
        """.trimIndent()
      )
    )
  }

  fun testRecoveryAfterMissingSemicolon() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('a')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  PsiElement(<methods>)('<methods>')
              PsiErrorElement:semicolon expected, got '}'
                <empty list>
              PsiElement(closing brace)('}')
          ProguardR8RuleImpl(RULE)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-rule')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class a {
          <methods>
        }

        -rule
        """.trimIndent()
      )
    )
  }

  fun testMultipleDimension() {

  }

  fun testNegatedClasses() {

    assertEquals(
      """
      FILE
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              PsiElement(!)('!')
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('android')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('support')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('v7')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('internal')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('view')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('menu')
                PsiElement(dot)('.')
                PsiElement(double asterisk)('**')
            PsiElement(comma)(',')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('android')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('support')
                PsiElement(dot)('.')
                PsiElement(double asterisk)('**')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8FieldsSpecificationImpl(FIELDS_SPECIFICATION)
                ProguardR8FieldImpl(FIELD)
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(asterisk)('*')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(interface)('interface')
            ProguardR8ClassNameImpl(CLASS_NAME)
              PsiElement(!)('!')
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('android')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('support')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('v7')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('internal')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('view')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('menu')
                PsiElement(dot)('.')
                PsiElement(double asterisk)('**')
            PsiElement(comma)(',')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('android')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('support')
                PsiElement(dot)('.')
                PsiElement(double asterisk)('**')
          ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
            PsiElement(opening brace)('{')
            ProguardR8JavaRuleImpl(JAVA_RULE)
              ProguardR8FieldsSpecificationImpl(FIELDS_SPECIFICATION)
                ProguardR8FieldImpl(FIELD)
                  ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                    PsiElement(asterisk)('*')
            PsiElement(semicolon)(';')
            PsiElement(closing brace)('}')
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            PsiElement(!)('!')
            PsiErrorElement:<qualified name> expected, got '!'
              PsiElement(!)('!')
            PsiElement(class)('class')
            PsiElement(dot)('.')
            PsiElement(JAVA_IDENTIFIER)('name')
        ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
          ProguardR8FlagImpl(FLAG)
            PsiElement(FLAG_TOKEN)('-keep')
          ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
            ProguardR8ClassTypeImpl(CLASS_TYPE)
              PsiElement(class)('class')
            ProguardR8ClassNameImpl(CLASS_NAME)
              PsiElement(!)('!')
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(class)('class')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('name')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class !android.support.v7.internal.view.menu.**,android.support.** {*;}
        -keep interface !android.support.v7.internal.view.menu.**,android.support.** {*;}
        -keep class !!class.name
        -keep class !  class.name
        """.trimIndent()
      )
    )
  }

  fun testAnyNonPrimitiveType() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('MyClass')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  ProguardR8MethodImpl(METHOD)
                    ProguardR8TypeImpl(TYPE)
                      ProguardR8AnyNotPrimitiveTypeImpl(ANY_NOT_PRIMITIVE_TYPE)
                        PsiElement(double asterisk)('**')
                      ProguardR8ArrayTypeImpl(ARRAY_TYPE)
                        PsiElement([)('[')
                        PsiElement(])(']')
                    ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                      PsiElement(JAVA_IDENTIFIER)('values')
                    ProguardR8ParametersImpl(PARAMETERS)
                      PsiElement(left parenthesis)('(')
                      ProguardR8TypeListImpl(TYPE_LIST)
                        <empty list>
                      PsiElement(right parenthesis)(')')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class MyClass {
           **[] values();
        }
        """.trimIndent()
      )
    )
  }

  fun testSyntheticModifier() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(asterisk)('*')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  ProguardR8ModifierImpl(MODIFIER)
                    PsiElement(synthetic)('synthetic')
                  PsiElement(<init>)('<init>')
                  ProguardR8ParametersImpl(PARAMETERS)
                    PsiElement(left parenthesis)('(')
                    PsiElement(...)('...')
                    PsiElement(right parenthesis)(')')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class * {
          synthetic <init>(...);
        }
        """.trimIndent()
      )
    )
  }

  fun testArrayAfterAnyPrimitiveType() {

    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('MyClass')
            ProguardR8ClassSpecificationBodyImpl(CLASS_SPECIFICATION_BODY)
              PsiElement(opening brace)('{')
              ProguardR8JavaRuleImpl(JAVA_RULE)
                ProguardR8MethodSpecificationImpl(METHOD_SPECIFICATION)
                  ProguardR8MethodImpl(METHOD)
                    ProguardR8TypeImpl(TYPE)
                      ProguardR8AnyPrimitiveTypeImpl(ANY_PRIMITIVE_TYPE)
                        PsiElement(%)('%')
                      ProguardR8ArrayTypeImpl(ARRAY_TYPE)
                        PsiElement([)('[')
                        PsiElement(])(']')
                    ProguardR8ClassMemberNameImpl(CLASS_MEMBER_NAME)
                      PsiElement(JAVA_IDENTIFIER)('values')
                    ProguardR8ParametersImpl(PARAMETERS)
                      PsiElement(left parenthesis)('(')
                      ProguardR8TypeListImpl(TYPE_LIST)
                        <empty list>
                      PsiElement(right parenthesis)(')')
              PsiElement(semicolon)(';')
              PsiElement(closing brace)('}')
      """.trimIndent(),
      toParseTreeText(
        """
        -keep class MyClass {
           %[] values();
        }
        """.trimIndent()
      )
    )
  }

  fun testAsteriskInFileName() {
    assertEquals(
      """
        FILE
          ProguardR8RuleImpl(RULE)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-rule')
            ProguardR8FlagArgumentImpl(FLAG_ARGUMENT)
              ProguardR8FileImpl(FILE)
                PsiElement(asterisk)('*')
      """.trimIndent(),
      toParseTreeText("""
        -rule *
      """.trimIndent())
    )
  }

  fun testQuotedClasses() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keepclassmembers')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(class)('class')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(DOUBLE_QUOTED_CLASS)('"a.b.c.**"')
              PsiElement(comma)(',')
              ProguardR8ClassNameImpl(CLASS_NAME)
                PsiElement(!)('!')
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER_WITH_WILDCARDS)('**d')
              PsiElement(comma)(',')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(SINGLE_QUOTED_CLASS)(''!**e'')
              PsiElement(comma)(',')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(DOUBLE_QUOTED_CLASS)('"!**f"')
              PsiElement(comma)(',')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('g')
              PsiElement(comma)(',')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(SINGLE_QUOTED_CLASS)(''h'')
              PsiElement(comma)(',')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(DOUBLE_QUOTED_CLASS)('"i"')
      """.trimIndent(),
      toParseTreeText(
        """
          -keepclassmembers class "a.b.c.**" , !**d , '!**e' , "!**f" , g , 'h' , "i"
        """.trimIndent()
      )
    )
  }

  fun testClassFilter() {
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassFilterImpl(RULE_WITH_CLASS_FILTER)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-dontwarn')
            ProguardR8ClassNameImpl(CLASS_NAME)
              ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                PsiElement(JAVA_IDENTIFIER)('com')
                PsiElement(dot)('.')
                PsiElement(JAVA_IDENTIFIER)('MyClass${"$"}InnerClass')
      """.trimIndent(),
      toParseTreeText(
        """
          -dontwarn com.MyClass${"$"}InnerClass
        """.trimIndent()
      )
    )
  }

  /** Regression test for b/158189488. */
  fun testAtInterface() {
    // See syntax at https://www.guardsquare.com/manual/configuration/usage#classspecification.
    // In this case, "@interface" is interpreted as the class type (interface|class|enum) before the class name.
    assertEquals(
      """
        FILE
          ProguardR8RuleWithClassSpecificationImpl(RULE_WITH_CLASS_SPECIFICATION)
            ProguardR8FlagImpl(FLAG)
              PsiElement(FLAG_TOKEN)('-keep')
            ProguardR8ClassSpecificationHeaderImpl(CLASS_SPECIFICATION_HEADER)
              ProguardR8ClassTypeImpl(CLASS_TYPE)
                PsiElement(AT_INTERFACE)('@interface')
              ProguardR8ClassNameImpl(CLASS_NAME)
                ProguardR8QualifiedNameImpl(QUALIFIED_NAME)
                  PsiElement(JAVA_IDENTIFIER)('butterknife')
                  PsiElement(dot)('.')
                  PsiElement(asterisk)('*')
      """.trimIndent(),
      toParseTreeText(
        """
          -keep @interface butterknife.*
        """.trimIndent()
      )
    )
  }
}
