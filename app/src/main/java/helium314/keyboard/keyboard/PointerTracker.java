/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

import static java.lang.Math.abs;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import helium314.keyboard.event.HapticEvent;
import helium314.keyboard.keyboard.internal.BatchInputArbiter;
import helium314.keyboard.keyboard.internal.BatchInputArbiter.BatchInputArbiterListener;
import helium314.keyboard.keyboard.internal.BogusMoveEventDetector;
import helium314.keyboard.keyboard.internal.DrawingProxy;
import helium314.keyboard.keyboard.internal.GestureEnabler;
import helium314.keyboard.keyboard.internal.GestureStrokeDrawingParams;
import helium314.keyboard.keyboard.internal.GestureStrokeDrawingPoints;
import helium314.keyboard.keyboard.internal.GestureStrokeRecognitionParams;
import helium314.keyboard.keyboard.internal.PointerTrackerQueue;
import helium314.keyboard.keyboard.internal.TimerProxy;
import helium314.keyboard.keyboard.internal.TypingTimeRecorder;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.CoordinateUtils;
import helium314.keyboard.latin.common.InputPointers;
import helium314.keyboard.latin.define.DebugFlags;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.settings.SettingsValues;
import helium314.keyboard.latin.utils.KtxKt;
import helium314.keyboard.latin.utils.Log;

import java.util.ArrayList;
import java.util.Locale;
import java.util.WeakHashMap;

