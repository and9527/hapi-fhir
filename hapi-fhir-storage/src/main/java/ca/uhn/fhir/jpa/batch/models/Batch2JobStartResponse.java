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
package ca.uhn.fhir.jpa.batch.models;

public class Batch2JobStartResponse {

	/**
	 * The job instance id
	 */
	private String myInstanceId;

	/**
	 * True if an existing job is being used instead
	 * (to prevent multiples of the exact same job being
	 * requested).
	 */
	private boolean myUsesCachedResult;

	public String getInstanceId() {
		return myInstanceId;
	}

	public void setInstanceId(String theInstanceId) {
		myInstanceId = theInstanceId;
	}

	public boolean isUsesCachedResult() {
		return myUsesCachedResult;
	}

	public void setUsesCachedResult(boolean theUseCachedResult) {
		myUsesCachedResult = theUseCachedResult;
	}
}
