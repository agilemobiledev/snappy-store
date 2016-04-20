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
package sql.dmlDistTxRRStatements;

import java.math.BigDecimal;
import java.sql.Connection;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import sql.SQLHelper;
import sql.sqlTx.SQLDistTxTest;
import sql.sqlTx.SQLTxBatchingFKBB;
import util.TestException;
import hydra.Log;
import sql.dmlDistTxStatements.TradeSellOrdersDMLDistTxStmt;
import util.TestHelper;


public class TradeSellOrdersDMLDistTxRRStmt extends
    TradeSellOrdersDMLDistTxStmt {

  @SuppressWarnings("unchecked")
  @Override
  public boolean insertGfxd(Connection gConn, boolean withDerby) {
    if (!withDerby) {
      return insertGfxdOnly(gConn);
    }
    int size = 1;
    int[] cid = new int[size];
    int[] sid = new int[size];
    int[] oid = new int[size];
    int[] qty = new int[size];
    String[] status = new String[size];
    Timestamp[] time = new Timestamp[size];
    BigDecimal[] ask = new BigDecimal[size];
    int[] updateCount = new int[size];
    boolean[] expectConflict = new boolean[1];
    Connection nonTxConn = (Connection)SQLDistTxTest.gfxdNoneTxConn.get();
    SQLException gfxdse = null;

    getKeysFromPortfolio(nonTxConn, cid, sid);
    getDataForInsert(nonTxConn, oid, cid, sid, qty, time, ask, size); //get the data
    for (int i = 0; i< status.length; i++) {
      status[i] = "open";
    }

    int chance = 200;
    if (rand.nextInt(chance) == 0) cid[0] = 0;
    else if (rand.nextInt(chance) == 0) sid[0] = 0;

    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    modifiedKeysByOp.put(getTableName()+"_"+oid[0], (Integer)SQLDistTxTest.curTxId.get());
    HashSet<String> parentKeysHold = new HashSet<String>();

    try {
      getKeysForInsert(nonTxConn, cid[0], sid[0], expectConflict, parentKeysHold);

      /* check through batching fk bb now
      if (batchingWithSecondaryData && expectConflict[0] == true) {
        SQLDistTxTest.expectForeignKeyConflictWithBatching.set(expectConflict[0]);
        //TODO need to think a better way when #43170 is fixed -- which foreign keys (range keys) are held
        //and by which threads need to be tracked and verified.
      }
      */
    } catch (SQLException se) {
      if (se.getSQLState().equals("X0Z01") && isHATest) { // handles HA issue for #41471
        Log.getLogWriter().warning("Not able to process the keys for this op due to HA, this insert op does not proceed");
        return true; //not able to process the keys due to HA, it is a no op
      } else SQLHelper.handleSQLException(se);
    }


    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();

    if (batchingWithSecondaryData) {
      //add to fk bb for the fk key hold due to insert into child table
      HashSet<String> holdFKsByThisTx = (HashSet<String>) SQLDistTxTest.foreignKeyHeldWithBatching.get();
      holdFKsByThisTx.addAll(parentKeysHold);
      SQLDistTxTest.foreignKeyHeldWithBatching.set(holdFKsByThisTx);

      hydra.blackboard.SharedMap holdingFKTxIds = SQLTxBatchingFKBB.getBB().getSharedMap();
      Integer myTxId = (Integer) SQLDistTxTest.curTxId.get();
      for (String key: parentKeysHold) {
        HashSet<Integer> txIds = (HashSet<Integer>) holdingFKTxIds.get(key);
        if (txIds == null) txIds = new HashSet<Integer>();
        txIds.add(myTxId);
        holdingFKTxIds.put(key, txIds);
      }
    }

    for(int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Inserting " + i + " times.");
        insertToGfxdTable(gConn, oid, cid, sid, qty, status, time, ask, updateCount, size);
        //the gfxd tx needs to handle prepareStatement failed due to node failure here
        //does not expect critical heap exception etc in current tx testing
        //once these coverage are added, similar handling of exceptions seen in getStmt()
        //need to be added here.
        break;
      } catch (SQLException se) {
        SQLHelper.printSQLException(se);
        if (se.getSQLState().equalsIgnoreCase("X0Z02")) {
          try {
            if (expectConflict[0]) {
              ; //if conflict caused by foreign key
            } else {
              if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, se, true);
              else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, se, hasSecondary, true);
              //check if conflict caused by multiple inserts on the same keys
            }
          } catch (TestException te) {
            if (te.getMessage().contains("but got conflict exception") && i < 9) {
              continue;
            } else throw te;
          }

          if (batchingWithSecondaryData) cleanUpFKHolds(); //got the exception, ops are rolled back due to #43170
          removePartialRangeForeignKeys(cid, sid);
          return false;
        } else if (gfxdtxHANotReady && isHATest &&
            SQLHelper.gotTXNodeFailureException(se)) {
          SQLHelper.printSQLException(se);
          Log.getLogWriter().info("got node failure exception during Tx with HA support, continue testing");

          if (batchingWithSecondaryData) cleanUpFKHolds(); //got the exception, ops are rolled back due to #43170
          removePartialRangeForeignKeys(cid, sid); //operation not successful, remove the fk constraint keys

          return false; //not able to handle node failure yet, needs to rollback ops
          // to be confirmed if select query could cause lock to be released
        } else {
          if (expectConflict[0] && !se.getSQLState().equals("23503")) {
            if (!batchingWithSecondaryData)
              throw new TestException("expect conflict exceptions, but did not get it" +
                  TestHelper.getStackTrace(se));
            else {
              //do nothing, as foreign key check may only be done on local node, conflict could be detected at commit time
              ;
            }
          }
          gfxdse = se;
          if (batchingWithSecondaryData) cleanUpFKHolds(); //got the exception, ops are rolled back due to #43170
          removePartialRangeForeignKeys(cid, sid); //operation not successful, remove the fk constraint keys
        }
      }
    }

    if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, gfxdse, false);
    else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, gfxdse, hasSecondary, false);

    if (expectConflict[0] && gfxdse == null) {
      if (!batchingWithSecondaryData)
        throw new TestException("Did not get conflict exception for foreign key check. " +
            "Please check for logs");
      else {
        //do nothing, as foreign key check may only be done on local node, conflict could be detected at commit time
        ;
      }
    }

    //add this operation also for derby
    if (withDerby) addInsertToDerbyTx(oid, cid, sid, qty, status, time,
        ask, updateCount, gfxdse);

    modifiedKeysByTx.putAll(modifiedKeysByOp);
    SQLDistTxTest.curTxModifiedKeys.set(modifiedKeysByTx);

    return true;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean updateGfxd(Connection gConn, boolean withDerby) {
    if (!withDerby) {
      return updateGfxdOnly(gConn);
    }
    if (!SQLDistTxTest.isTicket43188fiFixed && SQLDistTxTest.useThinClientDriverInTx)
      return true; //workaround #43188 Updatable resultset is not supported yet using thin client driver

    if (partitionKeys == null) setPartitionKeys();
    int size =1;
    int[] sid = new int[size];
    BigDecimal[] ask = new BigDecimal[size];
    Timestamp[] orderTime = new Timestamp[size];
    int[] cid = new int[size];
    int[] cid2 = new int[size];
    int[] qty = new int[size];
    ArrayList<Integer> oids = new ArrayList<Integer>();
    String status = statuses[rand.nextInt(statuses.length)];

    int[] whichUpdate = new int[size];

    SQLException gfxdse = null;

    boolean success = getDataForUpdate((Connection)SQLDistTxTest.gfxdNoneTxConn.get(), cid, cid2,
        sid, qty, orderTime, ask, whichUpdate, size);
    if (!success) return true; //did not get data or not commit early txs, it is a no op

    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();

    try {
      getKeysForUpdate((Connection)SQLDistTxTest.gfxdNoneTxConn.get(), modifiedKeysByOp,
          whichUpdate[0], cid[0], cid2[0], sid[0], ask[0], orderTime[0], oids);
    } catch (SQLException se) {
      if (se.getSQLState().equals("X0Z01") && isHATest) { // handles HA issue for #41471
        Log.getLogWriter().warning("Not able to process the keys for this op due to HA, this update op does not proceed");
        return true; //not able to process the keys due to HA, it is a no op
      } else SQLHelper.handleSQLException(se); //else gfxdse = se;
    }

    //Log.getLogWriter().info("oids size after get keys is " + oids.size());
    int[] updateCount = new int [oids.size()];

    for (int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Updating " + i + " times.");
        success = updateGfxdTable(gConn, cid, cid2, sid,
            ask, qty, orderTime, status, oids, whichUpdate, updateCount, size);
        if (!success) {
        /*
        if (SQLTest.isEdge && isHATest && !isTicket48176Fixed &&
            batchingWithSecondaryData &&(Boolean) SQLDistTxTest.failedToGetStmtNodeFailure.get()) {
          SQLDistTxTest.failedToGetStmtNodeFailure.set(false);
          return false; //due to node failure, need to rollback tx
        }
        else return true; //due to unsupported exception
        */

          //handles get stmt failure conditions -- node failure or unsupported update on partition field
          if (isHATest && (Boolean)SQLDistTxTest.failedToGetStmtNodeFailure.get()) {
            SQLDistTxTest.failedToGetStmtNodeFailure.set(false); //reset flag
            return false; //due to node failure, assume txn rolled back
          }
          if ((Boolean)SQLDistTxTest.updateOnPartitionCol.get()) {
            SQLDistTxTest.updateOnPartitionCol.set(false); //reset flag
            return true; //assume 0A000 exception does not cause txn to rollback
          }
        }
        break;
        //partitioned on partitoned key, needs to check if using URS will rollback
        //the tx, if so test needs to be modified. which may needs to separate update
        //by PK and URS (no of column case) and return accordingly here
      } catch (SQLException se) {
        SQLHelper.printSQLException(se);
        if (se.getSQLState().equalsIgnoreCase("X0Z02")) {
          try {
            if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, se, true);
            else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, se, hasSecondary, true);
          } catch (TestException te) {
            if (te.getMessage().contains("but got conflict exception") && i < 9) {
              continue;
            } else throw te;
          }
          return false;
        } else if (gfxdtxHANotReady && isHATest &&
            SQLHelper.gotTXNodeFailureException(se)) {
          SQLHelper.printSQLException(se);
          Log.getLogWriter().info("got node failure exception during Tx with HA support, continue testing");
          return false;
        } else {
          SQLHelper.handleSQLException(se);
        }
      }
    }

    if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, gfxdse, false);
    else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, gfxdse, hasSecondary, false);

    //add this operation for derby
    addUpdateToDerbyTx(cid, cid2, sid, ask, qty, orderTime, status, oids,
        whichUpdate, updateCount, gfxdse);

    modifiedKeysByTx.putAll(modifiedKeysByOp);
    SQLDistTxTest.curTxModifiedKeys.set(modifiedKeysByTx);
    return true;
  }

  protected boolean verifyConflict(HashMap<String, Integer> modifiedKeysByOp,
      HashMap<String, Integer>modifiedKeysByThisTx, SQLException gfxdse,
      boolean getConflict) {
    return verifyConflictForRR(modifiedKeysByOp, modifiedKeysByThisTx, gfxdse, getConflict);
  }
  
  public boolean queryGfxd(Connection gConn, boolean withDerby){
    if (!withDerby) {
      return queryGfxdOnly(gConn);
    }
    
    //TODO to implement query with derby, and add to read locked keys for the table
    return true;
  } 

  protected boolean queryGfxdOnly(Connection gConn){
    try {
      return super.queryGfxdOnly(gConn);
    } catch (TestException te) {
      if (te.getMessage().contains("X0Z02") && !reproduce49935) {
        Log.getLogWriter().info("hit #49935, continuing test");
        return false;
      }
       else throw te;
    }
  }

}
