package com.box.androidsdk.browse.activities;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;

import com.box.androidsdk.browse.R;
import com.box.androidsdk.browse.fragments.BoxBrowseFolderFragment;
import com.box.androidsdk.browse.fragments.BoxBrowseFragment;
import com.box.androidsdk.browse.fragments.BoxSearchFragment;
import com.box.androidsdk.browse.fragments.OnUpdateListener;
import com.box.androidsdk.browse.service.BoxBrowseController;
import com.box.androidsdk.browse.service.BrowseController;
import com.box.androidsdk.browse.uidata.BoxSearchView;
import com.box.androidsdk.content.BoxApiFile;
import com.box.androidsdk.content.BoxApiFolder;
import com.box.androidsdk.content.BoxApiSearch;
import com.box.androidsdk.content.BoxConstants;
import com.box.androidsdk.content.models.BoxFolder;
import com.box.androidsdk.content.models.BoxItem;
import com.box.androidsdk.content.models.BoxSession;
import com.box.androidsdk.content.requests.BoxResponse;
import com.box.androidsdk.content.utils.SdkUtils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;

public abstract class BoxBrowseActivity extends BoxThreadPoolExecutorActivity implements BoxBrowseFragment.OnItemClickListener, BoxSearchView.BoxCustomSearchListener {

    protected static final String EXTRA_SHOULD_SEARCH_ALL = "extraShouldSearchAll";

    protected static final String TAG = BoxBrowseActivity.class.getName();
    private static final String OUT_BROWSE_FRAGMENT = "outBrowseFragment";

    private static final ConcurrentLinkedQueue<BoxResponse> RESPONSE_QUEUE = new ConcurrentLinkedQueue<BoxResponse>();
    private static final String RESTORE_SEARCH = "restoreSearch";
    private static final String SEARCH_QUERY = "searchQuery";
    private static ThreadPoolExecutor mApiExecutor;
    private MenuItem mSearchViewMenuItem;
    private boolean mRestoreSearch;
    private String mSearchQuery;
    private BrowseController mController;
    private OnUpdateListener mUpdateListener;
    private BoxSearchView mSearchView;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mController = new BoxBrowseController(mSession, new BoxApiFile(mSession),
                new BoxApiFolder(mSession),
                new BoxApiSearch(mSession));

