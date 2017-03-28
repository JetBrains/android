package p1.p2;

import android.database.Cursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;

public class MyDocumentsProvider extends DocumentsProvider {
    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        return null;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        return null;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        return null;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        return null;
    }

    @Override
    public boolean onCreate() {
        return false;
    }
}
