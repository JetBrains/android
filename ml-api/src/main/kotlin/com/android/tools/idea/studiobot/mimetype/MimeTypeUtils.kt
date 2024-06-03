/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.studiobot.mimetype

import com.android.tools.idea.studiobot.MimeType
import com.android.tools.idea.studiobot.MimeType.Companion.AGSL
import com.android.tools.idea.studiobot.MimeType.Companion.AIDL
import com.android.tools.idea.studiobot.MimeType.Companion.ATTR_FOLDER_TYPE
import com.android.tools.idea.studiobot.MimeType.Companion.ATTR_ROLE
import com.android.tools.idea.studiobot.MimeType.Companion.ATTR_ROOT_TAG
import com.android.tools.idea.studiobot.MimeType.Companion.BATCH
import com.android.tools.idea.studiobot.MimeType.Companion.C
import com.android.tools.idea.studiobot.MimeType.Companion.CLOJURE
import com.android.tools.idea.studiobot.MimeType.Companion.COFFEESCRIPT
import com.android.tools.idea.studiobot.MimeType.Companion.CPP
import com.android.tools.idea.studiobot.MimeType.Companion.CSHARP
import com.android.tools.idea.studiobot.MimeType.Companion.CSS
import com.android.tools.idea.studiobot.MimeType.Companion.DART
import com.android.tools.idea.studiobot.MimeType.Companion.FSHARP
import com.android.tools.idea.studiobot.MimeType.Companion.GO
import com.android.tools.idea.studiobot.MimeType.Companion.GRADLE
import com.android.tools.idea.studiobot.MimeType.Companion.GRADLE_KTS
import com.android.tools.idea.studiobot.MimeType.Companion.GROOVY
import com.android.tools.idea.studiobot.MimeType.Companion.HAML
import com.android.tools.idea.studiobot.MimeType.Companion.HANDLEBARS
import com.android.tools.idea.studiobot.MimeType.Companion.HTML
import com.android.tools.idea.studiobot.MimeType.Companion.INI
import com.android.tools.idea.studiobot.MimeType.Companion.JADE
import com.android.tools.idea.studiobot.MimeType.Companion.JAVA
import com.android.tools.idea.studiobot.MimeType.Companion.JAVASCRIPT
import com.android.tools.idea.studiobot.MimeType.Companion.JSON
import com.android.tools.idea.studiobot.MimeType.Companion.KOTLIN
import com.android.tools.idea.studiobot.MimeType.Companion.LESS
import com.android.tools.idea.studiobot.MimeType.Companion.LUA
import com.android.tools.idea.studiobot.MimeType.Companion.MAKEFILE
import com.android.tools.idea.studiobot.MimeType.Companion.MANIFEST
import com.android.tools.idea.studiobot.MimeType.Companion.MARKDOWN
import com.android.tools.idea.studiobot.MimeType.Companion.MATLAB
import com.android.tools.idea.studiobot.MimeType.Companion.PHP
import com.android.tools.idea.studiobot.MimeType.Companion.POWERSHELL
import com.android.tools.idea.studiobot.MimeType.Companion.PROGUARD
import com.android.tools.idea.studiobot.MimeType.Companion.PROPERTIES
import com.android.tools.idea.studiobot.MimeType.Companion.PROTO
import com.android.tools.idea.studiobot.MimeType.Companion.PYTHON
import com.android.tools.idea.studiobot.MimeType.Companion.R
import com.android.tools.idea.studiobot.MimeType.Companion.REGEX
import com.android.tools.idea.studiobot.MimeType.Companion.RESOURCE
import com.android.tools.idea.studiobot.MimeType.Companion.RUBY
import com.android.tools.idea.studiobot.MimeType.Companion.RUST
import com.android.tools.idea.studiobot.MimeType.Companion.SASS
import com.android.tools.idea.studiobot.MimeType.Companion.SCALA
import com.android.tools.idea.studiobot.MimeType.Companion.SCSS
import com.android.tools.idea.studiobot.MimeType.Companion.SHELL
import com.android.tools.idea.studiobot.MimeType.Companion.SLIM
import com.android.tools.idea.studiobot.MimeType.Companion.SQL
import com.android.tools.idea.studiobot.MimeType.Companion.STYLUS
import com.android.tools.idea.studiobot.MimeType.Companion.SVG
import com.android.tools.idea.studiobot.MimeType.Companion.SWIFT
import com.android.tools.idea.studiobot.MimeType.Companion.TERRAFORM
import com.android.tools.idea.studiobot.MimeType.Companion.TEX
import com.android.tools.idea.studiobot.MimeType.Companion.TEXT
import com.android.tools.idea.studiobot.MimeType.Companion.TOML
import com.android.tools.idea.studiobot.MimeType.Companion.TSX
import com.android.tools.idea.studiobot.MimeType.Companion.TYPESCRIPT
import com.android.tools.idea.studiobot.MimeType.Companion.UNKNOWN
import com.android.tools.idea.studiobot.MimeType.Companion.VALUE_MANIFEST
import com.android.tools.idea.studiobot.MimeType.Companion.VALUE_RESOURCE
import com.android.tools.idea.studiobot.MimeType.Companion.VERSION_CATALOG
import com.android.tools.idea.studiobot.MimeType.Companion.XML
import com.android.tools.idea.studiobot.MimeType.Companion.YAML
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

/**
 * Utility methods for [MimeType]. When adding a new language, update all 5 lookup methods:
 * * [displayName]
 * * [ideLanguage]
 * * [fileType]
 * * [markdownLanguageName]
 * * [fromLanguage] (this may not be necessary unless your mime type has multiple dialects)
 */
