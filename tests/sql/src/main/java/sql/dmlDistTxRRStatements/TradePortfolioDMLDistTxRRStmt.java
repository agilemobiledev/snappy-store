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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransactionRollbackException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.gemstone.gemfire.cache.query.Struct;

import sql.SQLBB;
import sql.SQLHelper;
import sql.dmlDistTxStatements.TradePortfolioDMLDistTxStmt;
import sql.sqlTx.ReadLockedKey;
import sql.sqlTx.SQLDistRRTxTest;
import sql.sqlTx.SQLDistTxTest;
import sql.sqlTx.SQLTxBatchingFKBB;
import sql.sqlTx.SQLTxRRReadBB;
import sql.sqlutil.ResultSetHelper;
import util.TestException;
import util.TestHelper;

public class TradePortfolioDMLDistTxRRStmt extends TradePortfolioDMLDistTxStmt {
  /*
    select = {"select * from trade.portfolio where tid = ?",
      "select sid, cid, subTotal from trade.portfolio where (subTotal >? and subTotal <= ?) and tid=? ",
      "select count(distinct cid) as num_distinct_cid from trade.portfolio where (subTotal<? or subTotal >=?) and tid =?",
      "select distinct sid from trade.portfolio where (qty >=? and subTotal >= ?) and tid =?",
      "select sid, cid, qty from trade.portfolio  where (qty >=? and availQty<?) and tid =?",
      "select * from trade.portfolio where sid =? and cid=? and tid = ?",
      "select * from trade.portfolio ",
      "select sid, cid from trade.portfolio where subTotal >? and subTotal <= ? ",
      "select distinct cid from trade.portfolio where subTotal<? or subTotal >=?",
      "select distinct sid from trade.portfolio where qty >=? and subTotal >= ? ",
      "select sid, cid, qty from trade.portfolio  where (qty >=? and availQty<?) ",
      "select * from trade.portfolio where sid =? and cid=?"
      };
  */

