package com.enguicode.spinlaunchertv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Write-only door for home.json, sensors.json and settings.json: `adb shell content write --uri content://com.enguicode.spinlaunchertv.config/home.json < home.json`.
 * Android 11 keeps adb out of the app's own folders, and the manifest guards this with WRITE_SECURE_SETTINGS,
 * which adb's shell holds and ordinary apps cannot get, so nothing else on the TV can swap the config or read the token.
 */
public class ConfigProvider extends ContentProvider {
    static final String FILE = "home.json";
    /** Optional room list, {"sensors":[...]}, kept apart from the token so it can change on its own. */
    static final String ROOMS = "sensors.json";
    /** Optional behaviour settings (see LauncherSettings), without the token. */
    static final String SETTINGS = "settings.json";

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!(FILE.equals(name) || ROOMS.equals(name) || SETTINGS.equals(name)) || !mode.contains("w")) throw new FileNotFoundException(uri.toString());
        File f = new File(getContext().getFilesDir(), name);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY
                | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
    }

    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return "application/json"; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