fun MimeType.displayName(): String {
  return when (base().normalizeString()) {
    KOTLIN.mimeType -> if (isGradle()) "Gradle DSL" else "Kotlin"
    JAVA.mimeType -> "Java"
    XML.mimeType -> {
      when (getRole()) {
        null -> "XML"
        VALUE_MANIFEST -> "Manifest"
        VALUE_RESOURCE -> {
          // Utility function copied from org.jetbrains.kotlin.util.capitalizeDecapitalize
          fun String.capitalizeAsciiOnly(): String {
            if (isEmpty()) return this
            val c = this[0]
            return if (c in 'a'..'z')
              buildString(length) {
                append(c.uppercaseChar())
                append(this@capitalizeAsciiOnly, 1, this@capitalizeAsciiOnly.length)
              }
            else this
          }

          val folderType = getAttribute(ATTR_FOLDER_TYPE)
          folderType?.capitalizeAsciiOnly() ?: "Resource XML"
        }
        else -> "XML"
      }
    }
    JSON.mimeType -> "JSON"
    TEXT.mimeType -> "Text"
    REGEX.mimeType -> "Regular Expression"
    GROOVY.mimeType -> if (isGradle()) "Gradle" else "Groovy"
    TOML.mimeType -> if (isVersionCatalog()) "Version Catalog" else "TOML"
    C.mimeType -> "C"
    CPP.mimeType -> "C++"
    SVG.mimeType -> "SVG"
    AIDL.mimeType -> "AIDL"
    SQL.mimeType -> "SQL"
    PROGUARD.mimeType -> "Shrinker Config"
    PROPERTIES.mimeType -> "Properties"
    PROTO.mimeType -> "Protobuf"
    PYTHON.mimeType -> "Python"
    DART.mimeType -> "Dart"
    RUST.mimeType -> "Rust"
    JAVASCRIPT.mimeType -> "JavaScript"
    AGSL.mimeType -> "Android Graphics Shading Language"
    SHELL.mimeType -> "Shell Script"
    YAML.mimeType -> "YAML"
    GO.mimeType -> "Go"
    SCALA.mimeType -> "Scala"
    R.mimeType -> "R"
    RUBY.mimeType -> "Ruby"
    PHP.mimeType -> "PHP"
    MATLAB.mimeType -> "MATLAB"
    LUA.mimeType -> "Lua"
    MARKDOWN.mimeType -> "Markdown"
    CSHARP.mimeType -> "C#"
    BATCH.mimeType -> "Batch"
    CLOJURE.mimeType -> "Clojure"
    COFFEESCRIPT.mimeType -> "CoffeeScript"
    CSS.mimeType -> "CSS"
    HANDLEBARS.mimeType -> "Handlebars"
    HAML.mimeType -> "Haml"
    HTML.mimeType -> "HTML"
    INI.mimeType -> "Ini"
    TEX.mimeType -> "Latex"
    LESS.mimeType -> "LESS"
    MAKEFILE.mimeType -> "Makefile"
    POWERSHELL.mimeType -> "PowerShell"
    JADE.mimeType -> "Jade"
    SLIM.mimeType -> "Slim"
    SCSS.mimeType -> "SCSS"
    SASS.mimeType -> "SASS"
    STYLUS.mimeType -> "Stylus"
    SWIFT.mimeType -> "Swift"
    TSX.mimeType -> "TypeScript JSX"
    TERRAFORM.mimeType -> "HCL-Terraform"
    else -> {
      val base = base()
      if (base != this) {
        return base.displayName()
      }
      ideLanguage().displayName
    }
  }
}

/**
 * Returns the IDE [Language] support for this mime type; if it's not available returns the plain
 * text language instead
 */
fun MimeType.ideLanguage(): Language = getIdeLanguage(ideLanguageId())

private fun getIdeLanguage(languageId: String): Language {
  return Language.findLanguageByID(languageId) ?: PlainTextLanguage.INSTANCE
}

