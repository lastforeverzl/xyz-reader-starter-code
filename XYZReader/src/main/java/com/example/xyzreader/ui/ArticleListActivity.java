package com.example.xyzreader.ui;

import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.toString();
    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    private int clickedItemPos = -1;
    private boolean isReturn = false;
    private int endPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);

        mToolbar = (Toolbar) findViewById(R.id.my_toolbar);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        ActivityCompat.setExitSharedElementCallback(this, new MyExitSharedElementCallback());
        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState == null) {
            refresh();
        }
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mRecyclerView.setVisibility(View.VISIBLE);
                mRecyclerView.setEnabled(true);
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter = new Adapter(cursor, this);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            isReturn = true;
            endPosition = data.getIntExtra(getString(R.string.end_position_extra), -1);
            clickedItemPos = data.getIntExtra(getString(R.string.start_position_extra), -1);

            ActivityCompat.postponeEnterTransition(this);

            //Delays preDraw and scrollToPosition
            //This is because during an orientation change in Detail Activity,
            //the return transition fails to execute if the returning item is off-screen.
            //Through tests/inspection, this is because the loader has not loaded the data in-time for the preDraw & scrollToPosition to occur
            //Through tests/inspection, 100ms delay gives the most optimised result.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(endPosition != 1)
                        mRecyclerView.scrollToPosition(endPosition);
                    mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                            mRecyclerView.requestLayout();
                            ActivityCompat.startPostponedEnterTransition(ArticleListActivity.this);
                            return true;
                        }
                    });

                }
            }, 100);
        }
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;
        private ArticleListActivity mArticleListActivity;

        public Adapter(Cursor cursor, ArticleListActivity articleListActivity) {
            mCursor = cursor;
            mArticleListActivity = articleListActivity;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickedItemPos = vh.getAdapterPosition();
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            ItemsContract.Items.buildItemUri(getItemId(clickedItemPos)));

                    intent.putExtra(getString(R.string.start_position_extra), clickedItemPos);

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        Bundle bundle =
                                ActivityOptions.makeSceneTransitionAnimation(
                                        mArticleListActivity,
                                        vh.thumbnailView,
                                        vh.thumbnailView.getTransitionName())
                                        .toBundle();
                        startActivity(intent, bundle);
                    } else {
                        startActivity(intent);
                    }
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {

                holder.subtitleView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + "<br/>" + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            } else {
                holder.subtitleView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate)
                        + "<br/>" + " by "
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            }
            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));

            String thumbnailTransitionName = getString(
                    R.string.transition_name, mRecyclerView.getAdapter().getItemId(position));
            holder.thumbnailView.setTag(thumbnailTransitionName);
///           Log.d(TAG, thumbnailTransitionName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.thumbnailView.setTransitionName(thumbnailTransitionName);
            }
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public DynamicHeightNetworkImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }
    }

    private class MyExitSharedElementCallback extends SharedElementCallback {

        /**After testing, this seems to get called whenever the transition is starting/returning.
         //This also applies for setEnterSharedElementCallback in ArticleDetailActivity
         //After researching the DOC, exitSharedElement is the element for the exit activity, in this case ArticleListActivity
         //enterSharedElement is for the entering activity of the transition(ArticleDetailActivity)
         //We need to modify the shared element because of cases when users swipe left or right.
         //The fragment is no longer the same as the one we entered with and so we need to set the correct view to end the transition appropriately
         **/
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
            //As mentioned, this callback gets triggered whenever the transition is starting/returning
            //We only want to modify the element when it is returning AND the position has changed
            if(isReturn) {

                //position determines which item in the list that we need to apply the return transition
                //if user didn't swipe anywhere in the detail activity then keep it at the same clicked item position
                //else, set it to the correct item that the user swiped to
                int position = clickedItemPos;
                Log.d(TAG, "endPosition: " + endPosition + " clickedItemPos: " + clickedItemPos);
                if(endPosition != clickedItemPos && endPosition != -1) {
                    position = endPosition;
                }

                String transitionName = getString(R.string.transition_name, mRecyclerView.getAdapter().getItemId(position));
                View endThumbnailView = mRecyclerView.findViewWithTag(transitionName);

                if (endThumbnailView != null) {
                    names.clear();
                    names.add(transitionName);
                    sharedElements.clear();
                    sharedElements.put(transitionName, endThumbnailView);
                }
                //Set it back, otherwise we might modify it by accident when the user clicks another item
                isReturn = false;
            }
        }
    }

}
