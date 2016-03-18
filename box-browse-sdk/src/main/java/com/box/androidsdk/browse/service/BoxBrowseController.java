package com.box.androidsdk.browse.service;

import com.box.androidsdk.content.BoxApiFile;
import com.box.androidsdk.content.BoxApiFolder;
import com.box.androidsdk.content.BoxApiSearch;
import com.box.androidsdk.content.BoxFutureTask;
import com.box.androidsdk.content.models.BoxFolder;
import com.box.androidsdk.content.requests.BoxRequest;
import com.box.androidsdk.content.requests.BoxRequestsFile;
import com.box.androidsdk.content.requests.BoxRequestsFolder;
import com.box.androidsdk.content.requests.BoxRequestsSearch;
import com.box.androidsdk.content.utils.BoxLogUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/***
 * Default implementation for the {@link BrowseController}.
 */
public class BoxBrowseController implements BrowseController {
    private static final String TAG = BoxBrowseController.class.getName();

    // Static executors so that requests can be retained though activity/fragment lifecycle
    private static ThreadPoolExecutor mApiExecutor;
    private static ThreadPoolExecutor mThumbnailExecutor;

    private final BoxApiFile mFileApi;
    private final BoxApiFolder mFolderApi;
    private final BoxApiSearch mSearchApi;
    private BoxFutureTask.OnCompletedListener mListener;


    public BoxBrowseController(BoxApiFile apiFile, BoxApiFolder apiFolder, BoxApiSearch apiSearch) {
        mFileApi = apiFile;
        mFolderApi = apiFolder;
        mSearchApi = apiSearch;
    }

    @Override
    public BoxRequestsFolder.GetFolderWithAllItems getFolderWithAllItems(String folderId) {
        return mFolderApi.getFolderWithAllItems(folderId)
                .setFields(BoxFolder.ALL_FIELDS);
    }

    @Override
    public BoxRequestsSearch.Search getSearchRequest(String query) {
        return mSearchApi.getSearchRequest(query);
    }

    @Override
    public BoxRequestsFile.DownloadThumbnail getThumbnailRequest(String fileId, File downloadFile, int width, int height) {
        try {
            return mFileApi.getDownloadThumbnailRequest(downloadFile, fileId)
                    .setMinWidth(width)
                    .setMinHeight(height);
        } catch (IOException e) {
            BoxLogUtils.e(TAG, e.getMessage());
        }
        return null;
    }

    @Override
    public void execute(BoxRequest request) {
        if (request == null) {
            return;
        }

        BoxFutureTask task = request.toTask();
        if (mListener != null) {
            task.addOnCompletedListener(mListener);
        }

        // Thumbnail request should be executed in their own executor pool
        ThreadPoolExecutor executor = request instanceof BoxRequestsFile.DownloadThumbnail ?
                getThumbnailExecutor() :
                getApiExecutor();
        executor.submit(task);
    }

    @Override
    public BrowseController setCompletedListener(BoxFutureTask.OnCompletedListener listener) {
        mListener = listener;
        return this;
    }

    protected ThreadPoolExecutor getApiExecutor() {
        if (mApiExecutor == null || mApiExecutor.isShutdown()) {
            mApiExecutor = new ThreadPoolExecutor(1, 1, 3600, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        }
        return mApiExecutor;
    }

    /**
     * Executor that we will submit thumbnail tasks to.
     *
     * @return executor
     */
    protected ThreadPoolExecutor getThumbnailExecutor() {
        if (mThumbnailExecutor == null || mThumbnailExecutor.isShutdown()) {
            mThumbnailExecutor = new ThreadPoolExecutor(1, 10, 3600, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        }
        return mThumbnailExecutor;
    }
}