private fun MimeType.ideLanguageId(): String =
  when (base().toString()) {
    KOTLIN.mimeType -> "kotlin" // org.jetbrains.kotlin.idea.KotlinLanguage
    JAVA.mimeType -> "JAVA" // com.intellij.lang.java.JavaLanguage
    XML.mimeType -> "XML" // com.intellij.lang.xml.XMLLanguage
    JSON.mimeType -> "JSON" // com.intellij.json.JsonLanguage
    TEXT.mimeType -> "TEXT" // com.intellij.openapi.fileTypes.PlainTextLanguage
    REGEX.mimeType -> "RegExp" // com.intellij.lang.regexp.RegExpLanguage
    GROOVY.mimeType -> "Groovy" // org.jetbrains.plugins.groovy.GroovyLanguage
    TOML.mimeType -> "TOML" // org.toml.lang.TomlLanguage
    C.mimeType,
    CPP.mimeType -> "ObjectiveC" // org.jetbrains.cidr.lang.OCLanguage
    SVG.mimeType -> "SVG" // org.intellij.images.fileTypes.impl.SvgLanguage.INSTANCE
    AIDL.mimeType -> "AIDL" // com.android.tools.idea.lang.aidl.AidlLanguage
    SQL.mimeType -> "RoomSql" // com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
    PROGUARD.mimeType ->
      "SHRINKER_CONFIG" // com.android.tools.idea.lang.proguardR8.ProguardR8Language
    PROPERTIES.mimeType -> "Properties" // com.intellij.lang.properties.PropertiesLanguage
    PROTO.mimeType -> "protobuf" // com.intellij.protobuf.lang.PbLanguage
    PYTHON.mimeType -> "Python" // com.jetbrains.python.PythonLanguage
    DART.mimeType -> "Dart" // com.jetbrains.lang.dart.DartLanguage
    RUST.mimeType -> "Rust" // org.rust.lang.RsLanguage
    JAVASCRIPT.mimeType -> "JavaScript" // com.intellij.lang.javascript.JavascriptLanguage
    TYPESCRIPT.mimeType ->
      "TypeScript" // com.intellij.lang.javascript.dialects.TypeScriptLanguageDialect
    AGSL.mimeType -> "AGSL" // com.android.tools.idea.lang.agsl.AgslLanguage
    SHELL.mimeType -> "Shell Script" // com.intellij.sh.ShLanguage
    YAML.mimeType -> "yaml" // org.jetbrains.yaml.YAMLLanguage
    GO.mimeType -> "Go" // com.goide.GoLanguage
    SCALA.mimeType -> "Scala" // org.jetbrains.plugins.scala.ScalaLanguage
    R.mimeType -> "R" // org.jetbrains.r.RLanguage
    RUBY.mimeType -> "ruby" // org.jetbrains.plugins.ruby.ruby.lang.RubyLanguage
    PHP.mimeType -> "PHP" // com.jetbrains.php.lang.PhpLanguage
    MATLAB.mimeType -> "Matlab" // com.github.kornilova203.matlab.MatlabLanguage
    LUA.mimeType -> "Lua" // com.tang.intellij.lua.lang.LuaLanguage
    CSHARP.mimeType -> "C#" // com.jetbrains.rider.languages.fileTypes.csharp.CSharpLanguage
    BATCH.mimeType -> "Batch" // org.intellij.lang.batch.BatchLanguage
    CLOJURE.mimeType -> "Clojure" // cursive.ClojureLanguage
    COFFEESCRIPT.mimeType -> "CoffeeScript" // org.coffeescript.CoffeeScriptLanguage
    CSS.mimeType -> "CSS" // com.intellij.lang.css.CSSLanguage
    FSHARP.mimeType -> "F#" // com.jetbrains.rider.ideaInterop.fileTypes.fsharp.FSharpLanguage
    HANDLEBARS.mimeType -> "Handlebars" // com.dmarcotte.handlebars.HbLanguage
    HAML.mimeType -> "Haml" // org.jetbrains.plugins.haml.HAMLLanguage
    HTML.mimeType -> "HTML" // com.intellij.lang.html.HTMLLanguage
    INI.mimeType -> "Ini" // ini4idea.IniLanguage
    TEX.mimeType -> "Latex" // nl.hannahsten.texifyidea.grammar.LatexLanguage
    LESS.mimeType -> "LESS" // org.jetbrains.plugins.less.LESSLanguage
    MAKEFILE.mimeType -> "Makefile" // com.jetbrains.lang.makefile.MakefileLanguage
    POWERSHELL.mimeType -> "PowerShell" // com.intellij.plugin.powershell.lang.PowerShellLanguage
    JADE.mimeType -> "Jade" // com.jetbrains.plugins.jade.JadeLanguage
    SLIM.mimeType -> "Slim" // org.jetbrains.plugins.slim.SlimLanguage
    SCSS.mimeType -> "SCSS" // org.jetbrains.plugins.scss.SCSSLanguage
    SASS.mimeType -> "SASS" // org.jetbrains.plugins.sass.SASSLanguage
    STYLUS.mimeType -> "Stylus" // org.jetbrains.plugins.stylus.StylusLanguage
    TSX.mimeType ->
      "TypeScript JSX" // com.intellij.lang.javascript.dialects.TypeScriptJSXLanguageDialect
    TERRAFORM.mimeType -> "HCL-Terraform" // org.intellij.terraform.config.TerraformLanguage
    UNKNOWN.mimeType -> "TEXT" // com.intellij.openapi.fileTypes.PlainTextLanguage
    else -> "TEXT" // com.intellij.openapi.fileTypes.PlainTextLanguage
  }

/**
 * Returns a suitable [FileType] corresponding to files for this source, if it's a language that can
 * appear at the root level in a file.
 */
fun MimeType.fileType(): FileType? = fileTypeId()?.let { getFileType(it) }

private fun getFileType(fileTypeId: String): FileType? {
  return FileTypeRegistry.getInstance().findFileTypeByName(fileTypeId)
}

