package com.RNFetchBlob.Response;

import android.util.Log;

import com.RNFetchBlob.RNFetchBlobReq;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;

/**
 * Created by wkh237 on 2016/7/11.
 */
public class RNFetchBlobFileResp extends ResponseBody {

    String mTaskId;
    ResponseBody originalBody;
    String mPath;
    long bytesDownloaded = 0;
    ReactApplicationContext rctContext;
    FileOutputStream ofStream;

    public RNFetchBlobFileResp(ReactApplicationContext ctx, String taskId, ResponseBody body, String path) throws IOException {
        super();
        this.rctContext = ctx;
        this.mTaskId = taskId;
        this.originalBody = body;
        assert path != null;
        this.mPath = path;
        if (path != null) {
            File f = new File(path);
            if(f.exists() == false)
                f.createNewFile();
            ofStream = new FileOutputStream(new File(path));
        }
    }

    @Override
    public MediaType contentType() {
        return originalBody.contentType();
    }

    @Override
    public long contentLength() {
        return originalBody.contentLength();
    }

    @Override
    public BufferedSource source() {
        ProgressReportingSource countable = new ProgressReportingSource();
        return Okio.buffer(countable);
    }

    private class ProgressReportingSource implements Source {
        @Override
        public long read(Buffer sink, long byteCount) throws IOException {
            byte [] bytes = new byte[(int) byteCount];
            long read = originalBody.byteStream().read(bytes, 0, (int) byteCount);
            bytesDownloaded += read > 0 ? read : 0;
            Log.i("bytes downloaded", String.valueOf(byteCount) +"/"+ String.valueOf(read) + "=" + String.valueOf(bytesDownloaded));
            if(read > 0 ) {
                ofStream.write(bytes, 0, (int) read);
            }
            if(RNFetchBlobReq.isReportProgress(mTaskId)) {
                WritableMap args = Arguments.createMap();
                args.putString("taskId", mTaskId);
                args.putString("written", String.valueOf(bytesDownloaded));
                args.putString("total", String.valueOf(contentLength()));
                rctContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("RNFetchBlobProgress", args);
            }
            return read;
        }

        @Override
        public Timeout timeout() {
            return null;
        }

        @Override
        public void close() throws IOException {
            ofStream.close();

        }
    }

}
