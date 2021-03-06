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
package com.gemstone.gemfire.internal.cache.lru;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.internal.cache.InternalRegionArguments;

/**
 * NewLIFOClockHand holds the behavior for LIFO logic , Overwriting
 * getLRUEntry() to return most recently added Entry
 * 
 * @author aingle
 * @since 5.7
 */

public class NewLIFOClockHand extends NewLRUClockHand {
  /*
   * constructor
   */
  public NewLIFOClockHand(Object region, EnableLRU ccHelper, InternalRegionArguments internalRegionArgs) {
    super(region,ccHelper,internalRegionArgs);
  }
  
  public NewLIFOClockHand( Region region, EnableLRU ccHelper
      ,NewLRUClockHand oldList){
    super(region,ccHelper,oldList);
  }

  /*
   *  return the Entry that is considered most recently used
   *
  @Override
   public LRUClockNode getLRUEntry() { // new getLIFOEntry
    LRUClockNode aNode = null;
    synchronized (this.lock) {
      aNode = this.tail.prevLRUNode();
      if(aNode == this.head) {
        return null;
      }
      //TODO - Dan 9/23/09 We should probably
      //do something like this to change the tail pointer.
      //But this code wasn't changing the tail before
      //I made this a doubly linked list, and I don't
      //want to change it on this branch.
//      LRUClockNode prev = aNode.prevLRUNode();
//      prev.setNextLRUNode(this.tail);
//      aNode.setNextLRUNode(null);
//      aNode.setPrevLRUNode(null);
    }
    /* no need to update stats here as when this function finished executing 
       next few calls update stats *
    return aNode.testEvicted()? null:aNode;
  }
  */

  /**
   * return the tail entry in the list preserving the requirement of at least
   * one entry left in the list
   */
  @Override
  protected LRUClockNode getNextEntry() {
    synchronized (lock) {
      LRUClockNode aNode = this.tail.prevLRUNode();
      if (aNode == this.head) {
        return null;
      }

      LRUClockNode prev = aNode.prevLRUNode();
      this.tail.setPrevLRUNode(prev);
      prev.setNextLRUNode(this.tail);

      aNode.setNextLRUNode(null);
      aNode.setPrevLRUNode(null);
      this.size--;
      return aNode;
    }
  }

  @Override
  protected boolean checkRecentlyUsed(LRUClockNode aNode) {
    // reject recently used as criteria
    return false;
  }
}
