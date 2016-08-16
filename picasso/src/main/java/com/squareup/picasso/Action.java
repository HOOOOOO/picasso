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

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static com.squareup.picasso.Picasso.Priority;

/**
 * Action包含了Request 不同(target等不同)的Action的Request的基本参数可能是相同的 这样它们的key也相同
 * Action还包含了网络请求协议等等
 *
 * @param <T>
 */
abstract class Action<T> {
	final Picasso picasso;
	final Request request;
	final WeakReference<T> target;
	final boolean noFade;
	final int memoryPolicy;
	final int networkPolicy;
	final int errorResId;
	final Drawable errorDrawable;
	final String key;
	final Object tag;
	boolean willReplay;
	boolean cancelled;

	Action(Picasso picasso, T target, Request request, int memoryPolicy, int networkPolicy,
	       int errorResId, Drawable errorDrawable, String key, Object tag, boolean noFade) {
		this.picasso = picasso;
		this.request = request;

		// 在weak reference指向的对象被回收后, weak reference本身其实也就没有用了. java提供了一个
		// ReferenceQueue来保存这些所指向的对象已经被回收的reference. 用法是在定义WeakReference的时候将
		// 一个ReferenceQueue的对象作为参数传入构造函数.
		//
		// 如果target不为空，则对target进行弱引用，意思是就算是target被引用了，也还是可以被回收。
		// 可以使用**.get()方法老获取实际的强引用对象
		// 并且在构造时传入了referenceQueue，当target被回收后 这个RequestWeakReference就没用了，被保存在
		// referenceQueue中
		//
		// 同时 Picasso在后台启动了一个线程 不断的从referenceQueue中获取引用 并把action取消掉
		this.target =
				target == null ? null : new RequestWeakReference<T>(this, target, picasso.referenceQueue);
		this.memoryPolicy = memoryPolicy;
		this.networkPolicy = networkPolicy;
		this.noFade = noFade;
		this.errorResId = errorResId;
		this.errorDrawable = errorDrawable;
		this.key = key;
		this.tag = (tag != null ? tag : this);
	}

	abstract void complete(Bitmap result, Picasso.LoadedFrom from);

	abstract void error();

	void cancel() {
		cancelled = true;
	}

	Request getRequest() {
		return request;
	}

	T getTarget() {
		return target == null ? null : target.get();
	}

	String getKey() {
		return key;
	}

	boolean isCancelled() {
		return cancelled;
	}

	boolean willReplay() {
		return willReplay;
	}

	int getMemoryPolicy() {
		return memoryPolicy;
	}

	int getNetworkPolicy() {
		return networkPolicy;
	}

	Picasso getPicasso() {
		return picasso;
	}

	Priority getPriority() {
		return request.priority;
	}

	Object getTag() {
		return tag;
	}

	static class RequestWeakReference<M> extends WeakReference<M> {
		final Action action;

		public RequestWeakReference(Action action, M referent, ReferenceQueue<? super M> q) {
			super(referent, q);
			this.action = action;
		}
	}
}
