package com.vuze.android.remote;

import android.text.SpannableString;
import android.util.Log;
import android.view.View;
import android.view.animation.*;
import android.view.animation.Animation.AnimationListener;
import android.widget.TextView;

public class TextViewFlipper
{
	private static final boolean DEBUG_FLIPPER = false;
	private int animId;

	public static interface FlipValidator
	{
		public boolean isStillValid();
	}

	public TextViewFlipper(int animId) {
		this.animId = animId;
	}

	/**
	 * Change the text on repeat of Animation.
	 * 
	 * @param tv Widget to update
	 * @param newText New Text to set
	 * @param animate false to set right away, true to wait
	 * @param validator when animated, validator will be called to determine
	 * if text setting is still required
	 */
	public void changeText(final TextView tv, final String newText,
			boolean animate, final FlipValidator validator) {
		if (DEBUG_FLIPPER) {
			Log.d("flipper", "changeText: '" + newText + "';" + (animate ? "animate" : "now"));
		}
		if (!animate) {
			tv.setText(newText);
			tv.setVisibility(newText.length() == 0 ? View.GONE : View.VISIBLE);
			return;
		}
		if (!newText.equals(tv.getText())) {
			flipIt(tv, new AnimationAdapter() {
				@Override
				public void onAnimationRepeat(Animation animation) {
					if (validator != null && !validator.isStillValid()) {
						if (DEBUG_FLIPPER) {
							Log.d("flipper", "changeText: no longer valid");
						}
						return;
					}
					Log.d("flipper", "changeText: setting to " + newText);
					tv.setText(newText);
					tv.setVisibility(newText.length() == 0 ? View.GONE : View.VISIBLE);
				}
			});
		} else {
			if (DEBUG_FLIPPER) {
				Log.d("flipper", "changeText: ALREADY " + newText);
			}
		}
	}

	private void flipIt(View view, AnimationListener l) {
		Animation animation = AnimationUtils.loadAnimation(view.getContext(),
				animId);
		if (view.getVisibility() == View.GONE) {
			// Usually when the view is gone and is a TextView, the text is ""
			view.setVisibility(View.VISIBLE);
		}
		if (l != null) {
			if (animation instanceof AnimationSet) {
				AnimationSet as = (AnimationSet) animation;
				as.getAnimations().get(0).setAnimationListener(l);
			} else {
				animation.setAnimationListener(l);
			}
		}
		view.startAnimation(animation);
	}

	public void changeText(final TextView tv, final SpannableString newText,
			boolean animate, final FlipValidator validator) {
		String newTextString = newText.toString();
		if (!animate) {
			tv.setText(newText);
			tv.setVisibility(newTextString.length() == 0 ? View.GONE : View.VISIBLE);
			return;
		}
		if (!newTextString.equals(tv.getText().toString())) {
			flipIt(tv, new AnimationAdapter() {
				@Override
				public void onAnimationRepeat(Animation animation) {
					if (validator != null && !validator.isStillValid()) {
						return;
					}
					tv.setText(newText);
					tv.setVisibility(newText.toString().length() == 0 ? View.GONE
							: View.VISIBLE);
				}
			});
		}
	}

}
