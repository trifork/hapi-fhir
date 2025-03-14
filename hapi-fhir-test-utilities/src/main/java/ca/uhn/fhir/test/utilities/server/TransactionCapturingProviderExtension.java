/*-
 * #%L
 * HAPI FHIR Test Utilities
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
package ca.uhn.fhir.test.utilities.server;

import ca.uhn.fhir.rest.annotation.Transaction;
import ca.uhn.fhir.rest.annotation.TransactionParam;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class TransactionCapturingProviderExtension<T extends IBaseBundle> implements BeforeEachCallback, AfterEachCallback {

	private static final Logger ourLog = LoggerFactory.getLogger(TransactionCapturingProviderExtension.class);
	private final RestfulServerExtension myRestfulServerExtension;
	private final PlainProvider myProvider = new PlainProvider();

	/**
	 * Constructor
	 */
	public TransactionCapturingProviderExtension(RestfulServerExtension theRestfulServerExtension, Class<T> theBundleType) {
		myRestfulServerExtension = theRestfulServerExtension;
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		myRestfulServerExtension.getRestfulServer().unregisterProvider(myProvider);
		myProvider.clear();
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		myRestfulServerExtension.getRestfulServer().registerProvider(myProvider);
	}

	public void waitForTransactionCount(int theCount) {
		assertThat(theCount).isGreaterThanOrEqualTo(myProvider.size());
		await().until(()->myProvider.size() == theCount);
	}

	public List<T> getTransactions() {
		return myProvider.getTransactions();
	}

	private class PlainProvider {
		private final List<T> myInputBundles = Collections.synchronizedList(new ArrayList<>());

		@Transaction
		public T transaction(@TransactionParam T theInput) {
			ourLog.info("Received transaction update");
			myInputBundles.add(theInput);
			return theInput;
		}

		public void clear() {
			myInputBundles.clear();
		}

		public int size() {
			return myInputBundles.size();
		}

		public List<T> getTransactions() {
			return Collections.unmodifiableList(myInputBundles);
		}
	}


}