  @SuppressWarnings("unchecked")
  @Override
  public boolean insertGfxd(Connection gConn, boolean withDerby) {
    if (!withDerby) {
      return insertGfxdOnly(gConn);

    }
    int size = 1;
    int[] cid = new int[size];
    int[] sid = new int[size];
    int[] qty = new int[size];
    BigDecimal[] sub = new BigDecimal[size];
    BigDecimal[] price = new BigDecimal[size];
    int[] updateCount = new int[size];
    boolean[] expectConflict = new boolean[1];
    Connection nonTxConn = (Connection)SQLDistTxTest.gfxdNoneTxConn.get();
    SQLException gfxdse = null;

    getExistingCidFromCustomers(nonTxConn, cid);
    getDataFromResultSet(nonTxConn, sid, qty, sub, price, size); //get the data
    int chance = 200;
    if (rand.nextInt(chance) == 0) cid[0] = 0;
    else if (rand.nextInt(chance) == 0) sid[0] = 0;

    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    HashSet<String> parentKeysHold = new HashSet<String>();
    try {
      getKeysForInsert(nonTxConn, modifiedKeysByOp, cid[0], sid[0], expectConflict, parentKeysHold);
      /* using batching fk bb now
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

    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();

    for(int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Inserting " + i + " times.");
        insertToGfxdTable(gConn, cid, sid, qty, sub, updateCount, size);
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
              Log.getLogWriter().info("RR: got conflict, retrying the operations ");
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
          if (expectConflict[0] && !se.getSQLState().equals("23505")
              && !se.getSQLState().equals("23503")) {
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
    if (withDerby) addInsertToDerbyTx(cid, sid, qty, sub, updateCount, gfxdse);

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
    
    int numOfNonUniq = 6; //how many select statement is for non unique keys
    int whichQuery = rand.nextInt(select.length-numOfNonUniq); //only uses with tid condition
    
    int qty = 1000;
    int avail = 500;
    int startPoint = 10000;
    int range = 100000; //used for querying subTotal

    BigDecimal subTotal1 = new BigDecimal(Integer.toString(rand.nextInt(startPoint)));
    BigDecimal subTotal2 = subTotal1.add(new BigDecimal(Integer.toString(rand.nextInt(range))));
    int queryQty = rand.nextInt(qty);
    int queryAvail = rand.nextInt(avail);
    int sid = rand.nextInt((int)SQLBB.getBB().getSharedCounters().read(SQLBB.tradeSecuritiesPrimary));
    int cid = rand.nextInt((int) SQLBB.getBB().getSharedCounters().read(SQLBB.tradeCustomersPrimary));

    int tid = testUniqueKeys ? getMyTid() : getRandomTid();
    String sql = null;
    
    ResultSet gfxdRS = null;
    SQLException gfxdse = null;
    List<Struct> noneTxGfxdList = null;
    
    if (whichQuery < 3) whichQuery = whichQuery + 3; //do not hold too many keys to block other txs

    for (int i = 0; i < 10; i++) {
      Log.getLogWriter().info("RR: executing query " + i + "times");
      try {
        gfxdRS = query(gConn, whichQuery, subTotal1, subTotal2, queryQty, queryAvail, sid, cid, tid);
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
        }
        else if (se.getSQLState().equals("X0Z02") && (i <= 9)) {
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
        boolean[] success = new boolean[1];
        success[0] = false;


        if (whichQuery == 3) {
          //select distinct sid from trade.portfolio where (qty >=? and subTotal >= ?) and tid =?
          sql = "select cid, sid from trade.portfolio where (qty >=" + qty +
              " and subTotal >=" + subTotal1 + ") and tid =" + tid;
          while (!success[0]) {
            noneTxGfxdList = getKeysForQuery(sql, success);
          }

          Log.getLogWriter().info("noneTxGfxdList size is " + noneTxGfxdList.size());
          addReadLockedKeys(noneTxGfxdList);
        } else addReadLockedKeys(gfxdList);

        addQueryToDerbyTx(whichQuery, subTotal1, subTotal2,
            queryQty, queryAvail, sid, cid, tid, gfxdList, gfxdse);
        //only the first thread to commit the tx in this round could verify results
        //to avoid phantom read
        //this is handled in the SQLDistTxTest doDMLOp
      } catch (TestException te) {
        if (te.getCause() instanceof SQLTransactionRollbackException && (i <= 9)) {
          Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
          continue;
        } else throw te;
      }
      break;
    }
    return true;
  }  
  
  protected List<Struct> getKeysForQuery(String sql, boolean[] success) {
    Connection noneTxConn = (Connection) SQLDistTxTest.gfxdNoneTxConn.get();
    
    try {
      Log.getLogWriter().info("executing the following query: " + sql);
      ResultSet noneTxGfxdRS = noneTxConn.createStatement().executeQuery(sql);      
      List<Struct> noneTxGfxdList = ResultSetHelper.asList(noneTxGfxdRS, false);
      if (noneTxGfxdList == null && isHATest) {
        Log.getLogWriter().info("Testing HA and did not get GFXD result set");
        success[0] = false;
      } else {
        success[0] = true;
      }
      return noneTxGfxdList;
    } catch (SQLException se) {
      SQLHelper.handleSQLException(se);
    }  
    
    return null; //should not hit this as SQLHelper.handleSQLException(se) throws TestException.
  }
  
  @SuppressWarnings("unchecked")
  protected void addReadLockedKeys(List<Struct> gfxdList) {
    int txId = (Integer) SQLDistRRTxTest.curTxId.get();
    SharedMap readLockedKeysByRRTx = SQLTxRRReadBB.getBB().getSharedMap();   
    
    Log.getLogWriter().info("adding the RR read keys to the Map for " +
        "this txId: " + txId);
    for (int i=0; i<gfxdList.size(); i++) {
      int cid = (Integer) gfxdList.get(i).get("CID");
      int sid = (Integer) gfxdList.get(i).get("SID");
      String key = getTableName()+"_cid_"+cid+"_sid_"+sid;
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
      if (te.getMessage().contains("X0Z02") && !reproduce49935 ) {
        Log.getLogWriter().info("hit #49935, continuing test");
        return false;
      }
       else throw te;
    }
  }

}
