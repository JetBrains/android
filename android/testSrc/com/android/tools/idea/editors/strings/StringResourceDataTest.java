/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.concurrency.AsyncTestUtils.waitForCondition;

import com.android.SdkConstants;
import com.android.ide.common.resources.Locale;
import com.android.projectmodel.DynamicResourceValue;
import com.android.resources.ResourceType;
import com.android.tools.idea.editors.strings.model.StringResourceKey;
import com.android.tools.idea.res.DynamicValueResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourcesTestsUtil;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class StringResourceDataTest extends AndroidTestCase {
  private VirtualFile resourceDirectory;
  private StringResourceData data;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFacet.getProperties().ALLOW_USER_CONFIGURATION = false;

    resourceDirectory = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    setUpData();
  }

  private void setUpData() throws Exception {
    DynamicResourceValue field = new DynamicResourceValue(ResourceType.STRING, "L'Étranger");

    DynamicValueResourceRepository dynamicRepository =
      DynamicValueResourceRepository.createForTest(myFacet, RES_AUTO, Collections.singletonMap("dynamic_key1", field));

    LocalResourceRepository moduleRepository =
      ResourcesTestsUtil.createTestModuleRepository(myFacet, Collections.singletonList(resourceDirectory), RES_AUTO, dynamicRepository);

    data = StringResourceData.create(myModule.getProject(), Utils.createStringRepository(moduleRepository));
  }

  public void testSummarizeLocales() {
    assertEquals("", StringResourceData.summarizeLocales(Collections.emptySet()));

    List<Locale> locales = Lists.newArrayList(Locale.create("fr"), Locale.create("en"));
    assertEquals("English (en) and French (fr)", StringResourceData.summarizeLocales(locales));

    locales = Lists.newArrayList(Locale.create("en"), Locale.create("fr"), Locale.create("hi"));
    assertEquals("English (en), French (fr) and Hindi (hi)", StringResourceData.summarizeLocales(locales));

    locales = Lists.newArrayList(Locale.create("en"), Locale.create("fr"), Locale.create("hi"), Locale.create("no"));
    assertEquals("English (en), French (fr), Hindi (hi) and 1 more", StringResourceData.summarizeLocales(locales));

    locales = Lists.newArrayList(Locale.create("en"), Locale.create("fr"), Locale.create("hi"), Locale.create("no"), Locale.create("ta"),
                                 Locale.create("es"), Locale.create("ro"));
    assertEquals("English (en), French (fr), Hindi (hi) and 4 more", StringResourceData.summarizeLocales(locales));
  }

  public void testParser() {
    Object actual = data.getLocaleSet().stream()
      .map(Locale::toLocaleId)
      .collect(Collectors.toSet());

    assertEquals(ImmutableSet.of("en", "en-GB", "en-IN", "fr", "hi"), actual);

    assertNotNull(data.getStringResource(newStringResourceKey("key1")).getDefaultValueAsResourceItem());

    assertFalse(data.getStringResource(newStringResourceKey("key5")).isTranslatable());

    assertNull(data.getStringResource(newStringResourceKey("key1")).getTranslationAsResourceItem(Locale.create("hi")));
    assertEquals("Key 2 hi", data.getStringResource(newStringResourceKey("key2")).getTranslationAsString(Locale.create("hi")));
  }

  public void testResourceToStringPsi() {
    Locale locale = Locale.create("fr");

    assertEquals("L'Étranger", data.getStringResource(newStringResourceKey("key8")).getTranslationAsString(locale));
    assertEquals("<![CDATA[L'Étranger]]>", data.getStringResource(newStringResourceKey("key9")).getTranslationAsString(locale));
    assertEquals("<xliff:g>L'Étranger</xliff:g>", data.getStringResource(newStringResourceKey("key10")).getTranslationAsString(locale));
  }

  public void testResourceToStringDynamic() {
    assertEquals("L'Étranger", data.getStringResource(new StringResourceKey("dynamic_key1")).getDefaultValueAsString());
  }

  public void testValidation() {
    assertEquals("Key 'key1' has translations missing for locales French (fr) and Hindi (hi)",
                 data.validateKey(newStringResourceKey("key1")));

    assertNull(data.validateKey(newStringResourceKey("key2")));
    assertNull(data.validateKey(newStringResourceKey("key3")));
    assertEquals("Key 'key4' missing default value", data.validateKey(newStringResourceKey("key4")));
    assertNull(data.validateKey(newStringResourceKey("key5")));

    assertEquals("Key 'key6' is marked as non translatable, but is translated in locale French (fr)",
                 data.validateKey(newStringResourceKey("key6")));

    assertEquals("Key \"key1\" is missing its Hindi (hi) translation",
                 data.getStringResource(newStringResourceKey("key1")).validateTranslation(Locale.create("hi")));

    assertNull(data.getStringResource(newStringResourceKey("key2")).validateTranslation(Locale.create("hi")));

    assertEquals("Key \"key6\" is untranslatable and should not be translated to French (fr)",
                 data.getStringResource(newStringResourceKey("key6")).validateTranslation(Locale.create("fr")));

    assertNull(data.getStringResource(newStringResourceKey("key1")).validateDefaultValue());
    assertEquals("Key \"key4\" is missing its default value", data.getStringResource(newStringResourceKey("key4")).validateDefaultValue());
  }

  public void testGetMissingTranslations() {
    // @formatter:off
    Collection<Locale> expected = ImmutableSet.of(
      Locale.create("en"),
      Locale.create("en-rGB"),
      Locale.create("en-rIN"),
      Locale.create("fr"),
      Locale.create("hi"));
    // @formatter:on

    assertEquals(expected, data.getMissingTranslations(newStringResourceKey("key7")));
  }

  public void testIsTranslationMissing() {
    assertTrue(data.getStringResource(newStringResourceKey("key7")).isTranslationMissing(Locale.create("fr")));
  }

  public void testRegionQualifier() {
    Locale en_rGB = Locale.create("en-rGB");
    assertTrue(data.getStringResource(newStringResourceKey("key4")).isTranslationMissing(en_rGB));
    assertFalse(data.getStringResource(newStringResourceKey("key3")).isTranslationMissing(en_rGB));
    assertFalse(data.getStringResource(newStringResourceKey("key8")).isTranslationMissing(en_rGB));
  }

  public void testEditingDoNotTranslate() {
    VirtualFile stringsFile = resourceDirectory.findFileByRelativePath("values/strings.xml");
    assertNotNull(stringsFile);

    assertTrue(data.getStringResource(newStringResourceKey("key1")).isTranslatable());
    XmlTag tag = getNthXmlTag(stringsFile, 0);
    assertEquals("key1", tag.getAttributeValue(SdkConstants.ATTR_NAME));
    assertNull(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE));

    data.setTranslatable(newStringResourceKey("key1"), false);

    assertFalse(data.getStringResource(newStringResourceKey("key1")).isTranslatable());
    tag = getNthXmlTag(stringsFile, 0);
    assertEquals(SdkConstants.VALUE_FALSE, tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE));

    assertFalse(data.getStringResource(newStringResourceKey("key5")).isTranslatable());
    tag = getNthXmlTag(stringsFile, 3);
    assertEquals("key5", tag.getAttributeValue(SdkConstants.ATTR_NAME));
    assertEquals(SdkConstants.VALUE_FALSE, tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE));

    data.setTranslatable(newStringResourceKey("key5"), true);

    assertTrue(data.getStringResource(newStringResourceKey("key5")).isTranslatable());
    tag = getNthXmlTag(stringsFile, 3);
    assertNull(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE));
  }

  public void testEditingCdata() throws Exception {
    String expected = "<![CDATA[\n" +
                      "        <b>Google I/O 2014</b><br>\n" +
                      "        Version %s<br><br>\n" +
                      "        <a href=\"http://www.google.com/policies/privacy/\">Privacy Policy</a>\n" +
                      "  ]]>";

    StringResource resource = data.getStringResource(newStringResourceKey("key1"));
    Locale locale = Locale.create("en-rIN");

    assertEquals(expected, resource.getTranslationAsString(locale));

    expected = "<![CDATA[\n" +
               "        <b>Google I/O 2014</b><br>\n" +
               "        Version %1$s<br><br>\n" +
               "        <a href=\"http://www.google.com/policies/privacy/\">Privacy Policy</a>\n" +
               "  ]]>";

    assertTrue(putTranslation(resource, locale, expected));
    assertEquals(expected, resource.getTranslationAsString(locale));

    VirtualFile file = resourceDirectory.findFileByRelativePath("values-en-rIN/strings.xml");
    assert file != null;

    XmlTag tag = getNthXmlTag(file, 0);

    assertEquals("key1", tag.getAttributeValue(SdkConstants.ATTR_NAME));
    assertEquals(expected, tag.getValue().getText());
  }

  public void testEditingXliff() throws Exception {
    StringResource resource = data.getStringResource(newStringResourceKey("key3"));
    Locale locale = Locale.create("en-rIN");

    assertEquals("start <xliff:g>middle1</xliff:g>%s<xliff:g>middle3</xliff:g> end", resource.getTranslationAsString(locale));

    String expected = "start <xliff:g>middle1</xliff:g>%1$s<xliff:g>middle3</xliff:g> end";

    assertTrue(putTranslation(resource, locale, expected));
    assertEquals(expected, resource.getTranslationAsString(locale));

    VirtualFile file = resourceDirectory.findFileByRelativePath("values-en-rIN/strings.xml");
    assert file != null;

    XmlTag tag = getNthXmlTag(file, 2);

    assertEquals("key3", tag.getAttributeValue(SdkConstants.ATTR_NAME));
    assertEquals(expected, tag.getValue().getText());
  }

  public void testAddingNewTranslation() throws Exception {
    StringResource resource = data.getStringResource(newStringResourceKey("key4"));
    Locale locale = Locale.create("en");

    assertNull(resource.getTranslationAsResourceItem(locale));
    assertTrue(putTranslation(resource, locale, "Hello"));
    assertEquals("Hello", resource.getTranslationAsString(locale));

    VirtualFile file = resourceDirectory.findFileByRelativePath("values-en/strings.xml");
    assert file != null;

    // There was no key4 in the default locale en, a new key would be appended to the end of file.
    XmlTag tag = getNthXmlTag(file, 4);

    assertEquals("key4", tag.getAttributeValue(SdkConstants.ATTR_NAME));
    assertEquals("Hello", tag.getValue().getText());
  }

  public void testInsertingTranslation() throws Exception {
    // Adding key 2 first then adding key 1.
    // To follow the order of default locale file, the tag of key 1 should be before key 2, even key 2 is added first.
    Locale locale = Locale.create("zh");

    StringResource resource2 = data.getStringResource(newStringResourceKey("key2"));
    assertNull(resource2.getTranslationAsResourceItem(locale));
    assertTrue(putTranslation(resource2, locale, "二"));
    assertEquals("二", resource2.getTranslationAsString(locale));

    StringResource resource4 = data.getStringResource(newStringResourceKey("key1"));
    assertNull(resource4.getTranslationAsResourceItem(locale));
    assertTrue(putTranslation(resource4, locale, "一"));
    assertEquals("一", resource4.getTranslationAsString(locale));

    VirtualFile file = resourceDirectory.findFileByRelativePath("values-zh/strings.xml");
    assert file != null;

    XmlTag tag1 = getNthXmlTag(file, 0);
    assertEquals("key1", tag1.getAttributeValue(SdkConstants.ATTR_NAME));
    assertEquals("一", tag1.getValue().getText());

    XmlTag tag2 = getNthXmlTag(file, 1);
    assertEquals("key2", tag2.getAttributeValue(SdkConstants.ATTR_NAME));
    assertEquals("二", tag2.getValue().getText());
  }

  private boolean putTranslation(@NotNull StringResource resource, @NotNull Locale locale, @NotNull String value)
      throws TimeoutException, InterruptedException, ExecutionException {
    ListenableFuture<Boolean> futureResult = resource.putTranslation(locale, value);
    waitForCondition(2, TimeUnit.SECONDS, futureResult::isDone);
    return futureResult.get();
  }

  @NotNull
  private StringResourceKey newStringResourceKey(@NotNull String name) {
    return new StringResourceKey(name, resourceDirectory);
  }

  private XmlTag getNthXmlTag(@NotNull VirtualFile file, int index) {
    PsiFile psiFile = PsiManager.getInstance(myFacet.getModule().getProject()).findFile(file);
    assert psiFile != null;

    XmlTag rootTag = ((XmlFile)psiFile).getRootTag();
    assert rootTag != null;

    return rootTag.findSubTags("string")[index];
  }
}
