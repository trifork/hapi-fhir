/*-
 * #%L
 * HAPI FHIR JPA Server Test Utilities
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
package ca.uhn.fhir.jpa.test.config;

import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.time.Duration;

import static java.time.temporal.ChronoUnit.SECONDS;

public class TestElasticsearchContainerHelper {


	public static final String ELASTICSEARCH_VERSION = "7.17.3";
	public static final String ELASTICSEARCH_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:" + ELASTICSEARCH_VERSION;

	public static ElasticsearchContainer getEmbeddedElasticSearch() {

		return new ElasticsearchContainer(ELASTICSEARCH_IMAGE)
			// the default is 4GB which is too much for our little tests
			.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
			// turn off security warnings
			.withEnv("xpack.security.enabled", "false")
			// turn off machine learning (we don't need it in tests anyways)
			.withEnv("xpack.ml.enabled", "false")
			.withStartupTimeout(Duration.of(300, SECONDS));
	}

}
