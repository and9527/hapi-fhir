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
package ca.uhn.fhir.jpa.test.util;

import ca.uhn.fhir.jpa.subscription.match.deliver.resthook.SubscriptionDeliveringRestHookListener;
import ca.uhn.fhir.jpa.subscription.model.ResourceDeliveryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class StoppableSubscriptionDeliveringRestHookListener extends SubscriptionDeliveringRestHookListener {
	private static final Logger ourLog = LoggerFactory.getLogger(StoppableSubscriptionDeliveringRestHookListener.class);

	private boolean myPauseEveryMessage = false;
	private CountDownLatch myCountDownLatch;

	@Override
	public void handleMessage(ResourceDeliveryMessage theMessage) {
		if (myCountDownLatch != null) {
			myCountDownLatch.countDown();
		}
		if (myPauseEveryMessage) {
			waitIfPaused();
		}
		super.handleMessage(theMessage);
	}

	private synchronized void waitIfPaused() {
		try {
			if (myPauseEveryMessage) {
				wait();
			}
		} catch (InterruptedException theE) {
			ourLog.error("interrupted", theE);
		}
	}

	public void pause() {
		myPauseEveryMessage = true;
	}

	public synchronized void resume() {
		myPauseEveryMessage = false;
		notifyAll();
	}

	public void setCountDownLatch(CountDownLatch theCountDownLatch) {
		myCountDownLatch = theCountDownLatch;
	}
}
