package com.RNFetchBlob;

import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.CursorLoader;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by wkh237 on 2016/5/26.
 */
public class RNFetchBlobFS {

    ReactApplicationContext mCtx;
    DeviceEventManagerModule.RCTDeviceEventEmitter emitter;
    String encoding = "base64";
    boolean append = false;
    OutputStream writeStreamInstance = null;
    static HashMap<String, RNFetchBlobFS> fileStreams = new HashMap<>();

    RNFetchBlobFS(ReactApplicationContext ctx) {
        this.mCtx = ctx;
        this.emitter = ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
    }

    static String getExternalFilePath(ReactApplicationContext ctx, String taskId, RNFetchBlobConfig config) {
        if(config.path != null)
            return config.path;
        else if(config.fileCache && config.appendExt != null)
            return RNFetchBlobFS.getTmpPath(ctx, taskId) + "." + config.appendExt;
        else
            return RNFetchBlobFS.getTmpPath(ctx, taskId);
    }

    /**
     * Write string with encoding to file
     * @param path Destination file path.
     * @param encoding Encoding of the string.
     * @param data Array passed from JS context.
     * @param promise
     */
    static public void writeFile(String path, String encoding, String data, final boolean append, final Promise promise) {
        AsyncTask<String, Integer, Integer> task = new AsyncTask<String, Integer, Integer>() {
            @Override
            protected Integer doInBackground(String... args) {
                try {
                    String path = args[0];
                    String encoding = args[1];
                    String data = args[2];
                    File f = new File(path);
                    File dir = f.getParentFile();
                    if(!dir.exists())
                        dir.mkdirs();
                    FileOutputStream fout = new FileOutputStream(f, append);
                    fout.write(stringToBytes(data, encoding));
                    fout.close();
                    promise.resolve(Arguments.createArray());
                } catch (Exception e) {
                    promise.reject("RNFetchBlob writeFileError", e.getLocalizedMessage());
                }
                return null;
            }
        };
        task.execute(path, encoding, data);
    }

    /**
     * Write array of bytes into file
     * @param path Destination file path.
     * @param data Array passed from JS context.
     * @param promise
     */
    static public void writeFile(String path, ReadableArray data, final boolean append, final Promise promise) {
        AsyncTask<Object, Void, Void> task = new AsyncTask<Object, Void, Void>() {
            @Override
            protected Void doInBackground(Object... args) {
                try {
                    String path = (String)args[0];
                    ReadableArray data = (ReadableArray) args[1];
                    File f = new File(path);
                    File dir = f.getParentFile();
                    if(!dir.exists())
                        dir.mkdirs();
                    FileOutputStream os = new FileOutputStream(f, append);
                    byte [] bytes = new byte[data.size()];
                    for(int i=0;i<data.size();i++) {
                        bytes[i] = (byte) data.getInt(i);
                    }
                    os.write(bytes);
                    os.close();
                    promise.resolve(null);
                } catch (Exception e) {
                    promise.reject("RNFetchBlob writeFileError", e.getLocalizedMessage());
                }
                return null;
            }
        };
        task.execute(path, data);
    }

