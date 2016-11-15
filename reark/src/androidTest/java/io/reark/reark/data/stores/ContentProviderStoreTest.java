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
package io.reark.reark.data.stores;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.test.ProviderTestCase2;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reark.reark.data.stores.SimpleMockContentProvider.DataColumns;
import rx.functions.Action1;
import rx.observers.TestSubscriber;

public class ContentProviderStoreTest extends ProviderTestCase2<SimpleMockContentProvider> {

    private static final String AUTHORITY = "test.authority";
    private static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);
    private static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "veggies");
    private static final String[] PROJECTION = { DataColumns.KEY, DataColumns.VALUE };

    private TestStore store;

    public ContentProviderStoreTest() {
        super(SimpleMockContentProvider.class, AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        store = new TestStore(getMockContentResolver());

        Action1<String> insert = value ->
                getProvider().insert(
                        store.getUriForId(store.getIdFor(value)),
                        store.getContentValuesForItem(value)
                );

        // Prepare the mock content provider with values
        insert.call("parsnip");
        insert.call("lettuce");
        insert.call("spinach");
    }

    public void testGetOneWithData() {
        // ARRANGE
        TestSubscriber<String> testSubscriber = new TestSubscriber<>();
        List<String> expected = Collections.singletonList("parsnip");

        // ACT
        store.getOnce(store.getIdFor("parsnip")).subscribe(testSubscriber);

        // ASSERT
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
        testSubscriber.assertReceivedOnNext(expected);
    }

    public void testGetOneWithoutData() {
        // ARRANGE
        TestSubscriber<String> testSubscriber = new TestSubscriber<>();
        List<String> expected = Collections.singletonList(null);

        // ACT
        store.getOnce(store.getIdFor("bacon")).subscribe(testSubscriber);

        // ASSERT
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
        testSubscriber.assertReceivedOnNext(expected);
    }

    public void testGetWithData() {
        // ARRANGE
        TestSubscriber<List<String>> testSubscriber = new TestSubscriber<>();
        List<List<String>> expected = Collections.singletonList(Collections.singletonList("parsnip"));

        // ACT
        store.get(store.getIdFor("parsnip")).subscribe(testSubscriber);

        // ASSERT
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
        testSubscriber.assertReceivedOnNext(expected);
    }

    public void testGetAll() {
        // ARRANGE
        TestSubscriber<List<String>> testSubscriber = new TestSubscriber<>();
        List<List<String>> expected = Collections.singletonList(Arrays.asList("parsnip", "lettuce", "spinach"));

        // ACT
        // Wildcard depends on content provider. For tests we just use 0 while on SQL backend
        // this would be an asterisk. The exact wildcard is not important for the test as we just
        // want to make sure the provider stores can return a larger listing of results.
        store.get(0).subscribe(testSubscriber);

        // ASSERT
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
        testSubscriber.assertReceivedOnNext(expected);
    }

    public void testGetOnceAndStream() {
        // ARRANGE
        TestSubscriber<String> testSubscriber = new TestSubscriber<>();
        List<String> expected = Collections.singletonList("spinach");

        // ACT
        store.getOnceAndStream(store.getIdFor("spinach")).subscribe(testSubscriber);

        // ASSERT
        testSubscriber.awaitTerminalEvent(50, TimeUnit.MILLISECONDS);
        testSubscriber.assertNotCompleted();
        testSubscriber.assertNoErrors();
        testSubscriber.assertReceivedOnNext(expected);
    }

    public void testGetEmptyStream() {
        // ARRANGE
        TestSubscriber<String> testSubscriber = new TestSubscriber<>();

        // ACT
        store.getOnceAndStream(store.getIdFor("bacon")).subscribe(testSubscriber);

        // ASSERT
        testSubscriber.awaitTerminalEvent(50, TimeUnit.MILLISECONDS);
        testSubscriber.assertNotCompleted();
        testSubscriber.assertNoErrors();
        testSubscriber.assertNoValues();
    }

    /**
     * A simple store containing String values tracked with Integer keys.
     */
    public static class TestStore extends ContentProviderStore<String, Integer> {

        public TestStore(@NonNull final ContentResolver contentResolver) {
            super(contentResolver);
        }

        @NonNull
        @Override
        public Uri getUriForId(@NonNull final Integer id) {
            return Uri.withAppendedPath(getContentUri(), String.valueOf(id));
        }

        @NonNull
        @Override
        protected Integer getIdFor(@NonNull final String item) {
            return item.hashCode();
        }

        @NonNull
        @Override
        public Uri getContentUri() {
            return CONTENT_URI;
        }

        @NonNull
        @Override
        protected String[] getProjection() {
            return PROJECTION;
        }

        @NonNull
        @Override
        protected String read(@NonNull final Cursor cursor) {
            return cursor.getString(cursor.getColumnIndex(DataColumns.VALUE));
        }

        @NonNull
        @Override
        protected ContentValues getContentValuesForItem(@NonNull final String item) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataColumns.KEY, getIdFor(item));
            contentValues.put(DataColumns.VALUE, item);
            return contentValues;
        }
    }
}
