/*
 * The MIT License (MIT)
 * Copyright (c) 2018 Microsoft Corporation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.microsoft.azure.cosmosdb.internal.directconnectivity;

import com.microsoft.azure.cosmosdb.BridgeInternal;
import com.microsoft.azure.cosmosdb.ConnectionPolicy;
import com.microsoft.azure.cosmosdb.DatabaseAccount;
import com.microsoft.azure.cosmosdb.internal.BaseAuthorizationTokenProvider;
import com.microsoft.azure.cosmosdb.rx.AsyncDocumentClient;
import com.microsoft.azure.cosmosdb.rx.AsyncDocumentClient.Builder;
import com.microsoft.azure.cosmosdb.rx.TestConfigurations;
import com.microsoft.azure.cosmosdb.rx.TestSuiteBase;
import com.microsoft.azure.cosmosdb.rx.internal.SpyClientUnderTestFactory;
import com.microsoft.azure.cosmosdb.rx.internal.SpyClientUnderTestFactory.ClientUnderTest;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.commons.io.IOUtils;
import org.mockito.Mockito;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import reactor.netty.http.client.HttpClient;
import rx.Observable;
import rx.Single;
import rx.observers.TestSubscriber;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayServiceConfigurationReaderTest extends TestSuiteBase {

    private static final int TIMEOUT = 8000;
    private HttpClient mockHttpClient;
    private HttpClient httpClient;
    private BaseAuthorizationTokenProvider baseAuthorizationTokenProvider;
    private ConnectionPolicy connectionPolicy;
    private GatewayServiceConfigurationReader mockGatewayServiceConfigurationReader;
    private GatewayServiceConfigurationReader gatewayServiceConfigurationReader;
    private AsyncDocumentClient client;
    private String databaseAccountJson;
    private DatabaseAccount expectedDatabaseAccount;

    @Factory(dataProvider = "clientBuilders")
    public GatewayServiceConfigurationReaderTest(Builder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    @BeforeClass(groups = "simple")
    public void setup() throws Exception {
        client = clientBuilder.build();
        mockHttpClient = Mockito.mock(HttpClient.class);

        ClientUnderTest clientUnderTest = SpyClientUnderTestFactory.createClientUnderTest(this.clientBuilder);
        httpClient = clientUnderTest.getSpyHttpClient();
        baseAuthorizationTokenProvider = new BaseAuthorizationTokenProvider(TestConfigurations.MASTER_KEY);
        connectionPolicy = ConnectionPolicy.GetDefault();
        mockGatewayServiceConfigurationReader = new GatewayServiceConfigurationReader(new URI(TestConfigurations.HOST),
                false, TestConfigurations.MASTER_KEY, connectionPolicy, baseAuthorizationTokenProvider, mockHttpClient);

        gatewayServiceConfigurationReader = new GatewayServiceConfigurationReader(new URI(TestConfigurations.HOST),
                                                                                  false,
                                                                                  TestConfigurations.MASTER_KEY,
                                                                                  connectionPolicy,
                                                                                  baseAuthorizationTokenProvider,
                                                                                  httpClient);
        databaseAccountJson = IOUtils
                .toString(getClass().getClassLoader().getResourceAsStream("databaseAccount.json"), "UTF-8");
        expectedDatabaseAccount = new DatabaseAccount(databaseAccountJson);
    }

    @AfterClass(groups = { "simple" }, timeOut = SHUTDOWN_TIMEOUT, alwaysRun = true)
    public void afterClass() {
        safeClose(client);
    }

    @Test(groups = "simple")
    public void mockInitializeReaderAsync() throws Exception {

        HttpClient.ResponseReceiver mockedResponse = getMockResponse(databaseAccountJson);

        Mockito.when(mockHttpClient.get())
                .thenReturn(mockedResponse);

        Single<DatabaseAccount> databaseAccount = mockGatewayServiceConfigurationReader.initializeReaderAsync();
        validateSuccess(databaseAccount, expectedDatabaseAccount);
    }

    @Test(groups = "simple")
    public void mockInitializeReaderAsyncWithResourceToken() throws Exception {
        mockGatewayServiceConfigurationReader = new GatewayServiceConfigurationReader(new URI(TestConfigurations.HOST),
                true, "SampleResourceToken", connectionPolicy, baseAuthorizationTokenProvider, mockHttpClient);

        HttpClient.ResponseReceiver mockedResponse = getMockResponse(databaseAccountJson);

        Mockito.when(mockHttpClient.get())
                .thenReturn(mockedResponse);

        Single<DatabaseAccount> databaseAccount = mockGatewayServiceConfigurationReader.initializeReaderAsync();
        validateSuccess(databaseAccount, expectedDatabaseAccount);
    }

    @Test(groups = "simple")
    public void initializeReaderAsync() {
        Single<DatabaseAccount> databaseAccount = gatewayServiceConfigurationReader.initializeReaderAsync();
        validateSuccess(databaseAccount);
    }

    public static void validateSuccess(Single<DatabaseAccount> observable) {
        TestSubscriber<DatabaseAccount> testSubscriber = new TestSubscriber<DatabaseAccount>();

        observable.subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent(TIMEOUT, TimeUnit.MILLISECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
        testSubscriber.assertValueCount(1);
        assertThat(BridgeInternal.getQueryEngineConfiuration(testSubscriber.getOnNextEvents().get(0)).size() > 0).isTrue();
        assertThat(BridgeInternal.getReplicationPolicy(testSubscriber.getOnNextEvents().get(0))).isNotNull();
        assertThat(BridgeInternal.getSystemReplicationPolicy(testSubscriber.getOnNextEvents().get(0))).isNotNull();
    }

    public static void validateSuccess(Single<DatabaseAccount> observable, DatabaseAccount expectedDatabaseAccount)
            throws InterruptedException {
        TestSubscriber<DatabaseAccount> testSubscriber = new TestSubscriber<DatabaseAccount>();

        observable.subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent(TIMEOUT, TimeUnit.MILLISECONDS);
        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();
        testSubscriber.assertValueCount(1);
        assertThat(testSubscriber.getOnNextEvents().get(0).getId()).isEqualTo(expectedDatabaseAccount.getId());
        assertThat(testSubscriber.getOnNextEvents().get(0).getAddressesLink())
                .isEqualTo(expectedDatabaseAccount.getAddressesLink());
        assertThat(testSubscriber.getOnNextEvents().get(0).getWritableLocations().iterator().next().getEndpoint())
                .isEqualTo(expectedDatabaseAccount.getWritableLocations().iterator().next().getEndpoint());
        assertThat(BridgeInternal.getSystemReplicationPolicy(testSubscriber.getOnNextEvents().get(0)).getMaxReplicaSetSize())
                .isEqualTo(BridgeInternal.getSystemReplicationPolicy(expectedDatabaseAccount).getMaxReplicaSetSize());
        assertThat(BridgeInternal.getSystemReplicationPolicy(testSubscriber.getOnNextEvents().get(0)).getMaxReplicaSetSize())
                .isEqualTo(BridgeInternal.getSystemReplicationPolicy(expectedDatabaseAccount).getMaxReplicaSetSize());
        assertThat(BridgeInternal.getQueryEngineConfiuration(testSubscriber.getOnNextEvents().get(0)))
                .isEqualTo(BridgeInternal.getQueryEngineConfiuration(expectedDatabaseAccount));
    }

    private HttpClient.ResponseReceiver<?> getMockResponse(String databaseAccountJson) {
        HttpClient.ResponseReceiver<?> resp = Mockito.mock(HttpClient.ResponseReceiver.class);
        Mockito.doReturn(HttpResponseStatus.valueOf(200)).when(resp.response().block().status());
        Mockito.doReturn(Observable.just(ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, databaseAccountJson)))
                .when(resp).responseContent();

        DefaultHttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(200), EmptyHttpHeaders.INSTANCE);

        try {
            HttpHeaders httpResponseHeaders = EmptyHttpHeaders.INSTANCE;
            Mockito.doReturn(httpResponseHeaders).when(httpResponse).status();

        } catch (IllegalArgumentException | SecurityException e) {
            throw new IllegalStateException("Failed to instantiate class object.", e);
        }
        return resp;
    }
}
