# Undo and WriteCommandAction

What is the point of passing a list of PsiFiles when creating a
[WriteCommandAction](../../../../idea/platform/core-api/src/com/intellij/openapi/command/WriteCommandAction.java)?

This is to support IntelliJ's really great Undo architecture. If you make edits in 2 separate files, you may have noticed that you can
perform undoes and redoes in the two files completely independently of each other. In other words, each file has its own Undo history.

But, if you perform a Rename refactoring, that refactoring can touch a large number of files. And, you can undo the whole refactoring in a
single go! In fact you can go to any one of those affected files and perform that undo, and as long as none of the affected files have any
conflicting additional events after the refactoring, it will work.

THAT's the purpose of the file list passed to write command action: It lets the action know the set of affected files by the Undo, and it
will generate a single atomic undo event across all those files, which lets you undo the multi-file action, and at the same time let you
independently undo changes in *other* unrelated files.

If you have a giant action where it's difficult to predict the set of affected files, it's probably better to not specify any files at all;
that way the user can perform an undo to get rid of the whole change. But note that once you do this, a user can't go into one of their
previously edited source files (unrelated to your action) and undo one of their edits since the global undo event is on top of everything.
So if possible, try really hard to pre-compute the set of affected files.

When making new files, use the
[FileDocumentManager](../../../../idea/platform/core-api/src/com/intellij/openapi/fileEditor/FileDocumentManager.java) to get a
[Document](../../../../idea/platform/core-api/src/com/intellij/openapi/editor/Document.java) and call `setText` on it rather than calling
something like `virtualFile.setBinaryContent(text.getBytes(UTF_8)`. This works better with the undo manager (something we learned the hard
way when trying to make the New Project wizard's creation event be undoable.)
