/*
 * The MIT License
 *
 * Copyright (c) 2013-2016 reark project contributors
 *
 * https://github.com/reark/reark/graphs/contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.reark.reark.data.stores.cores;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

import io.reark.reark.utils.Log;
import io.reark.reark.utils.Preconditions;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

import static io.reark.reark.utils.Preconditions.checkNotNull;

/**
 * ContentProviderStoreCoreBase implements an Observable based item store that uses a content provider as
 * its data backing store.
 *
 * All content provider operations are threaded. The store executes put operations in order, but
 * provides no guarantee for the execution order between get and put operations.
 *
 * This in an abstract class that implements the content provider access and expects extending
 * classes to implement data type specific methods.
 *
 * @param <T> Type of the data this store core contains.
 */
public abstract class ContentProviderStoreCoreBase<T> {
    private static final String TAG = ContentProviderStoreCoreBase.class.getSimpleName();

    @NonNull
    private final ContentResolver contentResolver;

    @NonNull
    private final ContentObserver contentObserver = getContentObserver();

    @NonNull
    private final PublishSubject<Pair<T, Uri>> updateSubject = PublishSubject.create();

    protected ContentProviderStoreCoreBase(@NonNull final ContentResolver contentResolver) {
        this.contentResolver = Preconditions.get(contentResolver);
        this.contentResolver.registerContentObserver(getContentUri(), true, contentObserver);

        updateSubject
                .onBackpressureBuffer()
                .observeOn(Schedulers.io())
                .subscribe(pair -> {
                    updateIfValueChanged(this, pair);
                });
    }

    private static <T> void updateIfValueChanged(ContentProviderStoreCoreBase<T> store, Pair<T, Uri> pair) {
        final Cursor cursor = store.contentResolver.query(pair.second, store.getProjection(), null, null, null);
        T newItem = pair.first;
        boolean valuesEqual = false;

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                T currentItem = store.read(cursor);
                valuesEqual = newItem.equals(currentItem);

                if (!valuesEqual) {
                    Log.v(TAG, "Merging values at " + pair.second);
                    newItem = store.mergeValues(currentItem, newItem);
                    valuesEqual = newItem.equals(currentItem);
                }
            }
            cursor.close();
        }

        if (valuesEqual) {
            Log.v(TAG, "Data already up to date at " + pair.second);
            return;
        }

        final ContentValues contentValues = store.getContentValuesForItem(newItem);

        if (store.contentResolver.update(pair.second, contentValues, null, null) == 0) {
            final Uri resultUri = store.contentResolver.insert(pair.second, contentValues);
            Log.v(TAG, "Inserted at " + resultUri);
        } else {
            Log.v(TAG, "Updated at " + pair.second);
        }
    }

    @NonNull
    protected static Handler createHandler(@NonNull final String name) {
        checkNotNull(name);

        HandlerThread handlerThread = new HandlerThread(name);
        handlerThread.start();
        return new Handler(handlerThread.getLooper());
    }

    protected void put(@NonNull final T item, @NonNull final Uri uri) {
        checkNotNull(item);
        checkNotNull(uri);

        updateSubject.onNext(new Pair<>(item, uri));
    }

    @NonNull
    protected Observable<List<T>> get(@NonNull final Uri uri) {
        checkNotNull(uri);

        return Observable.just(uri)
                .observeOn(Schedulers.io())
                .map(this::queryList);
    }

    @NonNull
    protected Observable<T> getOne(@NonNull final Uri uri) {
        return get(Preconditions.get(uri))
                .map(queryResults -> {
                    if (queryResults.isEmpty()) {
                        return null;
                    }

                    if (queryResults.size() > 1) {
                        Log.w(TAG, String.format("%s items found in a get for a single item", queryResults.size()));
                    }

                    return queryResults.get(0);
                });
    }

    @NonNull
    private List<T> queryList(@NonNull final Uri uri) {
        Cursor cursor = contentResolver.query(uri, getProjection(), null, null, null);
        List<T> list = new ArrayList<>(10);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                list.add(read(cursor));
            }
            while (cursor.moveToNext()) {
                list.add(read(cursor));
            }
            cursor.close();
        }
        if (list.isEmpty()) {
            Log.v(TAG, "Could not find with id: " + uri);
        }
        return list;
    }

    @NonNull
    protected ContentResolver getContentResolver() {
        return contentResolver;
    }

    @NonNull
    protected abstract ContentObserver getContentObserver();

    @NonNull
    protected abstract Uri getContentUri();

    @NonNull
    protected abstract String[] getProjection();

    @NonNull
    protected abstract T read(@NonNull final Cursor cursor);

    @NonNull
    protected abstract ContentValues getContentValuesForItem(@NonNull final T item);

    @NonNull
    protected T mergeValues(@NonNull final T v1, @NonNull final T v2) {
        return v2; // Default behavior is new values overriding
    }
}
