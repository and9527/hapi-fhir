/*
 * #%L
 * HAPI FHIR - Client Framework
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
package ca.uhn.fhir.rest.client.method;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class NullParameter implements IParameter {

	@Override
	public void translateClientArgumentIntoQueryArgument(
			FhirContext theContext,
			Object theSourceClientArgument,
			Map<String, List<String>> theTargetQueryArguments,
			IBaseResource theTargetResource)
			throws InternalErrorException {
		// nothing
	}

	@Override
	public void initializeTypes(
			Method theMethod,
			Class<? extends Collection<?>> theOuterCollectionType,
			Class<? extends Collection<?>> theInnerCollectionType,
			Class<?> theParameterType) {
		// nothing
	}
}
