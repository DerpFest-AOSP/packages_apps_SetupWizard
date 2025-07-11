/*
 * SPDX-FileCopyrightText: 2008 The Android Open Source Project
 * SPDX-FileCopyrightText: 2022-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.derpfest.setupwizard.widget;

import android.annotation.Widget;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.TextView;

import androidx.annotation.IdRes;

import org.derpfest.setupwizard.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A widget that enables the user to select a number form a predefined range. There are two flavors
 * of this widget and which one is presented to the user depends on the current theme.
 * <ul>
 * <li>
 * If the current theme is derived from {@link android.R.style#Theme} the widget
 * presents the current value as an editable input field with an increment button
 * above and a decrement button below. Long pressing the buttons allows for a quick
 * change of the current value. Tapping on the input field allows to type in
 * a desired value.
 * </li>
 * <li>
 * If the current theme is derived from {@link android.R.style#Theme_Holo} or
 * {@link android.R.style#Theme_Holo_Light} the widget presents the current
 * value as an editable input field with a lesser value above and a greater
 * value below. Tapping on the lesser or greater value selects it by animating
 * the number axis up or down to make the chosen value current. Flinging up
 * or down allows for multiple increments or decrements of the current value.
 * Long pressing on the lesser and greater values also allows for a quick change
 * of the current value. Tapping on the current value allows to type in a
 * desired value.
 * </li>
 * </ul>
 * <p>
 * For an example of using this widget, see {@link android.widget.TimePicker}.
 * </p>
 */
@Widget
public class LocalePicker extends LinearLayout {

    /**
     * The number of items show in the selector wheel.
     */
    private static int sSelectorWheelItemCount = 3;

    /**
     * The default update interval during long press.
     */
    private static final long DEFAULT_LONG_PRESS_UPDATE_INTERVAL = 300;

    /**
     * The index of the middle selector item.
     */
    private static int sSelectorMiddleItemIndex = sSelectorWheelItemCount / 2;

    /**
     * The coefficient by which to adjust (divide) the max fling velocity.
     */
    private static final int SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT = 8;

    /**
     * The the duration for adjusting the selector wheel.
     */
    private static final int SELECTOR_ADJUSTMENT_DURATION_MILLIS = 800;

    /**
     * The duration of scrolling while snapping to a given position.
     */
    private static final int SNAP_SCROLL_DURATION = 300;

    /**
     * The strength of fading in the top and bottom while drawing the selector.
     */
    private static final float TOP_AND_BOTTOM_FADING_EDGE_STRENGTH = 0.9f;

    /**
     * The default unscaled height of the selection divider.
     */
    private static final int UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT = 1;

    /**
     * The default unscaled distance between the selection dividers.
     */
    private static final int UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE = 48;

    /**
     * The resource id for the default layout.
     */
    private static final int DEFAULT_LAYOUT_RESOURCE_ID =
            R.layout.locale_picker;

    /**
     * Constant for unspecified size.
     */
    private static final int SIZE_UNSPECIFIED = -1;

    /**
     * The increment button.
     */
    private final ImageButton mIncrementButton;

    /**
     * The decrement button.
     */
    private final ImageButton mDecrementButton;

    /**
     * The text for showing the current value.
     */
    private final EditText mInputText;

    /**
     * The distance between the two selection dividers.
     */
    private final int mSelectionDividersDistance;

    /**
     * The min height of this widget.
     */
    private final int mMinHeight;

    /**
     * The max height of this widget.
     */
    private final int mMaxHeight;

    /**
     * The max width of this widget.
     */
    private final int mMinWidth;

    /**
     * The max width of this widget.
     */
    private int mMaxWidth;

    /**
     * Flag whether to compute the max width.
     */
    private final boolean mComputeMaxWidth;

    /**
     * The height of the text.
     */
    private final int mTextSize;

    /**
     * The height of the gap between text elements if the selector wheel.
     */
    private int mSelectorTextGapHeight;

    /**
     * The values to be displayed instead the indices.
     */
    private String[] mDisplayedValues;

    /**
     * Lower value of the range of numbers allowed for the NumberPicker
     */
    private int mMinValue;

    /**
     * Upper value of the range of numbers allowed for the NumberPicker
     */
    private int mMaxValue;

    /**
     * Current value of this NumberPicker
     */
    private int mValue;

    /**
     * Listener to be notified upon current value change.
     */
    private OnValueChangeListener mOnValueChangeListener;

    /**
     * Listener to be notified upon scroll state change.
     */
    private OnScrollListener mOnScrollListener;

    /**
     * Formatter for for displaying the current value.
     */
    private Formatter mFormatter;

    /**
     * Cache for the string representation of selector indices.
     */
    private final SparseArray<String> mSelectorIndexToStringCache = new SparseArray<>();

    /**
     * The selector indices whose value are show by the selector.
     */
    private final int[] mSelectorIndices;

    /**
     * The {@link android.graphics.Paint} for drawing the selector.
     */
    private final Paint mSelectorWheelPaint;

    /**
     * The {@link android.graphics.drawable.Drawable} for pressed virtual (increment/decrement)
     * buttons.
     */
    private final Drawable mVirtualButtonPressedDrawable;

    /**
     * The height of a selector element (text + gap).
     */
    private int mSelectorElementHeight;

    /**
     * The initial offset of the scroll selector.
     */
    private int mInitialScrollOffset = Integer.MIN_VALUE;

    /**
     * The current offset of the scroll selector.
     */
    private int mCurrentScrollOffset;

    /**
     * The {@link android.widget.Scroller} responsible for flinging the selector.
     */
    private final Scroller mFlingScroller;

    /**
     * The {@link android.widget.Scroller} responsible for adjusting the selector.
     */
    private final Scroller mAdjustScroller;

    /**
     * The previous Y coordinate while scrolling the selector.
     */
    private int mPreviousScrollerY;

    /**
     * Handle to the reusable command for setting the input text selection.
     */
    private SetSelectionCommand mSetSelectionCommand;

    /**
     * Handle to the reusable command for changing the current value from long press by one.
     */
    private ChangeCurrentByOneFromLongPressCommand mChangeCurrentByOneFromLongPressCommand;

    /**
     * Command for beginning an edit of the current value via IME on long press.
     */
    private BeginSoftInputOnLongPressCommand mBeginSoftInputOnLongPressCommand;

    /**
     * The Y position of the last down event.
     */
    private float mLastDownEventY;

    /**
     * The time of the last down event.
     */
    private long mLastDownEventTime;

    /**
     * The Y position of the last down or move event.
     */
    private float mLastDownOrMoveEventY;

    /**
     * Determines speed during touch scrolling.
     */
    private VelocityTracker mVelocityTracker;

    /**
     * @see android.view.ViewConfiguration#getScaledTouchSlop()
     */
    private final int mTouchSlop;

    /**
     * @see android.view.ViewConfiguration#getScaledMinimumFlingVelocity()
     */
    private final int mMinimumFlingVelocity;

    /**
     * @see android.view.ViewConfiguration#getScaledMaximumFlingVelocity()
     */
    private final int mMaximumFlingVelocity;

    /**
     * Flag whether the selector should wrap around.
     */
    private boolean mWrapSelectorWheel;

    /**
     * The back ground color used to optimize scroller fading.
     */
    private final int mSolidColor;

    /**
     * Flag whether this widget has a selector wheel.
     */
    private final boolean mHasSelectorWheel;

    /**
     * Divider for showing item to be selected while scrolling
     */
    private final Drawable mSelectionDivider;

    /**
     * The height of the selection divider.
     */
    private final int mSelectionDividerHeight;

    /**
     * The current scroll state of the number picker.
     */
    private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;

    /**
     * Flag whether to ignore move events - we ignore such when we show in IME to prevent the
     * content from scrolling.
     */
    private boolean mIngonreMoveEvents;

    /**
     * Flag whether to show soft input on tap.
     */
    private boolean mShowSoftInputOnTap;

    /**
     * The top of the top selection divider.
     */
    private int mTopSelectionDividerTop;

    /**
     * The bottom of the bottom selection divider.
     */
    private int mBottomSelectionDividerBottom;

    /**
     * The virtual id of the last hovered child.
     */
    private int mLastHoveredChildVirtualViewId;

    /**
     * Whether the increment virtual button is pressed.
     */
    private boolean mIncrementVirtualButtonPressed;

    /**
     * Whether the decrement virtual button is pressed.
     */
    private boolean mDecrementVirtualButtonPressed;

    /**
     * Provider to report to clients the semantic structure of this widget.
     */
    private AccessibilityNodeProviderImpl mAccessibilityNodeProvider;

    /**
     * Helper class for managing pressed state of the virtual buttons.
     */
    private final PressedStateHelper mPressedStateHelper;

    /**
     * The keycode of the last handled DPAD down event.
     */
    private int mLastHandledDownDpadKeyCode = -1;

    /**
     * Interface to listen for changes of the current value.
     */
    public interface OnValueChangeListener {

        /**
         * Called upon a change of the current value.
         *
         * @param picker The NumberPicker associated with this listener.
         * @param oldVal The previous value.
         * @param newVal The new value.
         */
        void onValueChange(LocalePicker picker, int oldVal, int newVal);
    }

    /**
     * Interface to listen for the picker scroll state.
     */
    public interface OnScrollListener {

        /**
         * The view is not scrolling.
         */
        int SCROLL_STATE_IDLE = 0;

        /**
         * The user is scrolling using touch, and his finger is still on the screen.
         */
        int SCROLL_STATE_TOUCH_SCROLL = 1;

