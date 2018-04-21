/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static io.servicetalk.transport.api.FlushStrategyHolder.from;
import static java.util.Objects.requireNonNull;

final class FlushOnEach implements FlushStrategy {

    static final FlushOnEach FLUSH_ON_EACH = new FlushOnEach();

    private FlushOnEach() {
        // No instances.
    }

    @Override
    public <T> FlushStrategyHolder<T> apply(Publisher<T> source, Executor executor) {
        FlushStrategyHolder.FlushSignals signals = new FlushStrategyHolder.FlushSignals();
        return from(new Publisher<T>(executor) {
            @Override
            protected void handleSubscribe(Subscriber<? super T> s) {
                source.subscribe(new FlushOnEachSubscriber<>(s, signals));
            }
        }, signals);
    }

    private static final class FlushOnEachSubscriber<T> implements Subscriber<T> {

        private final Subscriber<T> original;
        private final FlushStrategyHolder.FlushSignals signals;

        FlushOnEachSubscriber(Subscriber<T> original, FlushStrategyHolder.FlushSignals signals) {
            this.original = requireNonNull(original);
            this.signals = requireNonNull(signals);
        }

        @Override
        public void onSubscribe(Subscription s) {
            original.onSubscribe(s);
        }

        @Override
        public void onNext(T t) {
            original.onNext(t);
            signals.signalFlush();
        }

        @Override
        public void onError(Throwable t) {
            original.onError(t);
        }

        @Override
        public void onComplete() {
            original.onComplete();
        }
    }
}
