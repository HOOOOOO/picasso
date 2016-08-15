/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB_MR1;

class DeferredRequestCreator implements ViewTreeObserver.OnPreDrawListener {

	final RequestCreator creator;

	//  当一个对象仅仅被weak reference指向, 而没有任何其他strong reference指向的时候, 如果GC运行, 那么
	//  这个对象就会被回收
	//  WeakReference的一个特点是它何时被回收是不可确定的, 因为这是由GC运行的不确定性所确定的. 所以, 一般
	// 用weak reference引用的对象是有价值被cache, 而且很容易被重新被构建, 且很消耗内存的对象.
	final WeakReference<ImageView> target;
	Object attachListener;
	Callback callback;

	@VisibleForTesting
	DeferredRequestCreator(RequestCreator creator, ImageView target) {
		this(creator, target, null);
	}

	DeferredRequestCreator(RequestCreator creator, ImageView target, Callback callback) {
		this.creator = creator;
		this.target = new WeakReference<ImageView>(target);
		this.callback = callback;

		// Since the view on which an image is being requested might not be attached to a hierarchy,
		// defer adding the pre-draw listener until the view is attached. This works around a platform
		// behavior where a global, dummy VTO is used until a real one is available on attach.
		// See: https://github.com/square/picasso/issues/1321
		if (SDK_INT >= HONEYCOMB_MR1 && target.getWindowToken() == null) {
			attachListener = HoneycombMr1ViewUtil.defer(target, this);
		} else {
			target.getViewTreeObserver().addOnPreDrawListener(this);
		}
	}

	@Override
	public boolean onPreDraw() {
		ImageView target = this.target.get();
		if (target == null) {
			return true;
		}
		ViewTreeObserver vto = target.getViewTreeObserver();
		if (!vto.isAlive()) {
			return true;
		}

		int width = target.getWidth();
		int height = target.getHeight();

		if (width <= 0 || height <= 0 || target.isLayoutRequested()) {
			return true;
		}

		vto.removeOnPreDrawListener(this);
		this.target.clear();

		this.creator.unfit().resize(width, height).into(target, callback);
		return true;
	}

	void cancel() {
		creator.clearTag();
		callback = null;

		ImageView target = this.target.get();
		if (target == null) {
			return;
		}
		this.target.clear();

		if (attachListener != null) { // Only non-null on Honeycomb MR1+
			HoneycombMr1ViewUtil.cancel(target, attachListener);
			attachListener = null;
		} else {
			ViewTreeObserver vto = target.getViewTreeObserver();
			if (!vto.isAlive()) {
				return;
			}
			vto.removeOnPreDrawListener(this);
		}
	}

	Object getTag() {
		return creator.getTag();
	}

	static class HoneycombMr1ViewUtil {
		static Object defer(View view, final DeferredRequestCreator creator) {
			OnAttachStateChangeListener listener = new OnAttachStateChangeListener() {
				@Override
				public void onViewAttachedToWindow(View view) {
					view.removeOnAttachStateChangeListener(this);
					view.getViewTreeObserver().addOnPreDrawListener(creator);

					creator.attachListener = null;
				}

				@Override
				public void onViewDetachedFromWindow(View view) {
				}
			};
			view.addOnAttachStateChangeListener(listener);
			return listener;
		}

		static void cancel(View view, Object attachListener) {
			view.removeOnAttachStateChangeListener((OnAttachStateChangeListener) attachListener);
		}
	}
}
