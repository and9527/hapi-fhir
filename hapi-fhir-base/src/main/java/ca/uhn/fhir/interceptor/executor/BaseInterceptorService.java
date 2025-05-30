/*-
 * #%L
 * HAPI FHIR - Core Library
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
package ca.uhn.fhir.interceptor.executor;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.HookParams;
import ca.uhn.fhir.interceptor.api.IBaseInterceptorBroadcaster;
import ca.uhn.fhir.interceptor.api.IBaseInterceptorService;
import ca.uhn.fhir.interceptor.api.IPointcut;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.util.ReflectionUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

public abstract class BaseInterceptorService<POINTCUT extends Enum<POINTCUT> & IPointcut>
		implements IBaseInterceptorService<POINTCUT>, IBaseInterceptorBroadcaster<POINTCUT> {
	private static final Logger ourLog = LoggerFactory.getLogger(BaseInterceptorService.class);
	private static final AttributeKey<String> OTEL_INTERCEPTOR_POINTCUT_NAME_ATT_KEY =
			AttributeKey.stringKey("hapifhir.interceptor.pointcut_name");
	private static final AttributeKey<String> OTEL_INTERCEPTOR_CLASS_NAME_ATT_KEY =
			AttributeKey.stringKey("hapifhir.interceptor.class_name");
	private static final AttributeKey<String> OTEL_INTERCEPTOR_METHOD_NAME_ATT_KEY =
			AttributeKey.stringKey("hapifhir.interceptor.method_name");

	private final List<Object> myInterceptors = new ArrayList<>();
	private final ListMultimap<POINTCUT, IInvoker> myGlobalInvokers = ArrayListMultimap.create();
	private final ListMultimap<POINTCUT, IInvoker> myAnonymousInvokers = ArrayListMultimap.create();
	private final Object myRegistryMutex = new Object();
	private final Class<POINTCUT> myPointcutType;
	private volatile EnumSet<POINTCUT> myRegisteredPointcuts;
	private boolean myWarnOnInterceptorWithNoHooks = true;

	/**
	 * Constructor which uses a default name of "default"
	 */
	public BaseInterceptorService(Class<POINTCUT> thePointcutType) {
		this(thePointcutType, "default");
	}

	/**
	 * Constructor
	 *
	 * @param theName The name for this registry (useful for troubleshooting)
	 * @deprecated The name parameter is not used for anything
	 */
	@Deprecated(since = "8.0.0", forRemoval = true)
	public BaseInterceptorService(Class<POINTCUT> thePointcutType, String theName) {
		super();
		myPointcutType = thePointcutType;
		rebuildRegisteredPointcutSet();
	}

	/**
	 * Should a warning be issued if an interceptor is registered and it has no hooks
	 */
	public void setWarnOnInterceptorWithNoHooks(boolean theWarnOnInterceptorWithNoHooks) {
		myWarnOnInterceptorWithNoHooks = theWarnOnInterceptorWithNoHooks;
	}

	@VisibleForTesting
	List<Object> getGlobalInterceptorsForUnitTest() {
		return myInterceptors;
	}

	/**
	 * @deprecated This value is not used anywhere
	 */
	@Deprecated(since = "8.0.0", forRemoval = true)
	public void setName(@SuppressWarnings("unused") String theName) {
		// nothing
	}

	protected void registerAnonymousInterceptor(POINTCUT thePointcut, Object theInterceptor, BaseInvoker theInvoker) {
		Validate.notNull(thePointcut, "thePointcut must not be null");
		Validate.notNull(theInterceptor, "theInterceptor must not be null");
		synchronized (myRegistryMutex) {
			myAnonymousInvokers.put(thePointcut, theInvoker);
			if (!isInterceptorAlreadyRegistered(theInterceptor)) {
				myInterceptors.add(theInterceptor);
			}

			rebuildRegisteredPointcutSet();
		}
	}

	@Override
	public List<Object> getAllRegisteredInterceptors() {
		synchronized (myRegistryMutex) {
			List<Object> retVal = new ArrayList<>(myInterceptors);
			return Collections.unmodifiableList(retVal);
		}
	}

	@Override
	@VisibleForTesting
	public void unregisterAllInterceptors() {
		synchronized (myRegistryMutex) {
			unregisterInterceptors(myAnonymousInvokers.values());
			unregisterInterceptors(myGlobalInvokers.values());
			unregisterInterceptors(myInterceptors);
		}
	}

	@Override
	public void unregisterInterceptors(@Nullable Collection<?> theInterceptors) {
		if (theInterceptors != null) {
			// We construct a new list before iterating because the service's internal
			// interceptor lists get passed into this method, and we get concurrent
			// modification errors if we modify them at the same time as we iterate them
			new ArrayList<>(theInterceptors).forEach(this::unregisterInterceptor);
		}
	}

	@Override
	public void registerInterceptors(@Nullable Collection<?> theInterceptors) {
		if (theInterceptors != null) {
			theInterceptors.forEach(this::registerInterceptor);
		}
	}

	@Override
	public void unregisterAllAnonymousInterceptors() {
		synchronized (myRegistryMutex) {
			unregisterInterceptorsIf(t -> true, myAnonymousInvokers);
		}
	}

	@Override
	public void unregisterInterceptorsIf(Predicate<Object> theShouldUnregisterFunction) {
		unregisterInterceptorsIf(theShouldUnregisterFunction, myGlobalInvokers);
		unregisterInterceptorsIf(theShouldUnregisterFunction, myAnonymousInvokers);
	}

	private void unregisterInterceptorsIf(
			Predicate<Object> theShouldUnregisterFunction, ListMultimap<POINTCUT, IInvoker> theGlobalInvokers) {
		synchronized (myRegistryMutex) {
			for (Map.Entry<POINTCUT, IInvoker> nextInvoker : new ArrayList<>(theGlobalInvokers.entries())) {
				if (theShouldUnregisterFunction.test(nextInvoker.getValue().getInterceptor())) {
					unregisterInterceptor(nextInvoker.getValue().getInterceptor());
				}
			}

			rebuildRegisteredPointcutSet();
		}
	}

	@Override
	public boolean registerInterceptor(Object theInterceptor) {
		synchronized (myRegistryMutex) {
			if (isInterceptorAlreadyRegistered(theInterceptor)) {
				return false;
			}

			List<HookInvoker> addedInvokers = scanInterceptorAndAddToInvokerMultimap(theInterceptor, myGlobalInvokers);
			if (addedInvokers.isEmpty()) {
				if (myWarnOnInterceptorWithNoHooks) {
					ourLog.warn(
							"Interceptor registered with no valid hooks - Type was: {}",
							theInterceptor.getClass().getName());
				}
				return false;
			}

			// Add to the global list
			myInterceptors.add(theInterceptor);
			sortByOrderAnnotation(myInterceptors);

			rebuildRegisteredPointcutSet();

			return true;
		}
	}

	private void rebuildRegisteredPointcutSet() {
		EnumSet<POINTCUT> registeredPointcuts = EnumSet.noneOf(myPointcutType);
		registeredPointcuts.addAll(myAnonymousInvokers.keySet());
		registeredPointcuts.addAll(myGlobalInvokers.keySet());
		myRegisteredPointcuts = registeredPointcuts;
	}

	private boolean isInterceptorAlreadyRegistered(Object theInterceptor) {
		for (Object next : myInterceptors) {
			if (next == theInterceptor) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean unregisterInterceptor(Object theInterceptor) {
		synchronized (myRegistryMutex) {
			boolean removed = myInterceptors.removeIf(t -> t == theInterceptor);
			removed |= myGlobalInvokers.entries().removeIf(t -> t.getValue().getInterceptor() == theInterceptor);
			removed |= myAnonymousInvokers.entries().removeIf(t -> t.getValue().getInterceptor() == theInterceptor);
			rebuildRegisteredPointcutSet();
			return removed;
		}
	}

	private void sortByOrderAnnotation(List<Object> theObjects) {
		IdentityHashMap<Object, Integer> interceptorToOrder = new IdentityHashMap<>();
		for (Object next : theObjects) {
			Interceptor orderAnnotation = next.getClass().getAnnotation(Interceptor.class);
			int order = orderAnnotation != null ? orderAnnotation.order() : 0;
			interceptorToOrder.put(next, order);
		}

		theObjects.sort((a, b) -> {
			Integer orderA = interceptorToOrder.get(a);
			Integer orderB = interceptorToOrder.get(b);
			return orderA - orderB;
		});
	}

	@Override
	public Object callHooksAndReturnObject(POINTCUT thePointcut, HookParams theParams) {
		assert haveAppropriateParams(thePointcut, theParams);
		assert thePointcut.getReturnType() != void.class;

		return doCallHooks(thePointcut, theParams);
	}

	@Override
	public boolean hasHooks(POINTCUT thePointcut) {
		return myRegisteredPointcuts.contains(thePointcut);
	}

	protected Class<?> getBooleanReturnType() {
		return boolean.class;
	}

	@Override
	public boolean callHooks(POINTCUT thePointcut, HookParams theParams) {
		assert haveAppropriateParams(thePointcut, theParams);
		assert thePointcut.getReturnType() == void.class || thePointcut.getReturnType() == getBooleanReturnType();

		Object retValObj = doCallHooks(thePointcut, theParams);
		retValObj = defaultIfNull(retValObj, true);
		return (Boolean) retValObj;
	}

	private Object doCallHooks(POINTCUT thePointcut, HookParams theParams) {
		List<IInvoker> invokers = getInvokersForPointcut(thePointcut);
		return callInvokers(thePointcut, theParams, invokers);
	}

	@VisibleForTesting
	List<Object> getInterceptorsWithInvokersForPointcut(POINTCUT thePointcut) {
		return getInvokersForPointcut(thePointcut).stream()
				.map(IInvoker::getInterceptor)
				.collect(Collectors.toList());
	}

	/**
	 * Returns a list of all invokers registered for the given pointcut. The list
	 * is ordered by the invoker order (specified on the {@link Interceptor#order()}
	 * and {@link Hook#order()} values.
	 *
	 * @return The list returned by this method will always be a newly created list, so it will be stable and can be modified.
	 */
	@Override
	public List<IInvoker> getInvokersForPointcut(POINTCUT thePointcut) {
		List<IInvoker> invokers;

		synchronized (myRegistryMutex) {
			List<IInvoker> globalInvokers = myGlobalInvokers.get(thePointcut);
			List<IInvoker> anonymousInvokers = myAnonymousInvokers.get(thePointcut);
			invokers = union(Arrays.asList(globalInvokers, anonymousInvokers));
		}

		return invokers;
	}

	/**
	 * Only call this when assertions are enabled, it's expensive
	 */
	public static boolean haveAppropriateParams(IPointcut thePointcut, HookParams theParams) {
		if (theParams.getParamsForType().values().size()
				!= thePointcut.getParameterTypes().size()) {
			throw new IllegalArgumentException(Msg.code(1909)
					+ String.format(
							"Wrong number of params for pointcut %s - Wanted %s but found %s",
							thePointcut.name(),
							toErrorString(thePointcut.getParameterTypes()),
							theParams.getParamsForType().values().stream()
									.map(t -> t != null ? t.getClass().getSimpleName() : "null")
									.sorted()
									.collect(Collectors.toList())));
		}

		List<String> wantedTypes = new ArrayList<>(thePointcut.getParameterTypes());

		ListMultimap<Class<?>, Object> givenTypes = theParams.getParamsForType();
		for (Class<?> nextTypeClass : givenTypes.keySet()) {
			String nextTypeName = nextTypeClass.getName();
			for (Object nextParamValue : givenTypes.get(nextTypeClass)) {
				Validate.isTrue(
						nextParamValue == null || nextTypeClass.isAssignableFrom(nextParamValue.getClass()),
						"Invalid params for pointcut %s - %s is not of type %s",
						thePointcut.name(),
						nextParamValue != null ? nextParamValue.getClass() : "null",
						nextTypeClass);
				Validate.isTrue(
						wantedTypes.remove(nextTypeName),
						"Invalid params for pointcut %s - Wanted %s but found %s",
						thePointcut.name(),
						toErrorString(thePointcut.getParameterTypes()),
						nextTypeName);
			}
		}

		return true;
	}

	private List<HookInvoker> scanInterceptorAndAddToInvokerMultimap(
			Object theInterceptor, ListMultimap<POINTCUT, IInvoker> theInvokers) {
		Class<?> interceptorClass = theInterceptor.getClass();
		int typeOrder = determineOrder(interceptorClass);

		List<HookInvoker> addedInvokers = scanInterceptorForHookMethods(theInterceptor, typeOrder);

		// Invoke the REGISTERED pointcut for any added hooks
		addedInvokers.stream()
				.filter(t -> Pointcut.INTERCEPTOR_REGISTERED.equals(t.getPointcut()))
				.forEach(t -> t.invoke(new HookParams()));

		// Register the interceptor and its various hooks
		for (HookInvoker nextAddedHook : addedInvokers) {
			POINTCUT nextPointcut = nextAddedHook.getPointcut();
			if (nextPointcut.equals(Pointcut.INTERCEPTOR_REGISTERED)) {
				continue;
			}
			theInvokers.put(nextPointcut, nextAddedHook);
		}

		// Make sure we're always sorted according to the order declared in @Order
		for (POINTCUT nextPointcut : theInvokers.keys()) {
			List<IInvoker> nextInvokerList = theInvokers.get(nextPointcut);
			nextInvokerList.sort(Comparator.naturalOrder());
		}

		return addedInvokers;
	}

	/**
	 * @return Returns a list of any added invokers
	 */
	private List<HookInvoker> scanInterceptorForHookMethods(Object theInterceptor, int theTypeOrder) {
		ArrayList<HookInvoker> retVal = new ArrayList<>();
		for (Method nextMethod : ReflectionUtil.getDeclaredMethods(theInterceptor.getClass(), true)) {
			Optional<HookDescriptor> hook = scanForHook(nextMethod);

			if (hook.isPresent()) {
				int methodOrder = theTypeOrder;
				int methodOrderAnnotation = hook.get().getOrder();
				if (methodOrderAnnotation != Interceptor.DEFAULT_ORDER) {
					methodOrder = methodOrderAnnotation;
				}

				retVal.add(new HookInvoker(hook.get(), theInterceptor, nextMethod, methodOrder));
			}
		}

		return retVal;
	}

	protected abstract Optional<HookDescriptor> scanForHook(Method nextMethod);

	public static Object callInvokers(IPointcut thePointcut, HookParams theParams, List<IInvoker> invokers) {

		Object retVal = null;

		/*
		 * Call each hook in order
		 */
		for (IInvoker nextInvoker : invokers) {
			Object nextOutcome = nextInvoker.invoke(theParams);
			Class<?> pointcutReturnType = thePointcut.getReturnType();
			if (pointcutReturnType.equals(thePointcut.getBooleanReturnTypeForEnum())) {
				Boolean nextOutcomeAsBoolean = (Boolean) nextOutcome;
				if (Boolean.FALSE.equals(nextOutcomeAsBoolean)) {
					ourLog.trace("callHooks({}) for invoker({}) returned false", thePointcut, nextInvoker);
					retVal = false;
					break;
				} else {
					retVal = true;
				}
			} else if (!pointcutReturnType.equals(void.class)) {
				if (nextOutcome != null) {
					retVal = nextOutcome;
					break;
				}
			}
		}

		return retVal;
	}

	/**
	 * First argument must be the global invoker list!!
	 */
	public static List<IInvoker> union(List<List<IInvoker>> theInvokersLists) {
		List<IInvoker> haveOne = null;
		boolean haveMultiple = false;
		for (List<IInvoker> nextInvokerList : theInvokersLists) {
			if (nextInvokerList == null || nextInvokerList.isEmpty()) {
				continue;
			}

			if (haveOne == null) {
				haveOne = nextInvokerList;
			} else {
				haveMultiple = true;
			}
		}

		if (haveOne == null) {
			return Collections.emptyList();
		}

		List<IInvoker> retVal;

		if (!haveMultiple) {

			// The global list doesn't need to be sorted every time since it's sorted on
			// insertion each time. Doing so is a waste of cycles..
			if (haveOne == theInvokersLists.get(0)) {
				retVal = haveOne;
			} else {
				retVal = new ArrayList<>(haveOne);
				retVal.sort(Comparator.naturalOrder());
			}

		} else {

			int totalSize = 0;
			for (List<IInvoker> list : theInvokersLists) {
				totalSize += list.size();
			}
			retVal = new ArrayList<>(totalSize);
			for (List<IInvoker> list : theInvokersLists) {
				retVal.addAll(list);
			}
			retVal.sort(Comparator.naturalOrder());
		}

		return retVal;
	}

	protected static <T extends Annotation> Optional<T> findAnnotation(
			AnnotatedElement theObject, Class<T> theHookClass) {
		T annotation;
		if (theObject instanceof Method) {
			annotation = MethodUtils.getAnnotation((Method) theObject, theHookClass, true, true);
		} else {
			annotation = theObject.getAnnotation(theHookClass);
		}
		return Optional.ofNullable(annotation);
	}

	private static int determineOrder(Class<?> theInterceptorClass) {
		return findAnnotation(theInterceptorClass, Interceptor.class)
				.map(Interceptor::order)
				.orElse(Interceptor.DEFAULT_ORDER);
	}

	private static String toErrorString(List<String> theParameterTypes) {
		return theParameterTypes.stream().sorted().collect(Collectors.joining(","));
	}

	private class HookInvoker extends BaseInvoker {

		private final Method myMethod;
		private final Class<?>[] myParameterTypes;
		private final int[] myParameterIndexes;
		private final POINTCUT myPointcut;

		/**
		 * Constructor
		 */
		private HookInvoker(
				HookDescriptor theHook, @Nonnull Object theInterceptor, @Nonnull Method theHookMethod, int theOrder) {
			super(theInterceptor, theOrder);
			myPointcut = theHook.getPointcut();
			myParameterTypes = theHookMethod.getParameterTypes();
			myMethod = theHookMethod;

			Class<?> returnType = theHookMethod.getReturnType();
			if (myPointcut.getReturnType().equals(myPointcut.getBooleanReturnTypeForEnum())) {
				Validate.isTrue(
						myPointcut.getBooleanReturnTypeForEnum().equals(returnType) || void.class.equals(returnType),
						"Method does not return %s or void: %s",
						myPointcut.getBooleanReturnTypeForEnum().getSimpleName(),
						theHookMethod);
			} else if (myPointcut.getReturnType().equals(void.class)) {
				Validate.isTrue(void.class.equals(returnType), "Method does not return void: %s", theHookMethod);
			} else {
				Validate.isTrue(
						myPointcut.getReturnType().isAssignableFrom(returnType) || void.class.equals(returnType),
						"Method does not return %s or void: %s",
						myPointcut.getReturnType(),
						theHookMethod);
			}

			myParameterIndexes = new int[myParameterTypes.length];
			Map<Class<?>, AtomicInteger> typeToCount = new HashMap<>();
			for (int i = 0; i < myParameterTypes.length; i++) {
				AtomicInteger counter = typeToCount.computeIfAbsent(myParameterTypes[i], t -> new AtomicInteger(0));
				myParameterIndexes[i] = counter.getAndIncrement();
			}

			myMethod.setAccessible(true);
		}

		@Override
		public String toString() {
			return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
					.append("method", myMethod)
					.toString();
		}

		@Override
		public String getHookDescription() {
			return getInterceptor().getClass().getName() + "." + myMethod.getName();
		}

		public POINTCUT getPointcut() {
			return myPointcut;
		}

		/**
		 * @return Returns true/false if the hook method returns a boolean, returns true otherwise
		 */
		@Override
		public Object invoke(HookParams theParams) {

			Object[] args = new Object[myParameterTypes.length];
			for (int i = 0; i < myParameterTypes.length; i++) {
				Class<?> nextParamType = myParameterTypes[i];
				if (nextParamType.equals(Pointcut.class)) {
					args[i] = myPointcut;
				} else {
					int nextParamIndex = myParameterIndexes[i];
					Object nextParamValue = theParams.get(nextParamType, nextParamIndex);
					args[i] = nextParamValue;
				}
			}

			// Invoke the method
			try {
				return invokeMethod(args);
			} catch (InvocationTargetException e) {
				Throwable targetException = e.getTargetException();
				if (myPointcut.isShouldLogAndSwallowException(targetException)) {
					ourLog.error("Exception thrown by interceptor: " + targetException.toString(), targetException);
					return null;
				}

				if (targetException instanceof RuntimeException) {
					throw ((RuntimeException) targetException);
				} else {
					throw new InternalErrorException(
							Msg.code(1910) + "Failure invoking interceptor for pointcut(s) " + getPointcut(),
							targetException);
				}
			} catch (Exception e) {
				throw new InternalErrorException(Msg.code(1911) + e);
			}
		}

		@WithSpan("hapifhir.interceptor")
		private Object invokeMethod(Object[] args) throws InvocationTargetException, IllegalAccessException {
			// Add attributes to the opentelemetry span
			Span currentSpan = Span.current();
			currentSpan.setAttribute(OTEL_INTERCEPTOR_POINTCUT_NAME_ATT_KEY, myPointcut.name());
			currentSpan.setAttribute(
					OTEL_INTERCEPTOR_CLASS_NAME_ATT_KEY,
					myMethod.getDeclaringClass().getName());
			currentSpan.setAttribute(OTEL_INTERCEPTOR_METHOD_NAME_ATT_KEY, myMethod.getName());

			return myMethod.invoke(getInterceptor(), args);
		}
	}

	protected class HookDescriptor {

		private final POINTCUT myPointcut;
		private final int myOrder;

		public HookDescriptor(POINTCUT thePointcut, int theOrder) {
			myPointcut = thePointcut;
			myOrder = theOrder;
		}

		POINTCUT getPointcut() {
			return myPointcut;
		}

		int getOrder() {
			return myOrder;
		}
	}

	public abstract static class BaseInvoker implements IInvoker {

		private final int myOrder;
		private final Object myInterceptor;

		BaseInvoker(Object theInterceptor, int theOrder) {
			myInterceptor = theInterceptor;
			myOrder = theOrder;
		}

		@Override
		public Object getInterceptor() {
			return myInterceptor;
		}

		@Override
		public int getOrder() {
			return myOrder;
		}

		@Override
		public int compareTo(IInvoker theInvoker) {
			return myOrder - theInvoker.getOrder();
		}
	}
}
