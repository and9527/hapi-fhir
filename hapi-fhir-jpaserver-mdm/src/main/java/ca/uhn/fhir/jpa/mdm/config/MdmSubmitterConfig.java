/*-
 * #%L
 * HAPI FHIR JPA Server - Master Data Management
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
package ca.uhn.fhir.jpa.mdm.config;

import ca.uhn.fhir.broker.api.IBrokerClient;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.IInterceptorBroadcaster;
import ca.uhn.fhir.jpa.mdm.interceptor.MdmSubmitterInterceptorLoader;
import ca.uhn.fhir.mdm.api.IMdmChannelSubmitterSvc;
import ca.uhn.fhir.mdm.api.IMdmSubmitSvc;
import ca.uhn.fhir.mdm.svc.MdmChannelSubmitterSvcImpl;
import ca.uhn.fhir.mdm.svc.MdmSearchParamSvc;
import ca.uhn.fhir.mdm.svc.MdmSubmitSvcImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

@Configuration
@Import(MdmCommonConfig.class)
public class MdmSubmitterConfig {

	@Bean
	MdmSubmitterInterceptorLoader mdmSubmitterInterceptorLoader() {
		return new MdmSubmitterInterceptorLoader();
	}

	@Bean
	MdmSearchParamSvc mdmSearchParamSvc() {
		return new MdmSearchParamSvc();
	}

	@Bean
	@Lazy
	IMdmChannelSubmitterSvc mdmChannelSubmitterSvc(
			FhirContext theFhirContext,
			IBrokerClient theBrokerClient,
			IInterceptorBroadcaster theInterceptorBroadcaster) {
		return new MdmChannelSubmitterSvcImpl(theFhirContext, theBrokerClient, theInterceptorBroadcaster);
	}

	@Bean
	IMdmSubmitSvc mdmSubmitService() {
		return new MdmSubmitSvcImpl();
	}
}
