/*
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.derpfest.setupwizard;

import static com.google.android.setupcompat.util.ResultCodes.RESULT_ACTIVITY_NOT_FOUND;

import static org.derpfest.setupwizard.SetupWizardApp.EXTRA_ACTION_ID;
import static org.derpfest.setupwizard.SetupWizardApp.EXTRA_SCRIPT_URI;
import static org.derpfest.setupwizard.SetupWizardApp.EXTRA_WIZARD_BUNDLE;
import static org.derpfest.setupwizard.SetupWizardApp.LOGV;

import android.annotation.NonNull;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;

public abstract class SubBaseActivity extends BaseSetupWizardActivity {

    public static final String TAG = SubBaseActivity.class.getSimpleName();

    protected boolean mIsSubactivityNotFound = false;

    protected abstract void onStartSubactivity();

    private ActivityResultLauncher<Intent> mSubactivityResultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (LOGV) {
            Log.d(TAG, "onCreate savedInstanceState=" + savedInstanceState);
        }
        super.onCreate(savedInstanceState);
        mSubactivityResultLauncher = registerForActivityResult(
                new StartDecoratedActivityForResult(),
                SubBaseActivity.this::onSubactivityResult);
        setNextAllowed(false);
        if (savedInstanceState == null) {
            onStartSubactivity();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSubactivityResultLauncher.unregister();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    protected void startSubactivity(Intent subactivityIntent) {
        Intent intent = getIntent();
        Bundle wizardBundle = intent.getBundleExtra(EXTRA_WIZARD_BUNDLE);
        if (wizardBundle.containsKey(EXTRA_SCRIPT_URI)) {
            subactivityIntent.putExtra(EXTRA_WIZARD_BUNDLE, wizardBundle);
        }
        try {
            mSubactivityResultLauncher.launch(subactivityIntent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "activity not found; start next screen and finish; intent=" + intent);
            mIsSubactivityNotFound = true;
            finishAction(RESULT_ACTIVITY_NOT_FOUND);
        }
    }

    @Override
    protected void onNextIntentResult(@NonNull ActivityResult activityResult) {
        super.onNextIntentResult(activityResult);
        int resultCode = activityResult.getResultCode();
        Intent data = activityResult.getData();
        if (data != null && data.getBooleanExtra("onBackPressed", false)) {
            onStartSubactivity();
        }
    }

    protected void onSubactivityResult(@NonNull ActivityResult activityResult) {
        int resultCode = activityResult.getResultCode();
        Intent data = activityResult.getData();
        if (resultCode != RESULT_CANCELED) {
            nextAction(resultCode, data);
        } else if (mIsSubactivityNotFound) {
            finishAction(RESULT_ACTIVITY_NOT_FOUND);
        } else if (data != null && data.getBooleanExtra("onBackPressed", false)) {
            onStartSubactivity();
        } else {
            finishAction(RESULT_CANCELED);
        }
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_loading_page;
    }

    @Override
    protected int getTitleResId() {
        return R.string.loading;
    }
}
