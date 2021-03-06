/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2014, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.tomahawk_android.dialogs;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.GooglePlayServicesUtil;

import org.tomahawk.libtomahawk.authentication.AuthenticatorManager;
import org.tomahawk.libtomahawk.resolver.PipeLine;
import org.tomahawk.libtomahawk.resolver.ScriptResolver;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.TomahawkApp;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link android.support.v4.app.DialogFragment} which redirects the user to an external login
 * activity.
 */
public class GMusicConfigDialog extends ConfigDialog {

    public final static String TAG = GMusicConfigDialog.class.getSimpleName();

    public final static int REQUEST_CODE_PLAY_SERVICES_ERROR = 12;

    public final static int REQUEST_CODE_RECOVERABLE_ERROR = 13;

    private ScriptResolver mScriptResolver;

    private RadioGroup mRadioGroup;

    private Map<Integer, Account> mAccountMap;

    public static class ActivityResultEvent {

        public int requestCode;

        public int resultCode;
    }

    @SuppressWarnings("unused")
    public void onEvent(ActivityResultEvent event) {
        if (event.resultCode == Activity.RESULT_OK) {
            final Account account = mAccountMap.get(mRadioGroup.getCheckedRadioButtonId());

            if (account != null) {
                Log.d(TAG, "Account " + account.name + " selected. Getting auth token ...");
                fetchToken(account.name);
            } else {
                Log.e(TAG, "Account was null");
            }
        } else {
            Log.d(TAG, "ActivityResult was not RESULT_OK. Aborting ...");
            stopLoadingAnimation();
        }
    }

    /**
     * Called when this {@link android.support.v4.app.DialogFragment} is being created
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mScriptResolver =
                (ScriptResolver) PipeLine.get().getResolver(TomahawkApp.PLUGINNAME_GMUSIC);

        TextView headerTextView = (TextView) addScrollingViewToFrame(R.layout.config_textview);
        headerTextView.setText(mScriptResolver.getDescription());

        TextView infoTextView = (TextView) addScrollingViewToFrame(R.layout.config_textview);
        infoTextView.setText(R.string.gmusic_info_text);

        String loggedInAccountName = null;
        Map<String, Object> config = mScriptResolver.getConfig();
        if (config.get("email") instanceof String) {
            loggedInAccountName = (String) config.get("email");
        }

        mRadioGroup = (RadioGroup) addScrollingViewToFrame(R.layout.config_radiogroup);
        final AccountManager accountManager = AccountManager.get(TomahawkApp.getContext());
        final Account[] accounts = accountManager.getAccountsByType("com.google");
        mAccountMap = new HashMap<>();
        LayoutInflater inflater = getActivity().getLayoutInflater();
        for (Account account : accounts) {
            RadioButton radioButton =
                    (RadioButton) inflater.inflate(R.layout.config_radiobutton, mRadioGroup, false);
            radioButton.setText(account.name);
            mRadioGroup.addView(radioButton);
            mAccountMap.put(radioButton.getId(), account);
            if (loggedInAccountName != null && account.name.equals(loggedInAccountName)) {
                mRadioGroup.check(radioButton.getId());
            }
        }

        setDialogTitle(mScriptResolver.getName());
        setStatus(mScriptResolver);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(getDialogView());
        return builder.create();
    }

    @Override
    protected void onEnabledCheckedChange(boolean checked) {
    }

    @Override
    protected void onConfigTestResult(Object component, int type, String message) {
        if (mScriptResolver == component && mScriptResolver.isConfigTestable()) {
            if (type == AuthenticatorManager.CONFIG_TEST_RESULT_TYPE_SUCCESS) {
                mScriptResolver.setEnabled(true);
                dismiss();
            } else {
                mScriptResolver.setEnabled(false);
            }
            stopLoadingAnimation();
        }
    }

    @Override
    protected void onPositiveAction() {
        final Account account = mAccountMap.get(mRadioGroup.getCheckedRadioButtonId());

        if (account != null) {
            Log.d(TAG, "Account " + account.name + " selected. Getting auth token ...");
            startLoadingAnimation();
            fetchToken(account.name);
        } else {
            Log.e(TAG, "Account was null");
        }
    }

    @Override
    protected void onNegativeAction() {
        dismiss();
    }

    private void fetchToken(final String accountName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String authToken = GoogleAuthUtil.getToken(TomahawkApp.getContext(),
                            accountName, "sj");
                    Log.d(TAG, "Received auth token!");
                    Map<String, Object> config = mScriptResolver.getConfig();
                    config.put("token", authToken);
                    config.put("email", accountName);
                    mScriptResolver.setConfig(config);
                    mScriptResolver.testConfig(config);
                } catch (GooglePlayServicesAvailabilityException e) {
                    Log.d(TAG, "GooglePlayServicesAvailabilityException: "
                            + e.getLocalizedMessage());
                    Dialog d = GooglePlayServicesUtil.getErrorDialog(
                            e.getConnectionStatusCode(), getActivity(),
                            REQUEST_CODE_PLAY_SERVICES_ERROR);
                    d.show();
                } catch (UserRecoverableAuthException e) {
                    Log.d(TAG, "UserRecoverableAuthException: " + e.getLocalizedMessage());
                    getActivity().startActivityForResult(e.getIntent(),
                            REQUEST_CODE_RECOVERABLE_ERROR);
                } catch (GoogleAuthException e) {
                    Log.d(TAG, "GoogleAuthException: " + e.getLocalizedMessage());
                } catch (IOException e) {
                    Log.d(TAG, "IOException: " + e.getLocalizedMessage());
                }
            }
        }).start();
    }
}
