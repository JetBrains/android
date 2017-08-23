#!/bin/sh
# This script generates _RenderscriptLexer.java from renderscript.flex
# Usage: ./runflex.sh <path/to/intellij-community-edition-src>/tools/lexer

JFLEX=$1

java -Dfile.encoding=UTF-8 \
	     -classpath $JFLEX/jflex-1.4/lib/JFlex.jar \
	     JFlex.Main --charat --nobak  \
	     --skel $JFLEX/idea-flex.skeleton \
	     --quiet \
	     renderscript.flex
