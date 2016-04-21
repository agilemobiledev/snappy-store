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

import hydra.Log;
import hydra.blackboard.SharedMap;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


import com.gemstone.gemfire.cache.query.Struct;

import sql.SQLBB;
import sql.SQLHelper;
import sql.SQLTest;
import sql.dmlDistTxStatements.TradeCustomersDMLDistTxStmt;
import sql.sqlTx.ReadLockedKey;
import sql.sqlTx.SQLDistRRTxTest;
import sql.sqlTx.SQLDistTxTest;
import sql.sqlTx.SQLTxRRReadBB;
import sql.sqlutil.ResultSetHelper;
import util.TestException;

public class TradeCustomersDMLDistTxRRStmt extends TradeCustomersDMLDistTxStmt {

  @SuppressWarnings("unchecked")
  @Override
  public boolean insertGfxd(Connection gConn, boolean withDerby){
    if (!withDerby) {
      return insertGfxdOnly(gConn);
    }

    //with derby case
    int chance= 10;
    boolean useBatchInsert = rand.nextInt(chance) == 1 ? true : false;

    int size = useBatchInsert? 5: 1;
    int[] cid = new int[size];
    String[] cust_name = new String[size];
    Date[] since = new Date[size];
    String[] addr = new String[size];
    SQLException gfxdse = null;
    int[] updateCount = new int[size];
    getDataForInsert(cid, cust_name,since,addr, size); //get the data
    if (rand.nextInt(100) == 1 && SQLDistTxTest.ticket43170fixed) --cid[0];  //add some insert/insert conflict

    boolean usePut = rand.nextBoolean(); //randomly use put statement

    /* do not execute this due to #42672, this will be tested in the new txn testing
     * when foreign key are being tracked.
    if (useBatchInsert && rand.nextInt(100) == 1 && !usePut) {
      cid[size-1] = rand.nextInt((int) SQLBB.getBB().getSharedCounters().
          read(SQLBB.tradeCustomersPrimary)) + 1;
      Log.getLogWriter().info("possibly use duplicate cid: " + cid[size-1]);
      //test batch insert with possible duplicate
    }
    */

    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    for (int i=0; i<size; i++) {
      modifiedKeysByOp.put(getTableName()+"_"+cid[i], (Integer)SQLDistTxTest.curTxId.get());
    }
    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();

    // we will retry 10 times in case of conflict
    for(int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Inserting " + i + " times.");
        insertToGfxdTable(gConn, cid, cust_name, since, addr, updateCount, size, usePut);
      } catch (SQLException se) {
        SQLHelper.printSQLException(se);
        if (se.getSQLState().equalsIgnoreCase("X0Z02")) {
          try {
            if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, se, true);
            else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, se, hasSecondary, true);
          } catch (TestException t) {
            if (t.getMessage().contains("but got conflict exception") && i < 9) {
              Log.getLogWriter().info("RR: got conflict, retrying the operations ");
              continue;
            }
            else throw t;
          }
          return false;
        } else if (gfxdtxHANotReady && isHATest &&
            SQLHelper.gotTXNodeFailureException(se)) {
          SQLHelper.printSQLException(se);
          Log.getLogWriter().info("got node failure exception during Tx with HA support, continue testing");
          return false;
        } else {
          gfxdse = se;
          SQLDistTxTest.batchInsertToCustomersSucceeded.set(false);
        }
      }
      break;
    }

    if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, null, false);
    else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, null, hasSecondary, false);

    SQLDistTxTest.cidInserted.set(cid[0]);

    //add this operation for derby
    addInsertToDerbyTx(cid, cust_name, since, addr, updateCount, gfxdse);

    modifiedKeysByTx.putAll(modifiedKeysByOp);
    SQLDistTxTest.curTxModifiedKeys.set(modifiedKeysByTx);
    return true;
  }

  @SuppressWarnings("unchecked")
  public boolean updateGfxd(Connection gConn, boolean withDerby){
    if (!withDerby) {
      return updateGfxdOnly(gConn);
    }

    /* no need here based on update statement, the cid got is from existing cid
    if (resetCurrentMaxCustId) {
      currentMaxCustId = (Integer) SQLBB.getBB().getSharedMap().get(SQLDistTxTest.CURRENTMAXCUSTID);
      resetCurrentMaxCustId = false;
    }
    */

    int size =1;
    int[] cid = new int[size];
    int[] newCid = new int[size];
    String[] cust_name = new String[size];
    Date[] since = new Date[size];
    String[] addr = new String[size];

    int[] whichUpdate = new int[size];
    int[] updateCount = new int[size];
    SQLException gfxdse = null;
    boolean[] expectConflict = new boolean[1]; //handle #42672

    if (!ticket42672fixed && isCustomersPartitionedOnPKOrReplicate()) {
      //work around #49889 by not updating for now
      //need additional test development to allow conflict
      Log.getLogWriter().info("not implemented due to #42672, abort this op for now");
      return true;
    }

    getDataForUpdate((Connection)SQLDistTxTest.gfxdNoneTxConn.get(), newCid,
        cid, cust_name, since, addr, whichUpdate, size);
    getExistingCidFromCustomers((Connection)SQLDistTxTest.gfxdNoneTxConn.get(),
        cid); //get random cid

    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();

    /* needs to be handed in actual dml op later
    if (SQLTest.testPartitionBy) {
      PreparedStatement stmt = getCorrectTxStmt(gConn, whichUpdate[0]);
      if (stmt == null) {
        if (isHATest && (Boolean) SQLDistTxTest.failedToGetStmt.get()) {
          SQLDistTxTest.failedToGetStmt.set(false);
          return false; //due to node failure, assume txn rolled back
        }
        else return true; //due to unsupported exception
      }
    }
    */

    for (int i=0; i<size; i++) whichUpdate[i] = getWhichUpdate(whichUpdate[i]);

    try {
      getKeysForUpdate(modifiedKeysByOp, whichUpdate[0], cid[0], newCid[0], since[0]);
    } catch (SQLException se) {
      SQLHelper.printSQLException(se);
      Log.getLogWriter().warning("not able to get the keys, abort this insert op");
      return true;
    }

    for(int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Updating " + i + " times.");
        updateGfxdTable(gConn, newCid, cid, cust_name, since, addr, whichUpdate, updateCount, size);

        //handles get stmt failure conditions -- node failure or unsupported update on partition field
        if (isHATest && (Boolean)SQLDistTxTest.failedToGetStmtNodeFailure.get()) {
          SQLDistTxTest.failedToGetStmtNodeFailure.set(false); //reset flag
          return false; //due to node failure, assume txn rolled back
        }
        if ((Boolean)SQLDistTxTest.updateOnPartitionCol.get()) {
          SQLDistTxTest.updateOnPartitionCol.set(false); //reset flag
          return true; //assume 0A000 exception does not cause txn to rollback
        }
      } catch (SQLException se) {
        SQLHelper.printSQLException(se);
        if (se.getSQLState().equalsIgnoreCase("X0Z02")) {
          try {
            if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, se, true);
            else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, se, hasSecondary, true);
          } catch (TestException t) {
            if (t.getMessage().contains("but got conflict exception") && i < 9) {
              Log.getLogWriter().info("RR: got conflict, retrying the operations ");
              continue;
            }
            else throw t;
          }
          return false;
        } else if (gfxdtxHANotReady && isHATest &&
            SQLHelper.gotTXNodeFailureException(se)) {
          SQLHelper.printSQLException(se);
          Log.getLogWriter().info("got node failure exception during Tx with HA support, continue testing");
          return false;
        } else {
          //SQLHelper.handleSQLException(se);
          gfxdse = se;
        }
      }
      break;
    }
    if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, null, false);
    else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, null, hasSecondary, false);

    //add this operation for derby
    addUpdateToDerbyTx(newCid, cid, cust_name, since, addr, whichUpdate, updateCount, gfxdse);

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
    
    int whichQuery = rand.nextInt(select.length); //randomly select one query sql
    int cid = rand.nextInt((int) SQLBB.getBB().getSharedCounters().read(SQLBB.tradeCustomersPrimary));
    //Date since = new Date ((rand.nextInt(10)+98),rand.nextInt(12), rand.nextInt(31));
    Date since = getSince();
    ResultSet gfxdRS = null;
    SQLException gfxdse = null;
    for (int i = 0; i < 10; i++) {
      try {
        Log.getLogWriter().info("RR: executing query " + i + "times");
        gfxdRS = query(gConn, whichQuery, cid, since, getMyTid());
        if (gfxdRS == null) {
          if (isHATest) {
            Log.getLogWriter().info("Testing HA and did not get GFXD result set");
            return true;
          } else
            throw new TestException("Not able to get gfxd result set");
        }
      } catch (SQLException se) {
        if (isHATest &&
            SQLHelper.gotTXNodeFailureException(se)) {
          SQLHelper.printSQLException(se);
          Log.getLogWriter().info("got node failure exception during Tx with HA support, continue testing");
          return false; //assume node failure exception causes the tx to rollback
        } else if (se.getSQLState().equals("X0Z02") && (i < 9)) {
          Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
          continue;
        }
        SQLHelper.printSQLException(se);
        gfxdse = se;
      }
      try {
        List<Struct> gfxdList = ResultSetHelper.asList(gfxdRS, false);

        if (gfxdList == null && isHATest) {
          Log.getLogWriter().info("Testing HA and did not get GFXD result set");
          return true; //do not compare query results as gemfirexd does not get any
        }

        addReadLockedKeys(gfxdList);

        addQueryToDerbyTx(whichQuery, cid, since, gfxdList, gfxdse);
        //only the first thread to commit the tx in this round could verify results
        //this is handled in the SQLDistTxTest doDMLOp
      } catch (TestException te) {
        if (te.getMessage().contains("Conflict detected in transaction operation and it will abort") && (i <9)) {
          Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
          continue;
        } else throw te;
      }
      break;
    }
    
    return true;
  }  
  
  @SuppressWarnings("unchecked")
  protected void addReadLockedKeys(List<Struct> gfxdList) {
    int txId = (Integer) SQLDistRRTxTest.curTxId.get();
    SharedMap readLockedKeysByRRTx = SQLTxRRReadBB.getBB().getSharedMap();   
    
    Log.getLogWriter().info("adding the RR read keys to the Map for " +
        "this txId: " + txId);
    for (int i=0; i<gfxdList.size(); i++) {
      int cid = (Integer) gfxdList.get(i).get("CID");
      String key = getTableName()+"_"+cid;
      Log.getLogWriter().info("RR read key to be added is " + key);
      ((HashMap<String, Integer>) SQLDistRRTxTest.curTxRRReadKeys.get()).put(key, txId);
      
      ReadLockedKey readKey = (ReadLockedKey) readLockedKeysByRRTx.get(key);
      if (readKey == null) readKey = new ReadLockedKey(key);
      readKey.addKeyByCurTx(txId);
      readLockedKeysByRRTx.put(key, readKey);
    }
    
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
  private ResultSet query(Connection conn, int whichQuery, int cid, Date since)
      throws SQLException {
    int tid = getMyTid();
    return query (conn, whichQuery, cid, since, tid);
  }

  public void query(Connection dConn, Connection gConn) {
    //  for testUniqueKeys both connections are needed
    int whichQuery = rand.nextInt(select.length); //randomly select one query sql
    int cid = rand.nextInt((int) SQLBB.getBB().getSharedCounters().read(SQLBB.tradeCustomersPrimary));
    //Date since = new Date ((rand.nextInt(10)+98),rand.nextInt(12), rand.nextInt(31));
    Date since = getSince();
    ResultSet discRS = null;
    ResultSet gfeRS = null;
    ArrayList<SQLException> exceptionList = new ArrayList<SQLException>();

    for( int i=0 ; i< 10; i++) {
      Log.getLogWriter().info("RR: executing query " + i + "times");
      if (dConn != null) {
        try {
          discRS = query(dConn, whichQuery, cid, since);
          if (discRS == null) {
            Log.getLogWriter().info("could not get the derby result set after retry, abort this query");
            Log.getLogWriter().info("Could not finish the op in derby, will abort this operation in derby");
            if (alterTableDropColumn && SQLTest.alterTableException.get() != null && (Boolean)SQLTest.alterTableException.get() == true)
              ; //do nothing and expect gfxd fail with the same reason due to alter table
            else return;
          }
        } catch (SQLException se) {
          SQLHelper.handleDerbySQLException(se, exceptionList);
        }
        try {
          gfeRS = query(gConn, whichQuery, cid, since);
          if (gfeRS == null) {
            if (isHATest) {
              Log.getLogWriter().info("Testing HA and did not get GFXD result set");
              return;
            } else if (setCriticalHeap) {
              Log.getLogWriter().info("got XCL54 and does not get query result");
              return; //prepare stmt may fail due to XCL54 now
            } /*if (alterTableDropColumn) {
            Log.getLogWriter().info("prepare stmt failed due to missing column");
            return; //prepare stmt may fail due to alter table now
          } */ else
              throw new TestException("Not able to get gfe result set after retry");
          }
        } catch (SQLException se) {
          if (se.getSQLState().equals("X0Z02") && (i < 9)) {
            Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
            continue;
          }
          SQLHelper.handleGFGFXDException(se, exceptionList);
        }
        SQLHelper.handleMissedSQLException(exceptionList);
        if (discRS == null || gfeRS == null) return;

        boolean success = ResultSetHelper.compareResultSets(discRS, gfeRS);
        if (!success) {
          Log.getLogWriter().info("Not able to compare results due to derby server error");
        } //not able to compare results due to derby server error
      }// we can verify resultSet
      else {
        try {
          gfeRS = query(gConn, whichQuery, cid, since);   //could not varify results.
        } catch (SQLException se) {
          if (se.getSQLState().equals("42502") && SQLTest.testSecurity) {
            Log.getLogWriter().info("Got expected no SELECT permission, continuing test");
            return;
          } else if (alterTableDropColumn && se.getSQLState().equals("42X04")) {
            Log.getLogWriter().info("Got expected column not found exception, continuing test");
            return;
          }else if (se.getSQLState().equals("X0Z02") && (i < 9)) {
            Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
            continue;
          }  else SQLHelper.handleSQLException(se);
        }

        try {
          if (gfeRS != null)
            ResultSetHelper.asList(gfeRS, false);
          else if (isHATest)
            Log.getLogWriter().info("could not get gfxd query results after retry due to HA");
          else if (setCriticalHeap)
            Log.getLogWriter().info("could not get gfxd query results after retry due to XCL54");
          else
            throw new TestException("gfxd query returns null and not a HA test");
        } catch (TestException te) {
          if (te.getMessage().contains("Conflict detected in transaction operation and it will abort") && (i < 9)) {
            Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
            continue;
          } else throw te;
        }
      }
      break;
    }

    SQLHelper.closeResultSet(gfeRS, gConn);
  }

}
