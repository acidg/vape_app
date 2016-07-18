package me.nerdsho.vape;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

/**
 * A class, that can be used as a TouchListener on any view (e.g. a Button).
 * It cyclically runs a clickListener, emulating keyboard-like behaviour. First
 * click is fired immediately, next one after the initialInterval, and subsequent
 * ones after the normalInterval.
 * <p>
 * <p>Interval is scheduled after the onClick completes, so it has to run fast.
 * If it runs slow, it does not generate skipped onClicks. Can be rewritten to
 * achieve this.
 */
public class TempButtonListener implements OnTouchListener {
    /**
     * The initial time in milliseconds, after which the first button pressed event is fired.
     */
    private static final int INITIAL_INTERVAL = 400;

    /**
     * The time in milliseconds, after which each subsequent button pressed event is fired.
     */
    private static final int SUBSEQUENT_INTERVAL = 50;

    private final TempButtonCallback callback;
    private Handler handler = new Handler();
    private View downView;
    private Runnable handlerRunnable = new Runnable() {
        @Override
        public void run() {
            handler.postDelayed(this, SUBSEQUENT_INTERVAL);
            callback.onTick();
        }
    };

    /**
     * @param callback        The TempButtonCallback, that will be called periodically
     */
    public TempButtonListener(TempButtonCallback callback) {
        this.callback = callback;
    }

    /**
     * {@inheritDoc}
     */
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handler.removeCallbacks(handlerRunnable);
                handler.postDelayed(handlerRunnable, INITIAL_INTERVAL);
                downView = view;
                downView.setPressed(true);
                callback.onTick();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(handlerRunnable);
                downView.setPressed(false);
                downView = null;
                callback.onFinal();
                return true;
            default:
                return false;
        }
    }

    public interface TempButtonCallback {
        /**
         * Called, every tick.
         */
        void onTick();

        /**
         * Called when the user stops pressing the button.
         */
        void onFinal();
    }
}