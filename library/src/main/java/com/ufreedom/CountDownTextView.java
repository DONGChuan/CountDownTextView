package com.ufreedom;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.Formatter;
import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * Custom TextView that implements a simple CountDown.
 * Author UFreedom, DChuan
 */
public class CountDownTextView extends TextView {

    private static final String TAG = "CountDownTextView";

    public static final int TIME_FORMAT_D_H_M_S = 10;
    public static final int TIME_FORMAT_H_M_S = 20;
    public static final int TIME_FORMAT_M_S = 30;
    public static final int TIME_FORMAT_S = 40;
    
    private static final String TIME_EX_D_H_M_S = "%1$02d:%2$02d:%3$02d:%4$02d";
    private static final String TIME_EX_H_M_S = "%1$02d:%2$02d:%3$02d";
    private static final String TIME_EX_M_S = "%1$02d:%2$02d";
    private static final String TIME_EX_S = "%1$02d";

    public long mCountDownInterval = 1000; // By default, it will count 1s
    private long scheduledTime;

    private boolean isAutoShowText;
    private boolean mVisible;
    private boolean mStarted;
    private boolean mRunning;
    private boolean mLogged;

    private CountDownCallback countDownCallback;
    private CountDownHelper  mCountDownHelper;

    private String mFormat;
    private Formatter mFormatter;
    private Locale mFormatterLocale;
    private Object[] mFormatterArgs = new Object[1];
    private StringBuilder mFormatBuilder;
    private int mTimeFormat;
    private StringBuilder mRecycle = new StringBuilder(12);

    /*************************** Constructors ************************************/

    public CountDownTextView(Context context) {
        super(context);
        init();
    }

    public CountDownTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /*************************** Callbacks ************************************/

    /**
     * Callbacks mush be implemented by client
     */
    public interface CountDownCallback {

        /**
         * Callback fired on regular interval.
         * @param countDownTextView The CountDownText instance.
         * @param millisUntilFinished The amount of time until finished.
         */
        void onTick(CountDownTextView countDownTextView,long millisUntilFinished);

        /**
         * Callback fired when the time is up.
         * @param countDownTextView The CountDownText instance.
         */
        void onFinish(CountDownTextView countDownTextView);

    }

    /***************************  Common Functions ************************************/

    private void init(){
        setTimeFormat(TIME_FORMAT_H_M_S);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = visibility == VISIBLE;
        updateRunning();
    }

    /**
     * Update mRunning flags and whether to start or cancel time counter
     * according to mVisible & mStarted & itself.
     */
    private void updateRunning() {
        boolean running = mVisible && mStarted;
        if (running != mRunning) {
            if (running) {
                mCountDownHelper.start();
            } else {
                mCountDownHelper.cancel();
            }
            mRunning = running;
        }
    }

    /**
     * Start the countdown
     */
    public void start() {
        startCountDown();
        mStarted = true;
        updateRunning();
    }

    /**
     * Cancel the countdown
     */
    public void cancel() {
        mStarted = false;
        updateRunning();
    }

    /**
     * @param isAutoShowText if true, it will display the current timer value
     */
    public void setAutoDisplayText(boolean isAutoShowText) {
        this.isAutoShowText = isAutoShowText;
    }

    /**
     * Sets the format string used for display. The CountDownTextView will display
     * this string, with the first "%s" replaced by the current timer value in
     * "MM:SS" or "HH:MM:SS" form which dependents on the time format {@link #setTimeFormat(int)}.
     *
     * If the format string is null, or if you never call setFormat(), the
     * CountDownTextView will simply display the timer value in "MM:SS" or "HH:MM:SS"
     * form which dependents on the time format {@link #setTimeFormat(int)}.
     *
     * @param format the format string.
     */
    public void setFormat(String format){
        mFormat = format;
        if (format != null && mFormatBuilder == null) {
            mFormatBuilder = new StringBuilder(format.length() * 2);
        }
    }