private fun MimeType.fileTypeId(): String? {
  return when (base().normalizeString()) {
    KOTLIN.mimeType -> "Kotlin" // org.jetbrains.kotlin.idea.KotlinFileType
    JAVA.mimeType -> "JAVA" // com.intellij.ide.highlighter.JavaFileType
    XML.mimeType -> "XML" // com.intellij.ide.highlighter.XmlFileType
    JSON.mimeType -> "JSON" // com.intellij.json.JsonFileType
    TEXT.mimeType -> "PLAIN_TEXT" // com.intellij.openapi.fileTypes.PlainTextFileType
    REGEX.mimeType -> "RegExp" // org.intellij.lang.regexp.RegExpFileType
    GROOVY.mimeType ->
      if (isGradle()) {
        "Gradle" // org.jetbrains.plugins.gradle.config.GradleFileType
      } else {
        "Groovy" // org.jetbrains.plugins.groovy.GroovyFileType
      }
    TOML.mimeType -> "TOML" // org.toml.lang.psi.TomlFileType
    C.mimeType,
    CPP.mimeType -> "ObjectiveC" // com.jetbrains.cidr.lang.OCFileType
    SVG.mimeType -> "SVG" // org.intellij.images.fileTypes.impl.SvgFileType
    AIDL.mimeType -> "AIDL" // com.android.tools.idea.lang.aidl.AidlFileType
    SQL.mimeType -> "Android Room SQL" // com.android.tools.idea.lang.androidSql.AndroidSqlFileType
    PROGUARD.mimeType ->
      "Shrinker Config File" // com.android.tools.idea.lang.proguardR8.ProguardR8FileType
    PROPERTIES.mimeType -> "Properties" // com.intellij.lang.properties.PropertiesFileType
    PROTO.mimeType -> "protobuf" // com.intellij.protobuf.lang.PbFileType
    PYTHON.mimeType -> "Python" // com.jetbrains.python.PythonFileType
    DART.mimeType -> "Dart" // com.jetbrains.lang.dart.DartFileType
    RUST.mimeType -> "Rust" // org.rust.lang.RsFileType
    // com.intellij.lang.javascript.JavaScriptFileType
    JAVASCRIPT.mimeType -> "JavaScript"
    // com.intellij.lang.javascript.TypeScriptFileType
    TYPESCRIPT.mimeType -> "TypeScript"
    AGSL.mimeType -> "AGSL" // com.android.tools.idea.lang.agsl.AgslFileType
    SHELL.mimeType -> "Shell Script" // com.intellij.sh.ShFileType
    YAML.mimeType -> "YAML" // org.jetbrains.yaml.YAMLFileType
    GO.mimeType -> "Go" // com.goide.GoFileType
    SCALA.mimeType -> "Scala" // org.jetbrains.plugins.scala.ScalaFileType
    R.mimeType -> "R" // org.jetbrains.r.RFileType
    RUBY.mimeType -> "Ruby" // org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
    PHP.mimeType -> "PHP" // com.jetbrains.php.lang.PhpFileType
    MATLAB.mimeType -> "Matlab" // com.github.kornilova203.matlab.MatlabFileType
    LUA.mimeType -> "lua" // com.tang.intellij.lua.lang.LuaFileType
    MARKDOWN.mimeType -> "Markdown" // org.intellij.plugins.markdown.lang.MarkdownFileType
    CSHARP.mimeType -> "C#" // com.jetbrains.rider.languages.fileTypes.csharp.CSharpFileType
    BATCH.mimeType -> "Batch" //  org.intellij.lang.batch.fileTypes.BatchFileType
    CLOJURE.mimeType -> "Clojure" // cursive.file.ClojureFileType
    COFFEESCRIPT.mimeType -> "CoffeeScript" // org.coffeescript.file.CoffeeScriptFileType
    CSS.mimeType -> "CSS" // com.intellij.psi.css.CssFileType
    FSHARP.mimeType -> "F#" // com.jetbrains.rider.ideaInterop.fileTypes.fsharp.FSharpFileType
    HANDLEBARS.mimeType -> "Handlebars" // com.dmarcotte.handlebars.file.HbFileType
    HAML.mimeType -> "Haml" // org.jetbrains.plugins.haml.HAMLFileType
    HTML.mimeType -> "HTML" // com.intellij.ide.highlighter.HtmlFileType
    INI.mimeType -> "Ini" // ini4idea.file.IniFileType
    TEX.mimeType -> "Latex" // nl.hannahsten.texifyidea.grammar.LatexFileType
    LESS.mimeType -> "LESS" // org.jetbrains.plugins.less.LESSFileType
    MAKEFILE.mimeType -> "Makefile" // com.jetbrains.lang.makefile.MakefileFileType
    POWERSHELL.mimeType -> "PowerShell" // com.intellij.plugin.powershell.PowerShellFileType
    JADE.mimeType -> "Jade" // com.jetbrains.plugins.jade.psi.JadeFileType
    SLIM.mimeType -> "Slim" // org.jetbrains.plugins.slim.SlimFileType
    SCSS.mimeType -> "SCSS" //  org.jetbrains.plugins.scss.SCSSFileType
    SASS.mimeType -> "SASS" // org.jetbrains.plugins.sass.SASSFileType
    STYLUS.mimeType -> "Stylus" // org.jetbrains.plugins.stylus.StylusFileType
    TSX.mimeType -> "TypeScript JSX" // com.intellij.lang.javascript.TypeScriptJSXFileType
    TERRAFORM.mimeType -> "HCL-Terraform" // org.intellij.terraform.config.TerraformFileType
    else -> null
  }
}