    /**
     * Read file with a buffer that has the same size as the target file.
     * @param path  Path of the file.
     * @param encoding  Encoding of read stream.
     * @param promise
     */
    static public void readFile(String path, String encoding, final Promise promise ) {
        path = normalizePath(path);
        AsyncTask<String, Integer, Integer> task = new AsyncTask<String, Integer, Integer>() {
            @Override
            protected Integer doInBackground(String... strings) {

                try {
                    String path = strings[0];
                    String encoding = strings[1];
                    byte[] bytes;

                    if(path.startsWith(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET)) {
                        String assetName = path.replace(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET, "");
                        long length = RNFetchBlob.RCTContext.getAssets().openFd(assetName).getLength();
                        bytes = new byte[(int) length];
                        InputStream in = RNFetchBlob.RCTContext.getAssets().open(assetName);
                        in.read(bytes, 0, (int) length);
                        in.close();
                    }
                    else {
                        File f = new File(path);
                        int length = (int) f.length();
                        bytes = new byte[length];
                        FileInputStream in = new FileInputStream(f);
                        in.read(bytes);
                        in.close();
                    }

                    switch (encoding.toLowerCase()) {
                        case "base64" :
                            promise.resolve(Base64.encodeToString(bytes, Base64.NO_WRAP));
                            break;
                        case "ascii" :
                            WritableArray asciiResult = Arguments.createArray();
                            for(byte b : bytes) {
                                asciiResult.pushInt((int)b);
                            }
                            promise.resolve(asciiResult);
                            break;
                        case "utf8" :
                            promise.resolve(new String(bytes));
                            break;
                        default:
                            promise.resolve(new String(bytes));
                            break;
                    }
                }
                catch(Exception err) {
                    promise.reject("ReadFile Error", err.getLocalizedMessage());
                }
                return null;
            }
        };
        task.execute(path, encoding);
    }

