Custom language support
=======================

There are a couple of custom languages that the Android plugin needs to support. Implementations were added over time and by different
people, so they don't all follow the same conventions, but all are based on Grammar-Kit, which is the recommended way of creating parsers.
This document was written while implementing SQL support, so most examples are from the [roomSql](roomSql/) package. Documentation for
adding a new language to the IDE is available in the IntelliJ SDK docs, but here we keep additional notes that may be useful for engineers
tackling this problem in the future.

Note that the process of parsing e.g. Java (as well as the generated trees) is quite different, because the parser and all other pieces
are hand-written and customized/optimized as needed.

## Parsing

There are three stages to parsing: finding tokens (lexing), building the AST and building the PSI tree. These parts are defined in
a `ParserDefinition` extension class (see e.g. [RoomSqlParserDefinition](roomSql/parser/Parser.kt)).

### Lexing

The job of a lexer is to be able to find the next "token" (or "lexeme"). Given a `CharSequence` and some initial state, the lexer is asked
to `advance` and then asked how far it went and what's the type of token that was just consumed. The type is `IElementType` which is a class
used to represent types of both tokens and AST nodes. Conceptually lexers are deterministic finite automata: they have very little state (in
IJ the whole state needs to fit into one int) and are generated from an ordered list of regular expressions.

There is no JVM object representing a "token", but the parsing machinery keeps track of the segment of input recognized and its type. Later
in the pipeline a leaf AST node is created for every token.

IJ lexers always needs to consume all the input given. This also means that they need to generate tokens for whitespace (these are ignored
by the parsers). Our whitespace tokens use the standard `IElementType` defined in `TokenTypes.WHITE_SPACE`, but in general the types of
whitespace tokens is defined in the `ParserDefinition` and used by `PsiBuilderImpl`. Note that Grammar-Kit's live preview feature needs to
have a concept of whitespace: the heuristic it uses it look for a token type that matches a space and is not used otherwise. Some of our
grammars include such a token definition (in the `tokens` section of the `*.bnf` file) just to make live preview work.

We define our tokens in the `*.bnf` files. They can be:
 * Defined implicitly by just using the string in question. In the BNF grammar files, any (potentially quoted) word that is not a reference
   to a rule is considered a token that will match this exact word. This is how we handle SQL keywords.
 * Defined explicitly in the `tokens` section. If the value starts with `regexp:`, the given regular expression will be used to recognize
   the token. An explicitly defined token (like COMMA=',') can still be used in the grammar using it's value (',') for readability, but the
   name (COMMA) will be used to define the `IElementType` constant.

Based on this, Grammar-Kit generates an `IElementType` for every type of token, as well as a `*.flex` file, which (through a right-click
action provided by the Grammar-Kit plugin) can be used to generate a Flex lexer, e.g. `_RoomSqlLexer`. We use the standard `FlexAdapter`
class to turn this into a `Lexer` instance.

Here's an example of how all of this (may not) work together. Suppose we define a token for comments in the `*.bnf` file:

    {
      // ...
      tokens=[
        MUL='*'
        DIV='/'
        IDENTIFIER='regexp:[a-z]+'
        COMMENT='regexp:/\*.*\*/'
        // ...
      ]
    }

When the user types `foo /* bar` and the IDE decides to re-run the lexer, our `COMMENT` token will not get recognized (because we can't find
the closing `*/`) and so the lexer will emit `DIV` token for `/` and `MUL` for `*` and carry on, recognizing `bar` as an identifier. When
the user finishes typing and we end up with `foo /* bar */ baz`, IDE will restart the lexer somewhere around the last edit position. The
lexer will now find a space and then emit `MUL` token for `*` and `DIV` for `/` and recognize `baz` as an identifier. You can see this is
not what we wanted. Now if you change something in `foo`, IDE will restart the lexer at the beginning of the line and the comment will be
correctly recognized. So we end up with "undeterministic" lexing results. See `LexerEditorHighlighter.documentChanged` for details of how
incremental lexing is started in the last place lexer was in "initial state". The correct way to handle this depends on whether an
unterminated token is valid:

 * For comments, make the closing sequence (`*/`) optional. This way everything until the end of file is considered a comment, which is what
   we want usually.
 * Unterminated strings should be recognized as `BAD_CHARACTER`. See `_RoomSqlLexer.flex` for a way to implement it.

### Building the AST

AST is built by an instance of `PsiParser` (ours are generated by Grammar-Kit) recording the recognized parts of the tree in a `PsiBuilder`
as it reads more tokens from the lexer. See more details in IntelliJ SDK docs. Our parsers are generated by Grammar-Kit and are reasonably
easy to read and debug, since they implement the simple recursive descent algorithm. Note that the content of a `*.bnf` file is a Parser
Expression Grammar (PEG), not a Context Free Grammar (CFG). This may be different from what you're used to from other parser generators or
CS education. Please read the wikipedia entry on PEGs and Grammar-Kit's documentation for details, but here are some highlights:

 * There's no ambiguation: for every valid input, there's exactly one parse tree.
 * Alternatives are tried in order (from left to right) and the rule "finishes" when the first alternative is matched. This means that a
   rule like `reference ::= identifier | identifier '.' identifier` will never match `foo.bar` because the first alternative succeeds before
   the second has a chance to run.
 * "Repeating operations" are naively greedy and don't backtrack: `rule ::= a* a` will never succeed because the first part will consume all
   the `a` symbols before the second part has a chance to match anything.
 * Every rule can be used as a predicate for unlimited lookahead, e.g. `rule ::= !(a a a) (a | b)+` will match any sequence of `a` and `b`
   except for the ones starting with three `a` symbols.
 * Grammar-Kit rejects left-recursive grammars (which would make the parser go into an inifnite loop), but there's a special case for
   parsing arithmetic-like expressions, see Grammar-Kit docs.

Parsers generated by Grammar-Kit call methods in `GeneratedParserUtilBase` when they are have recognized a node. By default (if a language
doesn't provide a custom `ASTFactory`), this ends up creating instances of `LeafPsiElement` and `CompositeElement`. If we ignore the `Psi`
in `LeafPsiElement` for a moment, we can see that the AST is pretty boring: every node knows the range of text that it represents (or
contains) as well as its children, parent and type (of `IElementType`).

Because every character in the file is processed by the lexer (and so assigned an `IElementType`) and there's a leaf AST node for every
token, you can think of the AST as a "range tree" for the file characters. This also means that there's always more than one "element under
cursor": exactly one leaf plus all its parents.

### Building the PSI

As we noticed above, AST is quite boring. The whole point of building it is to re-use its "shape" for the PSI: a proper graph of java
objects of different types that have interesting methods etc.

We use Grammar-Kit to create the PSI classes as well (there's one for every non-private rule in the grammar). They inherit from
`ASTWrapperPsiElement` and there's one such wrapper `PsiElement` created for every AST node (and vice-versa). Code for mapping the
`IElementType` of an AST node to a constructor of the right PSI class is generated by Grammar-Kit in the `Factory` class and ends up called
by `ParserDefinition.createElement`.

Note that not every PSI tree is backed up an AST: that's the whole point of stub indexes. See IntelliJ SDK docs for more.