/** Returns the name of this language as it should appear in a markdown fenced block. */
fun MimeType.markdownLanguageName(): String? {
  return when (normalizeString()) {
    KOTLIN.mimeType -> "kotlin"
    JAVA.mimeType -> "java"
    XML.mimeType -> "xml"
    JSON.mimeType -> "json"
    TEXT.mimeType -> null
    REGEX.mimeType -> "regex"
    GROOVY.mimeType -> "groovy"
    TOML.mimeType -> "toml"
    C.mimeType -> "c"
    CPP.mimeType -> "c++"
    SVG.mimeType -> "svg"
    AIDL.mimeType -> "aidl"
    SQL.mimeType -> "sql"
    PROGUARD.mimeType -> null
    PROPERTIES.mimeType -> "properties"
    PROTO.mimeType -> "protobuf"
    PYTHON.mimeType -> "python"
    DART.mimeType -> "dart"
    RUST.mimeType -> "rust"
    JAVASCRIPT.mimeType -> "javascript"
    TYPESCRIPT.mimeType -> "typescript"
    AGSL.mimeType -> "sksl"
    SHELL.mimeType -> "sh"
    YAML.mimeType -> "yaml"
    GO.mimeType -> "go"
    SCALA.mimeType -> "scala"
    R.mimeType -> "r"
    RUBY.mimeType -> "ruby"
    PHP.mimeType -> "php"
    MATLAB.mimeType -> "matlab"
    LUA.mimeType -> "lua"
    MARKDOWN.mimeType -> "md"
    BATCH.mimeType -> "bat"
    CLOJURE.mimeType -> "clojure"
    COFFEESCRIPT.mimeType -> "coffeescript"
    CSS.mimeType -> "css"
    HAML.mimeType -> "haml"
    HTML.mimeType -> "html"
    INI.mimeType -> "ini"
    MAKEFILE.mimeType -> "make"
    JADE.mimeType -> "jade"
    SCSS.mimeType -> "scss"
    SASS.mimeType -> "sass"
    else -> null
  }
}

/**
 * If this mime type is using a well known alias, switch to the same internal mime type.
 *
 * This does preserve parameters, so for example `text/x-java-source; charset="utf-8"` would
 * normalize to `text/java; charset="utf-8"`. To get just the base language, call [base] instead.
 */
fun MimeType.normalize(): MimeType {
  return MimeType(normalizeString())
}

fun MimeType.withAttribute(attribute: String, value: String?): MimeType {
  value ?: return this
  val sb = StringBuilder(mimeType)
  sb.append("; ")
  sb.append(attribute).append('=').append(value)
  return MimeType(sb.toString())
}

/** Returns whether the given attribute should be included in a normalized string */
private fun isRelevantAttribute(attribute: String): Boolean {
  return when (attribute) {
    ATTR_ROLE,
    ATTR_ROOT_TAG,
    ATTR_FOLDER_TYPE -> true
    else -> false
  }
}

private fun MimeType.normalizeString(): String {
  when (this) {
    // Built-ins are already normalized, don't do string and sorting work
    KOTLIN,
    JAVA,
    TEXT,
    XML,
    PROPERTIES,
    TOML,
    JSON,
    REGEX,
    GROOVY,
    C,
    CPP,
    SVG,
    AIDL,
    PROTO,
    SQL,
    PROGUARD,
    MANIFEST,
    RESOURCE,
    GRADLE,
    GRADLE_KTS,
    VERSION_CATALOG,
    PYTHON,
    DART,
    RUST,
    JAVASCRIPT,
    TYPESCRIPT,
    AGSL,
    SHELL,
    YAML,
    GO,
    SCALA,
    R,
    RUBY,
    PHP,
    MATLAB,
    LUA,
    MARKDOWN,
    UNKNOWN -> return this.mimeType
  }

  val baseEnd = mimeType.indexOf(';')
  val normalizedBase =
    when (val base = if (baseEnd == -1) mimeType else mimeType.substring(0, baseEnd)) {
      "text/x-java-source",
      "application/x-java",
      "text/x-java" -> JAVA.mimeType
      "application/kotlin-source",
      "text/x-kotlin",
      "text/x-kotlin-source" -> KOTLIN.mimeType
      "application/xml" -> XML.mimeType
      "application/json",
      "application/vnd.api+json",
      "application/hal+json",
      "application/ld+json" -> JSON.mimeType
      "image/svg+xml" -> XML.mimeType
      "text/x-python",
      "application/x-python-script" -> PYTHON.mimeType
      "text/dart",
      "text/x-dart",
      "application/dart",
      "application/x-dart" -> DART.mimeType
      "application/javascript",
      "application/x-javascript",
      "text/ecmascript",
      "application/ecmascript",
      "application/x-ecmascript" -> JAVASCRIPT.mimeType
      "application/typescript" + "application/x-typescript" -> TYPESCRIPT.mimeType
      "text/x-rust",
      "application/x-rust" -> RUST.mimeType
      "text/x-sksl" -> AGSL.mimeType
      "application/yaml",
      "text/x-yaml",
      "application/x-yaml" -> YAML.mimeType
      "text/scala",
      "application/x-scala" -> SCALA.mimeType
      "application/x-r",
      "application/x-rscript",
      "text/x-rscript",
      "text/rscript" -> R.mimeType
      "text/x-ruby",
      "text/x-ruby-script" -> RUBY.mimeType
      "text/x-php",
      "application/x-httpd-php" -> PHP.mimeType
      "text/x-matlab",
      "application/matlab",
      "application/x-mat4",
      "application/x-mat73" -> MATLAB.mimeType
      "text/x-lua",
      "application/lua",
      "application/x-luac" -> LUA.mimeType
      "text/x-markdown" -> MARKDOWN.mimeType
      else -> {
        base
      }
    }
  return if (baseEnd == -1) {
    normalizedBase
  } else {
    val attributes =
      mimeType
        .split(';')
        .asSequence()
        .drop(1)
        .sorted()
        .mapNotNull {
          val index = it.indexOf('=')
          if (index != -1) {
            Pair(it.substring(0, index).trim(), it.substring(index + 1).trim())
          } else {
            null
          }
        }
        .filter { isRelevantAttribute(it.first) }
        .map { "${it.first}=${it.second}" }
        .joinToString("; ")
    if (attributes.isNotBlank()) {
      "$normalizedBase; $attributes"
    } else {
      normalizedBase
    }
  }
}

