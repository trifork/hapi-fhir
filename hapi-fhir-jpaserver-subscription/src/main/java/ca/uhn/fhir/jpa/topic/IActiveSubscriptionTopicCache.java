package ca.uhn.fhir.jpa.topic;

import org.hl7.fhir.r5.model.SubscriptionTopic;

import java.util.Collection;
import java.util.Set;

public interface IActiveSubscriptionTopicCache {
	int size();

	boolean add(SubscriptionTopic theSubscriptionTopic);

	int removeIdsNotInCollection(Set<String> theIdsToRetain);

	Collection<SubscriptionTopic> getAll();

	void remove(String theSubscriptionTopicId);
}
