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

/**
 * This package contains implementation of support for all the different resource file formats
 * that are present in the Android framework. Read further to get some introductory remarks and
 * how to get more information.
 * <p/>
 * Each file format is defined by {@link com.intellij.util.xml.DomFileDescription} subclass, which provides a way to
 * tell apart XML files of different types. See {@link org.jetbrains.android.dom.transition.TransitionDomFileDescription} for an
 * example. For a case if you want to create a file format with single possible root, consider
 * using {@link org.jetbrains.android.dom.AbstractSingleRootFileDescription} instead of extending {@link com.intellij.util.xml.DomFileDescription}
 * directly. {@link com.intellij.util.xml.DomFileDescription} provides an interface that extends {@link com.intellij.util.xml.DomElement}
 * and describes schema of that file format. IntelliJ uses code generation to inspect these
 * interfaces and generate code that provides such features as code highlighting and completion,
 * and also gives you a way to manipulate XML files using these interfaces.
 * <a href="http://www.jetbrains.org/intellij/sdk/docs/reference_guide/frameworks_and_external_apis/xml_dom_api.html">
 * More info about that</a>
 * <p/>
 * File formats that are used by Android framework are loosely specified by documentation on
 * <a href="http://developer.android.com">developer.android.com</a>, but this information is quite often isn't accurate, and thus
 * framework inflaters should be used when implementing support for new formats / fixing issues
 * with support for existing ones. Please make sure to add pointers to framework code,
 * {@link com.intellij.util.xml.DomFileDescription} subclasses javadoc is a good place to store them.
 * <p/>
 * However, one problem is that implementation doesn't specify file formats fully. Parsing
 * code used in the framework doesn't enforce particular structure of XML files and can parse
 * more files successfully that seems to be intended. An example of that are transition manager
 * descriptions, see {@link org.jetbrains.android.dom.transitionManager.TransitionManagerDomFileDescription} for more information. The other
 * thing framework parsing code doesn't specify is resource folder where XML files should be
 * stored.
 * <p/>
 * More specifically, method loadXmlResourceParser from
 * <a href="http://developer.android.com/reference/android/content/res/Resources.html">Resources</a>
 * class in the framework uses "type" argument only for generating error messages in case of file hasn't been found.
 * In this case documentation from http://developer.android.com should be used to specify which
 * folder should files of which type stored. Unfortunately, documentation there about resource
 * types isn't clustered all in one place, but
 * <a href="http://developer.android.com/guide/topics/resources/providing-resources.html">this page</a> is a good
 * starting point for searching for relevant docs. Of course, when relevant documentation is
 * found you should consider adding a link to file description class as well.
 * <p/>
 * See Javadoc on <code>AndroidDomTest</code> class (and check its subclasses) to get an idea how to test changes to files in this package.
 * <p/>
 * Useful annotation facts:
 * <ul>
 *   <li>To disable spellchecking inside a tag's value, use {@link com.intellij.spellchecker.xml.NoSpellchecking} annotation.
 *   Look {@link com.intellij.spellchecker.xml.XmlSpellcheckingStrategy#isSuppressedFor(com.intellij.psi.PsiElement, java.lang.String)}
 *   for the code that implements that.</li>
 * </ul>
 *
 * {@link org.jetbrains.android.dom.AndroidDomExtender} contains code that adds information about file schema that isn't encoded in
 * DOM element interfaces. For example, one of the things this code does is loading information about attribute from Android framework
 * attribute definition files (attrs.xml and attrs_manifest.xml in the framework res/values folder). For a part of the tags, information
 * about which styleable definitions correspond to which tags is hard-coded, but there is a better mechanism for that -
 * {@link org.jetbrains.android.dom.Styleable} annotation. To provide information about styleable, which attributes should be used inside
 * an XML element, annotate its interface with @Styleable annotation, passing a list of names of styleable definitions.
 */
package org.jetbrains.android.dom;
