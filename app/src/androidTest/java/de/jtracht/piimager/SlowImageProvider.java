package de.jtracht.piimager;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.IOException;

/** Fixed synthetic data only. Java keeps this separate provider process independent of target-app libraries. */
public final class SlowImageProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        if (!"r".equals(mode)) throw new java.io.FileNotFoundException("Read only");
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    byte[] data = new byte[512 * 1024];
                    for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 251);
                    for (int i = 0; i < 128; i++) { output.write(data); Thread.sleep(100); }
                } catch (IOException | InterruptedException ignored) { /* Reader cancellation closes the pipe. */ }
            }).start();
            return pipe[0];
        } catch (IOException failure) { throw new java.io.FileNotFoundException(failure.getMessage()); }
    }
}
