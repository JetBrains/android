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
package com.android.tools.idea.lang.proguard.grammar;

import com.android.tools.idea.lang.proguard.psi.ProguardTypes;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;

/** A lexer for ProGuard files generated from Proguard.flex using JFlex. */
%%

%class _ProguardLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

CRLF = [ ]*[\n\r]+   // Newlines
WS = [ \t\f]+        // Whitespace

FLAG_NAME = -[a-zA-Z0-9_]+  // Flag name that includes the leading "-"
FLAG_ARG = [^ \n\r{#]+      // A single flag argument.
LINE_CMT = #[^\n\r]*        // A end of line comment, anything that starts with "#"
JAVA_DECL = [^\n\r}#]+;     // A single line of Java declaration in Java specification blocks.

OPEN_BRACE = "{"
CLOSE_BRACE = "}"

%state YYINITIAL
// Parses Java specifications allowed for certain flags.
%state STATE_JAVA_SECTION
// Parses flag arguments.
%state STATE_FLAG_ARG

%%

<YYINITIAL> {
    // Line comments.
    {LINE_CMT}       { return ProguardTypes.LINE_CMT; }

    // After a flag name is encountered, switch to the flag args state.
    {FLAG_NAME}      { yybegin(STATE_FLAG_ARG); return ProguardTypes.FLAG_NAME; }

    // Whitespace and newlines.
    {WS}             { return TokenType.WHITE_SPACE; }
    {CRLF}           { return ProguardTypes.CRLF; }
}

<STATE_FLAG_ARG> {
    // If an open brace is encountered, enter the java spec state.
    {OPEN_BRACE}     { yybegin(STATE_JAVA_SECTION); return ProguardTypes.OPEN_BRACE; }

    // Individual arguments.
    {FLAG_ARG}       { return ProguardTypes.FLAG_ARG; }

    // Line comments.
    {LINE_CMT}       { return ProguardTypes.LINE_CMT; }

    // Whitespace and newlines.
    {WS}             { return TokenType.WHITE_SPACE; }
    {CRLF}           { yybegin(YYINITIAL); return ProguardTypes.CRLF; }
}

<STATE_JAVA_SECTION> {
    // If a close brace is encountered, end the section and revert to original state.
    {CLOSE_BRACE}   { yybegin(YYINITIAL); return ProguardTypes.CLOSE_BRACE; }

    // Line comments.
    {LINE_CMT}      { return ProguardTypes.LINE_CMT; }

    // Whitespace and newlines.
    {WS}            { return TokenType.WHITE_SPACE; }
    {CRLF}          { return ProguardTypes.CRLF; }

    // A single line of Java declration.
    {JAVA_DECL}     { return ProguardTypes.JAVA_DECL; }
}

// If we've reached here, then the character is unrecognized.
[^] { return TokenType.BAD_CHARACTER; }
