package ca.uhn.fhir.jpa.subscription.message;

import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.subscription.BaseSubscriptionsR4Test;
import ca.uhn.fhir.jpa.subscription.channel.api.ChannelConsumerSettings;
import ca.uhn.fhir.jpa.subscription.channel.api.IChannelReceiver;
import ca.uhn.fhir.jpa.subscription.channel.subscription.SubscriptionChannelFactory;
import ca.uhn.fhir.jpa.subscription.model.ResourceModifiedJsonMessage;
import ca.uhn.fhir.jpa.subscription.model.ResourceModifiedMessage;
import ca.uhn.fhir.jpa.test.util.StoppableSubscriptionDeliveringRestHookSubscriber;
import ca.uhn.fhir.rest.client.api.Header;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.AdditionalRequestHeadersInterceptor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ca.uhn.fhir.jpa.model.util.JpaConstants.HEADER_META_SNAPSHOT_MODE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Test the rest-hook subscriptions
 */
public class MessageSubscriptionR4Test extends BaseSubscriptionsR4Test {
	@Autowired
	private SubscriptionChannelFactory myChannelFactory ;
	private static final Logger ourLog = LoggerFactory.getLogger(MessageSubscriptionR4Test.class);
	private TestQueueConsumerHandler<ResourceModifiedJsonMessage> handler;

	@Autowired
	StoppableSubscriptionDeliveringRestHookSubscriber myStoppableSubscriptionDeliveringRestHookSubscriber;

	@AfterEach
	public void cleanupStoppableSubscriptionDeliveringRestHookSubscriber() {
		myStoppableSubscriptionDeliveringRestHookSubscriber.setCountDownLatch(null);
		myStoppableSubscriptionDeliveringRestHookSubscriber.unPause();
		myStorageSettings.setTriggerSubscriptionsForNonVersioningChanges(new JpaStorageSettings().isTriggerSubscriptionsForNonVersioningChanges());
		myStorageSettings.setTagStorageMode(new JpaStorageSettings().getTagStorageMode());
	}

	@BeforeEach
	public void beforeRegisterRestHookListener() {
		mySubscriptionTestUtil.registerMessageInterceptor();

		IChannelReceiver receiver = myChannelFactory.newMatchingReceivingChannel("my-queue-name", new ChannelConsumerSettings());
		handler = new TestQueueConsumerHandler();
		receiver.subscribe(handler);
	}

	private Subscription createSubscriptionWithCriteria(String theCriteria) {
		Subscription subscription = new Subscription();
		subscription.setReason("Monitor new neonatal function (note, age will be determined by the monitor)");
		subscription.setStatus(Subscription.SubscriptionStatus.REQUESTED);
		subscription.setCriteria(theCriteria);

		Subscription.SubscriptionChannelComponent channel = subscription.getChannel();
		channel.setType(Subscription.SubscriptionChannelType.MESSAGE);
		channel.setPayload("application/fhir+json");
		channel.setEndpoint("channel:my-queue-name");

		subscription.setChannel(channel);
		postOrPutSubscription(subscription);
		return subscription;
	}

	private static Stream<Arguments> sourceTypes() {
		return Stream.of(
			Arguments.of(JpaStorageSettings.StoreMetaSourceInformationEnum.SOURCE_URI_AND_REQUEST_ID, "explicit-source", null, "explicit-source"),
			Arguments.of(JpaStorageSettings.StoreMetaSourceInformationEnum.REQUEST_ID, null, null, null),
			Arguments.of(JpaStorageSettings.StoreMetaSourceInformationEnum.SOURCE_URI, "explicit-source", "request-id", "explicit-source"),
			Arguments.of(JpaStorageSettings.StoreMetaSourceInformationEnum.SOURCE_URI_AND_REQUEST_ID, "explicit-source", "request-id", "explicit-source#request-id"),
			Arguments.of(JpaStorageSettings.StoreMetaSourceInformationEnum.SOURCE_URI, "explicit-source", null, "explicit-source"),
			Arguments.of(JpaStorageSettings.StoreMetaSourceInformationEnum.SOURCE_URI_AND_REQUEST_ID, null, "request-id", "#request-id"),
			Arguments.of(JpaStorageSettings.StoreMetaSourceInformationEnum.REQUEST_ID, "explicit-source", "request-id", "#request-id")
		);
	}

	@ParameterizedTest
	@MethodSource("sourceTypes")
	public void testCreateUpdateAndPatchRetainCorrectSourceThroughDelivery(JpaStorageSettings.StoreMetaSourceInformationEnum theStorageStyle, String theExplicitSource, String theRequestId, String theExpectedSourceValue) throws Exception {
		myStorageSettings.setStoreMetaSourceInformation(theStorageStyle);
		createSubscriptionWithCriteria("[Observation]");

		waitForActivatedSubscriptionCount(1);

		Observation obs = sendObservation("zoop", "SNOMED-CT", theExplicitSource, theRequestId);

		//Quick validation source stored.
		Observation readObs = myObservationDao.read(obs.getIdElement().toUnqualifiedVersionless());
		assertThat(readObs.getMeta().getSource(), is(equalTo(theExpectedSourceValue)));

		// Should see 1 subscription notification
		waitForQueueToDrain();

		//Should receive at our queue receiver
		IBaseResource resource = fetchSingleResourceFromSubscriptionTerminalEndpoint();
		assertThat(resource, instanceOf(Observation.class));
		Observation receivedObs = (Observation) resource;
		assertThat(receivedObs.getMeta().getSource(), is(equalTo(theExpectedSourceValue)));
	}