        if (savedInstanceState != null) {
            mRestoreSearch = savedInstanceState.getBoolean(RESTORE_SEARCH, false);
            mSearchQuery = savedInstanceState.getString(SEARCH_QUERY);
        }
    }


    @Override
    public ThreadPoolExecutor getApiExecutor(Application application) {
        if (mApiExecutor == null) {
            mApiExecutor = BoxThreadPoolExecutorActivity.createTaskMessagingExecutor(application, getResponseQueue());
        }
        return mApiExecutor;
    }

    @Override
    public Queue<BoxResponse> getResponseQueue() {
        return RESPONSE_QUEUE;
    }

    @Override
    protected void handleBoxResponse(BoxResponse response) {

    }

    protected BoxFolder getCurrentFolder() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.box_browsesdk_fragment_container);
        BoxFolder curFolder = fragment instanceof BoxBrowseFolderFragment ?
                    ((BoxBrowseFolderFragment) fragment).getFolder() :
                    null;
        return curFolder;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.box_browsesdk_action_search) {
            // Launch search experience
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    protected BoxBrowseFragment getTopBrowseFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment frag = fragmentManager.findFragmentById(R.id.box_browsesdk_fragment_container);
        return frag instanceof BoxBrowseFragment ? (BoxBrowseFragment) frag : null;
    }

    protected void handleBoxFolderClicked(final BoxFolder boxFolder) {
        FragmentTransaction trans = getSupportFragmentManager().beginTransaction();

        // All fragments will always navigate into folders
        BoxBrowseFolderFragment browseFolderFragment = createBrowseFolderFragment(boxFolder, mSession);
        trans.replace(R.id.box_browsesdk_fragment_container, browseFolderFragment);
        if (getSupportFragmentManager().getBackStackEntryCount() > 0 || getSupportFragmentManager().getFragments() != null) {
            trans.addToBackStack(BoxBrowseFragment.TAG);
        }
        trans.commit();
    }

    /**
     * Creates a {@link BoxBrowseFolderFragment} that will be used in the activity to display
     * BoxItems. For a more customized experience, a custom implementation of the fragment can
     * be provided here.
     *
     * @param folder the folder that will be browsed
     * @param session the session that will be used for browsing
     * @return Browsing fragment that will be used to show the BoxItems
     */
    protected BoxBrowseFolderFragment createBrowseFolderFragment(final BoxItem folder, final BoxSession session) {
        final BoxBrowseFolderFragment fragment = new BoxBrowseFolderFragment.Builder((BoxFolder) folder, session).build();
        if (mUpdateListener == null) {
            mUpdateListener = new OnUpdateListener() {
                @Override
                public void onUpdate() {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            setTitle(getCurrentFolder());
                        }
                    });
                }
            };
        }

        fragment.addOnUpdateListener(mUpdateListener);
        return fragment;
    }


    protected void setTitle(final BoxFolder folder) {
        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null && folder != null) {
            actionbar.setTitle(folder.getId() == BoxConstants.ROOT_FOLDER_ID
                    ? getString(R.string.box_browsesdk_all_files) : folder.getName());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mSearchViewMenuItem = menu.findItem(R.id.box_browsesdk_action_search);
        mSearchView = (BoxSearchView) MenuItemCompat.getActionView(mSearchViewMenuItem);
        mSearchView.setOnBoxSearchListener(this);
        if (mRestoreSearch) {
            mSearchViewMenuItem.expandActionView();
            mSearchView.setIconified(false);
            mSearchView.setQuery(mSearchQuery, false);
            mRestoreSearch = false;
        }

        return true;
    }

    public void setSearchQuery(String query) {
        if (mSearchView != null) {
            mSearchView.setSearchTerm(query);
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSearchViewMenuItem == null) {
            return;
        }
        BoxSearchView searchView = (BoxSearchView) MenuItemCompat.getActionView(mSearchViewMenuItem);
        outState.putBoolean(RESTORE_SEARCH, !searchView.isIconified());
        outState.putString(SEARCH_QUERY, searchView.getQuery().toString());
    }

    private void clearSearch() {
        if (mSearchViewMenuItem == null) {
            return;
        }
        BoxSearchView searchView = (BoxSearchView) MenuItemCompat.getActionView(mSearchViewMenuItem);
        searchView.onActionViewCollapsed();
    }

    @Override
    public void onItemClick(BoxItem item) {
        // Notify fragment about item click
        BoxBrowseFragment browseFrag = getTopBrowseFragment();
        if (browseFrag != null) {
            browseFrag.onItemClick(item);
        }

        // If click is on a folder, navigate to that folder
        if (item instanceof BoxFolder) {
            handleBoxFolderClicked((BoxFolder) item);
        }
    }

    @Override
    public void onSearchExpanded() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.box_browsesdk_fragment_container);
        if (!(fragment instanceof BoxSearchFragment)) {
            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();

            // All fragments will always navigate into folders
            BoxSearchFragment searchFragment = new BoxSearchFragment.Builder(mSession).build();
            trans.replace(R.id.box_browsesdk_fragment_container, searchFragment)
                    .addToBackStack(BoxBrowseFragment.TAG)
                    .commit();
        }
    }

    @Override
    public void onSearchCollapsed() {

    }

    @Override
    public void onQueryTextChange(String text) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.box_browsesdk_fragment_container);
        if (fragment instanceof BoxSearchFragment) {
            ((BoxSearchFragment)fragment).search(text);
        }
    }

    @Override
    public void onQueryTextSubmit(String text) {

    }

    /**
     * Create a builder object that can be used to construct an intent to launch an instance of this activity.
     */
    protected static abstract class IntentBuilder<R> {
        final BoxSession mSession;
        final Context mContext;
        BoxFolder mFolder;
        boolean mShouldSearchAll = false;


        /**
         * Create an new Intent Builder designed to create an intent to launch a child of BoxBrowseActivity.
         *
         * @param context current context.
         * @param session an authenticated session.
         */
        public IntentBuilder(final Context context, final BoxSession session) {
            super();
            mContext = context;
            mSession = session;
            if (context == null)
                throw new IllegalArgumentException("A valid context must be provided to browse");
            if (session == null || session.getUser() == null || SdkUtils.isBlank(session.getUser().getId()))
                throw new IllegalArgumentException("A valid user must be provided to browse");

        }

        /**
         * @param folder folder to start browsing in.
         * @return an IntentBuilder which can create an instance of this class.
         */
        public R setStartingFolder(final BoxFolder folder) {
            mFolder = folder;
            if (folder == null || SdkUtils.isBlank(folder.getId()))
                throw new IllegalArgumentException("A valid folder must be provided");
            return (R) this;
        }

        /**
         * @param searchAll true if searching should search entire account, false if searching should only search current folder. False by default.
         * @return an IntentBuilder which can create an instance of this class.
         */
        public R setShouldSearchAll(final boolean searchAll) {
            mShouldSearchAll = searchAll;
            return (R) this;
        }

        /**
         * @param intent intent to add extras from this builder to.
         */
        protected void addExtras(final Intent intent) {
            intent.putExtra(EXTRA_SHOULD_SEARCH_ALL, mShouldSearchAll);
        }

        protected abstract Intent createLaunchIntent();

        /**
         * Create an intent to launch an instance of this activity.
         *
         * @return an intent to launch an instance of this activity.
         */
        public Intent createIntent() {
            Intent intent = createLaunchIntent();
            addExtras(intent);
            return intent;
        }
    }
}
