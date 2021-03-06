/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ivory.rerun.policy;

import java.util.Date;

import org.apache.ivory.IvoryException;
import org.apache.ivory.entity.v0.Frequency;
import org.apache.ivory.expression.ExpressionHelper;

public abstract class AbstractRerunPolicy {

	public long getDurationInMilliSec(Frequency frequency)
			throws IvoryException {
		ExpressionHelper helper = ExpressionHelper.get();
		return helper.evaluate(frequency.toString(), Long.class);

	}

	public static Date addTime(Date date, int milliSecondsToAdd) {
		return new Date(date.getTime() + milliSecondsToAdd);
	}

	public abstract long getDelay(Frequency delay, int eventNumber)
			throws IvoryException;

	public abstract long getDelay(Frequency delay, Date nominaltime,
			Date cutOffTime) throws IvoryException;
}