public final class PointerTracker implements PointerTrackerQueue.Element,
        BatchInputArbiterListener {
    private static final String TAG = PointerTracker.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_MOVE_EVENT = false;
    private static final boolean DEBUG_LISTENER = false;
    private static final boolean DEBUG_MODE = DebugFlags.DEBUG_ENABLED || DEBUG_EVENT;

    static final class PointerTrackerParams {
        public final boolean mKeySelectionByDraggingFinger;
        public final int mTouchNoiseThresholdTime;
        public final int mTouchNoiseThresholdDistance;
        public final int mSuppressKeyPreviewAfterBatchInputDuration;
        public final int mKeyRepeatStartTimeout;
        public final int mKeyRepeatInterval;

        public PointerTrackerParams(final TypedArray mainKeyboardViewAttr) {
            mKeySelectionByDraggingFinger = mainKeyboardViewAttr.getBoolean(
                    R.styleable.MainKeyboardView_keySelectionByDraggingFinger, false);
            mTouchNoiseThresholdTime = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_touchNoiseThresholdTime, 0);
            mTouchNoiseThresholdDistance = mainKeyboardViewAttr.getDimensionPixelSize(
                    R.styleable.MainKeyboardView_touchNoiseThresholdDistance, 0);
            mSuppressKeyPreviewAfterBatchInputDuration = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_suppressKeyPreviewAfterBatchInputDuration, 0);
            mKeyRepeatStartTimeout = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_keyRepeatStartTimeout, 0);
            mKeyRepeatInterval = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_keyRepeatInterval, 0);
        }
    }

    static final class TrackerContext {
        public final PointerTrackerParams mParams;
        public final GestureStrokeRecognitionParams mGestureStrokeRecognitionParams;
        public final GestureStrokeDrawingParams mGestureStrokeDrawingParams;
        public final TypingTimeRecorder mTypingTimeRecorder;
        public final TimerProxy mTimerProxy;
        public final DrawingProxy mDrawingProxy;
        public final ArrayList<PointerTracker> mTrackers;
        public final GestureEnabler mGestureEnabler;
        public KeyboardActionListener mListener = KeyboardActionListener.EMPTY_LISTENER;

        public TrackerContext(final TypedArray mainKeyboardViewAttr, final TimerProxy timerProxy,
                final DrawingProxy drawingProxy) {
            mParams = new PointerTrackerParams(mainKeyboardViewAttr);
            mGestureStrokeRecognitionParams = new GestureStrokeRecognitionParams(mainKeyboardViewAttr);
            mGestureStrokeDrawingParams = new GestureStrokeDrawingParams(mainKeyboardViewAttr);
            mTypingTimeRecorder = new TypingTimeRecorder(
                    mGestureStrokeRecognitionParams.mStaticTimeThresholdAfterFastTyping,
                    mParams.mSuppressKeyPreviewAfterBatchInputDuration);
            mTimerProxy = timerProxy;
            mDrawingProxy = drawingProxy;
            mTrackers = new ArrayList<>();
            mGestureEnabler = new GestureEnabler();
        }
    }

    // map to store static objects that should be unique for each DrawingProxy (i.e.
    // MainKeyboardView as of now)
    // this is a workaround, so we can have a MainKeyboardView in emoji and
    // clipboard views too
    // but it will not allow two simultaneously displayed MainKeyboardViews
    private static final WeakHashMap<DrawingProxy, TrackerContext> sProxyMap = new WeakHashMap<>(4);

    // called when creating a new InputView
    // not sure why this is necessary... maybe misunderstanding regarding
    // WeakHashMap?
    public static void clearOldViewData() {
        sProxyMap.clear();
    }

    public static void switchTo(DrawingProxy drawingProxy) {
        final TrackerContext context = sProxyMap.get(drawingProxy);
        if (context == null) {
            // if it's null, the view we're switching to should not exist
            return;
        }
        sDrawingProxy = drawingProxy;
        sParams = context.mParams;
        sGestureStrokeRecognitionParams = context.mGestureStrokeRecognitionParams;
        sGestureStrokeDrawingParams = context.mGestureStrokeDrawingParams;
        sTypingTimeRecorder = context.mTypingTimeRecorder;
        sTimerProxy = context.mTimerProxy;
        sTrackers = context.mTrackers;
        sListener = context.mListener;
        sGestureEnabler = context.mGestureEnabler;
    }

    private static GestureEnabler sGestureEnabler;

    // Parameters for pointer handling.
    private static PointerTrackerParams sParams;
    private static final int sPointerStep = KtxKt.dpToPx(10, Resources.getSystem());
    private static GestureStrokeRecognitionParams sGestureStrokeRecognitionParams;
    private static GestureStrokeDrawingParams sGestureStrokeDrawingParams;

    private static ArrayList<PointerTracker> sTrackers = new ArrayList<>();
    private static final PointerTrackerQueue sPointerTrackerQueue = new PointerTrackerQueue();

    public final int mPointerId;
    private final TrackerContext mContext;

    private static DrawingProxy sDrawingProxy;
    private static TimerProxy sTimerProxy;
    private static KeyboardActionListener sListener = KeyboardActionListener.EMPTY_LISTENER;

    // The {@link KeyDetector} is set whenever the down event is processed. Also
    // this is updated
    // when new {@link Keyboard} is set by {@link #setKeyDetector(KeyDetector)}.
    private KeyDetector mKeyDetector = new KeyDetector();
    private Keyboard mKeyboard;
    private final BogusMoveEventDetector mBogusMoveEventDetector = new BogusMoveEventDetector();

    private boolean mIsDetectingGesture = false; // per PointerTracker.
    private static boolean sInGesture = false;
    private static TypingTimeRecorder sTypingTimeRecorder;

    // The position and time at which first down event occurred.
    private long mDownTime;
    @NonNull
    private final int[] mDownCoordinates = CoordinateUtils.newInstance();
    private long mUpTime;

    // The current key where this pointer is.
    private Key mCurrentKey = null;
    // The position where the current key was recognized for the first time.
    private int mKeyX;
    private int mKeyY;

    // Last pointer position.
    private int mLastX;
    private int mLastY;
    private int mStartX;
    private int mStartY;
    private long mStartTime;
    private boolean mInHorizontalSwipe = false;
    private boolean mInVerticalSwipe = false;

    // true if keyboard layout has been changed.
    private boolean mKeyboardLayoutHasBeenChanged;
    private int keyboardChangeOccupiedHeightDifference;

    // true if this pointer is no longer triggering any action because it has been
    // canceled.
    private boolean mIsTrackingForActionDisabled;

    // the popup keys panel currently being shown. equals null if no panel is
    // active.
    private PopupKeysPanel mPopupKeysPanel;

    // true if this pointer is in the dragging finger mode.
    boolean mIsInDraggingFinger;
    // true if this pointer is sliding from a modifier key and in the sliding key
    // input mode,
    // so that further modifier keys should be ignored.
    boolean mIsInSlidingKeyInput;
    // if not a NOT_A_CODE, the key of this code is repeating
    private int mCurrentRepeatingKeyCode = Constants.NOT_A_CODE;

    // true if dragging finger is allowed.
    private boolean mIsAllowedDraggingFinger;
    // true if a keyswipe gesture is enabled and warranted.
    private boolean mKeySwipeAllowed = false;
    private static boolean sInKeySwipe = false;

    // Touchpad mode for cursor control
    private static boolean sTouchpadModeActive = false;
    private boolean mInTouchpadMode = false;
    private int mTouchpadLastX = 0;
    private int mTouchpadLastY = 0;
    // Accumulators for fractional movement
    private int mTouchpadAccX = 0;
    private int mTouchpadAccY = 0;
    // Cached touchpad sensitivity to avoid repeated Settings lookups in hot path
    private static volatile int sCachedTouchpadSensitivity = -1;
    private static volatile long sLastTouchpadSensitivityUpdateTime = 0;
    private static final int TOUCHPAD_SENSITIVITY_UPDATE_INTERVAL_MS = 100;

    private static final float TOUCHPAD_ACCELERATION_FACTOR = 50.0f; // Lower = more acceleration

    public static void setTouchpadModeActive(boolean active) {
        sTouchpadModeActive = active;
    }

    public static boolean isTouchpadModeActive() {
        return sTouchpadModeActive;
    }

    private final BatchInputArbiter mBatchInputArbiter;
    private final GestureStrokeDrawingPoints mGestureStrokeDrawingPoints;

    // TODO: Add PointerTrackerFactory singleton and move some class static methods
    // into it.
    public static void init(final TypedArray mainKeyboardViewAttr, final TimerProxy timerProxy,
            final DrawingProxy drawingProxy) {
        final TrackerContext context = new TrackerContext(mainKeyboardViewAttr, timerProxy, drawingProxy);
        sProxyMap.put(drawingProxy, context);

        sDrawingProxy = drawingProxy;
        sParams = context.mParams;
        sGestureStrokeRecognitionParams = context.mGestureStrokeRecognitionParams;
        sGestureStrokeDrawingParams = context.mGestureStrokeDrawingParams;
        sTypingTimeRecorder = context.mTypingTimeRecorder;
        sTimerProxy = context.mTimerProxy;
        sTrackers = context.mTrackers;
        sGestureEnabler = context.mGestureEnabler;

        final Resources res = mainKeyboardViewAttr.getResources();
        BogusMoveEventDetector.init(res);
    }

    // Note that this method is called from a non-UI thread.
    public static void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        for (TrackerContext context : sProxyMap.values()) {
            context.mGestureEnabler.setMainDictionaryAvailability(mainDictionaryAvailable);
        }
    }

    public static void setGestureHandlingEnabledByUser(final boolean gestureHandlingEnabledByUser) {
        for (TrackerContext context : sProxyMap.values()) {
            context.mGestureEnabler.setGestureHandlingEnabledByUser(gestureHandlingEnabledByUser);
        }
    }

    public static PointerTracker getPointerTracker(final int id) {
        return getPointerTracker(id, sDrawingProxy);
    }

    public static PointerTracker getPointerTracker(final int id, final DrawingProxy drawingProxy) {
        final TrackerContext context = sProxyMap.get(drawingProxy);
        final ArrayList<PointerTracker> trackers = context.mTrackers;

        // Create pointer trackers until we can get 'id+1'-th tracker, if needed.
        for (int i = trackers.size(); i <= id; i++) {
            final PointerTracker tracker = new PointerTracker(i, context);
            trackers.add(tracker);
        }

        return trackers.get(id);
    }

    public static boolean isAnyInDraggingFinger() {
        return sPointerTrackerQueue.isAnyInDraggingFinger();
    }

    public static void cancelAllPointerTrackers() {
        sPointerTrackerQueue.cancelAllPointerTrackers();
    }

    public static void setKeyboardActionListener(final KeyboardActionListener listener) {
        sListener = listener;
        final TrackerContext context = sProxyMap.get(sDrawingProxy);
        if (context != null) {
            context.mListener = listener;
        }
    }

    public static void setKeyDetector(final KeyDetector keyDetector) {
        final Keyboard keyboard = keyDetector.getKeyboard();
        if (keyboard == null) {
            return;
        }
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.setKeyDetectorInner(keyDetector);
        }
        sGestureEnabler.setPasswordMode(keyboard.mId.passwordInput());
    }

    public static void setReleasedKeyGraphicsToAllKeys() {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.setReleasedKeyGraphics(tracker.getKey(), true);
        }
    }

    public static void dismissAllPopupKeysPanels() {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.dismissPopupKeysPanel();
        }
    }

    private PointerTracker(final int id, final TrackerContext context) {
        mPointerId = id;
        mContext = context;
        mBatchInputArbiter = new BatchInputArbiter(id, context.mGestureStrokeRecognitionParams);
        mGestureStrokeDrawingPoints = new GestureStrokeDrawingPoints(context.mGestureStrokeDrawingParams);
    }

    // Returns true if keyboard has been changed by this callback.
    private boolean callListenerOnPressAndCheckKeyboardLayoutChange(@NonNull final Key key, final int repeatCount) {
        // While gesture input is going on, this method should be a no-operation. But
        // when gesture
        // input has been canceled, <code>sInGesture</code> and
        // <code>mIsDetectingGesture</code>
        // are set to false. To keep this method is a no-operation,
        // <code>mIsTrackingForActionDisabled</code> should also be taken account of.
        if (sInGesture || mIsDetectingGesture || mIsTrackingForActionDisabled) {
            return false;
        }
        final boolean ignoreModifierKey = mIsInDraggingFinger && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onPress    : %s%s%s%s", mPointerId,
                    Constants.printableCode(key.getCode()),
                    ignoreModifierKey ? " ignoreModifier" : "",
                    key.isEnabled() ? "" : " disabled",
                    repeatCount > 0 ? " repeatCount=" + repeatCount : ""));
        }
        if (ignoreModifierKey) {
            return false;
        }
        if (key.isEnabled()) {
            mContext.mListener.onPressKey(key.getCode(), repeatCount, getActivePointerTrackerCount() == 1,
                    HapticEvent.KEY_PRESS);
            final boolean keyboardLayoutHasBeenChanged = mKeyboardLayoutHasBeenChanged;
            mKeyboardLayoutHasBeenChanged = false;
            mContext.mTimerProxy.startTypingStateTimer(key);
            return keyboardLayoutHasBeenChanged;
        }
        return false;
    }

    // Note that we need primaryCode argument because the keyboard may in shifted
    // state and the
    // primaryCode is different from {@link Key#mKeyCode}.
    private void callListenerOnCodeInput(final Key key, final int primaryCode, final int x,
            final int y, final long eventTime, final boolean isKeyRepeat) {
        final boolean ignoreModifierKey = mIsInDraggingFinger && key.isModifier()
                && key.getCode() != KeyCode.NUMPAD; // we allow for the numpad to be toggled from sliding input
        final boolean altersCode = key.altCodeWhileTyping() && mContext.mTimerProxy.isTypingState()
                && !isClearlyInsideKey(key, x, y);
        final int code = altersCode ? key.getAltCode() : primaryCode;
        if (DEBUG_LISTENER) {
            final String output = code == KeyCode.MULTIPLE_CODE_POINTS
                    ? key.getOutputText()
                    : Constants.printableCode(code);
            Log.d(TAG, String.format(Locale.US, "[%d] onCodeInput: %4d %4d %s%s%s%s", mPointerId, x, y,
                    output, ignoreModifierKey ? " ignoreModifier" : "",
                    altersCode ? " altersCode" : "", key.isEnabled() ? "" : " disabled"));
        }
        if (ignoreModifierKey) {
            return;
        }
        // Even if the key is disabled, it should respond if it is in the
        // altCodeWhileTyping state.
        if (key.isEnabled() || altersCode) {
            mContext.mTypingTimeRecorder.onCodeInput(code, eventTime);
            if (code == KeyCode.MULTIPLE_CODE_POINTS) {
                mContext.mListener.onTextInput(key.getOutputText());
            } else if (code != KeyCode.NOT_SPECIFIED) {
                if (mKeyboard.hasProximityCharsCorrection(code)) {
                    mContext.mListener.onCodeInput(code, x, y, isKeyRepeat);
                } else {
                    mContext.mListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, isKeyRepeat);
                }
            }
        }
    }

    // Note that we need primaryCode argument because the keyboard may be in shifted
    // state and the
    // primaryCode is different from {@link Key#mKeyCode}.
    private void callListenerOnRelease(final Key key, final int primaryCode, final boolean withSliding) {
        // See the comment at {@link
        // #callListenerOnPressAndCheckKeyboardLayoutChange(Key}}.
        if (sInGesture || mIsDetectingGesture || mIsTrackingForActionDisabled) {
            return;
        }
        final boolean ignoreModifierKey = mIsInDraggingFinger && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onRelease  : %s%s%s%s", mPointerId,
                    Constants.printableCode(primaryCode),
                    withSliding ? " sliding" : "", ignoreModifierKey ? " ignoreModifier" : "",
                    key.isEnabled() ? "" : " disabled"));
        }
        if (ignoreModifierKey) {
            return;
        }
        if (key.isEnabled()) {
            mContext.mListener.onReleaseKey(primaryCode, withSliding);
        }
    }

    private void callListenerOnFinishSlidingInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onFinishSlidingInput", mPointerId));
        }
        mContext.mListener.onFinishSlidingInput();
    }

    private void callListenerOnCancelInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onCancelInput", mPointerId));
        }
        mContext.mListener.onCancelInput();
    }

    private void setKeyDetectorInner(final KeyDetector keyDetector) {
        final Keyboard keyboard = keyDetector.getKeyboard();
        if (keyboard == null) {
            return;
        }
        if (keyDetector == mKeyDetector && keyboard == mKeyboard) {
            return;
        }
        if (mKeyboard != null) {
            // changing keyboards may change height
            // since y is measured from top of view, this change needs to be considered in
            // some places
            keyboardChangeOccupiedHeightDifference = keyboard.mOccupiedHeight - mKeyboard.mOccupiedHeight;
        }
        mKeyDetector = keyDetector;
        mKeyboard = keyboard;
        // Mark that keyboard layout has been changed.
        mKeyboardLayoutHasBeenChanged = true;
        final int keyWidth = mKeyboard.mMostCommonKeyWidth;
        final int keyHeight = mKeyboard.mMostCommonKeyHeight;
        mBatchInputArbiter.setKeyboardGeometry(keyWidth, mKeyboard.mOccupiedHeight);
        // Keep {@link #mCurrentKey} that comes from previous keyboard. The key preview
        // of
        // {@link #mCurrentKey} will be dismissed by {@setReleasedKeyGraphics(Key)} via
        // {@link onMoveEventInternal(int,int,long)} or {@link
        // #onUpEventInternal(int,int,long)}.
        mBogusMoveEventDetector.setKeyboardGeometry(keyWidth, keyHeight);
    }

    @Override
    public boolean isInDraggingFinger() {
        return mIsInDraggingFinger;
    }

    @Nullable
    public Key getKey() {
        return mCurrentKey;
    }

    @Override
    public boolean isModifier() {
        return mCurrentKey != null && mCurrentKey.isModifier();
    }

    public Key getKeyOn(final int x, final int y) {
        return mKeyDetector.detectHitKey(x, y);
    }

    private void setReleasedKeyGraphics(@Nullable final Key key, final boolean withAnimation) {
        if (key == null) {
            return;
        }

        mContext.mDrawingProxy.onKeyReleased(key, withAnimation);

        if (key.isShift()) {
            for (final Key shiftKey : mKeyboard.mShiftKeys) {
                if (shiftKey != key) {
                    mContext.mDrawingProxy.onKeyReleased(shiftKey, false);
                }
            }
        }

        if (key.altCodeWhileTyping()) {
            final int altCode = key.getAltCode();
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                mContext.mDrawingProxy.onKeyReleased(altKey, false);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.getAltCode() == altCode) {
                    mContext.mDrawingProxy.onKeyReleased(k, false);
                }
            }
        }
    }

    private boolean needsToSuppressKeyPreviewPopup(final long eventTime) {
        if (!mContext.mGestureEnabler.shouldHandleGesture())
            return false;
        return mContext.mTypingTimeRecorder.needsToSuppressKeyPreviewPopup(eventTime);
    }

    private void setPressedKeyGraphics(@Nullable final Key key, final long eventTime) {
        if (key == null) {
            return;
        }

        // Even if the key is disabled, it should respond if it is in the
        // altCodeWhileTyping state.
        final boolean altersCode = key.altCodeWhileTyping() && mContext.mTimerProxy.isTypingState();
        final boolean needsToUpdateGraphics = key.isEnabled() || altersCode;
        if (!needsToUpdateGraphics) {
            return;
        }

        final boolean noKeyPreview = sInGesture || needsToSuppressKeyPreviewPopup(eventTime);
        mContext.mDrawingProxy.onKeyPressed(key, !noKeyPreview);

        if (key.isShift()) {
            for (final Key shiftKey : mKeyboard.mShiftKeys) {
                if (shiftKey != key) {
                    mContext.mDrawingProxy.onKeyPressed(shiftKey, false);
                }
            }
        }

        if (altersCode) {
            final int altCode = key.getAltCode();
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                mContext.mDrawingProxy.onKeyPressed(altKey, false);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.getAltCode() == altCode) {
                    mContext.mDrawingProxy.onKeyPressed(k, false);
                }
            }
        }
    }

    public GestureStrokeDrawingPoints getGestureStrokeDrawingPoints() {
        return mGestureStrokeDrawingPoints;
    }

    public void getLastCoordinates(@NonNull final int[] outCoords) {
        CoordinateUtils.set(outCoords, mLastX, mLastY);
    }

    public long getDownTime() {
        return mDownTime;
    }

    public void getDownCoordinates(@NonNull final int[] outCoords) {
        CoordinateUtils.copy(outCoords, mDownCoordinates);
    }

    private Key onDownKey(final int x, final int y, final long eventTime) {
        mDownTime = eventTime;
        CoordinateUtils.set(mDownCoordinates, x, y);
        mBogusMoveEventDetector.onDownKey();
        return onMoveToNewKey(onMoveKeyInternal(x, y), x, y);
    }

    private static int getDistance(final int x1, final int y1, final int x2, final int y2) {
        return (int) Math.hypot(x1 - x2, y1 - y2);
    }

    private Key onMoveKeyInternal(final int x, final int y) {
        mBogusMoveEventDetector.onMoveKey(getDistance(x, y, mLastX, mLastY));
        mLastX = x;
        mLastY = y;
        return mKeyDetector.detectHitKey(x, y);
    }

    private Key onMoveKey(final int x, final int y) {
        return onMoveKeyInternal(x, y);
    }

    private Key onMoveToNewKey(final Key newKey, final int x, final int y) {
        mCurrentKey = newKey;
        mKeyX = x;
        mKeyY = y;
        return newKey;
    }

    /* package */ static int getActivePointerTrackerCount() {
        return sPointerTrackerQueue.size();
    }

    private boolean isOldestTrackerInQueue() {
        return sPointerTrackerQueue.getOldestElement() == this;
    }

    // Implements {@link BatchInputArbiterListener}.
    @Override
    public void onStartBatchInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onStartBatchInput", mPointerId));
        }
        mContext.mListener.onStartBatchInput();
        dismissAllPopupKeysPanels();
        mContext.mTimerProxy.cancelLongPressTimersOf(this);
    }

    private void showGestureTrail() {
        if (mIsTrackingForActionDisabled) {
            return;
        }
        // A gesture floating preview text will be shown at the oldest pointer/finger on
        // the screen.
        mContext.mDrawingProxy.showGestureTrail(this, isOldestTrackerInQueue());
    }

    public void updateBatchInputByTimer(final long syntheticMoveEventTime) {
        mBatchInputArbiter.updateBatchInputByTimer(syntheticMoveEventTime, this);
    }

    // Implements {@link BatchInputArbiterListener}.
    @Override
    public void onUpdateBatchInput(final InputPointers aggregatedPointers, final long eventTime) {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onUpdateBatchInput: batchPoints=%d", mPointerId,
                    aggregatedPointers.getPointerSize()));
        }
        mContext.mListener.onUpdateBatchInput(aggregatedPointers);
    }

    // Implements {@link BatchInputArbiterListener}.
    @Override
    public void onStartUpdateBatchInputTimer() {
        mContext.mTimerProxy.startUpdateBatchInputTimer(this);
    }

    // Implements {@link BatchInputArbiterListener}.
    @Override
    public void onEndBatchInput(final InputPointers aggregatedPointers, final long eventTime) {
        mContext.mTypingTimeRecorder.onEndBatchInput(eventTime);
        mContext.mTimerProxy.cancelAllUpdateBatchInputTimers();
        if (mIsTrackingForActionDisabled) {
            return;
        }
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onEndBatchInput   : batchPoints=%d",
                    mPointerId, aggregatedPointers.getPointerSize()));
        }
        mContext.mListener.onEndBatchInput(aggregatedPointers);
    }

    private void cancelBatchInput() {
        cancelAllPointerTrackers();
        mIsDetectingGesture = false;
        if (!sInGesture) {
            return;
        }
        sInGesture = false;
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onCancelBatchInput", mPointerId));
        }
        mContext.mListener.onCancelBatchInput();
    }

    public void processMotionEvent(final MotionEvent me, final KeyDetector keyDetector) {
        final int action = me.getActionMasked();
        final long eventTime = me.getEventTime();
        if (action == MotionEvent.ACTION_MOVE) {
            // When this pointer is the only active pointer and is showing a popup keys
            // panel,
            // we should ignore other pointers' motion event.
            final boolean shouldIgnoreOtherPointers = isShowingPopupKeysPanel() && getActivePointerTrackerCount() == 1;
            final int pointerCount = me.getPointerCount();
            for (int index = 0; index < pointerCount; index++) {
                final int id = me.getPointerId(index);
                if (shouldIgnoreOtherPointers && id != mPointerId) {
                    continue;
                }
                final int x = (int) me.getX(index);
                final int y = (int) me.getY(index);
                final PointerTracker tracker = getPointerTracker(id);
                tracker.onMoveEvent(x, y, eventTime, me);
            }
            return;
        }
        final int index = me.getActionIndex();
        final int x = (int) me.getX(index);
        final int y = (int) me.getY(index);
        switch (action) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onDownEvent(x, y, eventTime, keyDetector);
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> onUpEvent(x, y, eventTime);
            case MotionEvent.ACTION_CANCEL -> onCancelEvent(x, y, eventTime);
        }
    }

    private void onDownEvent(final int x, final int y, final long eventTime,
            final KeyDetector keyDetector) {
        setKeyDetectorInner(keyDetector);
        if (DEBUG_EVENT) {
            printTouchEvent("onDownEvent:", x, y, eventTime);
        }
        // Naive up-to-down noise filter.
        final long deltaT = eventTime - mUpTime;
        if (deltaT < mContext.mParams.mTouchNoiseThresholdTime) {
            final int distance = getDistance(x, y, mLastX, mLastY);
            if (distance < mContext.mParams.mTouchNoiseThresholdDistance) {
                if (DEBUG_MODE)
                    Log.w(TAG, String.format(Locale.US, "[%d] onDownEvent:"
                            + " ignore potential noise: time=%d distance=%d",
                            mPointerId, deltaT, distance));
                cancelTrackingForAction();
                return;
            }
        }

        final Key key = getKeyOn(x, y);
        mBogusMoveEventDetector.onActualDownEvent(x, y);
        if (key != null && key.isModifier()) {
            if (sInGesture) {
                // Make sure not to interrupt an active gesture
                return;
            } else {
                // Before processing a down event of modifier key, all pointers
                // already being tracked should be released.
                sPointerTrackerQueue.releaseAllPointers(eventTime);
            }
        }
        sPointerTrackerQueue.add(this);
        onDownEventInternal(x, y, eventTime);
        if (!mContext.mGestureEnabler.shouldHandleGesture()) {
            return;
        }
        // A gesture should start only from a non-modifier key. Note that the gesture
        // detection is
        // disabled when the key is repeating.
        mIsDetectingGesture = (mKeyboard != null) && mKeyboard.mId.isAlphabetKeyboard()
                && key != null && !key.isModifier() && !mKeySwipeAllowed && !sInKeySwipe;
        if (mIsDetectingGesture) {
            mBatchInputArbiter.addDownEventPoint(x, y, eventTime,
                    mContext.mTypingTimeRecorder.getLastLetterTypingTime(), getActivePointerTrackerCount());
            mGestureStrokeDrawingPoints.onDownEvent(
                    x, y, mBatchInputArbiter.getElapsedTimeSinceFirstDown(eventTime));
        }
    }

    /* package */ boolean isShowingPopupKeysPanel() {
        return (mPopupKeysPanel != null);
    }

    private void dismissPopupKeysPanel() {
        if (isShowingPopupKeysPanel()) {
            mPopupKeysPanel.dismissPopupKeysPanel();
            mPopupKeysPanel = null;
        }
    }

    private void onDownEventInternal(final int x, final int y, final long eventTime) {
        Key key = onDownKey(x, y, eventTime);
        // Key selection by dragging finger is allowed when 1) key selection by dragging
        // finger is
        // enabled by configuration, 2) this pointer starts dragging from modifier key,
        // or 3) this
        // pointer's KeyDetector always allows key selection by dragging finger, such as
        // {@link PopupKeysKeyboard}.
        mIsAllowedDraggingFinger = mContext.mParams.mKeySelectionByDraggingFinger
                || (key != null && key.isModifier())
                || mKeyDetector.alwaysAllowsKeySelectionByDraggingFinger();
        if (key != null && isSwiper(key.getCode()) && !sInGesture) {
            mKeySwipeAllowed = true;
            sInKeySwipe = true;
        }
        mKeyboardLayoutHasBeenChanged = false;
        mIsTrackingForActionDisabled = false;
        resetKeySelectionByDraggingFinger();
        if (key != null) {
            // This onPress call may have changed keyboard layout. Those cases are detected
            // at
            // {@link #setKeyboard}. In those cases, we should update key according to the
            // new
            // keyboard layout.
            // Also height difference between keyboards needs to be considered.
            if (callListenerOnPressAndCheckKeyboardLayoutChange(key, 0)) {
                final int yOffset = keyboardChangeOccupiedHeightDifference;
                keyboardChangeOccupiedHeightDifference = 0;
                CoordinateUtils.set(mDownCoordinates, x, y + yOffset);
                key = onDownKey(x, y + yOffset, eventTime);
            }

            startRepeatKey(key);
            startLongPressTimer(key);
            setPressedKeyGraphics(key, eventTime);
            mStartX = x;
            mStartY = y;
            mStartTime = System.currentTimeMillis();
        }
    }

    private void startKeySelectionByDraggingFinger(final Key key) {
        if (!mIsInDraggingFinger) {
            // the meta lock keys stay enabled after sliding input, but should not
            // (even without sliding input they actually behave the same... this is just
            // about the graphics)
            final int code = key.getCode();
            mIsInSlidingKeyInput = key.isModifier() && code != KeyCode.CTRL_LOCK && code != KeyCode.ALT_LOCK
                    && code != KeyCode.FN_LOCK && code != KeyCode.META_LOCK;
        }
        mIsInDraggingFinger = true;
    }

    private void resetKeySelectionByDraggingFinger() {
        mIsInDraggingFinger = false;
        mIsInSlidingKeyInput = false;
        mContext.mDrawingProxy.showSlidingKeyInputPreview(null);
    }

    private boolean isSwiper(final int code) {
        final SettingsValues sv = Settings.getValues();
        return switch (code) {
            case Constants.CODE_SPACE -> sv.mSpaceSwipeHorizontal != KeyboardActionListener.SWIPE_NO_ACTION
                    || sv.mSpaceSwipeVertical != KeyboardActionListener.SWIPE_NO_ACTION;
            case KeyCode.DELETE -> sv.mDeleteSwipeEnabled;
            default -> false;
        };
    }

    private void onGestureMoveEvent(final int x, final int y, final long eventTime,
            final boolean isMajorEvent, final Key key) {
        if (!mIsDetectingGesture || sInKeySwipe) {
            return;
        }
        final boolean onValidArea = mBatchInputArbiter.addMoveEventPoint(
                x, y, eventTime, isMajorEvent, this);
        // If the move event goes out from valid batch input area, cancel batch input.
        if (!onValidArea) {
            cancelBatchInput();
            return;
        }
        mGestureStrokeDrawingPoints.onMoveEvent(
                x, y, mBatchInputArbiter.getElapsedTimeSinceFirstDown(eventTime));
        // If the PopupKeysPanel is showing then do not attempt to enter gesture mode.
        // However,
        // the gestured touch points are still being recorded in case the panel is
        // dismissed.
        if (isShowingPopupKeysPanel()) {
            return;
        }
        if (!sInGesture && key != null && Character.isLetter(key.getCode())
                && mBatchInputArbiter.mayStartBatchInput(this)) {
            mContext.mListener.resetMetaState(); // avoid metaState getting stuck, doesn't work with gesture typing anyway
            sInGesture = true;
        }
        if (sInGesture) {
            if (key != null) {
                mBatchInputArbiter.updateBatchInput(eventTime, this);
            }
            showGestureTrail();
        }
    }

    private void onMoveEvent(final int x, final int y, final long eventTime, final MotionEvent me) {
        if (DEBUG_MOVE_EVENT) {
            printTouchEvent("onMoveEvent:", x, y, eventTime);
        }
        if (mIsTrackingForActionDisabled) {
            return;
        }

        if (mContext.mGestureEnabler.shouldHandleGesture() && me != null) {
            // Add historical points to gesture path.
            final int pointerIndex = me.findPointerIndex(mPointerId);
            final int historicalSize = me.getHistorySize();
            for (int h = 0; h < historicalSize; h++) {
                final int historicalX = (int) me.getHistoricalX(pointerIndex, h);
                final int historicalY = (int) me.getHistoricalY(pointerIndex, h);
                final long historicalTime = me.getHistoricalEventTime(h);
                onGestureMoveEvent(historicalX, historicalY, historicalTime, false, null);
            }
        }

        if (isShowingPopupKeysPanel()) {
            final int translatedX = mPopupKeysPanel.translateX(x);
            final int translatedY = mPopupKeysPanel.translateY(y);
            mPopupKeysPanel.onMoveEvent(translatedX, translatedY, mPointerId, eventTime);
            onMoveKey(x, y);
            if (mIsInSlidingKeyInput) {
                mContext.mDrawingProxy.showSlidingKeyInputPreview(this);
            }
            return;
        }
        onMoveEventInternal(x, y, eventTime);
    }

    private void processDraggingFingerInToNewKey(final Key newKey, final int x, final int y, final long eventTime) {
        // This onPress call may have changed keyboard layout. Those cases are detected
        // at {@link #setKeyboard}. In those cases, we should update key according
        // to the new keyboard layout.
        Key key = newKey;
        if (callListenerOnPressAndCheckKeyboardLayoutChange(key, 0)) {
            key = onMoveKey(x, y);
        }
        onMoveToNewKey(key, x, y);
        if (mIsTrackingForActionDisabled) {
            return;
        }
        startLongPressTimer(key);
        setPressedKeyGraphics(key, eventTime);
    }

    private void processProximateBogusDownMoveUpEventHack(final Key key, final int x, final int y,
            final long eventTime, final Key oldKey, final int lastX, final int lastY) {
        if (DEBUG_MODE) {
            final float keyDiagonal = (float) Math.hypot(
                    mKeyboard.mMostCommonKeyWidth, mKeyboard.mMostCommonKeyHeight);
            final float radiusRatio = mBogusMoveEventDetector.getDistanceFromDownEvent(x, y)
                    / keyDiagonal;
            Log.w(TAG, String.format(Locale.US, "[%d] onMoveEvent:"
                    + " bogus down-move-up event (raidus=%.2f key diagonal) is "
                    + " translated to up[%d,%d,%s]/down[%d,%d,%s] events",
                    mPointerId, radiusRatio,
                    lastX, lastY, Constants.printableCode(oldKey.getCode()),
                    x, y, Constants.printableCode(key.getCode())));
        }
        onUpEventInternal(x, y, eventTime);
        onDownEventInternal(x, y, eventTime);
    }

    private void processDraggingFingerOutFromOldKey(final Key oldKey) {
        setReleasedKeyGraphics(oldKey, true);
        callListenerOnRelease(oldKey, oldKey.getCode(), true);
        startKeySelectionByDraggingFinger(oldKey);
        mContext.mTimerProxy.cancelKeyTimersOf(this);
    }

    private void dragFingerFromOldKeyToNewKey(final Key key, final int x, final int y,
            final long eventTime, final Key oldKey, final int lastX, final int lastY) {
        // The pointer has been slid in to the new key from the previous key, we must
        // call
        // onRelease() first to notify that the previous key has been released, then
        // call
        // onPress() to notify that the new key is being pressed.
        processDraggingFingerOutFromOldKey(oldKey);
        startRepeatKey(key);
        if (mIsAllowedDraggingFinger) {
            processDraggingFingerInToNewKey(key, x, y, eventTime);
        }
        // HACK: On some devices, quick successive proximate touches may be reported as
        // a bogus
        // down-move-up event by touch panel firmware. This hack detects such cases and
        // breaks
        // these events into separate up and down events.
        else if (mContext.mTypingTimeRecorder.isInFastTyping(eventTime)
                && mBogusMoveEventDetector.isCloseToActualDownEvent(x, y)) {
            processProximateBogusDownMoveUpEventHack(key, x, y, eventTime, oldKey, lastX, lastY);
        }
        // HACK: If there are currently multiple touches, register the key even if the
        // finger
        // slides off the key. This defends against noise from some touch panels when
        // there are
        // close multiple touches.
        // Caveat: When in chording input mode with a modifier key, we don't use this
        // hack.
        else if (getActivePointerTrackerCount() > 1
                && !sPointerTrackerQueue.hasModifierKeyOlderThan(this)) {
            if (DEBUG_MODE) {
                Log.w(TAG, String.format(Locale.US, "[%d] onMoveEvent:"
                        + " detected sliding finger while multi touching", mPointerId));
            }
            onUpEvent(x, y, eventTime);
            cancelTrackingForAction();
            setReleasedKeyGraphics(oldKey, true);
        } else {
            if (!mIsDetectingGesture) {
                cancelTrackingForAction();
            }
            setReleasedKeyGraphics(oldKey, true);
        }
    }

    private void dragFingerOutFromOldKey(final Key oldKey, final int x, final int y) {
        // The pointer has been slid out from the previous key, we must call onRelease()
        // to
        // notify that the previous key has been released.
        processDraggingFingerOutFromOldKey(oldKey);
        if (mIsAllowedDraggingFinger) {
            onMoveToNewKey(null, x, y);
        } else {
            if (!mIsDetectingGesture) {
                cancelTrackingForAction();
            }
        }
    }

    private boolean oneShotSwipe(final int swipeSetting) {
        return switch (swipeSetting) {
            case KeyboardActionListener.SWIPE_NO_ACTION, KeyboardActionListener.SWIPE_TOGGLE_NUMPAD,
                    KeyboardActionListener.SWIPE_HIDE_KEYBOARD ->
                true;
            default -> false;
        };
    }

    private void onKeySwipe(final int code, final int x, final int y, final long eventTime) {
        final SettingsValues sv = Settings.getValues();
        final int fastTypingTimeout = 2 * sv.mKeyLongpressTimeout / 3;
        // we don't want keyswipes to start immediately if the user is fast-typing,
        // see https://github.com/openboard-team/openboard/issues/411
        if (System.currentTimeMillis() < mStartTime + fastTypingTimeout
                && mContext.mTypingTimeRecorder.isInFastTyping(eventTime))
            return;
        if (code == Constants.CODE_SPACE) {
            int dX = x - mStartX;
            int dY = y - mStartY;

            // Check if touchpad mode is active and we're in it
            if (sTouchpadModeActive && !mInTouchpadMode) {
                // Just entered touchpad mode - initialize
                mInTouchpadMode = true;
                mTouchpadLastX = x;
                mTouchpadLastY = y;
                mTouchpadAccX = 0;
                mTouchpadAccY = 0;
                // Signal start of touchpad mode for visual feedback (dimming)
                mContext.mListener.onCustomRequest(KeyboardActionListener.CODE_TOUCHPAD_ON);
                return;
            }

            if (mInTouchpadMode) {
                // In touchpad mode - track both horizontal and vertical movement for 2D cursor
                // control
                int deltaX = x - mTouchpadLastX;
                int deltaY = y - mTouchpadLastY;

                mTouchpadLastX = x;
                mTouchpadLastY = y;

                // Apply velocity-based acceleration using fast integer abs
                // Faster swipes (larger delta) get a higher multiplier
                float accFactorX = 1.0f + ((float)((deltaX ^ (deltaX >> 31)) - (deltaX >> 31)) / TOUCHPAD_ACCELERATION_FACTOR);
                float accFactorY = 1.0f + ((float)((deltaY ^ (deltaY >> 31)) - (deltaY >> 31)) / TOUCHPAD_ACCELERATION_FACTOR);

                mTouchpadAccX += (int) (deltaX * accFactorX);
                mTouchpadAccY += (int) (deltaY * accFactorY);

                // Handle horizontal movement with accumulator
                // Calculate dynamic threshold based on sensitivity setting (0-100)
                // Higher sensitivity = Lower threshold (faster cursor)
                // 0 -> 70px (Very Slow)
                // 50 -> 40px (Default)
                // 100 -> 10px (Very Fast)
                // Cache sensitivity value to avoid repeated Settings lookups in hot path
                final long currentTime = System.currentTimeMillis();
                if (currentTime - sLastTouchpadSensitivityUpdateTime > TOUCHPAD_SENSITIVITY_UPDATE_INTERVAL_MS) {
                    sCachedTouchpadSensitivity = Settings.getInstance().getCurrent().mTouchpadSensitivity;
                    sLastTouchpadSensitivityUpdateTime = currentTime;
                }
                final int moveThreshold = 70 - (int) (sCachedTouchpadSensitivity * 0.6f);

                // Handle horizontal movement with accumulator - optimized to avoid Math.abs() calls
                while (mTouchpadAccX >= moveThreshold || mTouchpadAccX <= -moveThreshold) {
                    boolean positive = mTouchpadAccX > 0;
                    int direction = positive ? KeyCode.ARROW_RIGHT : KeyCode.ARROW_LEFT;
                    mContext.mListener.onCodeInput(direction, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false);
                    mTouchpadAccX -= (positive ? moveThreshold : -moveThreshold);
                }

                // Handle vertical movement with accumulator - optimized to avoid Math.abs() calls
                while (mTouchpadAccY >= moveThreshold || mTouchpadAccY <= -moveThreshold) {
                    boolean positive = mTouchpadAccY > 0;
                    int direction = positive ? KeyCode.ARROW_DOWN : KeyCode.ARROW_UP;
                    mContext.mListener.onCodeInput(direction, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false);
                    mTouchpadAccY -= (positive ? moveThreshold : -moveThreshold);
                }
                return;
            }

            // Vertical movement
            int stepsY = dY / sPointerStep;
            if (stepsY != 0 && abs(dX) < abs(dY) && !mInHorizontalSwipe) {
                if (!mInVerticalSwipe) {
                    mContext.mTimerProxy.cancelKeyTimersOf(this);
                    mInVerticalSwipe = true;
                } else if (oneShotSwipe(sv.mSpaceSwipeVertical))
                    return;
                if (mContext.mListener.onVerticalSpaceSwipe(stepsY)) {
                    mStartY += stepsY * sPointerStep;
                }
                return;
            }

            // Horizontal movement
            int stepsX = dX / sPointerStep;
            if (stepsX != 0 && !mInVerticalSwipe) {
                if (!mInHorizontalSwipe) {
                    mContext.mTimerProxy.cancelKeyTimersOf(this);
                    mInHorizontalSwipe = true;
                } else if (oneShotSwipe(sv.mSpaceSwipeHorizontal))
                    return;
                if (mContext.mListener.onHorizontalSpaceSwipe(stepsX)) {
                    mStartX += stepsX * sPointerStep;
                }
            }
        } else if (code == KeyCode.DELETE) {
            // Delete slider
            int steps = (x - mStartX) / sPointerStep;
            if (steps != 0) {
                if (!mInHorizontalSwipe) {
                    mContext.mTimerProxy.cancelKeyTimersOf(this);
                    mInHorizontalSwipe = true;
                }
                mStartX += steps * sPointerStep;
                mContext.mListener.onMoveDeletePointer(steps);
            }
        }
    }

    private void onMoveEventInternal(final int x, final int y, final long eventTime) {
        final Key oldKey = mCurrentKey;

        // todo (later): move key swipe stuff to KeyboardActionListener (and finally
        // extend it)
        if (mKeySwipeAllowed) {
            onKeySwipe(oldKey.getCode(), x, y, eventTime);
            return;
        }

        final Key newKey = onMoveKey(x, y);
        final int lastX = mLastX;
        final int lastY = mLastY;

        if (mContext.mGestureEnabler.shouldHandleGesture()) {
            // Register move event on gesture tracker.
            onGestureMoveEvent(x, y, eventTime, true, newKey);
            if (sInGesture) {
                mCurrentKey = null;
                setReleasedKeyGraphics(oldKey, true);
                return;
            }
        }

        if (newKey != null) {
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, newKey)) {
                dragFingerFromOldKeyToNewKey(newKey, x, y, eventTime, oldKey, lastX, lastY);
            } else if (oldKey == null) {
                // The pointer has been slid in to the new key, but the finger was not on any
                // keys.
                // In this case, we must call onPress() to notify that the new key is being
                // pressed.
                processDraggingFingerInToNewKey(newKey, x, y, eventTime);
            }
        } else { // newKey == null
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, newKey)) {
                dragFingerOutFromOldKey(oldKey, x, y);
            }
        }
        if (mIsInSlidingKeyInput) {
            mContext.mDrawingProxy.showSlidingKeyInputPreview(this);
        }
    }

    private void onUpEvent(final int x, final int y, final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onUpEvent  :", x, y, eventTime);
        }

        mContext.mTimerProxy.cancelUpdateBatchInputTimer(this);
        if (!sInGesture) {
            if (mCurrentKey != null && mCurrentKey.isModifier()) {
                // Before processing an up event of modifier key, all pointers already being
                // tracked should be released.
                sPointerTrackerQueue.releaseAllPointersExcept(this, eventTime);
            } else {
                sPointerTrackerQueue.releaseAllPointersOlderThan(this, eventTime);
            }
        }
        onUpEventInternal(x, y, eventTime);
        sPointerTrackerQueue.remove(this);
    }

    // Let this pointer tracker know that one of newer-than-this pointer trackers
    // got an up event.
    // This pointer tracker needs to keep the key top graphics "pressed", but needs
    // to get a
    // "virtual" up event.
    @Override
    public void onPhantomUpEvent(final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onPhntEvent:", mLastX, mLastY, eventTime);
        }
        onUpEventInternal(mLastX, mLastY, eventTime);
        cancelTrackingForAction();
    }

    private void onUpEventInternal(final int x, final int y, final long eventTime) {
        mContext.mTimerProxy.cancelKeyTimersOf(this);
        final boolean isInDraggingFinger = mIsInDraggingFinger;
        final boolean isInSlidingKeyInput = mIsInSlidingKeyInput;
        resetKeySelectionByDraggingFinger();
        mIsDetectingGesture = false;
        final Key currentKey = mCurrentKey;
        mCurrentKey = null;
        final int currentRepeatingKeyCode = mCurrentRepeatingKeyCode;
        mCurrentRepeatingKeyCode = Constants.NOT_A_CODE;
        // Release the last pressed key.
        setReleasedKeyGraphics(currentKey, true);

        if (mInHorizontalSwipe && currentKey.getCode() == KeyCode.DELETE) {
            mContext.mListener.onUpWithDeletePointerActive();
        }

        if (isShowingPopupKeysPanel()) {
            if (!mIsTrackingForActionDisabled) {
                final int translatedX = mPopupKeysPanel.translateX(x);
                final int translatedY = mPopupKeysPanel.translateY(y);
                mPopupKeysPanel.onUpEvent(translatedX, translatedY, mPointerId, eventTime);
            }
            dismissPopupKeysPanel();
            if (isInSlidingKeyInput)
                callListenerOnFinishSlidingInput();
            return;
        }

        if (mKeySwipeAllowed) {
            mKeySwipeAllowed = false;
            sInKeySwipe = false;

            // Exit touchpad mode if active
            if (mInTouchpadMode) {
                mInTouchpadMode = false;
                sTouchpadModeActive = false;
                mContext.mListener.onCustomRequest(KeyboardActionListener.CODE_TOUCHPAD_OFF); // Signal end of touchpad mode
                                                                                     // (restore visuals)
            }

            if (mInHorizontalSwipe || mInVerticalSwipe) {
                mInHorizontalSwipe = false;
                mInVerticalSwipe = false;
                mContext.mListener.onEndSpaceSwipe();
                return;
            }
        }

        if (sInGesture) {
            if (currentKey != null) {
                callListenerOnRelease(currentKey, currentKey.getCode(), true);
            }
            if (mBatchInputArbiter.mayEndBatchInput(
                    eventTime, getActivePointerTrackerCount(), this)) {
                sInGesture = false;
            }
            showGestureTrail();
            return;
        }

        if (mIsTrackingForActionDisabled) {
            return;
        }
        if (currentKey != null && currentKey.isRepeatable()
                && (currentKey.getCode() == currentRepeatingKeyCode) && !isInDraggingFinger) {
            return;
        }
        detectAndSendKey(currentKey, mKeyX, mKeyY, eventTime);
        if (isInSlidingKeyInput) {
            callListenerOnFinishSlidingInput();
        }
    }

    @Override
    public void cancelTrackingForAction() {
        if (isShowingPopupKeysPanel()) {
            return;
        }
        mIsTrackingForActionDisabled = true;
    }

    public boolean isInOperation() {
        return !mIsTrackingForActionDisabled;
    }

    public void onLongPressed() {
        mContext.mTimerProxy.cancelLongPressTimersOf(this);
        if (isShowingPopupKeysPanel()) {
            return;
        }
        final Key key = getKey();
        if (key == null) {
            return;
        }
        mContext.mListener.onLongPressKey(key.getCode());
        if (key.hasNoPanelAutoPopupKey()) {
            cancelKeyTracking();
            final int popupKeyCode = key.getPopupKeys()[0].mCode;
            mContext.mListener.onPressKey(popupKeyCode, 0, true, HapticEvent.NO_HAPTICS);
            mContext.mListener.onCodeInput(popupKeyCode, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false);
            mContext.mListener.onReleaseKey(popupKeyCode, false);
            return;
        }
        final int code = key.getCode();
        final SettingsValues sv = Settings.getValues();
        if (code == KeyCode.LANGUAGE_SWITCH
                || (code == Constants.CODE_SPACE && key.getPopupKeys() == null
                        && sv.mSpaceForLangChange)) {
            // Long pressing the space key invokes IME switcher dialog.
            if (mContext.mListener.onCustomRequest(Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER)) {
                cancelKeyTracking();
                mContext.mListener.onReleaseKey(code, false);
                return;
            }
        }
        if (code == KeyCode.SYMBOL_ALPHA && sv.mLongPressSymbolsForNumpad) {
            // toggle numpad with sliding input enabled, forcing return to the alpha layout
            // when done
            mContext.mListener.toggleNumpad(true, true);
            return;
        }

        setReleasedKeyGraphics(key, false);
        final PopupKeysPanel popupKeysPanel = mContext.mDrawingProxy.showPopupKeysKeyboard(key, this);
        if (popupKeysPanel == null) {
            return;
        }
        final int translatedX = popupKeysPanel.translateX(mLastX);
        final int translatedY = popupKeysPanel.translateY(mLastY);
        popupKeysPanel.onDownEvent(translatedX, translatedY, mPointerId, SystemClock.uptimeMillis());
        mPopupKeysPanel = popupKeysPanel;
        if (mKeySwipeAllowed) {
            mKeySwipeAllowed = false;
            sInKeySwipe = false;
        }
    }

    private void cancelKeyTracking() {
        resetKeySelectionByDraggingFinger();
        cancelTrackingForAction();
        setReleasedKeyGraphics(mCurrentKey, true);
        sPointerTrackerQueue.remove(this);
    }

    private void onCancelEvent(final int x, final int y, final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onCancelEvt:", x, y, eventTime);
        }

        cancelBatchInput();
        cancelAllPointerTrackers();
        sPointerTrackerQueue.releaseAllPointers(eventTime);
        onCancelEventInternal();
    }

    private void onCancelEventInternal() {
        mContext.mTimerProxy.cancelKeyTimersOf(this);
        setReleasedKeyGraphics(mCurrentKey, true);
        resetKeySelectionByDraggingFinger();
        dismissPopupKeysPanel();
    }

    private boolean isMajorEnoughMoveToBeOnNewKey(final int x, final int y, final long eventTime,
            final Key newKey) {
        final Key curKey = mCurrentKey;
        if (newKey == curKey) {
            return false;
        }
        if (curKey == null /* && newKey != null */) {
            return true;
        }
        // Here curKey points to the different key from newKey.
        final int keyHysteresisDistanceSquared = mKeyDetector.getKeyHysteresisDistanceSquared(mIsInSlidingKeyInput);
        final int distanceFromKeyEdgeSquared = curKey.squaredDistanceToEdge(x, y);
        if (distanceFromKeyEdgeSquared >= keyHysteresisDistanceSquared) {
            if (DEBUG_MODE) {
                final float distanceToEdgeRatio = (float) Math.sqrt(distanceFromKeyEdgeSquared)
                        / mKeyboard.mMostCommonKeyWidth;
                Log.d(TAG, String.format(Locale.US, "[%d] isMajorEnoughMoveToBeOnNewKey:"
                        + " %.2f key width from key edge", mPointerId, distanceToEdgeRatio));
            }
            return true;
        }
        if (!mIsAllowedDraggingFinger && mContext.mTypingTimeRecorder.isInFastTyping(eventTime)
                && mBogusMoveEventDetector.hasTraveledLongDistance(x, y)) {
            if (DEBUG_MODE) {
                final float keyDiagonal = (float) Math.hypot(mKeyboard.mMostCommonKeyWidth,
                        mKeyboard.mMostCommonKeyHeight);
                final float lengthFromDownRatio = mBogusMoveEventDetector.getAccumulatedDistanceFromDownKey()
                        / keyDiagonal;
                Log.d(TAG, String.format(Locale.US, "[%d] isMajorEnoughMoveToBeOnNewKey:"
                        + " %.2f key diagonal from virtual down point", mPointerId, lengthFromDownRatio));
            }
            return true;
        }
        return false;
    }

    private void startLongPressTimer(final Key key) {
        // Note that we need to cancel all active long press shift key timers if any
        // whenever we
        // start a new long press timer for both non-shift and shift keys.
        mContext.mTimerProxy.cancelLongPressShiftKeyTimer();
        if (sInGesture)
            return;
        if (key == null)
            return;
        if (!key.isLongPressEnabled())
            return;
        // Caveat: Please note that isLongPressEnabled() can be true even if the current
        // key
        // doesn't have its popup keys. (e.g. spacebar, globe key) If we are in the
        // dragging finger
        // mode, we will disable long press timer of such key.
        // We always need to start the long press timer if the key has its popup keys
        // regardless of
        // whether or not we are in the dragging finger mode.
        if (mIsInDraggingFinger && key.getPopupKeys() == null)
            return;

        final int delay = getLongPressTimeout(key.getCode());
        if (delay <= 0)
            return;
        mContext.mTimerProxy.startLongPressTimerOf(this, delay);
    }

    private int getLongPressTimeout(final int code) {
        final int longpressTimeout = Settings.getValues().mKeyLongpressTimeout;
        if (code == KeyCode.SHIFT || code == KeyCode.SYMBOL_ALPHA) {
            // We use slightly longer timeout for shift-lock and the numpad long-press.
            return longpressTimeout * 3 / 2;
        } else if (mIsInSlidingKeyInput) {
            // We use longer timeout for sliding finger input started from a modifier key.
            return longpressTimeout * 3;
        }
        return longpressTimeout;
    }

    private boolean isClearlyInsideKey(final Key key, final int x, final int y) {
        // less than 15% of width from edge
        return x > key.getX() + key.getWidth() * 0.15 && x < key.getX() + key.getWidth() * 0.85
                && y > key.getY() + key.getHeight() * 0.15 && y < key.getY() + key.getHeight() * 0.85;
    }

    private void detectAndSendKey(final Key key, final int x, final int y, final long eventTime) {
        if (key == null) {
            callListenerOnCancelInput();
            return;
        }

        final int code = key.getCode();
        callListenerOnCodeInput(key, code, x, y, eventTime, false);
        callListenerOnRelease(key, code, false);
    }

    private void startRepeatKey(final Key key) {
        if (sInGesture)
            return;
        if (key == null)
            return;
        if (!key.isRepeatable())
            return;
        // Don't start key repeat when we are in the dragging finger mode.
        if (mIsInDraggingFinger)
            return;
        final int startRepeatCount = 1;
        startKeyRepeatTimer(startRepeatCount);
    }

    public void onKeyRepeat(final int code, final int repeatCount) {
        final Key key = getKey();
        if (key == null || key.getCode() != code) {
            mCurrentRepeatingKeyCode = Constants.NOT_A_CODE;
            return;
        }
        mCurrentRepeatingKeyCode = code;
        if (mKeySwipeAllowed) {
            mKeySwipeAllowed = false;
            sInKeySwipe = false;
        }
        mIsDetectingGesture = false;
        final int nextRepeatCount = repeatCount + 1;
        startKeyRepeatTimer(nextRepeatCount);
        callListenerOnPressAndCheckKeyboardLayoutChange(key, repeatCount);
        callListenerOnCodeInput(key, code, mKeyX, mKeyY, SystemClock.uptimeMillis(), true);
    }

    private void startKeyRepeatTimer(final int repeatCount) {
        final int delay = (repeatCount == 1) ? mContext.mParams.mKeyRepeatStartTimeout : mContext.mParams.mKeyRepeatInterval;
        mContext.mTimerProxy.startKeyRepeatTimerOf(this, repeatCount, delay);
    }

    private void printTouchEvent(final String title, final int x, final int y,
            final long eventTime) {
        final Key key = mKeyDetector.detectHitKey(x, y);
        final String code = (key == null ? "none" : Constants.printableCode(key.getCode()));
        Log.d(TAG, String.format(Locale.US, "[%d]%s%s %4d %4d %5d %s", mPointerId,
                (mIsTrackingForActionDisabled ? "-" : " "), title, x, y, eventTime, code));
    }
}
