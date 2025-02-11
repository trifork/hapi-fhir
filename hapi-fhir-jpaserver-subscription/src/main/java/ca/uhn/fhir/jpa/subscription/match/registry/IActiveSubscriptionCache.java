package ca.uhn.fhir.jpa.subscription.match.registry;

import java.util.Collection;
import java.util.List;

public interface IActiveSubscriptionCache {
	ActiveSubscription get(String theIdPart);

	Collection<ActiveSubscription> getAll();

	int size();

	void put(String theSubscriptionId, ActiveSubscription theActiveSubscription);

	ActiveSubscription remove(String theSubscriptionId);

	List<String> markAllSubscriptionsNotInCollectionForDeletionAndReturnIdsToDelete(Collection<String> theAllIds);

	List<ActiveSubscription> getTopicSubscriptionsForTopic(String theTopic);

	List<ActiveSubscription> getAllNonTopicSubscriptions();
}
