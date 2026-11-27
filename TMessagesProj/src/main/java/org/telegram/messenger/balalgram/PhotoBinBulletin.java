package org.telegram.messenger.balalgram;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;

/**
 * Displays a custom bulletin above the compose bar informing the user that media will delete after send.
 */
public final class PhotoBinBulletin {

    public interface ComposeTopSupplier {
        double getAsDouble();
    }

    private PhotoBinBulletin() {
    }

    public static void show(
            FrameLayout container,
            Theme.ResourcesProvider resourcesProvider,
            int tag,
            ComposeTopSupplier composeTop
    ) {
        ComposeLayout layout = new ComposeLayout(container, resourcesProvider, composeTop);
        layout.setAnimation(R.raw.ic_delete, 36, 36);
        layout.textView.setText(LocaleController.getString(R.string.DeleteFromDeviceMessage));
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        layout.imageView.setTranslationY(AndroidUtilities.dp(1));
        Bulletin.make(container, layout, Bulletin.DURATION_LONG)
                .setTag(tag)
                .useLayoutExitTransition()
                .show();
    }

    private static final class ComposeLayout extends Bulletin.LottieLayout {

        private final ComposeTopSupplier composeTop;
        private float reveal = 1f;
        private ValueAnimator animator;
        private final int[] parentLocation = new int[2];
        private final ViewTreeObserver.OnPreDrawListener positionListener = () -> {
            updatePosition();
            return true;
        };

        ComposeLayout(
                FrameLayout container,
                Theme.ResourcesProvider resourcesProvider,
                ComposeTopSupplier composeTop
        ) {
            super(container.getContext(), resourcesProvider);
            this.composeTop = composeTop;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(positionListener);
        }

        @Override
        protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(positionListener);
            if (animator != null) {
                animator.cancel();
            }
            super.onDetachedFromWindow();
        }

        private float edgeInParent() {
            if (!(getParent() instanceof View)) {
                return Float.NaN;
            }
            View host = (View) getParent();
            float edge = (float) composeTop.getAsDouble();
            if (!Float.isFinite(edge)) {
                return Float.NaN;
            }
            host.getLocationOnScreen(parentLocation);
            return edge - parentLocation[1] + host.getScrollY();
        }

        @Override
        public void updatePosition() {
            float edge = edgeInParent();
            if (!Float.isFinite(edge) || getHeight() == 0) {
                return;
            }
            int paintedBottom = getMeasuredBackgroundHeight() - getPaddingBottom();
            int paintedHeight = paintedBottom - getPaddingTop();
            int restingGap = AndroidUtilities.dp(12);
            setTranslationY(edge - getTop() - getPaddingTop() - (paintedHeight + restingGap) * reveal);
        }

        @Override
        public boolean clipTransitionToDelegate() {
            return false;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            float edge = edgeInParent();
            if (!Float.isFinite(edge)) {
                return;
            }
            int paintedBottom = getMeasuredBackgroundHeight() - getPaddingBottom();
            float visibleBottom = Math.min((float) paintedBottom, edge - (getTop() + getTranslationY()));
            if (visibleBottom <= getPaddingTop()) {
                return;
            }
            int save = canvas.save();
            canvas.clipRect(0f, (float) getPaddingTop(), (float) getWidth(), visibleBottom);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }

        @Override
        public Bulletin.Layout.Transition createTransition() {
            return new Bulletin.Layout.Transition() {
                @Override
                public void animateEnter(
                        Bulletin.Layout layout,
                        Runnable startAction,
                        Runnable endAction,
                        Consumer<Float> onUpdate,
                        int bottomOffset
                ) {
                    setReveal(0f);
                    animateReveal(1f, startAction, endAction, onUpdate);
                }

                @Override
                public void animateExit(
                        Bulletin.Layout layout,
                        Runnable startAction,
                        Runnable endAction,
                        Consumer<Float> onUpdate,
                        int bottomOffset
                ) {
                    animateReveal(0f, startAction, endAction, onUpdate);
                }
            };
        }

        private void setReveal(float value) {
            reveal = value;
            updatePosition();
            invalidate();
        }

        private void animateReveal(float end, Runnable startAction, Runnable endAction, Consumer<Float> onUpdate) {
            if (animator != null) {
                animator.cancel();
            }
            final ValueAnimator anim = ValueAnimator.ofFloat(reveal, end);
            anim.setDuration(Math.round(260f * Math.abs(end - reveal)));
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            anim.addUpdateListener(a -> {
                setReveal((float) a.getAnimatedValue());
                if (onUpdate != null) {
                    onUpdate.accept(getTranslationY());
                }
            });
            anim.addListener(new AnimatorListenerAdapter() {
                private boolean canceled = false;

                @Override
                public void onAnimationStart(Animator animation) {
                    if (startAction != null) {
                        startAction.run();
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animator == animation) {
                        animator = null;
                    }
                    if (!canceled && endAction != null) {
                        endAction.run();
                    }
                }
            });
            animator = anim;
            anim.start();
        }
    }
}