    /**
     * Return the time in the given format mTimeFormat
     *
     * @param now current time
     * @return the time in the given format mTimeFormat
     */
    private String getFormatTime(long now){

        long day = ElapsedTimeUtil.MILLISECONDS.toDays(now);
        long hour = ElapsedTimeUtil.MILLISECONDS.toHours(now);
        long minute = ElapsedTimeUtil.MILLISECONDS.toMinutes(now);
        long seconds = ElapsedTimeUtil.MILLISECONDS.toSeconds(now);

        mRecycle.setLength(0);
        Formatter f = new Formatter(mRecycle, Locale.getDefault());

        switch (mTimeFormat) {
            case TIME_FORMAT_D_H_M_S:
                return f.format(TIME_EX_D_H_M_S, day, hour, minute, seconds).toString();
            case TIME_FORMAT_H_M_S:
            default:
                return f.format(TIME_EX_H_M_S, hour, minute, seconds).toString();
            case TIME_FORMAT_M_S:
                return f.format(TIME_EX_M_S, minute, seconds).toString();
            case TIME_FORMAT_S:
                return f.format(TIME_EX_S, seconds).toString();
        }
    }

    /**
     * Set text (Time) on CountDownTextView
     * @param now time to be shown
     */
    private synchronized void updateText(long now) {

        String text = getFormatTime(now);

        if (mFormat != null) {
            Locale loc = Locale.getDefault();
            if (mFormatter == null || !loc.equals(mFormatterLocale)) {
                mFormatterLocale = loc;
                mFormatter = new Formatter(mFormatBuilder, loc);
            }
            mFormatBuilder.setLength(0);
            mFormatterArgs[0] = text;
            try {
                mFormatter.format(mFormat, mFormatterArgs);
                text = mFormatBuilder.toString();
            } catch (IllegalFormatException ex) {
                if (!mLogged) {
                    Log.w(TAG, "Illegal format string: " + mFormat);
                    mLogged = true;
                }
            }
        }
        setText(text);
    }

    /**
     * @param millisInFuture The number of millis in the future from the call 
     *   to {@link #start()} until the countdown is done. The value should 
     *  in the {@link SystemClock#elapsedRealtime} timebase
     */
    public void setTimeInFuture(long millisInFuture){
        scheduledTime = millisInFuture;
    }
    
    public void addCountDownCallback(CountDownCallback callback) {
        countDownCallback = callback;
    }

    /**
     * Start to count down time
     */
    private void startCountDown(){

        mCountDownHelper  = new CountDownHelper(scheduledTime, mCountDownInterval) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (isAutoShowText) {
                    updateText(millisUntilFinished);
                }
                if (countDownCallback != null) {
                    countDownCallback.onTick(CountDownTextView.this,millisUntilFinished);
                }
            }

            @Override
            public void onFinish() {
                if (countDownCallback != null) {
                    countDownCallback.onFinish(CountDownTextView.this);
                }
            }
        };
        mCountDownHelper.start();

    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(CountDownTextView.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(CountDownTextView.class.getName());
    }

    /***************************  Setter and Getter ************************************/

    public long getCountDownInterval() {
        return mCountDownInterval;
    }

    public void setCountDownInterval(long mCountDownInterval) {
        this.mCountDownInterval = mCountDownInterval;
    }

    /**
     * Sets the format string used for time display.The default display format is "HH:MM:SS"
     * <p> {@link #TIME_FORMAT_D_H_M_S } the  format is "DD:HH:MM:SS" </p>
     * <p> {@link #TIME_FORMAT_H_M_S } the  format is "HH:MM:SS" </p>
     * <p> {@link #TIME_FORMAT_M_S } the  format is "MM:SS" </p>
     * <p> {@link #TIME_FORMAT_S } the  format is "SS" </p>
     *
     * @param timeFormat the display time flag
     */
    public void setTimeFormat(int timeFormat) {
        mTimeFormat = timeFormat;
    }

}