/**
 * Returns just the language portion of the mime type.
 *
 * For example, for `text/kotlin; role=gradle` this will return `text/kotlin`. For `text/plain;
 * charset=us-ascii` this returns `text/plain`
 */
fun MimeType.base(): MimeType {
  return if (mimeType.any { it == ';' || it.isWhitespace() }) {
    MimeType(mimeType.substringBefore(';').trim())
  } else {
    this
  }
}

/** Returns the default file extension for language source files of this type */
fun MimeType.extension(): String? {
  return fileType()?.defaultExtension
}

/** Returns true if this mime type represents text */
fun MimeType.isText(): Boolean {
  val base = base()
  return base.mimeType.startsWith("text/") || base == SHELL || base == DART
}

internal fun MimeType.getRole(): String? {
  return getAttribute(ATTR_ROLE)
}

private fun MimeType.getFolderType(): String? {
  return getAttribute(ATTR_FOLDER_TYPE)
}

fun MimeType.getAttribute(name: String): String? {
  val marker = "$name="
  var start = mimeType.indexOf(marker)
  if (start == -1) {
    return null
  }
  start += marker.length
  var end = start
  while (end < mimeType.length && !mimeType[end].isWhitespace() && mimeType[end] != ';') {
    end++
  }
  return mimeType.substring(start, end).removeSurrounding("\"")
}

/**
 * Checks whether this [MimeType] is "compatible" with the [other] [MimeType].
 *
 * Compatibility here refers to whether it makes sense to for example insert the other code snippet
 * into this one.
 *
 * This is usually true when the [base] language is the same; e.g. you can insert Java code into
 * Java, both KTS and Kotlin into Kotlin or KTS, etc.
 *
 * However, for some language dialects we want to be more strict; for example, you don't want to put
 * AndroidManifest content into a string resource file, or even a layout snippet into a drawable
 * file.
 */
fun MimeType.isCompatibleWith(other: MimeType?): Boolean {
  other ?: return false
  if (this == other) return true
  val language = base()
  val otherLanguage = other.base()
  if (language != otherLanguage) {
    return false
  }
  if (language == XML) {
    // If an attribute is missing, be permissive; we may have incomplete
    // information from the model, so allow the user to insert if they really
    // want to do that.

    val role = getRole()
    val otherRole = other.getRole()
    if (role != null && otherRole != null && role != otherRole) {
      // for example manifest versus resource
      return false
    }
    val folderType = getFolderType()
    val otherFolderType = other.getFolderType()
    if (folderType != null && otherFolderType != null && folderType != otherFolderType) {
      return false
    }
  }

  return true
}

/**
 * Instead of calling `MimeType.fromLanguage(psiElement.language)`, use this utility method.
 * [LanguageUtil.getLanguageForPsi] may give a different result than [PsiElement.getLanguage]
 * because of [com.intellij.psi.LanguageSubstitutor].
 *
 * See b/339030535.
 */
fun MimeType.Companion.forPsiElement(element: PsiElement): MimeType? {
  val virtualFile =
    element.containingFile?.virtualFile
      ?: return MimeType.fromLanguage(LanguageUtil.getRootLanguage(element))
  return MimeType.forVirtualFile(element.project, virtualFile)
}

fun MimeType.Companion.forVirtualFile(project: Project, file: VirtualFile): MimeType? {
  val ideLanguage = LanguageUtil.getLanguageForPsi(project, file) ?: return null
  return MimeType.fromLanguage(ideLanguage, file)
}

/**
 * Returns the [MimeType] for the given [ideLanguage], [virtualFile] and [source] contents, if any.
 *
 * If you just want the [MimeType] for a [VirtualFile] or [PsiElement], use
 * [MimeType.Companion.forVirtualFile] or [MimeType.Companion.forPsiElement] instead, because they
 * will go through the right utilities to get the most accurate language.
 */
fun MimeType.Companion.fromLanguage(
  ideLanguage: Language,
  virtualFile: VirtualFile? = null,
  source: CharSequence? = null,
): MimeType? {
  val baseType =
    when (ideLanguage.id) {
      "kotlin" ->
        if (virtualFile?.name?.endsWith(".gradle.kts") == true) {
          GRADLE_KTS
        } else {
          KOTLIN
        }
      "JAVA" -> JAVA
      "XML" -> XML
      "TEXT" -> TEXT
      "Properties" -> PROPERTIES
      "TOML" -> TOML
      "JSON" -> JSON
      "RegExp" -> REGEX
      "Groovy" ->
        return if (virtualFile?.name?.endsWith(".gradle.kts") == true) {
          GRADLE
        } else {
          GROOVY
        }
      "ObjectiveC" ->
        return if (virtualFile?.name?.endsWith(".c") == true) {
          C
        } else {
          CPP
        }
      "SVG" -> SVG
      "AIDL" -> AIDL
      "RoomSql" -> SQL
      "SQL" -> SQL
      "GenericSQL" -> SQL
      "SHRINKER_CONFIG" -> PROGUARD
      "protobuf" -> PROTO
      "Dart" -> DART
      "Rust" -> RUST
      "ECMAScript 6" -> JAVASCRIPT
      "JavaScript" -> JAVASCRIPT
      "TypeScript" -> TYPESCRIPT
      "Python" -> PYTHON
      "AGSL" -> AGSL
      "Shell Script" -> SHELL
      "yaml" -> YAML
      "YAML" -> YAML
      "Go" -> GO
      "Scala" -> SCALA
      "R" -> R
      "ruby" -> RUBY
      "Ruby" -> RUBY
      "PHP" -> PHP
      "Matlab" -> MATLAB
      "Lua" -> LUA
      "Markdown" -> MARKDOWN
      "C#" -> CSHARP
      "Batch" -> BATCH
      "Clojure" -> CLOJURE
      "CoffeeScript" -> COFFEESCRIPT
      "CSS" -> CSS
      "F#" -> FSHARP
      "Handlebars" -> HANDLEBARS
      "Haml" -> HAML
      "HTML" -> HTML
      "Ini" -> INI
      "Latex" -> TEX
      "LESS" -> LESS
      "Makefile" -> MAKEFILE
      "PowerShell" -> POWERSHELL
      "Jade" -> JADE
      "Slim" -> SLIM
      "SCSS" -> SCSS
      "SASS" -> SASS
      "Stylus" -> STYLUS
      "TypeScript JSX" -> TSX
      "HCL-Terraform" -> TERRAFORM
      else -> {
        virtualFile?.extension?.let { MimeType.fromExtension(it) }
          ?: ideLanguage.mimeTypes.firstOrNull()?.let { MimeType(it) }
      }
    }

  if (baseType == null) return null

  val refinedType = MimeTypeAugmenter.augment(baseType, virtualFile, source)
  return refinedType
}

