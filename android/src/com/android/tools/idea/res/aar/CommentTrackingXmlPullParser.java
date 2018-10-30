/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.aar;

import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;
import static com.android.SdkConstants.TAG_EAT_COMMENT;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * An {@link XmlPullParser} that keeps track of the last comment preceding an XML tag and special comments
 * that are used in the framework resource files for describing groups of "attr" resources. Here is
 * an example of an "attr" group comment:
 * <pre>
 *   &lt;!-- =========== --&gt;
 *   &lt;!-- Text styles --&gt;
 *   &lt;!-- =========== --&gt;
 *   &lt;eat-comment/&gt;
 * </pre>
 */
public class CommentTrackingXmlPullParser extends KXmlParser {
  // Used for parsing group of attributes, used heuristically to skip long comments before <eat-comment/>.
  private static final int ATTR_GROUP_MAX_CHARACTERS = 40;

  @Nullable String myLastComment;
  boolean tagEncounteredAfterComment;
  @Nullable String myAttrGroupComment;

  /**
   * Initializes the parser. XML namespaces are supported by default.
   */
  public CommentTrackingXmlPullParser() {
    try {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
    }
    catch (XmlPullParserException e) {
      throw new Error(e); // KXmlParser is guaranteed to support FEATURE_PROCESS_NAMESPACES.
    }
  }

  /**
   * Returns the last encountered comment that is not an ASCII art.
   */
  @Nullable
  public String getLastComment() {
    return myLastComment;
  }

  /**
   * Returns the name of the current "attr" group, e.g. "Button Styles" group for "buttonStyleSmall" "attr" tag.
   */
  @Nullable
  public String getAttrGroupComment() {
    return myAttrGroupComment;
  }

  @Override
  public int nextToken() throws XmlPullParserException, IOException {
    int token = super.nextToken();
    processToken(token);
    return token;
  }

  @Override
  public int next() throws XmlPullParserException, IOException {
    int token = super.next();
    processToken(token);
    return token;
  }

  private void processToken(int token) {
    switch (token) {
      case START_TAG:
        if (tagEncounteredAfterComment) {
          myLastComment = null;
        }
        tagEncounteredAfterComment = true;
        if (getPrefix() == null) {
          switch (getName()) {
            case TAG_EAT_COMMENT:
              // The framework attribute file follows a special convention where related attributes are grouped together,
              // and there is always a set of comments that indicate these sections which look like this:
              //     <!-- =========== -->
              //     <!-- Text styles -->
              //     <!-- =========== -->
              //     <eat-comment/>
              // These section headers are always immediately followed by an <eat-comment>. Not all <eat-comment/> sections are
              // actually attribute headers, some are comments. We identify these by looking at the line length; category comments
              // are short, and descriptive comments are longer.
              if (myLastComment != null && myLastComment.length() <= ATTR_GROUP_MAX_CHARACTERS && !myLastComment.startsWith("TODO:")) {
                myAttrGroupComment = myLastComment;
                if (myAttrGroupComment.endsWith(".")) {
                  myAttrGroupComment = myAttrGroupComment.substring(0, myAttrGroupComment.length() - 1); // Strip the trailing period.
                }
              }
              break;

            case TAG_DECLARE_STYLEABLE:
              myAttrGroupComment = null;
              break;
          }
        }
        break;

      case END_TAG:
        myLastComment = null;
        if (getName().equals(TAG_DECLARE_STYLEABLE) && getPrefix() == null) {
          myAttrGroupComment = null;
        }
        break;

      case COMMENT: {
        String commentText = getText().trim();
        if (!isEmptyOrAsciiArt(commentText)) {
          myLastComment = commentText;
          tagEncounteredAfterComment = false;
        }
        break;
      }
    }
  }

  @Override
  public void setInput(@NotNull Reader reader) throws XmlPullParserException {
    super.setInput(reader);
    myLastComment = null;
    myAttrGroupComment = null;
  }

  @Override
  public void setInput(@NotNull InputStream inputStream, @Nullable String encoding) throws XmlPullParserException {
    super.setInput(inputStream, encoding);
    myLastComment = null;
    myAttrGroupComment = null;
  }

  private static boolean isEmptyOrAsciiArt(@NotNull String commentText) {
    return commentText.isEmpty() || commentText.charAt(0) == '*' || commentText.charAt(0) == '=';
  }
}
