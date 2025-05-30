/*-
 * #%L
 * HAPI FHIR Storage api
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
package ca.uhn.fhir.jpa.subscription.model;

import ca.uhn.fhir.rest.server.messaging.ICanNullPayload;
import ca.uhn.fhir.rest.server.messaging.json.BaseJsonMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ResourceModifiedJsonMessage extends BaseJsonMessage<ResourceModifiedMessage> implements ICanNullPayload {

	@JsonProperty("payload")
	private ResourceModifiedMessage myPayload;

	/**
	 * Constructor
	 */
	public ResourceModifiedJsonMessage() {
		super();
	}

	/**
	 * Constructor
	 */
	public ResourceModifiedJsonMessage(ResourceModifiedMessage thePayload) {
		myPayload = thePayload;
	}

	@Override
	public ResourceModifiedMessage getPayload() {
		return myPayload;
	}

	public void setPayload(ResourceModifiedMessage thePayload) {
		myPayload = thePayload;
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this).append("myPayload", myPayload).toString();
	}

	@Override
	public void setPayloadToNull() {
		if (myPayload != null) {
			myPayload.setPayloadToNull();
		}
	}
}