/** Maps from a markdown language [name] back to a mime type. */
fun MimeType.Companion.fromMarkdownLanguageName(name: String): MimeType? {
  return when (name) {
    "kotlin",
    "kt",
    "kts" -> KOTLIN
    "java" -> JAVA
    "xml" -> XML
    "json",
    "json5" -> JSON
    "regex",
    "regexp" -> REGEX
    "groovy" -> GROOVY
    "toml" -> TOML
    "c" -> C
    "c++" -> CPP
    "svg" -> SVG
    "aidl" -> AIDL
    "sql" -> SQL
    "properties" -> PROPERTIES
    "protobuf" -> PROTO
    "python2",
    "python3",
    "py",
    "python" -> PYTHON
    "dart" -> DART
    "rust" -> RUST
    "js",
    "javascript" -> JAVASCRIPT
    "typescript" -> TYPESCRIPT
    "sksl" -> AGSL
    "sh",
    "bash",
    "zsh",
    "shell" -> SHELL
    "yaml",
    "yml" -> YAML
    "go",
    "golang" -> YAML
    "scala" -> SCALA
    "r",
    "rscript" -> R
    "ruby" -> RUBY
    "php" -> PHP
    "matlab" -> MATLAB
    "lua" -> LUA
    "md" -> MARKDOWN
    "bat" -> BATCH
    "clojure" -> COFFEESCRIPT
    "css" -> CSS
    "haml" -> HAML
    "html" -> HTML
    "ini" -> INI
    "make" -> MAKEFILE
    "jade" -> JADE
    "scss" -> SCSS
    "sass" -> SASS
    else -> null
  }
}

/**
 * Maps from a file extension to mime type. In case users do not install the language support
 * plugin, so there is no language ID related to the virtual file or psi element. This mapping is
 * derived from
 * http://google3/third_party/cloudcode/intellij/language_client/src/main/resources/languages_metadata/known_languages.properties
 */
