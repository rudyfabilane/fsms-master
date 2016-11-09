package com.rojangames.freetextph.ui.compose;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Toast;

import com.android.ex.chips.recipientchip.DrawableRecipientChip;

import com.rojangames.freetextph.R;
import com.rojangames.freetextph.common.utils.KeyboardUtils;
import com.rojangames.freetextph.common.utils.PhoneNumberUtils;
import com.rojangames.freetextph.interfaces.ActivityLauncher;
import com.rojangames.freetextph.interfaces.RecipientProvider;
import com.rojangames.freetextph.mmssms.Utils;
import com.rojangames.freetextph.ui.MainActivity;
import com.rojangames.freetextph.ui.base.QKActivity;
import com.rojangames.freetextph.ui.base.QKContentFragment;
import com.rojangames.freetextph.ui.view.AutoCompleteContactView;
import com.rojangames.freetextph.ui.view.ComposeView;
import com.rojangames.freetextph.ui.view.StarredContactsView;

public class ComposeFragment extends QKContentFragment implements ActivityLauncher, RecipientProvider,
        ComposeView.OnSendListener, AdapterView.OnItemClickListener {


    /**
     * Set to true in the bundle if the ComposeFragment should show the keyboard. Defaults to false.
     */
    public static final String ARG_SHOW_KEYBOARD = "showKeyboard";

    /**
     * Set a FOCUS string to indicate where the focus should be for the keyboard. Defaults to
     * FOCUS_NOTHING.
     */
    public static final String ARG_FOCUS = "focus";

    public static final String FOCUS_NOTHING = "nothing";
    public static final String FOCUS_RECIPIENTS = "recipients";
    public static final String FOCUS_REPLY = "reply";

    private AutoCompleteContactView mRecipients;
    private ComposeView mComposeView;
    private StarredContactsView mStarredContactsView;

    private SharedPreferences mPrefs;
    private String mSendSmsType;

    // True if the fragment's arguments have changed, and we need to potentially perform a focus
    // operation when the fragment opens.
    private boolean mPendingFocus = false;

    /**
     * Returns a new ComposeFragment, configured with the args.
     *
     * @param args A Bundle with options for configuring this fragment. See the ARG_ constants for
     *             configuration options.
     * @return the new ComposeFragment
     */
    public static ComposeFragment getInstance(Bundle args) {
        return getInstance(args, null);
    }

    /**
     * Returns a ComposeFragment, configured with the args. If possible, the given fragment
     * is used instead of creating a new ComposeFragment.
     *
     * @param args          A Bundle with options for configuring this fragment. See the ARG_ constants for
     *                      configuration options.
     * @param reuseFragment A fragment that can be used instead of creating a new one.
     * @return the ComposeFragment, which may be recycled
     */
    public static ComposeFragment getInstance(Bundle args, Fragment reuseFragment) {
        ComposeFragment f;

        // Check if we can reuse the passed fragment.
        if (reuseFragment != null && reuseFragment instanceof ComposeFragment) {
            f = (ComposeFragment) reuseFragment;
        } else {
            f = new ComposeFragment();
        }

        // Set the arguments in this fragment.
        f.updateArguments(args);

        return f;
    }

    @Override
    public void onNewArguments() {
        // Set pending focus, because the new configuration means that we may need to focus.
        mPendingFocus = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_compose, container, false);

        mRecipients = (AutoCompleteContactView) view.findViewById(R.id.compose_recipients);
        mRecipients.setOnItemClickListener(this);

        mComposeView = (ComposeView) view.findViewById(R.id.compose_view);
        mComposeView.onOpenConversation(null, null);
        mComposeView.setActivityLauncher(this);
        mComposeView.setRecipientProvider(this);
        mComposeView.setOnSendListener(this);
        mComposeView.setLabel("Compose");

        mStarredContactsView = (StarredContactsView) view.findViewById(R.id.starred_contacts);
        mStarredContactsView.setComposeScreenViews(mRecipients, mComposeView);

        setHasOptionsMenu(true);
        mPrefs = mContext.getPrefs();
        mSendSmsType = mPrefs.getString("SendSmsType", "ON");


        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        boolean handledByComposeView = mComposeView.onActivityResult(requestCode, resultCode, data);
        if (!handledByComposeView) {
            // ...
        }
    }

    @Override
    public void onSend(String[] recipients, String body) {
        long threadId = Utils.getOrCreateThreadId(mContext, recipients[0]);
        if (recipients.length == 1) {
            ((MainActivity) mContext).setConversation(threadId);
        } else {
            ((MainActivity) mContext).showMenu();
        }
    }

    @Override
    public void onContentOpened() {
        setupInput();
    }

    /**
     * Shows the keyboard and focuses on the recipients text view.
     */
    public void setupInput() {
        Bundle args = getArguments() == null ? new Bundle() : getArguments();

        if (mPendingFocus) {
            if (args.getBoolean(ARG_SHOW_KEYBOARD, false)) {
                KeyboardUtils.show(mContext);
            }

            String focus = args.getString(ARG_FOCUS, FOCUS_NOTHING);
            if (FOCUS_RECIPIENTS.equals(focus)) {
                mRecipients.requestFocus();
            } else if (FOCUS_REPLY.equals(focus)) {
                mComposeView.requestReplyTextFocus();
            }
        }
    }

    @Override
    public void onContentClosing() {
        // Clear the focus from this fragment.
        if (getActivity() != null && getActivity().getCurrentFocus() != null) {
            getActivity().getCurrentFocus().clearFocus();
        }
    }


    @Override
    public void onContentClosed() {
        super.onContentClosed();
        if (mComposeView != null) {
            mComposeView.saveDraft();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mComposeView != null) {
            mComposeView.saveDraft();
        }
    }


    @Override
    public void onMenuChanging(float percentOpen) {

    }

    @Override
    public void inflateToolbar(Menu menu, MenuInflater inflater, Context context) {
        inflater.inflate(R.menu.compose, menu);
        try {
            ((QKActivity) context).setTitle(R.string.title_compose);
            if ("ON".equals(mSendSmsType)) {
                menu.findItem(R.id.menu_sms_compose).setIcon(R.drawable.free);
            } else {
                menu.findItem(R.id.menu_sms_compose).setIcon(R.drawable.sim);
            }
        } catch (Exception x) {
            x.printStackTrace();
        }

        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sms_compose:
                switchSendSmsType();
                mContext.invalidateOptionsMenu();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void switchSendSmsType() {
        mPrefs = mContext.getPrefs();
        SharedPreferences.Editor editor = mPrefs.edit();
        if ("ON".equals(mSendSmsType)) {
            editor.putString("SendSmsType", "OFF");
            mSendSmsType = "OFF";
            //   Toast.makeText(mContext,R.string.freetext_off, Toast.LENGTH_LONG).show();
            warning();
        } else {
            editor.putString("SendSmsType", "ON");
            mSendSmsType = "ON";
            Toast.makeText(mContext, R.string.freetext_on, Toast.LENGTH_SHORT).show();
        }
        editor.apply();
    }


    void warning() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mContext);
        alertDialogBuilder.setMessage(R.string.menu_via_sim_warning);

        alertDialogBuilder.setNegativeButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.setTitle(R.string.warning_title);
        alertDialog.setIcon(R.drawable.ic_warning_black_36dp);
        alertDialog.setCancelable(false);
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.show();
    }


    /**
     * @return the addresses of all the contacts in the AutoCompleteContactsView.
     */
    @Override
    public String[] getRecipientAddresses() {
        DrawableRecipientChip[] chips = mRecipients.getRecipients();
        String[] addresses = new String[chips.length];

        for (int i = 0; i < chips.length; i++) {
            addresses[i] = PhoneNumberUtils.stripSeparators(chips[i].getEntry().getDestination());
        }

        return addresses;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mRecipients.onItemClick(parent, view, position, id);
        mStarredContactsView.collapse();
        mComposeView.requestReplyTextFocus();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

}
