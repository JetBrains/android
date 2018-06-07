ProGuard Parser and Lexer
-------------------------

This directory, amongst other things, contains the grammar used to generate
the parser and lexer for editing ProGuard files.

The grammar is described by Proguard.bnf and Proguard.flex. Modifications made
to either file require re-running the parser generator (GrammarKit) and the
lexer generator (JFlex).


Prerequisites
-------------

Install the GrammarKit plugin for IDEA IntelliJ from the default plug-ins
repository. The URL for the plugin is:

https://plugins.jetbrains.com/plugin/6606

More documentation about GrammarKit can be found at:

https://github.com/JetBrains/Grammar-Kit


Regenerating the grammar
------------------------

Open "Proguard.bnf" in IntelliJ and select the "Tools => Generate Parser Code"
menu option. This will generate code into the top level "gen/" directory.

Open "Proguard.flex" in IntelliJ and select the "Tools => Run JFlex Generator"
menu option. If JFlex is not already installed, you will be prompted to
install it. This will generate "_ProguardLexer" into _this_ directory.