fun MimeType.Companion.fromExtension(extension: String): MimeType? {
  return when (extension) {
    "bat",
    "cmd" -> BATCH
    "clj",
    "boot",
    "cl2",
    "cljc",
    "cljs",
    "cljs.hl",
    "cljscm",
    "cljx",
    "hic" -> CLOJURE
    "cake",
    "coffee",
    "_coffee",
    "cjsx",
    "cson",
    "iced" -> COFFEESCRIPT
    "c",
    "cats",
    "idc",
    "w" -> C
    "cpp",
    "c++",
    "cc",
    "cp",
    "cxx",
    "h",
    "h++",
    "hh",
    "hpp",
    "hxx",
    "inl",
    "ipp",
    "tcc",
    "tpp",
    "cu",
    "cuh",
    "m",
    "mm" -> CPP
    "cs",
    "cshtml",
    "csx" -> CSHARP
    "css" -> CSS
    "fs",
    "fsi",
    "fsx" -> FSHARP
    "go" -> GO
    "groovy",
    "grt",
    "gtpl",
    "gvy",
    "gsp" -> GROOVY
    "handlebars",
    "hbs" -> HANDLEBARS
    "haml",
    "haml.deface" -> HAML
    "html",
    "htm",
    "html.hl",
    "inc",
    "st",
    "xht",
    "xhtml" -> HTML
    "ini",
    "cfg",
    "prefs",
    "properties" -> INI
    "java" -> JAVA
    "js",
    "_js",
    "bones",
    "es6",
    "jake",
    "jsb",
    "jscad",
    "jsfl",
    "jsm",
    "jss",
    "jsx",
    "njs",
    "pac",
    "sjs",
    "ssjs",
    "sublime-build",
    "sublime-commands",
    "sublime-completions",
    "sublime-keymap",
    "sublime-macro",
    "sublime-menu",
    "sublime-mousemap",
    "sublime-project",
    "sublime-settings",
    "sublime-theme",
    "sublime-workspace",
    "sublime_metrics",
    "sublime_session",
    "xsjs",
    "xsjslib",
    "vue" -> JAVASCRIPT
    "scala" -> SCALA
    "json",
    "geojson",
    "lock",
    "topojson" -> JSON
    "tex",
    "aux",
    "bbx",
    "cbx",
    "dtx",
    "ins",
    "lbx",
    "ltx",
    "mkii",
    "mkiv",
    "mkvi",
    "sty",
    "toc" -> TEX
    "less" -> LESS
    "lua",
    "nse",
    "pd_lua",
    "rbxs",
    "wlua" -> LUA
    "d",
    "mak",
    "mk",
    "mkfile" -> MAKEFILE
    "md",
    "markdown",
    "mkd",
    "mkdn",
    "mkdown",
    "ron" -> MARKDOWN
    "php",
    "aw",
    "ctp",
    "php3",
    "php4",
    "php5",
    "phps",
    "phpt" -> PHP
    "fr",
    "nb",
    "ncl",
    "txt",
    "no" -> TEXT
    "ps1",
    "psd1",
    "psm1" -> POWERSHELL
    "jade" -> JADE
    "py",
    "bzl",
    "gyp",
    "lmi",
    "pyde",
    "pyp",
    "pyt",
    "pyw",
    "rpy",
    "tac",
    "wsgi",
    "xpy" -> PYTHON
    "r",
    "rd",
    "rsx" -> R
    "rb",
    "builder",
    "gemspec",
    "god",
    "irbrc",
    "jbuilder",
    "mspec",
    "pluginspec",
    "podspec",
    "rabl",
    "rake",
    "rbuild",
    "rbw",
    "rbx",
    "ru",
    "ruby",
    "thor",
    "watchr" -> RUBY
    "rs",
    "rs.in" -> RUST
    "scss" -> SCSS
    "sass" -> SASS
    "sh",
    "bash",
    "bats",
    "command",
    "ksh",
    "sh.in",
    "tmux",
    "tool",
    "zsh" -> SHELL
    "slim" -> SLIM
    "sql",
    "cql",
    "ddl",
    "prc",
    "tab",
    "udf",
    "viw" -> SQL
    "styl" -> STYLUS
    "swift" -> SWIFT
    "ts" -> TYPESCRIPT
    "tsx" -> TSX
    "xml",
    "ant",
    "axml",
    "ccxml",
    "clixml",
    "cproject",
    "csl",
    "csproj",
    "ct",
    "dita",
    "ditamap",
    "ditaval",
    "dll.config",
    "dotsettings",
    "filters",
    "fsproj",
    "fxml",
    "glade",
    "gml",
    "grxml",
    "iml",
    "ivy",
    "jelly",
    "jsproj",
    "kml",
    "launch",
    "mdpolicy",
    "mod",
    "mxml",
    "nproj",
    "nuspec",
    "odd",
    "osm",
    "plist",
    "props",
    "ps1xml",
    "psc1",
    "pt",
    "rdf",
    "rss",
    "scxml",
    "srdf",
    "storyboard",
    "sttheme",
    "sublime-snippet",
    "targets",
    "tmcommand",
    "tml",
    "tmlanguage",
    "tmpreferences",
    "tmsnippet",
    "tmtheme",
    "ui",
    "urdf",
    "ux",
    "vbproj",
    "vcxproj",
    "vssettings",
    "vxml",
    "wsdl",
    "wsf",
    "wxi",
    "wxl",
    "wxs",
    "x3d",
    "xacro",
    "xaml",
    "xib",
    "xlf",
    "xliff",
    "xmi",
    "xml.dist",
    "xproj",
    "xsd",
    "xul",
    "zcml" -> XML
    "xsl" -> XSL
    "yml",
    "reek",
    "rviz",
    "sublime-syntax",
    "syntax",
    "yaml",
    "yaml-tmlanguage" -> YAML
    "tf",
    "tf.json" -> TERRAFORM
    "kt",
    "ktm",
    "kts" -> KOTLIN
    else -> null
  }
}

/** Returns the [MimeType] for this editor, if any. */
fun Editor.getLanguage(): MimeType? {
  val project = project!!

  // Logic copied from internal API
  // com.intellij.openapi.fileEditor.impl.text.TextEditorImpl.getDocumentLanguage
  if (project.isDisposed) {
    logger<Editor>()
      .warn("Attempting to get a language for document on a disposed project: ${project.name}")
    return null
  }

  val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
  val ideLanguage = LanguageUtil.getRootLanguage(psiFile)

  return MimeType.fromLanguage(ideLanguage, virtualFile, document.immutableCharSequence)
}

/** Is the base language for this mime type Kotlin? */
fun MimeType?.isKotlin(): Boolean = this?.base() == KOTLIN

/** Is the base language for this mime type Java? */
fun MimeType?.isJava(): Boolean = this?.base() == JAVA

/** Is the base language for this mime type XML? */
fun MimeType?.isXml(): Boolean = this?.base() == XML

/** Is this a Gradle file (which could be in Groovy, *or*, Kotlin) */
fun MimeType?.isGradle(): Boolean = this?.getRole() == "gradle"

/** Is this a version catalog file (which could be in TOML, or in Groovy) */
fun MimeType?.isVersionCatalog(): Boolean = this?.getRole() == "version-catalog"

/** Is this an Android manifest file? */
fun MimeType?.isManifest(): Boolean = this?.getRole() == "manifest"

/** Is the base language for this mime type SQL? */
fun MimeType?.isSql(): Boolean = this?.base() == SQL

/** Is the base language for this mime type a regular expression? */
fun MimeType?.isRegex(): Boolean = this?.base() == REGEX

/** Is the base language for this mime type a protobuf? */
fun MimeType?.isProto(): Boolean = this?.base() == PROTO