        /**
         * The user had previously been scrolling using touch and performed a fling.
         */
        int SCROLL_STATE_FLING = 2;

        /**
         * Callback invoked while the number picker scroll state has changed.
         *
         * @param view        The view whose scroll state is being reported.
         * @param scrollState The current scroll state. One of {@link #SCROLL_STATE_IDLE}, {@link
         *                    #SCROLL_STATE_TOUCH_SCROLL} or {@link #SCROLL_STATE_IDLE}.
         */
        void onScrollStateChange(LocalePicker view, int scrollState);
    }

    /**
     * Interface used to format current value into a string for presentation.
     */
    public interface Formatter {

        /**
         * Formats a string representation of the current value.
         *
         * @param value The currently selected value.
         * @return A formatted string representation.
         */
        String format(int value);
    }

    /**
     * Create a new number picker.
     *
     * @param context The application environment.
     */
    public LocalePicker(Context context) {
        this(context, null);
    }

    /**
     * Create a new number picker.
     *
     * @param context The application environment.
     * @param attrs   A collection of attributes.
     */
    public LocalePicker(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.localePickerStyle);
    }

    /**
     * Create a new number picker
     *
     * @param context  the application environment.
     * @param attrs    a collection of attributes.
     * @param defStyle The default style to apply to this view.
     */
    public LocalePicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        sSelectorWheelItemCount = context.getResources().getInteger(R.integer.local_picker_items);
        sSelectorMiddleItemIndex = context.getResources().getInteger(R.integer.local_picker_items)
                / 2;
        mSelectorIndices = new int[sSelectorWheelItemCount];
        // process style attributes
        TypedArray attributesArray = context.obtainStyledAttributes(
                attrs, R.styleable.LocalePicker, defStyle, 0);
        final int layoutResId = attributesArray.getResourceId(
                R.styleable.LocalePicker_internalLayout, DEFAULT_LAYOUT_RESOURCE_ID);

        mHasSelectorWheel = true;

        mSolidColor = attributesArray.getColor(R.styleable.LocalePicker_solidColor, 0);

        mSelectionDivider = attributesArray.getDrawable(R.styleable.LocalePicker_selectionDivider);

        final int defSelectionDividerHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, UNSCALED_DEFAULT_SELECTION_DIVIDER_HEIGHT,
                getResources().getDisplayMetrics());
        mSelectionDividerHeight = attributesArray.getDimensionPixelSize(
                R.styleable.LocalePicker_selectionDividerHeight, defSelectionDividerHeight);

        final int defSelectionDividerDistance = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, UNSCALED_DEFAULT_SELECTION_DIVIDERS_DISTANCE,
                getResources().getDisplayMetrics());
        mSelectionDividersDistance = attributesArray.getDimensionPixelSize(
                R.styleable.LocalePicker_selectionDividersDistance, defSelectionDividerDistance);

        mMinHeight = attributesArray.getDimensionPixelSize(
                R.styleable.LocalePicker_internalMinHeight, SIZE_UNSPECIFIED);

        mMaxHeight = attributesArray.getDimensionPixelSize(
                R.styleable.LocalePicker_internalMaxHeight, SIZE_UNSPECIFIED);
        if (mMinHeight != SIZE_UNSPECIFIED && mMaxHeight != SIZE_UNSPECIFIED
                && mMinHeight > mMaxHeight) {
            throw new IllegalArgumentException("minHeight > maxHeight");
        }

        mMinWidth = attributesArray.getDimensionPixelSize(
                R.styleable.LocalePicker_internalMinWidth, SIZE_UNSPECIFIED);

        mMaxWidth = attributesArray.getDimensionPixelSize(
                R.styleable.LocalePicker_internalMaxWidth, SIZE_UNSPECIFIED);
        if (mMinWidth != SIZE_UNSPECIFIED && mMaxWidth != SIZE_UNSPECIFIED
                && mMinWidth > mMaxWidth) {
            throw new IllegalArgumentException("minWidth > maxWidth");
        }

        mComputeMaxWidth = (mMaxWidth == SIZE_UNSPECIFIED);

        mVirtualButtonPressedDrawable = attributesArray.getDrawable(
                R.styleable.LocalePicker_virtualButtonPressedDrawable);

        attributesArray.recycle();

        mPressedStateHelper = new PressedStateHelper();

        // By default Linearlayout that we extend is not drawn. This is
        // its draw() method is not called but dispatchDraw() is called
        // directly (see ViewGroup.drawChild()). However, this class uses
        // the fading edge effect implemented by View and we need our
        // draw() method to be called. Therefore, we declare we will draw.
        setWillNotDraw(!mHasSelectorWheel);

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(layoutResId, this, true);

        OnClickListener onClickListener = new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput();
                mInputText.clearFocus();
                changeValueByOne(v.getId() == R.id.lp__increment);
            }
        };

        OnLongClickListener onLongClickListener = new OnLongClickListener() {
            public boolean onLongClick(View v) {
                hideSoftInput();
                mInputText.clearFocus();
                postChangeCurrentByOneFromLongPress(v.getId() == R.id.lp__increment, 0);
                return true;
            }
        };

        // increment button
        if (!mHasSelectorWheel) {
            mIncrementButton = findViewById(R.id.lp__increment);
            mIncrementButton.setOnClickListener(onClickListener);
            mIncrementButton.setOnLongClickListener(onLongClickListener);
        } else {
            mIncrementButton = null;
        }

        // decrement button
        if (!mHasSelectorWheel) {
            mDecrementButton = findViewById(R.id.lp__decrement);
            mDecrementButton.setOnClickListener(onClickListener);
            mDecrementButton.setOnLongClickListener(onLongClickListener);
        } else {
            mDecrementButton = null;
        }

        // input text
        mInputText = findViewById(R.id.localepicker_input);
        mInputText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mInputText.selectAll();
            } else {
                mInputText.setSelection(0, 0);
                validateInputTextView(v);
            }
        });
        mInputText.setFilters(new InputFilter[]{
                new InputTextFilter()
        });

        mInputText.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        mInputText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        // initialize constants
        ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = configuration.getScaledMaximumFlingVelocity()
                / SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT;
        mTextSize = (int) mInputText.getTextSize();

        // create the selector wheel paint
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Align.CENTER);
        paint.setTextSize(mTextSize);
        paint.setTypeface(mInputText.getTypeface());
        ColorStateList colors = mInputText.getTextColors();
        int color = colors.getColorForState(ENABLED_STATE_SET, Color.WHITE);
        paint.setColor(color);
        mSelectorWheelPaint = paint;

        // create the fling and adjust scrollers
        mFlingScroller = new Scroller(getContext(), null, true);
        mAdjustScroller = new Scroller(getContext(), new DecelerateInterpolator(2.5f));

        updateInputTextView();

        // If not explicitly specified this view is important for accessibility.
        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (!mHasSelectorWheel) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        final int msrdWdth = getMeasuredWidth();
        final int msrdHght = getMeasuredHeight();

        // Input text centered horizontally.
        final int inptTxtMsrdWdth = mInputText.getMeasuredWidth();
        final int inptTxtMsrdHght = mInputText.getMeasuredHeight();
        final int inptTxtLeft = (msrdWdth - inptTxtMsrdWdth) / 2;
        final int inptTxtTop = (msrdHght - inptTxtMsrdHght) / 2;
        final int inptTxtRight = inptTxtLeft + inptTxtMsrdWdth;
        final int inptTxtBottom = inptTxtTop + inptTxtMsrdHght;
        mInputText.layout(inptTxtLeft, inptTxtTop, inptTxtRight, inptTxtBottom);

        if (changed) {
            // need to do all this when we know our size
            initializeSelectorWheel();
            initializeFadingEdges();
            mTopSelectionDividerTop = (getHeight() - mSelectionDividersDistance) / 2
                    - mSelectionDividerHeight;
            mBottomSelectionDividerBottom = mTopSelectionDividerTop + 2 * mSelectionDividerHeight
                    + mSelectionDividersDistance;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!mHasSelectorWheel) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // Try greedily to fit the max width and height.
        final int newWidthMeasureSpec = makeMeasureSpec(widthMeasureSpec, mMaxWidth);
        final int newHeightMeasureSpec = makeMeasureSpec(heightMeasureSpec, mMaxHeight);
        super.onMeasure(newWidthMeasureSpec, newHeightMeasureSpec);
        // Flag if we are measured with width or height less than the respective min.
        final int widthSize = resolveSizeAndStateRespectingMinSize(mMinWidth, getMeasuredWidth(),
                widthMeasureSpec);
        final int heightSize = resolveSizeAndStateRespectingMinSize(mMinHeight, getMeasuredHeight(),
                heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);
    }

    /**
     * Move to the final position of a scroller. Ensures to force finish the scroller and if it is
     * not at its final position a scroll of the selector wheel is performed to fast forward to the
     * final position.
     *
     * @param scroller The scroller to whose final position to get.
     * @return True of the a move was performed, i.e. the scroller was not in final position.
     */
    private boolean moveToFinalScrollerPosition(Scroller scroller) {
        scroller.forceFinished(true);
        int amountToScroll = scroller.getFinalY() - scroller.getCurrY();
        int futureScrollOffset = (mCurrentScrollOffset + amountToScroll) % mSelectorElementHeight;
        int overshootAdjustment = mInitialScrollOffset - futureScrollOffset;
        if (overshootAdjustment != 0) {
            if (Math.abs(overshootAdjustment) > mSelectorElementHeight / 2) {
                if (overshootAdjustment > 0) {
                    overshootAdjustment -= mSelectorElementHeight;
                } else {
                    overshootAdjustment += mSelectorElementHeight;
                }
            }
            amountToScroll += overshootAdjustment;
            scrollBy(0, amountToScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!mHasSelectorWheel || !isEnabled()) {
            return false;
        }
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            removeAllCallbacks();
            mInputText.setVisibility(View.INVISIBLE);
            mLastDownOrMoveEventY = mLastDownEventY = event.getY();
            mLastDownEventTime = event.getEventTime();
            mIngonreMoveEvents = false;
            mShowSoftInputOnTap = false;
            // Handle pressed state before any state change.
            if (mLastDownEventY < mTopSelectionDividerTop) {
                if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                    mPressedStateHelper.buttonPressDelayed(
                            PressedStateHelper.BUTTON_DECREMENT);
                }
            } else if (mLastDownEventY > mBottomSelectionDividerBottom) {
                if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                    mPressedStateHelper.buttonPressDelayed(
                            PressedStateHelper.BUTTON_INCREMENT);
                }
            }
            // Make sure we support flinging inside scrollables.
            getParent().requestDisallowInterceptTouchEvent(true);
            if (!mFlingScroller.isFinished()) {
                mFlingScroller.forceFinished(true);
                mAdjustScroller.forceFinished(true);
                onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
            } else if (!mAdjustScroller.isFinished()) {
                mFlingScroller.forceFinished(true);
                mAdjustScroller.forceFinished(true);
            } else if (mLastDownEventY < mTopSelectionDividerTop) {
                hideSoftInput();
                postChangeCurrentByOneFromLongPress(
                        false, ViewConfiguration.getLongPressTimeout());
            } else if (mLastDownEventY > mBottomSelectionDividerBottom) {
                hideSoftInput();
                postChangeCurrentByOneFromLongPress(
                        true, ViewConfiguration.getLongPressTimeout());
            } else {
                mShowSoftInputOnTap = true;
                postBeginSoftInputOnLongPressCommand();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !mHasSelectorWheel) {
            return false;
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (mIngonreMoveEvents) {
                    break;
                }
                float currentMoveY = event.getY();
                if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    int deltaDownY = (int) Math.abs(currentMoveY - mLastDownEventY);
                    if (deltaDownY > mTouchSlop) {
                        removeAllCallbacks();
                        onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                    }
                } else {
                    int deltaMoveY = (int) ((currentMoveY - mLastDownOrMoveEventY));
                    scrollBy(0, deltaMoveY);
                    invalidate();
                }
                mLastDownOrMoveEventY = currentMoveY;
            }
            break;
            case MotionEvent.ACTION_UP: {
                removeBeginSoftInputCommand();
                removeChangeCurrentByOneFromLongPress();
                mPressedStateHelper.cancel();
                VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                int initialVelocity = (int) velocityTracker.getYVelocity();
                if (Math.abs(initialVelocity) > mMinimumFlingVelocity) {
                    fling(initialVelocity);
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
                } else {
                    int eventY = (int) event.getY();
                    int deltaMoveY = (int) Math.abs(eventY - mLastDownEventY);
                    long deltaTime = event.getEventTime() - mLastDownEventTime;
                    if (deltaMoveY <= mTouchSlop && deltaTime < ViewConfiguration.getTapTimeout()) {
                        if (mShowSoftInputOnTap) {
                            mShowSoftInputOnTap = false;
                            showSoftInput();
                        } else {
                            int selectorIndexOffset = (eventY / mSelectorElementHeight)
                                    - sSelectorMiddleItemIndex;
                            if (selectorIndexOffset > 0) {
                                changeValueByOne(true);
                                mPressedStateHelper.buttonTapped(
                                        PressedStateHelper.BUTTON_INCREMENT);
                            } else if (selectorIndexOffset < 0) {
                                changeValueByOne(false);
                                mPressedStateHelper.buttonTapped(
                                        PressedStateHelper.BUTTON_DECREMENT);
                            }
                        }
                    } else {
                        ensureScrollWheelAdjusted();
                    }
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                }
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
            break;
        }
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                removeAllCallbacks();
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                removeAllCallbacks();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!mHasSelectorWheel) {
                    break;
                }
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (mWrapSelectorWheel || (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                                ? getValue() < getMaxValue() : getValue() > getMinValue()) {
                            requestFocus();
                            mLastHandledDownDpadKeyCode = keyCode;
                            removeAllCallbacks();
                            if (mFlingScroller.isFinished()) {
                                changeValueByOne(keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
                            }
                            return true;
                        }
                        break;
                    case KeyEvent.ACTION_UP:
                        if (mLastHandledDownDpadKeyCode == keyCode) {
                            mLastHandledDownDpadKeyCode = -1;
                            return true;
                        }
                        break;
                }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                removeAllCallbacks();
                break;
        }
        return super.dispatchTrackballEvent(event);
    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        if (!mHasSelectorWheel) {
            return super.dispatchHoverEvent(event);
        }
        if (AccessibilityManager.getInstance(mContext).isEnabled()) {
            final int eventY = (int) event.getY();
            final int hoveredVirtualViewId;
            if (eventY < mTopSelectionDividerTop) {
                hoveredVirtualViewId = AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_DECREMENT;
            } else if (eventY > mBottomSelectionDividerBottom) {
                hoveredVirtualViewId = AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INCREMENT;
            } else {
                hoveredVirtualViewId = AccessibilityNodeProviderImpl.VIRTUAL_VIEW_ID_INPUT;
            }
            final int action = event.getActionMasked();
            AccessibilityNodeProviderImpl provider =
                    (AccessibilityNodeProviderImpl) getAccessibilityNodeProvider();
            switch (action) {
                case MotionEvent.ACTION_HOVER_ENTER: {
                    provider.sendAccessibilityEventForVirtualView(hoveredVirtualViewId,
                            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
                    mLastHoveredChildVirtualViewId = hoveredVirtualViewId;
                    provider.performAction(hoveredVirtualViewId,
                            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                }
                break;
                case MotionEvent.ACTION_HOVER_MOVE: {
                    if (mLastHoveredChildVirtualViewId != hoveredVirtualViewId
                            && mLastHoveredChildVirtualViewId != View.NO_ID) {
                        provider.sendAccessibilityEventForVirtualView(
                                mLastHoveredChildVirtualViewId,
                                AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                        provider.sendAccessibilityEventForVirtualView(hoveredVirtualViewId,
                                AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
                        mLastHoveredChildVirtualViewId = hoveredVirtualViewId;
                        provider.performAction(hoveredVirtualViewId,
                                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
                    }
                }
                break;
                case MotionEvent.ACTION_HOVER_EXIT: {
                    provider.sendAccessibilityEventForVirtualView(hoveredVirtualViewId,
                            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
                    mLastHoveredChildVirtualViewId = View.NO_ID;
                }
                break;
            }
        }
        return false;
    }

    @Override
    public void computeScroll() {
        Scroller scroller = mFlingScroller;
        if (scroller.isFinished()) {
            scroller = mAdjustScroller;
            if (scroller.isFinished()) {
                return;
            }
        }
        scroller.computeScrollOffset();
        int currentScrollerY = scroller.getCurrY();
        if (mPreviousScrollerY == 0) {
            mPreviousScrollerY = scroller.getStartY();
        }
        scrollBy(0, currentScrollerY - mPreviousScrollerY);
        mPreviousScrollerY = currentScrollerY;
        if (scroller.isFinished()) {
            onScrollerFinished(scroller);
        } else {
            invalidate();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!mHasSelectorWheel) {
            mIncrementButton.setEnabled(enabled);
        }
        if (!mHasSelectorWheel) {
            mDecrementButton.setEnabled(enabled);
        }
        mInputText.setEnabled(enabled);
    }

    @Override
    public void scrollBy(int x, int y) {
        int[] selectorIndices = mSelectorIndices;
        if (!mWrapSelectorWheel && y > 0
                && selectorIndices[sSelectorMiddleItemIndex] <= mMinValue) {
            mCurrentScrollOffset = mInitialScrollOffset;
            return;
        }
        if (!mWrapSelectorWheel && y < 0
                && selectorIndices[sSelectorMiddleItemIndex] >= mMaxValue) {
            mCurrentScrollOffset = mInitialScrollOffset;
            return;
        }
        mCurrentScrollOffset += y;
        while (mCurrentScrollOffset - mInitialScrollOffset > mSelectorTextGapHeight) {
            mCurrentScrollOffset -= mSelectorElementHeight;
            decrementSelectorIndices(selectorIndices);
            setValueInternal(selectorIndices[sSelectorMiddleItemIndex], true);
            if (!mWrapSelectorWheel && selectorIndices[sSelectorMiddleItemIndex] <= mMinValue) {
                mCurrentScrollOffset = mInitialScrollOffset;
            }
        }
        while (mCurrentScrollOffset - mInitialScrollOffset < -mSelectorTextGapHeight) {
            mCurrentScrollOffset += mSelectorElementHeight;
            incrementSelectorIndices(selectorIndices);
            setValueInternal(selectorIndices[sSelectorMiddleItemIndex], true);
            if (!mWrapSelectorWheel && selectorIndices[sSelectorMiddleItemIndex] >= mMaxValue) {
                mCurrentScrollOffset = mInitialScrollOffset;
            }
        }
    }

    @Override
    public int getSolidColor() {
        return mSolidColor;
    }

    /**
     * Sets the listener to be notified on change of the current value.
     *
     * @param onValueChangedListener The listener.
     */
    public void setOnValueChangedListener(OnValueChangeListener onValueChangedListener) {
        mOnValueChangeListener = onValueChangedListener;
    }

    /**
     * Set listener to be notified for scroll state changes.
     *
     * @param onScrollListener The listener.
     */
    public void setOnScrollListener(OnScrollListener onScrollListener) {
        mOnScrollListener = onScrollListener;
    }

    /**
     * Set the current value for the number picker.
     * <p>
     * If the argument is less than the getMinValue() and {@link
     * LocalePicker#getWrapSelectorWheel()} is <code>false</code> the current value is set to the
     * getMinValue() value.
     * </p>
     * <p>
     * If the argument is less than the getMinValue() and {@link
     * LocalePicker#getWrapSelectorWheel()} is <code>true</code> the current value is set to the
     * {@link LocalePicker#getMaxValue()} value.
     * </p>
     * <p>
     * If the argument is less than the {@link LocalePicker#getMaxValue()} and {@link
     * LocalePicker#getWrapSelectorWheel()} is <code>false</code> the current value is set to the
     * {@link LocalePicker#getMaxValue()} value.
     * </p>
     * <p>
     * If the argument is less than the {@link LocalePicker#getMaxValue()} and {@link
     * LocalePicker#getWrapSelectorWheel()} is <code>true</code> the current value is set to the
     * getMinValue() value.
     * </p>
     *
     * @param value The current value.
     * @see #setWrapSelectorWheel(boolean)
     * @see #setMaxValue(int)
     */
    public void setValue(int value) {
        setValueInternal(value, false);
    }

    /**
     * Sets the next focused item for a remote D-pad key right.
     * @param id The ID of the next view.
     */
    public void setNextRight(@IdRes int id) {
        setNextFocusRightId(id);
    }

    /**
     * Shows the soft input for its input text.
     */
    private void showSoftInput() {
        InputMethodManager inputMethodManager = mContext.getSystemService(InputMethodManager.class);
        if (inputMethodManager != null) {
            if (mHasSelectorWheel) {
                mInputText.setVisibility(View.VISIBLE);
            }
            mInputText.requestFocus();
            inputMethodManager.showSoftInput(mInputText, 0);
        }
    }

    /**
     * Hides the soft input if it is active for the input text.
     */
    private void hideSoftInput() {
        InputMethodManager inputMethodManager = mContext.getSystemService(InputMethodManager.class);
        if (inputMethodManager != null && inputMethodManager.isActive(mInputText)) {
            inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
            if (mHasSelectorWheel) {
                mInputText.setVisibility(View.INVISIBLE);
            }
        }
    }

    /**
     * Computes the max width if no such specified as an attribute.
     */
    private void tryComputeMaxWidth() {
        if (!mComputeMaxWidth) {
            return;
        }
        int maxTextWidth = 0;
        if (mDisplayedValues == null) {
            float maxDigitWidth = 0;
            for (int i = 0; i <= 9; i++) {
                final float digitWidth = mSelectorWheelPaint.measureText(formatNumberWithLocale(i));
                if (digitWidth > maxDigitWidth) {
                    maxDigitWidth = digitWidth;
                }
            }
            int numberOfDigits = 0;
            int current = mMaxValue;
            while (current > 0) {
                numberOfDigits++;
                current = current / 10;
            }
            maxTextWidth = (int) (numberOfDigits * maxDigitWidth);
        } else {
            final int valueCount = mDisplayedValues.length;
            for (String displayedValue : mDisplayedValues) {
                final float textWidth = mSelectorWheelPaint.measureText(displayedValue);
                if (textWidth > maxTextWidth) {
                    maxTextWidth = (int) textWidth;
                }
            }
        }
        maxTextWidth += mInputText.getPaddingLeft() + mInputText.getPaddingRight();
        if (mMaxWidth != maxTextWidth) {
            mMaxWidth = Math.max(maxTextWidth, mMinWidth);
            invalidate();
        }
    }

    /**
     * Gets whether the selector wheel wraps when reaching the min/max value.
     *
     * @return True if the selector wheel wraps.
     * @see #getMaxValue()
     */
    public boolean getWrapSelectorWheel() {
        return mWrapSelectorWheel;
    }

    /**
     * Sets whether the selector wheel shown during flinging/scrolling should wrap around the
     * getMinValue() and {@link LocalePicker#getMaxValue()} values.
     * <p>
     * By default if the range (max - min) is more than the number of items shown on the selector
     * wheel the selector wheel wrapping is enabled.
     * </p>
     * <p>
     * <strong>Note:</strong> If the number of items, i.e. the range (
     * {@link #getMaxValue()} - getMinValue()) is less than the number of items shown on
     * the selector wheel, the selector wheel will not wrap. Hence, in such a case calling this
     * method is a NOP.
     * </p>
     *
     * @param wrapSelectorWheel Whether to wrap.
     */
    public void setWrapSelectorWheel(boolean wrapSelectorWheel) {
        final boolean wrappingAllowed = (mMaxValue - mMinValue) >= mSelectorIndices.length;
        if ((!wrapSelectorWheel || wrappingAllowed) && wrapSelectorWheel != mWrapSelectorWheel) {
            mWrapSelectorWheel = wrapSelectorWheel;
        }
    }

    /**
     * Returns the value of the picker.
     *
     * @return The value.
     */
    public int getValue() {
        return mValue;
    }

    /**
     * Returns the min value of the picker.
     *
     * @return The min value
     */
    public int getMinValue() {
        return mMinValue;
    }

    /**
     * Returns the max value of the picker.
     *
     * @return The max value.
     */
    public int getMaxValue() {
        return mMaxValue;
    }

    /**
     * Sets the max value of the picker.
     *
     * @param maxValue The max value inclusive.
     * <p>
     *                 <strong>Note:</strong> The length of the displayed values array
     *                 set via {@link #setDisplayedValues(String[])} must be equal to the range of
     *                 selectable numbers which is equal to {@link #getMaxValue()} -
     *                 getMinValue() + 1.
     */
    public void setMaxValue(int maxValue) {
        if (mMaxValue == maxValue) {
            return;
        }
        if (maxValue < 0) {
            throw new IllegalArgumentException("maxValue must be >= 0");
        }
        mMaxValue = maxValue;
        if (mMaxValue < mValue) {
            mValue = mMaxValue;
        }
        boolean wrapSelectorWheel = mMaxValue - mMinValue > mSelectorIndices.length;
        setWrapSelectorWheel(wrapSelectorWheel);
        initializeSelectorWheelIndices();
        updateInputTextView();
        tryComputeMaxWidth();
        invalidate();
    }

    /**
     * Sets the values to be displayed.
     *
     * @param displayedValues The displayed values.
     * <p>
     *                        <strong>Note:</strong> The length of the displayed values array
     *                        must be equal to the range of selectable numbers which is equal to
     *                        {@link #getMaxValue()} - getMinValue() + 1.
     */
    public void setDisplayedValues(String[] displayedValues) {
        if (mDisplayedValues == displayedValues) {
            return;
        }
        mDisplayedValues = displayedValues;
        if (mDisplayedValues != null) {
            // Allow text entry rather than strictly numeric entry.
            mInputText.setRawInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        } else {
            mInputText.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        }
        updateInputTextView();
        initializeSelectorWheelIndices();
        tryComputeMaxWidth();
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeAllCallbacks();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mHasSelectorWheel) {
            super.onDraw(canvas);
            return;
        }
        float x = (float) (mRight - mLeft) / 2;
        float y = mCurrentScrollOffset;

        // draw the virtual buttons pressed state if needed
        if (mVirtualButtonPressedDrawable != null
                && mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
            if (mDecrementVirtualButtonPressed) {
                mVirtualButtonPressedDrawable.setState(PRESSED_STATE_SET);
                mVirtualButtonPressedDrawable.setBounds(0, 0, mRight, mTopSelectionDividerTop);
                mVirtualButtonPressedDrawable.draw(canvas);
            }
            if (mIncrementVirtualButtonPressed) {
                mVirtualButtonPressedDrawable.setState(PRESSED_STATE_SET);
                mVirtualButtonPressedDrawable.setBounds(0, mBottomSelectionDividerBottom, mRight,
                        mBottom);
                mVirtualButtonPressedDrawable.draw(canvas);
            }
        }

        // draw the selector wheel
        int[] selectorIndices = mSelectorIndices;
        for (int i = 0; i < selectorIndices.length; i++) {
            int selectorIndex = selectorIndices[i];
            String scrollSelectorValue = mSelectorIndexToStringCache.get(selectorIndex);
            // Do not draw the middle item if input is visible since the input
            // is shown only if the wheel is static and it covers the middle
            // item. Otherwise, if the user starts editing the text via the
            // IME he may see a dimmed version of the old value intermixed
            // with the new one.
            if (i != sSelectorMiddleItemIndex || mInputText.getVisibility() != VISIBLE) {
                canvas.drawText(scrollSelectorValue, x, y, mSelectorWheelPaint);
            }
            y += mSelectorElementHeight;
        }

        // draw the selection dividers
        if (mSelectionDivider != null) {
            // draw the top divider
            int topOfTopDivider = mTopSelectionDividerTop;
            int bottomOfTopDivider = topOfTopDivider + mSelectionDividerHeight;
            mSelectionDivider.setBounds(0, topOfTopDivider, mRight, bottomOfTopDivider);
            mSelectionDivider.draw(canvas);

            // draw the bottom divider
            int bottomOfBottomDivider = mBottomSelectionDividerBottom;
            int topOfBottomDivider = bottomOfBottomDivider - mSelectionDividerHeight;
            mSelectionDivider.setBounds(0, topOfBottomDivider, mRight, bottomOfBottomDivider);
            mSelectionDivider.draw(canvas);
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(LocalePicker.class.getName());
        event.setScrollable(true);
        event.setScrollY((mMinValue + mValue) * mSelectorElementHeight);
        event.setMaxScrollY((mMaxValue - mMinValue) * mSelectorElementHeight);
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (!mHasSelectorWheel) {
            return super.getAccessibilityNodeProvider();
        }
        if (mAccessibilityNodeProvider == null) {
            mAccessibilityNodeProvider = new AccessibilityNodeProviderImpl();
        }
        return mAccessibilityNodeProvider;
    }

    /**
     * Makes a measure spec that tries greedily to use the max value.
     *
     * @param measureSpec The measure spec.
     * @param maxSize     The max value for the size.
     * @return A measure spec greedily imposing the max size.
     */
    private int makeMeasureSpec(int measureSpec, int maxSize) {
        if (maxSize == SIZE_UNSPECIFIED) {
            return measureSpec;
        }
        final int size = MeasureSpec.getSize(measureSpec);
        final int mode = MeasureSpec.getMode(measureSpec);
        return switch (mode) {
            case MeasureSpec.EXACTLY -> measureSpec;
            case MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(Math.min(size, maxSize),
                    MeasureSpec.EXACTLY);
            case MeasureSpec.UNSPECIFIED -> MeasureSpec.makeMeasureSpec(maxSize,
                    MeasureSpec.EXACTLY);
            default -> throw new IllegalArgumentException("Unknown measure mode: " + mode);
        };
    }

    /**
     * Utility to reconcile a desired size and state, with constraints imposed by a MeasureSpec.
     * Tries to respect the min size, unless a different size is imposed by the constraints.
     *
     * @param minSize      The minimal desired size.
     * @param measuredSize The currently measured size.
     * @param measureSpec  The current measure spec.
     * @return The resolved size and state.
     */
    private int resolveSizeAndStateRespectingMinSize(
            int minSize, int measuredSize, int measureSpec) {
        if (minSize != SIZE_UNSPECIFIED) {
            final int desiredWidth = Math.max(minSize, measuredSize);
            return resolveSizeAndState(desiredWidth, measureSpec, 0);
        } else {
            return measuredSize;
        }
    }

    /**
     * Resets the selector indices and clear the cached string representation of these indices.
     */
    private void initializeSelectorWheelIndices() {
        mSelectorIndexToStringCache.clear();
        int[] selectorIndices = mSelectorIndices;
        int current = getValue();
        for (int i = 0; i < mSelectorIndices.length; i++) {
            int selectorIndex = current + (i - sSelectorMiddleItemIndex);
            if (mWrapSelectorWheel) {
                selectorIndex = getWrappedSelectorIndex(selectorIndex);
            }
            selectorIndices[i] = selectorIndex;
            ensureCachedScrollSelectorValue(selectorIndices[i]);
        }
    }

    /**
     * Sets the current value of this NumberPicker.
     *
     * @param current      The new value of the NumberPicker.
     * @param notifyChange Whether to notify if the current value changed.
     */
    private void setValueInternal(int current, boolean notifyChange) {
        if (mValue == current) {
            return;
        }
        // Wrap around the values if we go past the start or end
        if (mWrapSelectorWheel) {
            current = getWrappedSelectorIndex(current);
        } else {
            current = Math.max(current, mMinValue);
            current = Math.min(current, mMaxValue);
        }
        int previous = mValue;
        mValue = current;
        updateInputTextView();
        if (notifyChange) {
            notifyChange(previous, current);
        }
        initializeSelectorWheelIndices();
        invalidate();
    }

    /**
     * Changes the current value by one which is increment or decrement based on the passes
     * argument. decrement the current value.
     *
     * @param increment True to increment, false to decrement.
     */
    private void changeValueByOne(boolean increment) {
        if (mHasSelectorWheel) {
            mInputText.setVisibility(View.INVISIBLE);
            if (!moveToFinalScrollerPosition(mFlingScroller)) {
                moveToFinalScrollerPosition(mAdjustScroller);
            }
            mPreviousScrollerY = 0;
            if (increment) {
                mFlingScroller.startScroll(0, 0, 0, -mSelectorElementHeight, SNAP_SCROLL_DURATION);
            } else {
                mFlingScroller.startScroll(0, 0, 0, mSelectorElementHeight, SNAP_SCROLL_DURATION);
            }
            invalidate();
        } else {
            if (increment) {
                setValueInternal(mValue + 1, true);
            } else {
                setValueInternal(mValue - 1, true);
            }
        }
    }

    private void initializeSelectorWheel() {
        initializeSelectorWheelIndices();
        int[] selectorIndices = mSelectorIndices;
        int totalTextHeight = selectorIndices.length * mTextSize;
        float totalTextGapHeight = (mBottom - mTop) - totalTextHeight;
        float textGapCount = selectorIndices.length;
        mSelectorTextGapHeight = (int) (totalTextGapHeight / textGapCount + 0.5f);
        mSelectorElementHeight = mTextSize + mSelectorTextGapHeight;
        // Ensure that the middle item is positioned the same as the text in
        // mInputText
        int editTextTextPosition = mInputText.getBaseline() + mInputText.getTop();
        mInitialScrollOffset = editTextTextPosition
                - (mSelectorElementHeight * sSelectorMiddleItemIndex);
        mCurrentScrollOffset = mInitialScrollOffset;
        updateInputTextView();
    }

    private void initializeFadingEdges() {
        setVerticalFadingEdgeEnabled(true);
        setFadingEdgeLength((mBottom - mTop - mTextSize) / 2);
    }

    /**
     * Callback invoked upon completion of a given <code>scroller</code>.
     */
    private void onScrollerFinished(Scroller scroller) {
        if (scroller == mFlingScroller) {
            if (!ensureScrollWheelAdjusted()) {
                updateInputTextView();
            }
            onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
        } else {
            if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                updateInputTextView();
            }
        }
    }

    /**
     * Handles transition to a given <code>scrollState</code>
     */
    private void onScrollStateChange(int scrollState) {
        if (mScrollState == scrollState) {
            return;
        }
        mScrollState = scrollState;
        if (mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChange(this, scrollState);
        }
    }

    /**
     * Flings the selector with the given <code>velocityY</code>.
     */
    private void fling(int velocityY) {
        mPreviousScrollerY = 0;

        if (velocityY > 0) {
            mFlingScroller.fling(0, 0, 0, velocityY, 0, 0, 0, Integer.MAX_VALUE);
        } else {
            mFlingScroller.fling(0, Integer.MAX_VALUE, 0, velocityY, 0, 0, 0, Integer.MAX_VALUE);
        }

        invalidate();
    }

    /**
     * @return The wrapped index <code>selectorIndex</code> value.
     */
    private int getWrappedSelectorIndex(int selectorIndex) {
        if (selectorIndex > mMaxValue) {
            return mMinValue + (selectorIndex - mMaxValue) % (mMaxValue - mMinValue) - 1;
        } else if (selectorIndex < mMinValue) {
            return mMaxValue - (mMinValue - selectorIndex) % (mMaxValue - mMinValue) + 1;
        }
        return selectorIndex;
    }

    /**
     * Increments the <code>selectorIndices</code> whose string representations will be displayed in
     * the selector.
     */
    private void incrementSelectorIndices(int[] selectorIndices) {
        for (int i = 0; i < selectorIndices.length - 1; i++) {
            selectorIndices[i] = selectorIndices[i + 1];
        }
        int nextScrollSelectorIndex = selectorIndices[selectorIndices.length - 2] + 1;
        if (mWrapSelectorWheel && nextScrollSelectorIndex > mMaxValue) {
            nextScrollSelectorIndex = mMinValue;
        }
        selectorIndices[selectorIndices.length - 1] = nextScrollSelectorIndex;
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex);
    }

    /**
     * Decrements the <code>selectorIndices</code> whose string representations will be displayed in
     * the selector.
     */
    private void decrementSelectorIndices(int[] selectorIndices) {
        for (int i = selectorIndices.length - 1; i > 0; i--) {
            selectorIndices[i] = selectorIndices[i - 1];
        }
        int nextScrollSelectorIndex = selectorIndices[1] - 1;
        if (mWrapSelectorWheel && nextScrollSelectorIndex < mMinValue) {
            nextScrollSelectorIndex = mMaxValue;
        }
        selectorIndices[0] = nextScrollSelectorIndex;
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex);
    }

    /**
     * Ensures we have a cached string representation of the given <code> selectorIndex</code> to
     * avoid multiple instantiations of the same string.
     */
    private void ensureCachedScrollSelectorValue(int selectorIndex) {
        SparseArray<String> cache = mSelectorIndexToStringCache;
        String scrollSelectorValue = cache.get(selectorIndex);
        if (scrollSelectorValue != null) {
            return;
        }
        if (selectorIndex < mMinValue || selectorIndex > mMaxValue) {
            scrollSelectorValue = "";
        } else {
            if (mDisplayedValues != null) {
                int displayedValueIndex = selectorIndex - mMinValue;
                scrollSelectorValue = mDisplayedValues[displayedValueIndex];
            } else {
                scrollSelectorValue = formatNumber(selectorIndex);
            }
        }
        cache.put(selectorIndex, scrollSelectorValue);
    }

    private String formatNumber(int value) {
        return (mFormatter != null) ? mFormatter.format(value) : formatNumberWithLocale(value);
    }

    private void validateInputTextView(View v) {
        String str = String.valueOf(((TextView) v).getText());
        if (TextUtils.isEmpty(str)) {
            // Restore to the old value as we don't allow empty values
            updateInputTextView();
        } else {
            // Check the new value and ensure it's in range
            int current = getSelectedPos(str);
            setValueInternal(current, true);
        }
    }

    /**
     * Updates the view of this NumberPicker. If displayValues were specified in the string
     * corresponding to the index specified by the current value will be returned.
     *
     * @return Whether the text was updated.
     */
    private boolean updateInputTextView() {
        /*
         * If we don't have displayed values then use the current number else
         * find the correct value in the displayed values for the current
         * number.
         */
        String text = (mDisplayedValues == null) ? formatNumber(mValue)
                : mDisplayedValues[mValue - mMinValue];
        if (!TextUtils.isEmpty(text) && !text.equals(mInputText.getText().toString())) {
            mInputText.setText(text);
            return true;
        }

        return false;
    }

    /**
     * Notifies the listener, if registered, of a change of the value of this NumberPicker.
     */
    private void notifyChange(int previous, int current) {
        if (mOnValueChangeListener != null) {
            mOnValueChangeListener.onValueChange(this, previous, mValue);
        }
    }

    /**
     * Posts a command for changing the current value by one.
     *
     * @param increment Whether to increment or decrement the value.
     */
    private void postChangeCurrentByOneFromLongPress(boolean increment, long delayMillis) {
        if (mChangeCurrentByOneFromLongPressCommand == null) {
            mChangeCurrentByOneFromLongPressCommand = new ChangeCurrentByOneFromLongPressCommand();
        } else {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
        mChangeCurrentByOneFromLongPressCommand.setStep(increment);
        postDelayed(mChangeCurrentByOneFromLongPressCommand, delayMillis);
    }

    /**
     * Removes the command for changing the current value by one.
     */
    private void removeChangeCurrentByOneFromLongPress() {
        if (mChangeCurrentByOneFromLongPressCommand != null) {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
    }

    /**
     * Posts a command for beginning an edit of the current value via IME on long press.
     */
    private void postBeginSoftInputOnLongPressCommand() {
        if (mBeginSoftInputOnLongPressCommand == null) {
            mBeginSoftInputOnLongPressCommand = new BeginSoftInputOnLongPressCommand();
        } else {
            removeCallbacks(mBeginSoftInputOnLongPressCommand);
        }
        postDelayed(mBeginSoftInputOnLongPressCommand, ViewConfiguration.getLongPressTimeout());
    }

    /**
     * Removes the command for beginning an edit of the current value via IME.
     */
    private void removeBeginSoftInputCommand() {
        if (mBeginSoftInputOnLongPressCommand != null) {
            removeCallbacks(mBeginSoftInputOnLongPressCommand);
        }
    }

    /**
     * Removes all pending callback from the message queue.
     */
    private void removeAllCallbacks() {
        if (mChangeCurrentByOneFromLongPressCommand != null) {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
        if (mSetSelectionCommand != null) {
            removeCallbacks(mSetSelectionCommand);
        }
        if (mBeginSoftInputOnLongPressCommand != null) {
            removeCallbacks(mBeginSoftInputOnLongPressCommand);
        }
        mPressedStateHelper.cancel();
    }

    /**
     * @return The selected index given its displayed <code>value</code>.
     */
    private int getSelectedPos(String value) {
        // Ignore as if it's not a number we don't care
        if (mDisplayedValues != null) {
            for (int i = 0; i < mDisplayedValues.length; i++) {
                // Don't force the user to type in jan when ja will do
                value = value.toLowerCase();
                if (mDisplayedValues[i].toLowerCase().startsWith(value)) {
                    return mMinValue + i;
                }
            }

            /*
             * The user might have typed in a number into the month field i.e.
             * 10 instead of OCT so support that too.
             */
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Ignore as if it's not a number we don't care
        }
        return mMinValue;
    }

    /**
     * Posts an {@link SetSelectionCommand} from the given <code>selectionStart
     * </code> to <code>selectionEnd</code>.
     */
    private void postSetSelectionCommand(int selectionStart, int selectionEnd) {
        if (mSetSelectionCommand == null) {
            mSetSelectionCommand = new SetSelectionCommand();
        } else {
            removeCallbacks(mSetSelectionCommand);
        }
        mSetSelectionCommand.mSelectionStart = selectionStart;
        mSetSelectionCommand.mSelectionEnd = selectionEnd;
        post(mSetSelectionCommand);
    }

    /**
     * The numbers accepted by the input text's {@link android.view.LayoutInflater.Filter}
     */
    private static final char[] DIGIT_CHARACTERS = new char[]{
            // Latin digits are the common case
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            // Arabic-Indic
            '٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨'
            , '٩',
            // Extended Arabic-Indic
            '۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸'
            , '۹'
    };

    /**
     * Filter for accepting only valid indices or prefixes of the string representation of valid
     * indices.
     */
    class InputTextFilter extends NumberKeyListener {

        // XXX This doesn't allow for range limits when controlled by a
        // soft input method!
        public int getInputType() {
            return InputType.TYPE_CLASS_TEXT;
        }

        @Override
        protected char[] getAcceptedChars() {
            return DIGIT_CHARACTERS;
        }

        @Override
        public CharSequence filter(
                CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (mDisplayedValues == null) {
                CharSequence filtered = super.filter(source, start, end, dest, dstart, dend);
                if (filtered == null) {
                    filtered = source.subSequence(start, end);
                }

                String result = String.valueOf(dest.subSequence(0, dstart)) + filtered
                        + dest.subSequence(dend, dest.length());

                if (result.isEmpty()) {
                    return result;
                }
                int val = getSelectedPos(result);

                /*
                 * Ensure the user can't type in a value greater than the max
                 * allowed. We have to allow less than min as the user might
                 * want to delete some numbers and then type a new number.
                 * And prevent multiple-"0" that exceeds the length of upper
                 * bound number.
                 */
                if (val > mMaxValue || result.length() > String.valueOf(mMaxValue).length()) {
                    return "";
                } else {
                    return filtered;
                }
            } else {
                CharSequence filtered = String.valueOf(source.subSequence(start, end));
                if (TextUtils.isEmpty(filtered)) {
                    return "";
                }
                String result = String.valueOf(dest.subSequence(0, dstart)) + filtered
                        + dest.subSequence(dend, dest.length());
                String str = result.toLowerCase();
                for (String val : mDisplayedValues) {
                    String valLowerCase = val.toLowerCase();
                    if (valLowerCase.startsWith(str)) {
                        postSetSelectionCommand(result.length(), val.length());
                        return val.subSequence(dstart, val.length());
                    }
                }
                return "";
            }
        }
    }

    /**
     * Ensures that the scroll wheel is adjusted i.e. there is no offset and the middle element is
     * in the middle of the widget.
     *
     * @return Whether an adjustment has been made.
     */
    private boolean ensureScrollWheelAdjusted() {
        // adjust to the closest value
        int deltaY = mInitialScrollOffset - mCurrentScrollOffset;
        if (deltaY != 0) {
            mPreviousScrollerY = 0;
            if (Math.abs(deltaY) > mSelectorElementHeight / 2) {
                deltaY += (deltaY > 0) ? -mSelectorElementHeight : mSelectorElementHeight;
            }
            mAdjustScroller.startScroll(0, 0, 0, deltaY, SELECTOR_ADJUSTMENT_DURATION_MILLIS);
            invalidate();
            return true;
        }
        return false;
    }

    class PressedStateHelper implements Runnable {
        public static final int BUTTON_INCREMENT = 1;
        public static final int BUTTON_DECREMENT = 2;

        private final int MODE_PRESS = 1;
        private final int MODE_TAPPED = 2;

        private int mManagedButton;
        private int mMode;

        public void cancel() {
            mMode = 0;
            mManagedButton = 0;
            LocalePicker.this.removeCallbacks(this);
            if (mIncrementVirtualButtonPressed) {
                mIncrementVirtualButtonPressed = false;
                invalidate();
            }
            mDecrementVirtualButtonPressed = false;
            if (mDecrementVirtualButtonPressed) {
                invalidate();
            }
        }

        public void buttonPressDelayed(int button) {
            cancel();
            mMode = MODE_PRESS;
            mManagedButton = button;
            LocalePicker.this.postDelayed(this, ViewConfiguration.getTapTimeout());
        }

        public void buttonTapped(int button) {
            cancel();
            mMode = MODE_TAPPED;
            mManagedButton = button;
            LocalePicker.this.post(this);
        }

        @Override
        public void run() {
            switch (mMode) {
                case MODE_PRESS: {
                    switch (mManagedButton) {
                        case BUTTON_INCREMENT: {
                            mIncrementVirtualButtonPressed = true;
                            invalidate();
                        }
                        break;
                        case BUTTON_DECREMENT: {
                            mDecrementVirtualButtonPressed = true;
                            invalidate();
                        }
                    }
                }
                break;
                case MODE_TAPPED: {
                    switch (mManagedButton) {
                        case BUTTON_INCREMENT: {
                            if (!mIncrementVirtualButtonPressed) {
                                LocalePicker.this.postDelayed(this,
                                        ViewConfiguration.getPressedStateDuration());
                            }
                            mIncrementVirtualButtonPressed ^= true;
                            invalidate();
                        }
                        break;
                        case BUTTON_DECREMENT: {
                            if (!mDecrementVirtualButtonPressed) {
                                LocalePicker.this.postDelayed(this,
                                        ViewConfiguration.getPressedStateDuration());
                            }
                            mDecrementVirtualButtonPressed ^= true;
                            invalidate();
                        }
                    }
                }
                break;
            }
        }
    }

    /**
     * Command for setting the input text selection.
     */
    class SetSelectionCommand implements Runnable {
        private int mSelectionStart;

        private int mSelectionEnd;

        public void run() {
            mInputText.setSelection(mSelectionStart, mSelectionEnd);
        }
    }

    /**
     * Command for changing the current value from a long press by one.
     */
    class ChangeCurrentByOneFromLongPressCommand implements Runnable {
        private boolean mIncrement;

        private void setStep(boolean increment) {
            mIncrement = increment;
        }

        @Override
        public void run() {
            changeValueByOne(mIncrement);
            /**
             * The speed for updating the value form long press.
             */
            postDelayed(this, DEFAULT_LONG_PRESS_UPDATE_INTERVAL);
        }
    }

    public static class CustomEditText extends EditText {

        public CustomEditText(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void onEditorAction(int actionCode) {
            super.onEditorAction(actionCode);
            if (actionCode == EditorInfo.IME_ACTION_DONE) {
                clearFocus();
            }
        }
    }

    /**
     * Command for beginning soft input on long press.
     */
    class BeginSoftInputOnLongPressCommand implements Runnable {

        @Override
        public void run() {
            showSoftInput();
            mIngonreMoveEvents = true;
        }
    }

    /**
     * Class for managing virtual view tree rooted at this picker.
     */
    class AccessibilityNodeProviderImpl extends AccessibilityNodeProvider {
        private static final int UNDEFINED = Integer.MIN_VALUE;

        private static final int VIRTUAL_VIEW_ID_INCREMENT = 1;

        private static final int VIRTUAL_VIEW_ID_INPUT = 2;

        private static final int VIRTUAL_VIEW_ID_DECREMENT = 3;

        private final Rect mTempRect = new Rect();

        private final int[] mTempArray = new int[2];

        private int mAccessibilityFocusedView = UNDEFINED;

        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
            return switch (virtualViewId) {
                case View.NO_ID -> createAccessibilityNodeInfoForNumberPicker(mScrollX, mScrollY,
                        mScrollX + (mRight - mLeft), mScrollY + (mBottom - mTop));
                case VIRTUAL_VIEW_ID_DECREMENT -> createAccessibilityNodeInfoForVirtualButton(
                        VIRTUAL_VIEW_ID_DECREMENT,
                        getVirtualDecrementButtonText(), mScrollX, mScrollY,
                        mScrollX + (mRight - mLeft),
                        mTopSelectionDividerTop + mSelectionDividerHeight);
                case VIRTUAL_VIEW_ID_INPUT -> createAccessibiltyNodeInfoForInputText(mScrollX,
                        mTopSelectionDividerTop + mSelectionDividerHeight,
                        mScrollX + (mRight - mLeft),
                        mBottomSelectionDividerBottom - mSelectionDividerHeight);
                case VIRTUAL_VIEW_ID_INCREMENT -> createAccessibilityNodeInfoForVirtualButton(
                        VIRTUAL_VIEW_ID_INCREMENT,
                        getVirtualIncrementButtonText(), mScrollX,
                        mBottomSelectionDividerBottom - mSelectionDividerHeight,
                        mScrollX + (mRight - mLeft), mScrollY + (mBottom - mTop));
                default -> super.createAccessibilityNodeInfo(virtualViewId);
            };
        }

        @Override
        public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String searched,
                int virtualViewId) {
            if (TextUtils.isEmpty(searched)) {
                return Collections.emptyList();
            }
            String searchedLowerCase = searched.toLowerCase();
            List<AccessibilityNodeInfo> result = new ArrayList<>();
            switch (virtualViewId) {
                case View.NO_ID: {
                    findAccessibilityNodeInfosByTextInChild(searchedLowerCase,
                            VIRTUAL_VIEW_ID_DECREMENT, result);
                    findAccessibilityNodeInfosByTextInChild(searchedLowerCase,
                            VIRTUAL_VIEW_ID_INPUT, result);
                    findAccessibilityNodeInfosByTextInChild(searchedLowerCase,
                            VIRTUAL_VIEW_ID_INCREMENT, result);
                    return result;
                }
                case VIRTUAL_VIEW_ID_DECREMENT:
                case VIRTUAL_VIEW_ID_INCREMENT:
                case VIRTUAL_VIEW_ID_INPUT: {
                    findAccessibilityNodeInfosByTextInChild(searchedLowerCase, virtualViewId,
                            result);
                    return result;
                }
            }
            return super.findAccessibilityNodeInfosByText(searched, virtualViewId);
        }

        @Override
        public boolean performAction(int virtualViewId, int action, Bundle arguments) {
            switch (virtualViewId) {
                case View.NO_ID: {
                    switch (action) {
                        case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
                            if (mAccessibilityFocusedView != virtualViewId) {
                                mAccessibilityFocusedView = virtualViewId;
                                requestAccessibilityFocus();
                                return true;
                            }
                        }
                        return false;
                        case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: {
                            if (mAccessibilityFocusedView == virtualViewId) {
                                mAccessibilityFocusedView = UNDEFINED;
                                clearAccessibilityFocus();
                                return true;
                            }
                            return false;
                        }
                        case AccessibilityNodeInfo.ACTION_SCROLL_FORWARD: {
                            if (LocalePicker.this.isEnabled()
                                    && (getWrapSelectorWheel() || getValue() < getMaxValue())) {
                                changeValueByOne(true);
                                return true;
                            }
                        }
                        return false;
                        case AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD: {
                            if (LocalePicker.this.isEnabled()
                                    && (getWrapSelectorWheel() || getValue() > getMinValue())) {
                                changeValueByOne(false);
                                return true;
                            }
                        }
                        return false;
                    }
                }
                break;
                case VIRTUAL_VIEW_ID_INPUT: {
                    switch (action) {
                        case AccessibilityNodeInfo.ACTION_FOCUS: {
                            if (LocalePicker.this.isEnabled() && !mInputText.isFocused()) {
                                return mInputText.requestFocus();
                            }
                        }
                        break;
                        case AccessibilityNodeInfo.ACTION_CLEAR_FOCUS: {
                            if (LocalePicker.this.isEnabled() && mInputText.isFocused()) {
                                mInputText.clearFocus();
                                return true;
                            }
                            return false;
                        }
                        case AccessibilityNodeInfo.ACTION_CLICK: {
                            if (LocalePicker.this.isEnabled()) {
                                showSoftInput();
                                return true;
                            }
                            return false;
                        }
                        case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
                            if (mAccessibilityFocusedView != virtualViewId) {
                                mAccessibilityFocusedView = virtualViewId;
                                sendAccessibilityEventForVirtualView(virtualViewId,
                                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                                mInputText.invalidate();
                                return true;
                            }
                        }
                        return false;
                        case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: {
                            if (mAccessibilityFocusedView == virtualViewId) {
                                mAccessibilityFocusedView = UNDEFINED;
                                sendAccessibilityEventForVirtualView(virtualViewId,
                                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                                mInputText.invalidate();
                                return true;
                            }
                        }
                        return false;
                        default: {
                            return mInputText.performAccessibilityAction(action, arguments);
                        }
                    }
                }
                return false;
                case VIRTUAL_VIEW_ID_INCREMENT: {
                    switch (action) {
                        case AccessibilityNodeInfo.ACTION_CLICK: {
                            if (LocalePicker.this.isEnabled()) {
                                LocalePicker.this.changeValueByOne(true);
                                sendAccessibilityEventForVirtualView(virtualViewId,
                                        AccessibilityEvent.TYPE_VIEW_CLICKED);
                                return true;
                            }
                        }
                        return false;
                        case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
                            if (mAccessibilityFocusedView != virtualViewId) {
                                mAccessibilityFocusedView = virtualViewId;
                                sendAccessibilityEventForVirtualView(virtualViewId,
                                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                                invalidate();
                                return true;
                            }
                        }
                        return false;
                        case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: {
                            if (mAccessibilityFocusedView == virtualViewId) {
                                mAccessibilityFocusedView = UNDEFINED;
                                sendAccessibilityEventForVirtualView(virtualViewId,
                                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                                invalidate();
                                return true;
                            }
                        }
                        return false;
                    }
                }
                return false;
                case VIRTUAL_VIEW_ID_DECREMENT: {
                    switch (action) {
                        case AccessibilityNodeInfo.ACTION_CLICK: {
                            if (LocalePicker.this.isEnabled()) {
                                final boolean increment =
                                        (virtualViewId == VIRTUAL_VIEW_ID_INCREMENT);
                                LocalePicker.this.changeValueByOne(increment);
                                sendAccessibilityEventForVirtualView(virtualViewId,
                                        AccessibilityEvent.TYPE_VIEW_CLICKED);
                                return true;
                            }
                        }
                        return false;
                        case AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS: {
                            if (mAccessibilityFocusedView != virtualViewId) {
                                mAccessibilityFocusedView = virtualViewId;
                                sendAccessibilityEventForVirtualView(virtualViewId,
                                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                                invalidate();
                                return true;
                            }
                        }
                        return false;
                        case AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS: {
                            if (mAccessibilityFocusedView == virtualViewId) {
                                mAccessibilityFocusedView = UNDEFINED;
                                sendAccessibilityEventForVirtualView(virtualViewId,
                                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                                invalidate();
                                return true;
                            }
                        }
                        return false;
                    }
                }
                return false;
            }
            return super.performAction(virtualViewId, action, arguments);
        }

        public void sendAccessibilityEventForVirtualView(int virtualViewId, int eventType) {
            switch (virtualViewId) {
                case VIRTUAL_VIEW_ID_DECREMENT: {
                    if (hasVirtualDecrementButton()) {
                        sendAccessibilityEventForVirtualButton(virtualViewId, eventType,
                                getVirtualDecrementButtonText());
                    }
                }
                break;
                case VIRTUAL_VIEW_ID_INPUT: {
                    sendAccessibilityEventForVirtualText(eventType);
                }
                break;
                case VIRTUAL_VIEW_ID_INCREMENT: {
                    if (hasVirtualIncrementButton()) {
                        sendAccessibilityEventForVirtualButton(virtualViewId, eventType,
                                getVirtualIncrementButtonText());
                    }
                }
                break;
            }
        }

        private void sendAccessibilityEventForVirtualText(int eventType) {
            if (AccessibilityManager.getInstance(mContext).isEnabled()) {
                AccessibilityEvent event = new AccessibilityEvent(eventType);
                mInputText.onInitializeAccessibilityEvent(event);
                mInputText.onPopulateAccessibilityEvent(event);
                event.setSource(LocalePicker.this, VIRTUAL_VIEW_ID_INPUT);
                requestSendAccessibilityEvent(LocalePicker.this, event);
            }
        }

        private void sendAccessibilityEventForVirtualButton(int virtualViewId, int eventType,
                String text) {
            if (AccessibilityManager.getInstance(mContext).isEnabled()) {
                AccessibilityEvent event = new AccessibilityEvent(eventType);
                event.setClassName(Button.class.getName());
                event.setPackageName(mContext.getPackageName());
                event.getText().add(text);
                event.setEnabled(LocalePicker.this.isEnabled());
                event.setSource(LocalePicker.this, virtualViewId);
                requestSendAccessibilityEvent(LocalePicker.this, event);
            }
        }

        private void findAccessibilityNodeInfosByTextInChild(String searchedLowerCase,
                int virtualViewId, List<AccessibilityNodeInfo> outResult) {
            switch (virtualViewId) {
                case VIRTUAL_VIEW_ID_DECREMENT: {
                    String text = getVirtualDecrementButtonText();
                    if (!TextUtils.isEmpty(text)
                            && text.toLowerCase().contains(searchedLowerCase)) {
                        outResult.add(createAccessibilityNodeInfo(VIRTUAL_VIEW_ID_DECREMENT));
                    }
                }
                return;
                case VIRTUAL_VIEW_ID_INPUT: {
                    CharSequence text = mInputText.getText();
                    if (!TextUtils.isEmpty(text) &&
                            text.toString().toLowerCase().contains(searchedLowerCase)) {
                        outResult.add(createAccessibilityNodeInfo(VIRTUAL_VIEW_ID_INPUT));
                        return;
                    }
                    CharSequence contentDesc = mInputText.getText();
                    if (!TextUtils.isEmpty(contentDesc) &&
                            contentDesc.toString().toLowerCase().contains(searchedLowerCase)) {
                        outResult.add(createAccessibilityNodeInfo(VIRTUAL_VIEW_ID_INPUT));
                        return;
                    }
                }
                break;
                case VIRTUAL_VIEW_ID_INCREMENT: {
                    String text = getVirtualIncrementButtonText();
                    if (!TextUtils.isEmpty(text)
                            && text.toLowerCase().contains(searchedLowerCase)) {
                        outResult.add(createAccessibilityNodeInfo(VIRTUAL_VIEW_ID_INCREMENT));
                    }
                }
            }
        }

        private AccessibilityNodeInfo createAccessibiltyNodeInfoForInputText(
                int left, int top, int right, int bottom) {
            AccessibilityNodeInfo info = mInputText.createAccessibilityNodeInfo();
            info.setSource(LocalePicker.this, VIRTUAL_VIEW_ID_INPUT);
            if (mAccessibilityFocusedView != VIRTUAL_VIEW_ID_INPUT) {
                info.addAction(AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            }
            if (mAccessibilityFocusedView == VIRTUAL_VIEW_ID_INPUT) {
                info.addAction(AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }
            Rect boundsInParent = mTempRect;
            boundsInParent.set(left, top, right, bottom);
            info.setVisibleToUser(isVisibleToUser(boundsInParent));
            int[] locationOnScreen = mTempArray;
            getLocationOnScreen(locationOnScreen);
            boundsInParent.offset(locationOnScreen[0], locationOnScreen[1]);
            info.setBoundsInScreen(boundsInParent);
            return info;
        }

        private AccessibilityNodeInfo createAccessibilityNodeInfoForVirtualButton(int virtualViewId,
                String text, int left, int top, int right, int bottom) {
            AccessibilityNodeInfo info = new AccessibilityNodeInfo();
            info.setClassName(Button.class.getName());
            info.setPackageName(mContext.getPackageName());
            info.setSource(LocalePicker.this, virtualViewId);
            info.setParent(LocalePicker.this);
            info.setText(text);
            info.setClickable(true);
            info.setLongClickable(true);
            info.setEnabled(LocalePicker.this.isEnabled());
            Rect boundsInParent = mTempRect;
            boundsInParent.set(left, top, right, bottom);
            info.setVisibleToUser(isVisibleToUser(boundsInParent));
            int[] locationOnScreen = mTempArray;
            getLocationOnScreen(locationOnScreen);
            boundsInParent.offset(locationOnScreen[0], locationOnScreen[1]);
            info.setBoundsInScreen(boundsInParent);

            if (mAccessibilityFocusedView != virtualViewId) {
                info.addAction(AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            }
            if (mAccessibilityFocusedView == virtualViewId) {
                info.addAction(AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }
            if (LocalePicker.this.isEnabled()) {
                info.addAction(AccessibilityAction.ACTION_CLICK);
            }

            return info;
        }

        private AccessibilityNodeInfo createAccessibilityNodeInfoForNumberPicker(int left, int top,
                int right, int bottom) {
            AccessibilityNodeInfo info = new AccessibilityNodeInfo();
            info.setClassName(LocalePicker.class.getName());
            info.setPackageName(mContext.getPackageName());
            info.setSource(LocalePicker.this);

            if (hasVirtualDecrementButton()) {
                info.addChild(LocalePicker.this, VIRTUAL_VIEW_ID_DECREMENT);
            }
            info.addChild(LocalePicker.this, VIRTUAL_VIEW_ID_INPUT);
            if (hasVirtualIncrementButton()) {
                info.addChild(LocalePicker.this, VIRTUAL_VIEW_ID_INCREMENT);
            }

            info.setParent((View) getParentForAccessibility());
            info.setEnabled(LocalePicker.this.isEnabled());
            info.setScrollable(true);

            final float applicationScale =
                    getContext().getResources().getCompatibilityInfo().applicationScale;

            Rect boundsInParent = mTempRect;
            boundsInParent.set(left, top, right, bottom);
            boundsInParent.scale(applicationScale);

            info.setVisibleToUser(isVisibleToUser());

            int[] locationOnScreen = mTempArray;
            getLocationOnScreen(locationOnScreen);
            boundsInParent.offset(locationOnScreen[0], locationOnScreen[1]);
            boundsInParent.scale(applicationScale);
            info.setBoundsInScreen(boundsInParent);

            if (mAccessibilityFocusedView != View.NO_ID) {
                info.addAction(AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            }
            if (mAccessibilityFocusedView == View.NO_ID) {
                info.addAction(AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            }
            if (LocalePicker.this.isEnabled()) {
                if (getWrapSelectorWheel() || getValue() < getMaxValue()) {
                    info.addAction(AccessibilityAction.ACTION_SCROLL_FORWARD);
                }
                if (getWrapSelectorWheel() || getValue() > getMinValue()) {
                    info.addAction(AccessibilityAction.ACTION_SCROLL_BACKWARD);
                }
            }

            return info;
        }

        private boolean hasVirtualDecrementButton() {
            return getWrapSelectorWheel() || getValue() > getMinValue();
        }

        private boolean hasVirtualIncrementButton() {
            return getWrapSelectorWheel() || getValue() < getMaxValue();
        }

        private String getVirtualDecrementButtonText() {
            int value = mValue - 1;
            if (mWrapSelectorWheel) {
                value = getWrappedSelectorIndex(value);
            }
            if (value >= mMinValue) {
                return (mDisplayedValues == null) ? formatNumber(value)
                        : mDisplayedValues[value - mMinValue];
            }
            return null;
        }

        private String getVirtualIncrementButtonText() {
            int value = mValue + 1;
            if (mWrapSelectorWheel) {
                value = getWrappedSelectorIndex(value);
            }
            if (value <= mMaxValue) {
                return (mDisplayedValues == null) ? formatNumber(value)
                        : mDisplayedValues[value - mMinValue];
            }
            return null;
        }
    }

    static private String formatNumberWithLocale(int value) {
        return String.format(Locale.getDefault(), "%d", value);
    }
}
