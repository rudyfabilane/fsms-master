package com.rojangames.freetextph.ui.conversationlist;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.appodeal.ads.Appodeal;
import com.appodeal.ads.BannerCallbacks;
import com.melnykov.fab.FloatingActionButton;
import com.pollfish.constants.Position;
import com.pollfish.interfaces.PollfishOpenedListener;
import com.pollfish.interfaces.PollfishSurveyCompletedListener;
import com.pollfish.interfaces.PollfishSurveyNotAvailableListener;
import com.pollfish.interfaces.PollfishSurveyReceivedListener;
import com.pollfish.main.PollFish;

import java.util.Observable;
import java.util.Observer;

import butterknife.Bind;
import butterknife.ButterKnife;
import com.rojangames.freetextph.R;
import com.rojangames.freetextph.common.BlockedConversationHelper;
import com.rojangames.freetextph.common.DialogHelper;
import com.rojangames.freetextph.common.LiveViewManager;
import com.rojangames.freetextph.common.utils.ColorUtils;
import com.rojangames.freetextph.data.Contact;
import com.rojangames.freetextph.data.Conversation;
import com.rojangames.freetextph.data.ConversationLegacy;
import com.rojangames.freetextph.enums.QKPreference;
import com.rojangames.freetextph.transaction.SmsHelper;
import com.rojangames.freetextph.ui.ContentFragment;
import com.rojangames.freetextph.ui.MainActivity;
import com.rojangames.freetextph.ui.ThemeManager;
import com.rojangames.freetextph.ui.base.QKFragment;
import com.rojangames.freetextph.ui.base.RecyclerCursorAdapter;
import com.rojangames.freetextph.ui.compose.ComposeFragment;
import com.rojangames.freetextph.ui.dialog.conversationdetails.ConversationDetailsDialog;
import com.rojangames.freetextph.ui.settings.SettingsFragment;


