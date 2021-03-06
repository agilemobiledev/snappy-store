/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.gemstone.gemfire.cache.operations;

import com.gemstone.gemfire.cache.InterestResultPolicy;

/**
 * Encapsulates a {@link com.gemstone.gemfire.cache.operations.OperationContext.OperationCode#REGISTER_INTEREST} region operation for
 * the pre-operation case.
 * 
 * @author Sumedh Wale
 * @since 5.5
 */
public class RegisterInterestOperationContext extends InterestOperationContext {

  /** The <code>InterestResultPolicy</code> for this list of keys. */
  private InterestResultPolicy policy;

  /**
   * Constructor for the register interest operation.
   * 
   * @param key
   *                the key or list of keys being registered
   * @param interestType
   *                the <code>InterestType</code> of the register request
   * @param policy
   *                the <code>InterestResultPolicy</code> of the register
   *                request
   */
  public RegisterInterestOperationContext(Object key,
      InterestType interestType, InterestResultPolicy policy) {
    super(key, interestType);
    this.policy = policy;
  }

  /**
   * Return the operation associated with the <code>OperationContext</code>
   * object.
   * 
   * @return <code>OperationCode.REGISTER_INTEREST</code>.
   */
  @Override
  public OperationCode getOperationCode() {
    return OperationCode.REGISTER_INTEREST;
  }

  /**
   * Get the <code>InterestResultPolicy</code> of this register/unregister
   * operation.
   * 
   * @return the <code>InterestResultPolicy</code> of this request.
   */
  public InterestResultPolicy getInterestResultPolicy() {
    return this.policy;
  }

}
