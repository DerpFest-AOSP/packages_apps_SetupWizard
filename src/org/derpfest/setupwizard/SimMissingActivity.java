/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.derpfest.setupwizard;

import static com.google.android.setupcompat.util.ResultCodes.RESULT_ACTIVITY_NOT_FOUND;
import static com.google.android.setupcompat.util.ResultCodes.RESULT_SKIP;

import android.content.Intent;
import android.widget.ImageView;

import androidx.activity.result.ActivityResult;

import org.derpfest.setupwizard.util.SetupWizardUtils;

public class SimMissingActivity extends SubBaseActivity {

    protected void onStartSubactivity() {
        if (!SetupWizardUtils.simMissing(this) || !SetupWizardUtils.hasTelephony(this)) {
            finishAction(RESULT_SKIP);
            return;
        }
        getGlifLayout().setDescriptionText(getString(R.string.sim_missing_summary));
        setNextAllowed(true);
        ImageView simLogo = ((ImageView) findViewById(R.id.sim_slot_image));
        simLogo.setImageResource(R.drawable.sim);
        simLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    }

    @Override
    protected void onSubactivityResult(ActivityResult activityResult) {
        int resultCode = activityResult.getResultCode();
        Intent data = activityResult.getData();
        if (resultCode != RESULT_CANCELED) {
            nextAction(resultCode, data);
        } else if (mIsSubactivityNotFound) {
            finishAction(RESULT_ACTIVITY_NOT_FOUND);
        } else if (data != null && data.getBooleanExtra("onBackPressed", false)) {
            if (SetupWizardUtils.simMissing(this)) {
                onStartSubactivity();
            } else {
                finishAction(RESULT_CANCELED, data);
            }
          } else if (!SetupWizardUtils.simMissing(this)) {
            nextAction(RESULT_OK);
        }
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.sim_missing_page;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_sim_missing;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_sim;
    }

}