	private static Stream<Arguments> metaTagsSource(){
		List<Header> snapshotModeHeader = asList(new Header(HEADER_META_SNAPSHOT_MODE, "TAG"));

		return Stream.of(
				Arguments.of(asList("tag-1","tag-2"), asList("tag-3"), asList("tag-1","tag-2","tag-3"), emptyList()),
				Arguments.of(asList("tag-1","tag-2"), asList("tag-1","tag-2","tag-3"), asList("tag-1","tag-2","tag-3"), emptyList()),
				Arguments.of(emptyList(), asList("tag-1","tag-2"), asList("tag-1","tag-2"), emptyList()),
//				Arguments.of(asList("tag-1","tag-2"), emptyList(), asList("tag-1","tag-2"), emptyList()), // will not trigger an update since tags are merged
				Arguments.of(asList("tag-1","tag-2"), emptyList(), emptyList(), snapshotModeHeader),
				Arguments.of(asList("tag-1","tag-2"), asList("tag-3"), asList("tag-3"), snapshotModeHeader),
				Arguments.of(asList("tag-1","tag-2","tag-3"), asList("tag-1","tag-2"), asList("tag-1","tag-2"), snapshotModeHeader),
				Arguments.of(asList("tag-1","tag-2","tag-3"), asList("tag-2","tag-3"), asList("tag-2","tag-3"), snapshotModeHeader),
				Arguments.of(asList("tag-1","tag-2","tag-3"), asList("tag-1","tag-3"), asList("tag-1","tag-3"), snapshotModeHeader)
				);
	}
	@ParameterizedTest
	@MethodSource("metaTagsSource")
	public void testUpdateResource_withHeaderSnapshotMode_willRetainCorrectMetaTagsThroughDelivery(List<String> theTagsForCreate, List<String> theTagsForUpdate, List<String> theExpectedTags, List<Header> theHeaders) throws Exception {
		myStorageSettings.setTagStorageMode(JpaStorageSettings.TagStorageModeEnum.NON_VERSIONED);
		createSubscriptionWithCriteria("[Patient]");

		waitForActivatedSubscriptionCount(1);

		Patient patient = new Patient();
		patient.setActive(true);
		patient.getMeta().setTag(toSimpleCodingList(theTagsForCreate));

		IIdType id = myClient.create().resource(patient).execute().getId();

		waitForQueueToDrain();

		Patient receivedPatient = fetchSingleResourceFromSubscriptionTerminalEndpoint();
		assertThat(receivedPatient.getMeta().getTag(), hasSize(theTagsForCreate.size()));

		patient = new Patient();
		patient.setId(id);
		patient.setActive(true);
		patient.getMeta().setTag(toSimpleCodingList(theTagsForUpdate));

		maybeAddHeaderInterceptor(myClient, theHeaders);

		myClient.update().resource(patient).execute();

		waitForQueueToDrain();

		receivedPatient = fetchSingleResourceFromSubscriptionTerminalEndpoint();;

		ourLog.info(getFhirContext().newJsonParser().setPrettyPrint(true).encodeResourceToString(receivedPatient));

		List<String> receivedTagList = toSimpleTagList(receivedPatient.getMeta().getTag());
		assertThat(receivedTagList, containsInAnyOrder(theExpectedTags.toArray()));

	}

	private void maybeAddHeaderInterceptor(IGenericClient theClient, List<Header> theHeaders) {
		if(theHeaders.isEmpty()){
			return;
		}

		AdditionalRequestHeadersInterceptor additionalRequestHeadersInterceptor = new AdditionalRequestHeadersInterceptor();

		theHeaders.forEach(aHeader ->
			additionalRequestHeadersInterceptor
				.addHeaderValue(
					aHeader.getName(),
					aHeader.getValue()
				)
		);
		theClient.registerInterceptor(additionalRequestHeadersInterceptor);
	}

	private List<Coding> toSimpleCodingList(List<String> theTags) {
		return theTags.stream().map(theString -> new Coding().setCode(theString)).collect(Collectors.toList());
	}

	private List<String> toSimpleTagList(List<Coding> theTags) {
		return theTags.stream().map(t -> t.getCode()).collect(Collectors.toList());
	}

	private static Coding toSimpleCode(String theCode){
		return new Coding().setCode(theCode);
	}

	private <T> T fetchSingleResourceFromSubscriptionTerminalEndpoint() {
		assertThat(handler.getMessages().size(), is(equalTo(1)));
		ResourceModifiedJsonMessage resourceModifiedJsonMessage = handler.getMessages().get(0);
		ResourceModifiedMessage payload = resourceModifiedJsonMessage.getPayload();
		String payloadString = payload.getPayloadString();
		IBaseResource resource = myFhirContext.newJsonParser().parseResource(payloadString);
		handler.clearMessages();
		return (T) resource;
	}

}