    /**
     * Static method that returns system folders to JS context
     * @param ctx   React Native application context
     */
    static public Map<String, Object> getSystemfolders(ReactApplicationContext ctx) {
        Map<String, Object> res = new HashMap<>();
        res.put("DocumentDir", ctx.getFilesDir().getAbsolutePath());
        res.put("CacheDir", ctx.getCacheDir().getAbsolutePath());
        res.put("DCIMDir", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        res.put("PictureDir", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
        res.put("MusicDir", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
        res.put("DownloadDir", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        res.put("MovieDir", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath());
        res.put("RingtoneDir", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES).getAbsolutePath());
        return res;
    }

    /**
     * Static method that returns a temp file path
     * @param ctx   React Native application context
     * @param taskId    An unique string for identify
     * @return
     */
    static public String getTmpPath(ReactApplicationContext ctx, String taskId) {
        return ctx.getFilesDir() + "/RNFetchBlobTmp_" + taskId;
    }

    /**
     * Create a file stream for read
     * @param path  File stream target path
     * @param encoding  File stream decoder, should be one of `base64`, `utf8`, `ascii`
     * @param bufferSize    Buffer size of read stream, default to 4096 (4095 when encode is `base64`)
     */
    public void readStream( String path, String encoding, int bufferSize) {
        path = normalizePath(path);
        AsyncTask<String, Integer, Integer> task = new AsyncTask<String, Integer, Integer>() {
            @Override
            protected Integer doInBackground(String ... args) {
                String path = args[0];
                String encoding = args[1];
                int bufferSize = Integer.parseInt(args[2]);
                String eventName = "RNFetchBlobStream+" + path;
                try {

                    int chunkSize = encoding.equalsIgnoreCase("base64") ? 4095 : 4096;
                    if(bufferSize > 0)
                        chunkSize = bufferSize;

                    InputStream fs;
                    if(path.startsWith(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET)) {
                        fs = RNFetchBlob.RCTContext.getAssets().open(path.replace(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET, ""));
                    }
                    else {
                        fs = new FileInputStream(new File(path));
                    }

                    byte[] buffer = new byte[chunkSize];
                    int cursor = 0;
                    boolean error = false;

                    if (encoding.equalsIgnoreCase("utf8")) {
                        while ((cursor = fs.read(buffer)) != -1) {
                            String chunk = new String(buffer, 0, cursor, "UTF-8");
                            emitStreamEvent(eventName, "data", chunk);
                        }
                    } else if (encoding.equalsIgnoreCase("ascii")) {
                        while ((cursor = fs.read(buffer)) != -1) {
                            WritableArray chunk = Arguments.createArray();
                            for(int i =0;i<cursor;i++)
                            {
                                chunk.pushInt((int)buffer[i]);
                            }
                            emitStreamEvent(eventName, "data", chunk);
                        }
                    } else if (encoding.equalsIgnoreCase("base64")) {
                        while ((cursor = fs.read(buffer)) != -1) {
                            if(cursor < chunkSize) {
                                byte [] copy = new byte[cursor];
                                for(int i =0;i<cursor;i++) {
                                    copy[i] = buffer[i];
                                }
                                emitStreamEvent(eventName, "data", Base64.encodeToString(copy, Base64.NO_WRAP));
                            }
                            else
                                emitStreamEvent(eventName, "data", Base64.encodeToString(buffer, Base64.NO_WRAP));
                        }
                    } else {
                        String msg = "unrecognized encoding `" + encoding + "`";
                        emitStreamEvent(eventName, "error", msg);
                        error = true;
                    }

                    if(!error)
                        emitStreamEvent(eventName, "end", "");
                    fs.close();
                    buffer = null;

                } catch (Exception err) {
                    emitStreamEvent(eventName, "error", err.getLocalizedMessage());
                }
                return null;
            }
        };
        task.execute(path, encoding, String.valueOf(bufferSize));
    }

    /**
     * Create a write stream and store its instance in RNFetchBlobFS.fileStreams
     * @param path  Target file path
     * @param encoding Should be one of `base64`, `utf8`, `ascii`
     * @param append    Flag represents if the file stream overwrite existing content
     * @param callback
     */
    public void writeStream(String path, String encoding, boolean append, Callback callback) {
        File dest = new File(path);
        if(!dest.exists() || dest.isDirectory()) {
            callback.invoke("write stream error: target path `" + path + "` may not exists or it's a folder");
            return;
        }
        try {
            OutputStream fs = new FileOutputStream(path, append);
            this.encoding = encoding;
            this.append = append;
            String streamId = UUID.randomUUID().toString();
            RNFetchBlobFS.fileStreams.put(streamId, this);
            this.writeStreamInstance = fs;
            callback.invoke(null, streamId);
        } catch(Exception err) {
            callback.invoke("write stream error: failed to create write stream at path `"+path+"` "+ err.getLocalizedMessage());
        }

    }

    /**
     * Write a chunk of data into a file stream.
     * @param streamId File stream ID
     * @param data  Data chunk in string format
     * @param callback JS context callback
     */
    static void writeChunk(String streamId, String data, Callback callback) {

        RNFetchBlobFS fs = fileStreams.get(streamId);
        OutputStream stream = fs.writeStreamInstance;
        byte [] chunk = RNFetchBlobFS.stringToBytes(data, fs.encoding);

        try {
            stream.write(chunk);
            callback.invoke();
        } catch (Exception e) {
            callback.invoke(e.getLocalizedMessage());
        }
    }

    /**
     * Write data using ascii array
     * @param streamId File stream ID
     * @param data  Data chunk in ascii array format
     * @param callback JS context callback
     */
    static void writeArrayChunk(String streamId, ReadableArray data, Callback callback) {

        RNFetchBlobFS fs = fileStreams.get(streamId);
        OutputStream stream = fs.writeStreamInstance;
        byte [] chunk = new byte[data.size()];
        for(int i =0; i< data.size();i++) {
            chunk[i] = (byte) data.getInt(i);
        }
        try {
            stream.write(chunk);
            callback.invoke();
            chunk = null;
        } catch (Exception e) {
            callback.invoke(e.getLocalizedMessage());
        }
    }

    /**
     * Close file write stream by ID
     * @param streamId Stream ID
     * @param callback JS context callback
     */
    static void closeStream(String streamId, Callback callback) {
        try {
            RNFetchBlobFS fs = fileStreams.get(streamId);
            OutputStream stream = fs.writeStreamInstance;
            fileStreams.remove(streamId);
            stream.close();
            callback.invoke();
        } catch(Exception err) {
            callback.invoke(err.getLocalizedMessage());
        }
    }

    /**
     * Unlink file at path
     * @param path  Path of target
     * @param callback  JS context callback
     */
    static void unlink(String path, Callback callback) {
        try {
            boolean success = new File(path).delete();
            callback.invoke( null, success);
        } catch(Exception err) {
            if(err != null)
            callback.invoke(err.getLocalizedMessage());
        }
    }
    /**
     * Make a folder
     * @param path Source path
     * @param callback  JS context callback
     */
    static void mkdir(String path, Callback callback) {
        File dest = new File(path);
        if(dest.exists()) {
            callback.invoke("mkdir error: failed to create folder at `" + path + "` folder already exists");
            return;
        }
        dest.mkdirs();
        callback.invoke();
    }
    /**
     * Copy file to destination path
     * @param path Source path
     * @param dest Target path
     * @param callback  JS context callback
     */
    static void cp(String path, String dest, Callback callback) {
        path = normalizePath(path);
        InputStream in = null;
        OutputStream out = null;

        try {

            if(!isPathExists(path)) {
                callback.invoke("cp error: source file at path`" + path + "` not exists");
                return;
            }

            if(!new File(dest).exists())
                new File(dest).createNewFile();

            in = inputStreamFromPath(path);
            out = new FileOutputStream(dest);

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }

        } catch (Exception err) {
            if(err != null)
                callback.invoke(err.getLocalizedMessage());
        } finally {
            try {
                in.close();
                out.close();
                callback.invoke();
            } catch (IOException e) {
                callback.invoke(e.getLocalizedMessage());
            }
        }
    }

    /**
     * Move file
     * @param path Source file path
     * @param dest Destination file path
     * @param callback JS context callback
     */
    static void mv(String path, String dest, Callback callback) {
        File src = new File(path);
        if(!src.exists()) {
            callback.invoke("mv error: source file at path `" + path + "` does not exists");
            return;
        }
        src.renameTo(new File(dest));
        callback.invoke();
    }

    /**
     * Check if the path exists, also check if it is a folder when exists.
     * @param path Path to check
     * @param callback  JS context callback
     */
    static void exists(String path, Callback callback) {
        path = normalizePath(path);
        if(isAsset(path)) {
            try {
                String filename = path.replace(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET, "");
                AssetFileDescriptor fd = RNFetchBlob.RCTContext.getAssets().openFd(filename);
                callback.invoke(true, false);
            } catch (IOException e) {
                callback.invoke(false, false);
            }
        }
        else {
            boolean exist = new File(path).exists();
            boolean isDir = new File(path).isDirectory();
            callback.invoke(exist, isDir);
        }

    }

    /**
     * List content of folder
     * @param path Target folder
     * @param callback  JS context callback
     */
    static void ls(String path, Callback callback) {
        path = normalizePath(path);
        File src = new File(path);
        if (!src.exists() || !src.isDirectory()) {
            callback.invoke("ls error: failed to list path `" + path + "` for it is not exist or it is not a folder");
            return;
        }
        String[] files = new File(path).list();
        WritableArray arg = Arguments.createArray();
        for (String i : files) {
            arg.pushString(i);
        }
        callback.invoke(null, arg);
    }

    static void lstat(String path, final Callback callback) {
        path = normalizePath(path);

        new AsyncTask<String, Integer, Integer>() {
            @Override
            protected Integer doInBackground(String ...args) {
                WritableArray res = Arguments.createArray();
                File src = new File(args[0]);
                if(!src.exists()) {
                    callback.invoke("lstat error: failed to list path `" + args[0] + "` for it is not exist or it is not a folder");
                    return 0;
                }
                if(src.isDirectory()) {
                    String [] files = src.list();
                    for(String p : files) {
                        res.pushMap(statFile ( src.getPath() + "/" + p));
                    }
                }
                else {
                    res.pushMap(statFile(src.getAbsolutePath()));
                }
                callback.invoke(null, res);
                return 0;
            }
        }.execute(path);
    }

    /**
     * show status of a file or directory
     * @param path
     * @param callback
     */
    static void stat(String path, Callback callback) {
        try {
            WritableMap result = statFile(path);
            if(result == null)
                callback.invoke("stat error: failed to list path `" + path + "` for it is not exist or it is not a folder", null);
            else
                callback.invoke(null, result);
        } catch(Exception err) {
            callback.invoke(err.getLocalizedMessage());
        }
    }

    /**
     * Basic stat method
     * @param path
     * @return Stat result of a file or path
     */
    static WritableMap statFile(String path) {
        try {
            path = normalizePath(path);
            WritableMap stat = Arguments.createMap();
            if(isAsset(path)) {
                String name = path.replace(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET, "");
                AssetFileDescriptor fd = RNFetchBlob.RCTContext.getAssets().openFd(name);
                stat.putString("filename", name);
                stat.putString("path", path);
                stat.putString("type", "asset");
                stat.putString("size", String.valueOf(fd.getLength()));
                stat.putString("lastModified", "0");
            }
            else {
                File target = new File(path);
                if (!target.exists()) {
                    return null;
                }
                stat.putString("filename", target.getName());
                stat.putString("path", target.getPath());
                stat.putString("type", target.isDirectory() ? "directory" : "file");
                stat.putString("size", String.valueOf(target.length()));
                String lastModified = String.valueOf(target.lastModified());
                stat.putString("lastModified", lastModified);

            }
            return stat;
        } catch(Exception err) {
            return null;
        }
    }

    /**
     * Media scanner scan file
     * @param path
     * @param mimes
     * @param callback
     */
    void scanFile(String [] path, String[] mimes, final Callback callback) {
        try {
            MediaScannerConnection.scanFile(mCtx, path, mimes, new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted(String s, Uri uri) {
                    callback.invoke(null, true);
                }
            });
        } catch(Exception err) {
            callback.invoke(err.getLocalizedMessage(), null);
        }
    }

    /**
     * Create new file at path
     * @param path
     * @param data
     * @param encoding
     * @param callback
     */
    static void createFile(String path, String data, String encoding, Callback callback) {
        try {
            File dest = new File(path);
            boolean created = dest.createNewFile();
            if(!created) {
                callback.invoke("create file error: failed to create file at path `" + path + "` for its parent path may not exists");
                return;
            }
            OutputStream ostream = new FileOutputStream(dest);
            ostream.write(RNFetchBlobFS.stringToBytes(data, encoding));
            callback.invoke(null, path);
        } catch(Exception err) {
            callback.invoke(err.getLocalizedMessage());
        }
    }

    /**
     * Create file for ASCII encoding
     * @param path  Path of new file.
     * @param data  Content of new file
     * @param callback  JS context callback
     */
    static void createFileASCII(String path, ReadableArray data, Callback callback) {
        try {
            File dest = new File(path);
            if(dest.exists()) {
                callback.invoke("create file error: failed to create file at path `" + path + "`, file already exists.");
                return;
            }
            boolean created = dest.createNewFile();
            if(!created) {
                callback.invoke("create file error: failed to create file at path `" + path + "` for its parent path may not exists");
                return;
            }
            OutputStream ostream = new FileOutputStream(dest);
            byte [] chunk = new byte[data.size()];
            for(int i =0; i<data.size();i++) {
                chunk[i] = (byte) data.getInt(i);
            }
            ostream.write(chunk);
            chunk = null;
            callback.invoke(null, path);
        } catch(Exception err) {
            callback.invoke(err.getLocalizedMessage());
        }
    }

    /**
     * Remove files in session.
     * @param paths An array of file paths.
     * @param callback JS contest callback
     */
    static void removeSession(ReadableArray paths, final Callback callback) {

        AsyncTask<ReadableArray, Integer, Integer> task = new AsyncTask<ReadableArray, Integer, Integer>() {
            @Override
            protected Integer doInBackground(ReadableArray ...paths) {
                try {
                    for (int i = 0; i < paths[0].size(); i++) {
                        File f = new File(paths[0].getString(i));
                        if (f.exists())
                            f.delete();
                    }
                    callback.invoke(null, true);
                } catch(Exception err) {
                    callback.invoke(err.getLocalizedMessage());
                }
                return paths[0].size();
            }
        };
        task.execute(paths);
    }

    /**
     * String to byte converter method
     * @param data  Raw data in string format
     * @param encoding Decoder name
     * @return  Converted data byte array
     */
    private static byte[] stringToBytes(String data, String encoding) {
        if(encoding.equalsIgnoreCase("ascii")) {
            return data.getBytes(Charset.forName("US-ASCII"));
        }
        else if(encoding.equalsIgnoreCase("base64")) {
            return Base64.decode(data, Base64.NO_WRAP);
        }
        else if(encoding.equalsIgnoreCase("utf8")) {
            return data.getBytes(Charset.forName("UTF-8"));
        }
        return data.getBytes(Charset.forName("US-ASCII"));
    }

    /**
     * Private method for emit read stream event.
     * @param streamName    ID of the read stream
     * @param event Event name, `data`, `end`, `error`, etc.
     * @param data  Event data
     */
    void emitStreamEvent(String streamName, String event, String data) {
        WritableMap eventData = Arguments.createMap();
        eventData.putString("event", event);
        eventData.putString("detail", data);
        this.emitter.emit(streamName, eventData);
    }

    void emitStreamEvent(String streamName, String event, WritableArray  data) {
        WritableMap eventData = Arguments.createMap();
        eventData.putString("event", event);
        eventData.putArray("detail", data);
        this.emitter.emit(streamName, eventData);
    }

    // TODO : should we remove this ?
    void emitFSData(String taskId, String event, String data) {
        WritableMap eventData = Arguments.createMap();
        eventData.putString("event", event);
        eventData.putString("detail", data);
        this.emitter.emit("RNFetchBlobStream" + taskId, eventData);
    }

    /**
     * Get input stream of the given path, when the path is a string starts with bundle-assets://
     * the stream is created by Assets Manager, otherwise use FileInputStream.
     * @param path
     * @return
     * @throws IOException
     */
    static InputStream inputStreamFromPath(String path) throws IOException {
        if (path.startsWith(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET)) {
            return RNFetchBlob.RCTContext.getAssets().open(path.replace(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET, ""));
        }
        return new FileInputStream(new File(path));
    }

    /**
     * Check if the asset or the file exists
     * @param path
     * @return
     */
    static boolean isPathExists(String path) {
        if(path.startsWith(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET)) {
            try {
                RNFetchBlob.RCTContext.getAssets().open(path.replace(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET, ""));
            } catch (IOException e) {
                return false;
            }
            return true;
        }
        else {
            return new File(path).exists();
        }

    }

    public static boolean isAsset(String path) {
        return path.startsWith(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET);
    }

    public static String normalizePath(String path) {
        if(path.startsWith(RNFetchBlobConst.FILE_PREFIX_BUNDLE_ASSET)) {
            return path;
        }
        else if (path.startsWith(RNFetchBlobConst.FILE_PREFIX_CONTENT)) {
            String filePath = null;
            Uri uri = Uri.parse(path);
            if (uri != null && "content".equals(uri.getScheme())) {
                ContentResolver resolver = RNFetchBlob.RCTContext.getContentResolver();
                Cursor cursor = resolver.query(uri, new String[] { MediaStore.Images.ImageColumns.DATA }, null, null, null);
                cursor.moveToFirst();
                filePath = cursor.getString(0);
                cursor.close();
            } else {
                filePath = uri.getPath();
            }
            return filePath;
        }
        return path;
    }

}
