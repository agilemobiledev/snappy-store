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

package com.gemstone.gemfire.cache.execute;

/**
 * An extension of {@link FunctionException} to indicate that no member was
 * found for executing a function using FunctionService.onMembers or a targeted
 * function using routing objects or in a replicated region.
 * 
 * @author swale
 * @since 7.0
 */
public class NoMemberFoundException extends FunctionException {

  private static final long serialVersionUID = -5628339159484969203L;

  /** for deserialization */
  public NoMemberFoundException() {
    super();
  }

  /**
   * Creates new function exception with given error message.
   */
  public NoMemberFoundException(String msg) {
    super(msg);
  }

  /**
   * Creates new function exception with given error message and optional nested
   * exception.
   */
  public NoMemberFoundException(String msg, Throwable cause) {
    super(msg, cause);
  }
}