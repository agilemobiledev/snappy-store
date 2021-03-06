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

package com.gemstone.gemfire.lang;

/**
 * The AttachAPINotFoundException class is a RuntimeException indicating that the JDK tools.jar has not been properly
 * set on the user's classpath
 * <p/>
 * @author John Blum
 * @see java.lang.RuntimeException
 * @since 7.0
 */
@SuppressWarnings("unused")
public class AttachAPINotFoundException extends RuntimeException {

  /**
   * Constructs an instance of the AttachAPINotFoundException class.
   */
  public AttachAPINotFoundException() {
  }

  /**
   * Constructs an instance of the AttachAPINotFoundException class with a description of the problem.
   * <p/>
   * @param message a String describing the nature of the Exception and why it was thrown.
   */
  public AttachAPINotFoundException(final String message) {
    super(message);
  }

  /**
   * Constructs an instance of the AttachAPINotFoundException class with a reference to the underlying Exception
   * causing this Exception to be thrown.
   * <p/>
   * @param cause a Throwable indicating the reason this Exception was thrown.
   */
  public AttachAPINotFoundException(final Throwable cause) {
    super(cause);
  }

  /**
   * Constructs an instance of the AttachAPINotFoundException class with a reference to the underlying Exception
   * causing this Exception to be thrown in addition to a description of the problem.
   * <p/>
   * @param message a String describing the nature of the Exception and why it was thrown.
   * @param cause a Throwable indicating the reason this Exception was thrown.
   */
  public AttachAPINotFoundException(final String message, final Throwable cause) {
    super(message, cause);
  }

}
