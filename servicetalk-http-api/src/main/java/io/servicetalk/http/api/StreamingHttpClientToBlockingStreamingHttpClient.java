/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.api;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static io.servicetalk.http.api.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.RequestResponseFactories.toBlockingStreaming;
import static io.servicetalk.http.api.StreamingHttpConnectionToBlockingStreamingHttpConnection.DEFAULT_BLOCKING_STREAMING_CONNECTION_STRATEGY;
import static java.util.Objects.requireNonNull;

final class StreamingHttpClientToBlockingStreamingHttpClient implements BlockingStreamingHttpClient {
    private final StreamingHttpClient client;
    private final HttpExecutionStrategy strategy;
    private final HttpExecutionContext context;
    private final BlockingStreamingHttpRequestResponseFactory reqRespFactory;

    StreamingHttpClientToBlockingStreamingHttpClient(final StreamingHttpClient client,
                                                     final HttpExecutionStrategyInfluencer influencer) {
        strategy = influencer.influenceStrategy(DEFAULT_BLOCKING_STREAMING_CONNECTION_STRATEGY);
        this.client = client;
        context = new DelegatingHttpExecutionContext(client.executionContext()) {
            @Override
            public HttpExecutionStrategy executionStrategy() {
                return strategy;
            }
        };
        reqRespFactory = toBlockingStreaming(client);
    }

    @Override
    public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) throws Exception {
        return request(strategy, request);
    }

    @Override
    public ReservedBlockingStreamingHttpConnection reserveConnection(final HttpRequestMetaData metaData)
            throws Exception {
        return reserveConnection(strategy, metaData);
    }

    @Override
    public ReservedBlockingStreamingHttpConnection reserveConnection(final HttpExecutionStrategy strategy,
                                                                     final HttpRequestMetaData metaData)
            throws Exception {
        // It is assumed that users will always apply timeouts at the StreamingHttpService layer (e.g. via filter).
        // So we don't apply any explicit timeout here and just wait forever.
        return new ReservedStreamingHttpConnectionToBlockingStreaming(
                blockingInvocation(client.reserveConnection(strategy, metaData)), this.strategy, reqRespFactory);
    }

    @Override
    public StreamingHttpClient asStreamingClient() {
        return client;
    }

    @Override
    public BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                 final BlockingStreamingHttpRequest request) throws Exception {
        return blockingInvocation(client.request(strategy, request.toStreamingRequest())).toBlockingStreamingResponse();
    }

    @Override
    public HttpExecutionContext executionContext() {
        return context;
    }

    @Override
    public BlockingStreamingHttpResponseFactory httpResponseFactory() {
        return reqRespFactory;
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    @Override
    public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return reqRespFactory.newRequest(method, requestTarget);
    }

    static final class ReservedStreamingHttpConnectionToBlockingStreaming implements
                                                                              ReservedBlockingStreamingHttpConnection {
        private final ReservedStreamingHttpConnection connection;
        private final HttpExecutionStrategy strategy;
        private final ConnectionContext context;
        private final HttpExecutionContext executionContext;
        private final BlockingStreamingHttpRequestResponseFactory reqRespFactory;

        ReservedStreamingHttpConnectionToBlockingStreaming(ReservedStreamingHttpConnection connection,
                                                           final HttpExecutionStrategyInfluencer influencer) {
            this(connection, influencer.influenceStrategy(DEFAULT_BLOCKING_STREAMING_CONNECTION_STRATEGY),
                    toBlockingStreaming(connection));
        }

        ReservedStreamingHttpConnectionToBlockingStreaming(ReservedStreamingHttpConnection connection,
                                                           HttpExecutionStrategy strategy,
                                                           BlockingStreamingHttpRequestResponseFactory reqRespFactory) {
            this.connection = requireNonNull(connection);
            this.strategy = strategy;
            ConnectionContext originalCtx = connection.connectionContext();
            executionContext = new DelegatingHttpExecutionContext(connection.executionContext()) {
                @Override
                public HttpExecutionStrategy executionStrategy() {
                    return strategy;
                }
            };
            context = new DelegatingConnectionContext(originalCtx) {
                @Override
                public ExecutionContext executionContext() {
                    return executionContext;
                }
            };
            this.reqRespFactory = reqRespFactory;
        }

        @Override
        public void release() throws Exception {
            blockingInvocation(connection.releaseAsync());
        }

        @Override
        public ReservedStreamingHttpConnection asStreamingConnection() {
            return connection;
        }

        @Override
        public BlockingStreamingHttpResponse request(final BlockingStreamingHttpRequest request) throws Exception {
            return request(strategy, request);
        }

        @Override
        public ConnectionContext connectionContext() {
            return context;
        }

        @Override
        public <T> BlockingIterable<? extends T> transportEventIterable(final HttpEventKey<T> eventKey) {
            return connection.transportEventStream(eventKey).toIterable();
        }

        @Override
        public BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                     final BlockingStreamingHttpRequest request) throws Exception {
            return blockingInvocation(connection.request(strategy, request.toStreamingRequest()))
                    .toBlockingStreamingResponse();
        }

        @Override
        public HttpExecutionContext executionContext() {
            return executionContext;
        }

        @Override
        public BlockingStreamingHttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
        }

        @Override
        public void close() throws Exception {
            connection.close();
        }

        @Override
        public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }
    }
}