public class ConversationListFragment extends QKFragment implements LoaderManager.LoaderCallbacks<Cursor>,
        RecyclerCursorAdapter.ItemClickListener<Conversation>, RecyclerCursorAdapter.MultiSelectListener, Observer {

    private final String TAG = "ConversationList";

    @Bind(R.id.empty_state)
    View mEmptyState;
    @Bind(R.id.empty_state_icon)
    ImageView mEmptyStateIcon;
    @Bind(R.id.conversations_list)
    RecyclerView mRecyclerView;
    @Bind(R.id.fab)
    FloatingActionButton mFab;

    private ConversationListAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    private ConversationDetailsDialog mConversationDetailsDialog;
    private SharedPreferences mPrefs;
    private MenuItem mBlockedItem;
    private boolean mShowBlocked = false;

    private boolean mViewHasLoaded = false;

    // This does not hold the current position of the list, rather the position the list is pending being set to
    private int mPosition;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        setHasOptionsMenu(true);

        mAdapter = new ConversationListAdapter(mContext);
        mAdapter.setItemClickListener(this);
        mAdapter.setMultiSelectListener(this);
        mLayoutManager = new LinearLayoutManager(mContext);
        mConversationDetailsDialog = new ConversationDetailsDialog(mContext, getFragmentManager());

        LiveViewManager.registerView(QKPreference.THEME, this, key -> {
            if (!mViewHasLoaded) {
                return;
            }

            mFab.setColorNormal(ThemeManager.getColor());
            mFab.setColorPressed(ColorUtils.lighten(ThemeManager.getColor()));
            mFab.getDrawable().setColorFilter(ThemeManager.getTextOnColorPrimary(), PorterDuff.Mode.SRC_ATOP);
            mEmptyStateIcon.setColorFilter(ThemeManager.getTextOnBackgroundPrimary());
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversations, null);
        ButterKnife.bind(this, view);


        mEmptyStateIcon.setColorFilter(ThemeManager.getTextOnBackgroundPrimary());

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);

        mFab.setColorNormal(ThemeManager.getColor());
        mFab.setColorPressed(ColorUtils.lighten(ThemeManager.getColor()));
        mFab.attachToRecyclerView(mRecyclerView);
        mFab.setColorFilter(ThemeManager.getTextOnColorPrimary());
        mFab.setOnClickListener(v -> {
            if (mAdapter.isInMultiSelectMode()) {
                mAdapter.disableMultiSelectMode(true);
            } else {
                // Show the compose fragment, showing the keyboard and focusing on the recipients edittext.
                Bundle args = new Bundle();
                args.putBoolean(ComposeFragment.ARG_SHOW_KEYBOARD, true);
                args.putString(ComposeFragment.ARG_FOCUS, ComposeFragment.FOCUS_RECIPIENTS);

                Fragment content = getFragmentManager().findFragmentById(R.id.content_frame);
                switchFragment(ComposeFragment.getInstance(args, content));
            }
        });

        mViewHasLoaded = true;

        initLoaderManager();
        BlockedConversationHelper.FutureBlockedConversationObservable.getInstance().addObserver(this);

        return view;
    }

    /**
     * Returns the weighting for unread vs. read conversations that are selected, to decide
     * which options we should show in the multi selction toolbar
     */
    private int getUnreadWeight() {
        int unreadWeight = 0;
        for (Conversation conversation : mAdapter.getSelectedItems().values()) {
            unreadWeight += conversation.hasUnreadMessages() ? 1 : -1;
        }
        return unreadWeight;
    }

    /**
     * Returns the weighting for blocked vs. unblocked conversations that are selected
     */
    private int getBlockedWeight() {
        int blockedWeight = 0;
        for (Conversation conversation : mAdapter.getSelectedItems().values()) {
            blockedWeight += BlockedConversationHelper.isConversationBlocked(mPrefs, conversation.getThreadId()) ? 1 : -1;
        }
        return blockedWeight;
    }

    /**
     * Returns whether or not any of the selected conversations have errors
     */
    private boolean doSomeHaveErrors() {
        for (Conversation conversation : mAdapter.getSelectedItems().values()) {
            if (conversation.hasError()) {
                return true;
            }
        }
        return false;
    }

    public void inflateToolbar(Menu menu, MenuInflater inflater, Context context) {
        if (mAdapter.isInMultiSelectMode()) {
            inflater.inflate(R.menu.conversations_selection, menu);
            mContext.setTitle(getString(R.string.title_conversations_selected, mAdapter.getSelectedItems().size()));

            menu.findItem(R.id.menu_block).setVisible(mPrefs.getBoolean(SettingsFragment.BLOCKED_ENABLED, false));

            menu.findItem(R.id.menu_mark_read).setIcon(getUnreadWeight() >= 0 ? R.drawable.ic_mark_read : R.drawable.ic_mark_unread);
            menu.findItem(R.id.menu_mark_read).setTitle(getUnreadWeight() >= 0 ? R.string.menu_mark_read : R.string.menu_mark_unread);
            menu.findItem(R.id.menu_block).setTitle(getBlockedWeight() > 0 ? R.string.menu_unblock_conversations : R.string.menu_block_conversations);
            menu.findItem(R.id.menu_delete_failed).setVisible(doSomeHaveErrors());
        } else {
            inflater.inflate(R.menu.conversations, menu);
            mContext.setTitle(mShowBlocked ? R.string.title_blocked : R.string.title_conversation_list);

            mBlockedItem = menu.findItem(R.id.menu_blocked);
            BlockedConversationHelper.bindBlockedMenuItem(mContext, mPrefs, mBlockedItem, mShowBlocked);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_blocked:
                setShowingBlocked(!mShowBlocked);
                return true;

            case R.id.menu_delete:
                DialogHelper.showDeleteConversationsDialog((MainActivity) mContext, mAdapter.getSelectedItems().keySet());
                mAdapter.disableMultiSelectMode(true);
                return true;

            case R.id.menu_mark_read:
                for (long threadId : mAdapter.getSelectedItems().keySet()) {
                    if (getUnreadWeight() >= 0) {
                        new ConversationLegacy(mContext, threadId).markRead();
                    } else {
                        new ConversationLegacy(mContext, threadId).markUnread();
                    }
                }
                mAdapter.disableMultiSelectMode(true);
                return true;

            case R.id.menu_block:
                for (long threadId : mAdapter.getSelectedItems().keySet()) {
                    if (getBlockedWeight() > 0) {
                        BlockedConversationHelper.unblockConversation(mPrefs, threadId);
                    } else {
                        BlockedConversationHelper.blockConversation(mPrefs, threadId);
                    }
                }
                mAdapter.disableMultiSelectMode(true);
                initLoaderManager();
                return true;

            case R.id.menu_delete_failed:
                DialogHelper.showDeleteFailedMessagesDialog((MainActivity) mContext, mAdapter.getSelectedItems().keySet());
                mAdapter.disableMultiSelectMode(true);
                return true;

            case R.id.menu_done:
                mAdapter.disableMultiSelectMode(true);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean isShowingBlocked() {
        return mShowBlocked;
    }

    public void setShowingBlocked(boolean showBlocked) {
        mShowBlocked = showBlocked;
        mContext.setTitle(mShowBlocked ? R.string.title_blocked : R.string.title_conversation_list);
        BlockedConversationHelper.bindBlockedMenuItem(mContext, mPrefs, mBlockedItem, mShowBlocked);
        initLoaderManager();
    }

    @Override
    public void onItemClick(Conversation conversation, View view) {
        if (mAdapter.isInMultiSelectMode()) {
            mAdapter.toggleSelection(conversation.getThreadId(), conversation);
        } else {
            ((MainActivity) mContext).setConversation(conversation.getThreadId(), -1, null, true);
        }
    }

    @Override
    public void onItemLongClick(final Conversation conversation, View view) {
        mAdapter.toggleSelection(conversation.getThreadId(), conversation);
    }

    public void setPosition(int position) {
        mPosition = position;
        if (mLayoutManager != null && mAdapter != null) {
            mLayoutManager.scrollToPosition(Math.min(mPosition, mAdapter.getCount() - 1));
        }
    }

    public int getPosition() {
        return mLayoutManager.findFirstVisibleItemPosition();
    }

    private void initLoaderManager() {
        getLoaderManager().restartLoader(0, null, this);
    }

    private void switchFragment(ContentFragment fragment) {
        ((MainActivity) getActivity()).switchContent(fragment, true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        BlockedConversationHelper.FutureBlockedConversationObservable.getInstance().deleteObserver(this);

        if (null == mRecyclerView) {
            return;
        }
        try {
            for (int i = 0; i < mRecyclerView.getChildCount(); i++) {
                View child = mRecyclerView.getChildAt(i);
                RecyclerView.ViewHolder holder = mRecyclerView.getChildViewHolder(child);
                if (holder instanceof ConversationListViewHolder) {
                    Contact.removeListener((ConversationListViewHolder) holder);
                }
            }
        } catch (Exception ignored) {
            //
        }

    }

    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(mContext, SmsHelper.CONVERSATIONS_CONTENT_PROVIDER, Conversation.ALL_THREADS_PROJECTION,
                BlockedConversationHelper.getCursorSelection(mPrefs, mShowBlocked),
                BlockedConversationHelper.getBlockedConversationArray(mPrefs), "date DESC");
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (mAdapter != null) {
            // Swap the new cursor in.  (The framework will take care of closing the, old cursor once we return.)
            mAdapter.changeCursor(data);
            if (mPosition != 0) {
                mRecyclerView.scrollToPosition(Math.min(mPosition, data.getCount() - 1));
                mPosition = 0;
            }
        }

        mEmptyState.setVisibility(data != null && data.getCount() > 0 ? View.GONE : View.VISIBLE);
    }

    public void onLoaderReset(Loader<Cursor> loader) {
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
    }

    @Override
    public void onMultiSelectStateChanged(boolean enabled) {
        mContext.invalidateOptionsMenu();
        mFab.setImageResource(enabled ? R.drawable.ic_accept : R.drawable.ic_add);
    }

    @Override
    public void onItemAdded(long id) {
        mContext.invalidateOptionsMenu();
    }

    @Override
    public void onItemRemoved(long id) {
        mContext.invalidateOptionsMenu();
    }

    /**
     * This should be called when there's a future blocked conversation, and it's received
     */
    @Override
    public void update(Observable observable, Object data) {
        initLoaderManager();
    }

    @Override
    public void onResume() {
        super.onResume();
        banner();
        Appodeal.onResume(mContext, Appodeal.BANNER);
        pollfish();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    void pollfish() {
        PollFish.ParamsBuilder paramsBuilder = new PollFish.ParamsBuilder(getString(R.string.pFishKey))
                .indicatorPosition(Position.MIDDLE_RIGHT).pollfishSurveyReceivedListener(new PollfishSurveyReceivedListener() {
                    @Override
                    public void onPollfishSurveyReceived(final boolean playfulSurvey, final int surveyPrice) {
                        //         Log.i(TAG, "Pollfish survey received");
                    }
                }).pollfishSurveyCompletedListener(new PollfishSurveyCompletedListener() {
                    @Override
                    public void onPollfishSurveyCompleted(final boolean playfulSurvey, final int surveyPrice) {

/*                        //Getting the current date and time
                        Calendar c = Calendar.getInstance();
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/M/yyyy hh:mm:ss");
                        String timenow = simpleDateFormat.format(c.getTime());

                        //Saving the date and time when the survey is completed
                        SharedPreferences pref = mContext.getSharedPreferences(
                                "adnetwork", 0);
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putString("expiration", timenow).apply();
                        PollFish.hide();
                        Log.i(TAG, "Pollfish survey completed");*/
                    }
                }).pollfishOpenedListener(new PollfishOpenedListener() {
                    @Override
                    public void onPollfishOpened() {
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
                        alertDialogBuilder.setMessage(R.string.survey_dialog_message);

/*                        alertDialogBuilder.setNegativeButton("Not Now", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PollFish.hide();
                            }
                        });*/

                        alertDialogBuilder.setPositiveButton("Start Survey", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                PollFish.show();
                            }
                        });

                        AlertDialog alertDialog = alertDialogBuilder.create();
                        alertDialog.setTitle(R.string.survey_dialog_title);
                        alertDialog.setIcon(R.drawable.ic_notification);
                        alertDialog.setCanceledOnTouchOutside(false);
                        alertDialog.setCancelable(false);
                        alertDialog.show();
                    }
                }).pollfishSurveyNotAvailableListener(new PollfishSurveyNotAvailableListener() {
                    @Override
                    public void onPollfishSurveyNotAvailable() {

                    }
                }).build();
        PollFish.initWith(mContext, paramsBuilder);
    }


    void banner() {
        try {
            Appodeal.disableNetwork(mContext, "applovin");
            Appodeal.disableNetwork(mContext, "chartboost");
            Appodeal.disableNetwork(mContext, "flurry");
            Appodeal.disableNetwork(mContext, "unity_ads");
            Appodeal.disableNetwork(mContext, "mailru");
            Appodeal.disableNetwork(mContext, "adcolony");
            Appodeal.disableNetwork(mContext, "vungle");
            Appodeal.disableNetwork(mContext, "yandex");
            Appodeal.disableNetwork(mContext, "avocarrot");
            Appodeal.disableNetwork(mContext, "cheetah");

            Appodeal.disableNetwork(mContext, "startapp", Appodeal.BANNER);

            Appodeal.setBannerViewId(R.id.appodealBannerView);
            Appodeal.setAutoCache(Appodeal.BANNER, false);
            Appodeal.initialize(mContext, getString(R.string.apokey), Appodeal.BANNER);
            Appodeal.cache(mContext, Appodeal.BANNER);

            Appodeal.setBannerCallbacks(new BannerCallbacks() {

                @Override
                public void onBannerLoaded(int height, boolean isPrecache) {
                    Appodeal.show(mContext, Appodeal.BANNER_VIEW);
                }

                @Override
                public void onBannerFailedToLoad() {
                }

                @Override
                public void onBannerShown() {
                }

                @Override
                public void onBannerClicked() {
                }
            });

        } catch (Exception x) {
            x.printStackTrace();
        }
    }
}