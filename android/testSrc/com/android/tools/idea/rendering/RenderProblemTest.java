/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.rendering.ProblemSeverity.ERROR;
import static com.android.tools.idea.rendering.RenderProblem.PRIORITY_RENDERING_FIDELITY;
import static com.android.tools.idea.rendering.RenderProblem.PRIORITY_UNEXPECTED;

public class RenderProblemTest extends TestCase {
  public void testCreateFull() {
    RenderProblem message = RenderProblem.createPlain(ERROR, "This is a <test> !");
    assertEquals("This is a &lt;test> !", message.getHtml());
  }

  public void testCreateHtml() {
    RenderProblem.Html message = RenderProblem.create(ERROR);
    message.getHtmlBuilder().add("Plain").newline().addLink("mylink", "runnable:0").newline();
    message.getHtmlBuilder().beginList().listItem().add("item 1").listItem().add("item 2").endList();

    assertEquals("Plain<BR/>" +
                 "<A HREF=\"runnable:0\">mylink</A><BR/>" +
                 "<DL>" +
                 "<DD>-&NBSP;item 1" +
                 "<DD>-&NBSP;item 2" +
                 "</DL>", message.getHtml());
  }

  public void testSorting() {
    List<RenderProblem> list = new ArrayList<>();
    list.add(RenderProblem.createPlain(ERROR, "first").priority(PRIORITY_RENDERING_FIDELITY));
    list.add(RenderProblem.createPlain(ERROR, "second"));
    list.add(RenderProblem.createPlain(ERROR, "third"));
    list.add(RenderProblem.createPlain(ERROR, "fourth").priority(PRIORITY_UNEXPECTED));
    Collections.reverse(list);

    Collections.sort(list);
    assertEquals("second<br/>\n" +
                 "third<br/>\n" +
                 "fourth<br/>\n" +
                 "first<br/>\n", RenderProblem.format(list));
  }
}

