package com.android.tools.idea.lang.androidSql.parser;

import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.android.tools.idea.lang.androidSql.psi.AndroidSqlPsiTypes.*;
import static com.android.tools.idea.lang.androidSql.psi.LiteralTokenTypes.*;

%%

%{
  public _AndroidSqlLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _AndroidSqlLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%caseless

WHITE_SPACE=\s+

COMMENT="/*" ( ([^"*"]|[\r\n])* ("*"+ [^"*""/"] )? )* ("*" | "*"+"/")?
IDENTIFIER=([[:jletter:]--$])[:jletterdigit:]*
LINE_COMMENT=--[^\r\n]*
NUMERIC_LITERAL=(([0-9]+(\.[0-9]*)?|\.[0-9]+)(E(\+|-)?[0-9]+)?)|(0x[0-9a-f]+)
NAMED_PARAMETER=[:@$][:jletterdigit:]+
NUMBERED_PARAMETER=\?\d*

UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL=X?\'(\'\'|[^\'])*
SINGLE_QUOTE_STRING_LITERAL={UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL} \'
UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL=X?\"(\"\"|[^\"])*
DOUBLE_QUOTE_STRING_LITERAL={UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL} \"
UNTERMINATED_BACKTICK_LITERAL=\`(\`\`|[^\`])*
BACKTICK_LITERAL={UNTERMINATED_BACKTICK_LITERAL} \`
UNTERMINATED_BRACKET_LITERAL=\[[^\]]*
BRACKET_LITERAL={UNTERMINATED_BRACKET_LITERAL} \]

%%
<YYINITIAL> {
  {WHITE_SPACE}                       { return WHITE_SPACE; }

  "!="                                { return NOT_EQ; }
  "%"                                 { return MOD; }
  "&"                                 { return AMP; }
  "("                                 { return LPAREN; }
  ")"                                 { return RPAREN; }
  "*"                                 { return STAR; }
  "+"                                 { return PLUS; }
  ","                                 { return COMMA; }
  "-"                                 { return MINUS; }
  "."                                 { return DOT; }
  "/"                                 { return DIV; }
  ";"                                 { return SEMICOLON; }
  "<"                                 { return LT; }
  "<<"                                { return SHL; }
  "<="                                { return LTE; }
  "<>"                                { return UNEQ; }
  "="                                 { return EQ; }
  "=="                                { return EQEQ; }
  ">"                                 { return GT; }
  ">="                                { return GTE; }
  ">>"                                { return SHR; }
  "ABORT"                             { return ABORT; }
  "ACTION"                            { return ACTION; }
  "ADD"                               { return ADD; }
  "AFTER"                             { return AFTER; }
  "ALL"                               { return ALL; }
  "ALTER"                             { return ALTER; }
  "ANALYZE"                           { return ANALYZE; }
  "AND"                               { return AND; }
  "AS"                                { return AS; }
  "ASC"                               { return ASC; }
  "ATTACH"                            { return ATTACH; }
  "AUTOINCREMENT"                     { return AUTOINCREMENT; }
  "BEFORE"                            { return BEFORE; }
  "BEGIN"                             { return BEGIN; }
  "BETWEEN"                           { return BETWEEN; }
  "BY"                                { return BY; }
  "CASCADE"                           { return CASCADE; }
  "CASE"                              { return CASE; }
  "CAST"                              { return CAST; }
  "CHECK"                             { return CHECK; }
  "COLLATE"                           { return COLLATE; }
  "COLUMN"                            { return COLUMN; }
  "COMMIT"                            { return COMMIT; }
  "CONFLICT"                          { return CONFLICT; }
  "CONSTRAINT"                        { return CONSTRAINT; }
  "CREATE"                            { return CREATE; }
  "CROSS"                             { return CROSS; }
  "CURRENT"                           { return CURRENT; }
  "CURRENT_DATE"                      { return CURRENT_DATE; }
  "CURRENT_TIME"                      { return CURRENT_TIME; }
  "CURRENT_TIMESTAMP"                 { return CURRENT_TIMESTAMP; }
  "DATABASE"                          { return DATABASE; }
  "DEFAULT"                           { return DEFAULT; }
  "DEFERRABLE"                        { return DEFERRABLE; }
  "DEFERRED"                          { return DEFERRED; }
  "DELETE"                            { return DELETE; }
  "DESC"                              { return DESC; }
  "DETACH"                            { return DETACH; }
  "DISTINCT"                          { return DISTINCT; }
  "DROP"                              { return DROP; }
  "EACH"                              { return EACH; }
  "ELSE"                              { return ELSE; }
  "END"                               { return END; }
  "ESCAPE"                            { return ESCAPE; }
  "EXCEPT"                            { return EXCEPT; }
  "EXCLUDE"                           { return EXCLUDE; }
  "EXCLUSIVE"                         { return EXCLUSIVE; }
  "EXISTS"                            { return EXISTS; }
  "EXPLAIN"                           { return EXPLAIN; }
  "FAIL"                              { return FAIL; }
  "FALSE"                             { return FALSE; }
  "FILTER"                            { return FILTER; }
  "FOLLOWING"                         { return FOLLOWING; }
  "FOR"                               { return FOR; }
  "FOREIGN"                           { return FOREIGN; }
  "FROM"                              { return FROM; }
  "FULL"                              { return FULL; }
  "GLOB"                              { return GLOB; }
  "GROUP"                             { return GROUP; }
  "GROUPS"                            { return GROUPS; }
  "HAVING"                            { return HAVING; }
  "IF"                                { return IF; }
  "IGNORE"                            { return IGNORE; }
  "IMMEDIATE"                         { return IMMEDIATE; }
  "IN"                                { return IN; }
  "INDEX"                             { return INDEX; }
  "INDEXED"                           { return INDEXED; }
  "INITIALLY"                         { return INITIALLY; }
  "INNER"                             { return INNER; }
  "INSERT"                            { return INSERT; }
  "INSTEAD"                           { return INSTEAD; }
  "INTERSECT"                         { return INTERSECT; }
  "INTO"                              { return INTO; }
  "IS"                                { return IS; }
  "ISNULL"                            { return ISNULL; }
  "JOIN"                              { return JOIN; }
  "KEY"                               { return KEY; }
  "LEFT"                              { return LEFT; }
  "LIKE"                              { return LIKE; }
  "LIMIT"                             { return LIMIT; }
  "MATCH"                             { return MATCH; }
  "NATURAL"                           { return NATURAL; }
  "NO"                                { return NO; }
  "NOT"                               { return NOT; }
  "NOTNULL"                           { return NOTNULL; }
  "NULL"                              { return NULL; }
  "NUMBERED_PARAMETER"                { return NUMBERED_PARAMETER; }
  "OF"                                { return OF; }
  "OFFSET"                            { return OFFSET; }
  "ON"                                { return ON; }
  "OR"                                { return OR; }
  "ORDER"                             { return ORDER; }
  "OTHERS"                            { return OTHERS; }
  "OUTER"                             { return OUTER; }
  "OVER"                              { return OVER; }
  "PARTITION"                         { return PARTITION; }
  "PLAN"                              { return PLAN; }
  "PRAGMA"                            { return PRAGMA; }
  "PRECEDING"                         { return PRECEDING; }
  "PRIMARY"                           { return PRIMARY; }
  "QUERY"                             { return QUERY; }
  "RAISE"                             { return RAISE; }
  "RANGE"                             { return RANGE; }
  "RECURSIVE"                         { return RECURSIVE; }
  "REFERENCES"                        { return REFERENCES; }
  "REGEXP"                            { return REGEXP; }
  "REINDEX"                           { return REINDEX; }
  "RELEASE"                           { return RELEASE; }
  "RENAME"                            { return RENAME; }
  "REPLACE"                           { return REPLACE; }
  "RESTRICT"                          { return RESTRICT; }
  "RIGHT"                             { return RIGHT; }
  "ROLLBACK"                          { return ROLLBACK; }
  "ROW"                               { return ROW; }
  "ROWS"                              { return ROWS; }
  "SAVEPOINT"                         { return SAVEPOINT; }
  "SELECT"                            { return SELECT; }
  "SET"                               { return SET; }
  "TABLE"                             { return TABLE; }
  "TEMP"                              { return TEMP; }
  "TEMPORARY"                         { return TEMPORARY; }
  "THEN"                              { return THEN; }
  "TIES"                              { return TIES; }
  "TO"                                { return TO; }
  "TRANSACTION"                       { return TRANSACTION; }
  "TRIGGER"                           { return TRIGGER; }
  "TRUE"                              { return TRUE; }
  "UNBOUNDED"                         { return UNBOUNDED; }
  "UNION"                             { return UNION; }
  "UNIQUE"                            { return UNIQUE; }
  "UPDATE"                            { return UPDATE; }
  "USING"                             { return USING; }
  "VACUUM"                            { return VACUUM; }
  "VALUES"                            { return VALUES; }
  "VIEW"                              { return VIEW; }
  "VIRTUAL"                           { return VIRTUAL; }
  "WHEN"                              { return WHEN; }
  "WHERE"                             { return WHERE; }
  "WINDOW"                            { return WINDOW; }
  "WITH"                              { return WITH; }
  "WITHOUT"                           { return WITHOUT; }
  "|"                                 { return BAR; }
  "||"                                { return CONCAT; }
  "~"                                 { return TILDE; }

  {BACKTICK_LITERAL}                  { return BACKTICK_LITERAL; }
  {BRACKET_LITERAL}                   { return BRACKET_LITERAL; }
  {COMMENT}                           { return COMMENT; }
  {DOUBLE_QUOTE_STRING_LITERAL}       { return DOUBLE_QUOTE_STRING_LITERAL; }
  {IDENTIFIER}                        { return IDENTIFIER; }
  {LINE_COMMENT}                      { return LINE_COMMENT; }
  {NUMERIC_LITERAL}                   { return NUMERIC_LITERAL; }
  {NAMED_PARAMETER}                   { return NAMED_PARAMETER; }
  {NUMBERED_PARAMETER}                { return NUMBERED_PARAMETER; }
  {SINGLE_QUOTE_STRING_LITERAL}       { return SINGLE_QUOTE_STRING_LITERAL; }

  {UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL} { return UNTERMINATED_SINGLE_QUOTE_STRING_LITERAL; }
  {UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL} { return UNTERMINATED_DOUBLE_QUOTE_STRING_LITERAL; }
  {UNTERMINATED_BRACKET_LITERAL}             { return UNTERMINATED_BRACKET_LITERAL; }
  {UNTERMINATED_BACKTICK_LITERAL}            { return UNTERMINATED_BACKTICK_LITERAL; }
}

[^] { return BAD_CHARACTER; }
