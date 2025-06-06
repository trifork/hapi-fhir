/*-
 * #%L
 * HAPI FHIR JPA - Search Parameters
 * %%
 * Copyright (C) 2014 - 2025 Smile CDR, Inc.
 * %%
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
 * #L%
 */
package ca.uhn.fhir.jpa.searchparam.retry;

import ca.uhn.fhir.system.HapiSystemProperties;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.function.Supplier;

public class Retrier<T> {
	private static final Logger ourLog = LoggerFactory.getLogger(Retrier.class);

	private final Supplier<T> mySupplier;

	private final RetryTemplate myRetryTemplate;

	public Retrier(Supplier<T> theSupplier, int theMaxRetries) {
		Validate.isTrue(theMaxRetries > 0, "maxRetries must be above zero.");
		mySupplier = theSupplier;

		myRetryTemplate = new RetryTemplate();

		ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
		backOff.setInitialInterval(500);
		backOff.setMaxInterval(DateUtils.MILLIS_PER_MINUTE);
		backOff.setMultiplier(2);
		myRetryTemplate.setBackOffPolicy(backOff);

		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy() {
			private static final long serialVersionUID = -4522467251787518700L;

			@Override
			public boolean canRetry(RetryContext context) {
				Throwable lastThrowable = context.getLastThrowable();
				if (lastThrowable instanceof BeanCreationException || lastThrowable instanceof NullPointerException) {
					return false;
				}
				return super.canRetry(context);
			}
		};
		retryPolicy.setMaxAttempts(theMaxRetries);
		myRetryTemplate.setRetryPolicy(retryPolicy);

		RetryListener listener = new RetryListener() {
			@Override
			public <TT, E extends Throwable> void onError(
					RetryContext context, RetryCallback<TT, E> callback, Throwable throwable) {
				if (throwable instanceof NullPointerException
						|| throwable instanceof UnsupportedOperationException
						|| HapiSystemProperties.isUnitTestModeEnabled()
						|| context.getRetryCount() == 1) {
					/*
					 * Log the stack trace only on the first retry in order to
					 * avoid noise in the logs (except for unit test mode, and for
					 * exceptions that indicate a bug)
					 */
					ourLog.error(
							"Retry failure {}/{}: {}",
							context.getRetryCount(),
							theMaxRetries,
							throwable.getMessage(),
							throwable);
				} else {
					ourLog.error(
							"Retry failure {}/{}: {}", context.getRetryCount(), theMaxRetries, throwable.toString());
				}
			}
		};
		myRetryTemplate.registerListener(listener);
	}

	public T runWithRetry() {
		return myRetryTemplate.execute(retryContext -> mySupplier.get());
	}
}
