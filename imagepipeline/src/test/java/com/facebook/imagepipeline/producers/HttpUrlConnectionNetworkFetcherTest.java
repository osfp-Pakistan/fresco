/*
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.imagepipeline.producers;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.Queue;

import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ HttpUrlConnectionNetworkFetcher.class })
public class HttpUrlConnectionNetworkFetcherTest {

  public static final String INITIAL_TEST_URL = "http://localhost/";
  public static final String HTTPS_URL = "https://localhost/";

  @Mock private FetchState mMockFetchState;
  @Mock private ProducerContext mMockProducerContext;
  @Mock private NetworkFetcher.Callback mMockCallback;

  private HttpUrlConnectionNetworkFetcher mFetcher;
  private Queue<HttpURLConnection> mConnectionsQueue;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    mFetcher = new HttpUrlConnectionNetworkFetcher();
    mConnectionsQueue = new LinkedList<>();
    mockUrlConnections();

    when(mMockFetchState.getContext()).thenReturn(mMockProducerContext);
    when(mMockFetchState.getUri()).thenReturn(Uri.parse(INITIAL_TEST_URL));
  }

  private void mockUrlConnections() throws Exception {
    URL mockUrl = PowerMockito.mock(URL.class);
    PowerMockito.whenNew(URL.class).withAnyArguments().thenReturn(mockUrl);

    PowerMockito.when(mockUrl.openConnection()).then(new Answer<URLConnection>() {
      @Override
      public URLConnection answer(InvocationOnMock invocation) throws Throwable {
        return mConnectionsQueue.poll();
      }
    });
  }

  @Test
  public void testFetchSendsSuccessToCallback() throws IOException {
    InputStream mockInputStream = mock(InputStream.class);
    HttpURLConnection mockConnection = mockSuccessWithStream(mockInputStream);

    runFetch();

    InOrder inOrder = inOrder(mMockCallback, mockConnection);
    inOrder.verify(mockConnection).getInputStream();
    inOrder.verify(mMockCallback).onResponse(mockInputStream, -1);
    inOrder.verify(mockConnection).disconnect();

    verifyNoMoreInteractions(mMockCallback);
  }

  @Test
  public void testFetchSendsErrorToCallbackAfterHttpError() throws IOException {
    HttpURLConnection mockResponse = mockFailure();

    runFetch();

    verify(mMockCallback).onFailure(any(IOException.class));
    verify(mockResponse).disconnect();

    verifyNoMoreInteractions(mMockCallback);
  }

  @Test
  public void testFetchSendsSuccessToCallbackAfterRedirect() throws IOException {
    HttpURLConnection mockRedirect = mockRedirectTo(HTTPS_URL);

    InputStream mockInputStream = mock(InputStream.class);
    HttpURLConnection mockRedirectedConnection = mockSuccessWithStream(mockInputStream);

    runFetch();

    verify(mockRedirect).disconnect();

    InOrder inOrder = inOrder(mMockCallback, mockRedirectedConnection);
    inOrder.verify(mMockCallback).onResponse(mockInputStream, -1);
    inOrder.verify(mockRedirectedConnection).disconnect();

    verifyNoMoreInteractions(mMockCallback);
  }

  @Test
  public void testFetchSendsErrorToCallbackAfterRedirectToSameLocation() throws IOException {
    HttpURLConnection mockRedirect = mockRedirectTo(INITIAL_TEST_URL);
    HttpURLConnection mockSuccess = mockSuccess();

    runFetch();

    verify(mMockCallback).onFailure(any(IOException.class));
    verify(mockRedirect).disconnect();
    verifyZeroInteractions(mockSuccess);

    verifyNoMoreInteractions(mMockCallback);
  }

  @Test
  public void testFetchSendsErrorToCallbackAfterTooManyRedirects() throws IOException {
    mockRedirectTo(HTTPS_URL);
    mockRedirectTo(INITIAL_TEST_URL);
    mockRedirectTo(HTTPS_URL);
    mockRedirectTo(INITIAL_TEST_URL);
    mockRedirectTo(HTTPS_URL);
    mockRedirectTo(INITIAL_TEST_URL);
    HttpURLConnection mockResponseAfterSixRedirects = mockSuccess();

    runFetch();

    verify(mMockCallback).onFailure(any(IOException.class));
    verifyZeroInteractions(mockResponseAfterSixRedirects);

    verifyNoMoreInteractions(mMockCallback);
  }

  private HttpURLConnection mockSuccess() throws IOException {
    return mockSuccessWithStream(mock(InputStream.class));
  }

  private HttpURLConnection mockSuccessWithStream(InputStream is) throws IOException {
    HttpURLConnection mockResponse = mock(HttpURLConnection.class);
    when(mockResponse.getResponseCode()).thenReturn(200);
    when(mockResponse.getInputStream()).thenReturn(is);

    queueConnection(mockResponse);

    return mockResponse;
  }

  private HttpURLConnection mockFailure() throws IOException {
    HttpURLConnection mockResponse = mock(HttpURLConnection.class);
    when(mockResponse.getResponseCode()).thenReturn(404);

    queueConnection(mockResponse);

    return mockResponse;
  }

  private HttpURLConnection mockRedirectTo(String redirectUrl) throws IOException {
    HttpURLConnection mockResponse = mock(HttpURLConnection.class);
    when(mockResponse.getResponseCode()).thenReturn(301);
    when(mockResponse.getHeaderField("Location")).thenReturn(redirectUrl);

    queueConnection(mockResponse);

    return mockResponse;
  }

  private void queueConnection(HttpURLConnection connection) {
    mConnectionsQueue.add(connection);
  }

  private void runFetch() {
    mFetcher.fetchSync(mMockFetchState, mMockCallback);
  }

}