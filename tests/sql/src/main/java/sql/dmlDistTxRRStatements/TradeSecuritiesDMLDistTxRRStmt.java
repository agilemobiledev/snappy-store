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
import hydra.TestConfig;
import hydra.blackboard.SharedMap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.gemstone.gemfire.cache.query.Struct;

import sql.SQLBB;
import sql.SQLHelper;
import sql.SQLPrms;
import sql.SQLTest;
import sql.dmlDistTxStatements.TradeSecuritiesDMLDistTxStmt;
import sql.sqlTx.ReadLockedKey;
import sql.sqlTx.SQLDistRRTxTest;
import sql.sqlTx.SQLDistTxTest;
import sql.sqlTx.SQLTxRRReadBB;
import sql.sqlutil.ResultSetHelper;
import util.TestException;
import util.TestHelper;

public class TradeSecuritiesDMLDistTxRRStmt extends
    TradeSecuritiesDMLDistTxStmt {
  /*
  select = {"select * from trade.securities where tid = ?",
      "select avg( distinct price) as avg_distinct_price from trade.securities where tid=? and symbol >?", 
       "select price, symbol, exchange from trade.securities where (price<? or price >=?) and tid =?",    
       "select sec_id, symbol, price, exchange from trade.securities  where (price >=? and price<?) and exchange =? and tid =?",
       "select * from trade.securities where sec_id = ?",
       "select sec_id, price, symbol from trade.securities where symbol >?",
        "select price, symbol, exchange from trade.securities where (price<? or price >=?) ",
        "select sec_id, symbol, price, exchange from trade.securities  where (price >=? and price<?) and exchange =?"
        };
  */
  protected static boolean reproduce39455 = TestConfig.tab().booleanAt(SQLPrms.toReproduce39455, false);


  @SuppressWarnings("unchecked")
  @Override
  public boolean insertGfxd(Connection gConn, boolean withDerby) {
    if (!withDerby) {
      return insertGfxdOnly(gConn);
    }
    int size = 1;
    int[] sec_id = new int[size];
    String[] symbol = new String[size];
    String[] exchange = new String[size];
    BigDecimal[] price = new BigDecimal[size];
    int[] updateCount = new int[size];
    SQLException gfxdse = null;

    getDataForInsert(sec_id, symbol, exchange, price, size); //get the data
    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    modifiedKeysByOp.put(getTableName()+"_"+sec_id[0], (Integer)SQLDistTxTest.curTxId.get());

    //when two tx insert/update could lead to unique key constraint violation,
    //only one tx could hold the unique key lock

    //if unique key already exists, committed prior to this round, it should get
    //unique key constraint violation, not conflict or lock not held exception
    modifiedKeysByOp.put(getTableName()+"_unique_"+symbol[0] + "_" + exchange[0],
        (Integer)SQLDistTxTest.curTxId.get());
    Log.getLogWriter().info("gemfirexd - TXID:" + (Integer)SQLDistTxTest.curTxId.get()+ " need to hold the unique key "
        + getTableName()+"_unique_"+symbol[0] + "_" + exchange[0]);

    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get(); //no need to check fk, as securities is a parent table

    for( int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Inserting " + i + " times.");
        insertToGfxdTable(gConn, sec_id, symbol, exchange, price, updateCount, size);
      } catch (SQLException se) {
        SQLHelper.printSQLException(se);
        if (se.getSQLState().equalsIgnoreCase("X0Z02")) {
          try {
            if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, se, true);
            else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, se, hasSecondary, true);
          }
          catch(TestException te){
            if (te.getMessage().contains("but got conflict exception") && i < 9) {
              Log.getLogWriter().info("RR: got conflict, retrying the operations ");
              continue;
            }
            else throw te;
          }
          return false;
        } else if (gfxdtxHANotReady && isHATest &&
            SQLHelper.gotTXNodeFailureException(se)) {
          SQLHelper.printSQLException(se);
          Log.getLogWriter().info("gemfirexd - TXID:" + (Integer)SQLDistTxTest.curTxId.get() + " got node failure exception during Tx with HA support, continue testing");
          return false;
        } else {
          gfxdse = se;
        }
      }
      break;
    }

    if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, gfxdse, false);
    else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, gfxdse, hasSecondary, false);

    //add this operation for derby
    addInsertToDerbyTx(sec_id, symbol, exchange, price, updateCount, gfxdse);

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
    int whichUpdate= getWhichUpdate(rand.nextInt(update.length));
    int size = 1;
    int[] sec_id = new int[size];
    String[] symbol = new String[size];
    String[] exchange = new String[size];
    BigDecimal[] price = new BigDecimal[size];
    String[] lowEnd = new String[size];
    String[] highEnd = new String[size];
    Connection nonTxConn = (Connection)SQLDistTxTest.gfxdNoneTxConn.get();

    boolean[] expectConflict = new boolean[1];

    getDataForUpdate(gConn, sec_id, symbol, exchange, price, size); //get the data
    for (int i = 0 ; i <size ; i++) {
      sec_id[i]= getExistingSid(); //to avoid phantom read
    }
    getSidForTx(nonTxConn, sec_id);
    getAdditionalUpdateData(lowEnd, highEnd);

    getExistingSidFromSecurities(nonTxConn, sec_id); //any sec_id already committed
    if (isHATest && sec_id[0] == 0) {
      Log.getLogWriter().info("gemfirexd - TXID:" + (Integer)SQLDistTxTest.curTxId.get()  + "could not get valid sec_id, abort this op");
      return true;
    }
    int[] updateCount = new int[size];
    SQLException gfxdse = null;

    int txId = (Integer)SQLDistTxTest.curTxId.get();

    //TODO, need to consider the following configuration: isWanTest && !isSingleSitePublisher
    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();

    /* handled in actual dml op instead
    if (SQLTest.testPartitionBy) {
      PreparedStatement stmt = getCorrectTxStmt(gConn, whichUpdate);
      if (stmt == null) {
        if (SQLTest.isEdge && isHATest && !isTicket48176Fixed &&
            batchingWithSecondaryData &&(Boolean) SQLDistTxTest.failedToGetStmtNodeFailure.get()) {
          SQLDistTxTest.failedToGetStmtNodeFailure.set(false);
          return false; //due to node failure, need to rollback tx
        }
        else return true; //due to unsupported exception
      }
    }
    */

    try {
      getKeysForUpdate(nonTxConn, modifiedKeysByOp, whichUpdate,
          sec_id[0], lowEnd[0], highEnd[0], expectConflict);

      /* tracked in batching fk bb now
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
    //when two tx insert/update could lead to unique key constraint violation,
    //only one tx could hold the unique key lock

    //if unique key already exists, committed prior to this round, it should get
    //unique key constraint violation, not conflict or lock not held exception
    if (whichUpdate == 2) {
      //"update trade.securities set symbol = ?, exchange =? where sec_id = ?",
      //add the unique key going to be held
      modifiedKeysByOp.put(getTableName()+"_unique_"+symbol[0] + "_" + exchange[0],
          txId);
      Log.getLogWriter().info("need to hold the unique key "
          + getTableName()+"_unique_"+symbol[0] + "_" + exchange[0]);
      //should also hold the lock for the current unique key as well.
      //using the current gConn, so the read will reflect the modification within tx as well

      //TODO may need to reconsider this for RR case after #43170 is fixed -- it is
      //possible an update failed due to unique key violation and does not rollback
      //the previous operations and the RR read lock on the key may cause commit issue
      //may need to add to the RR read locked keys once #43170 is fixed.
      try {
        String sql = "select symbol, exchange from trade.securities where sec_id = " + sec_id[0];
        ResultSet rs = nonTxConn.createStatement().executeQuery(sql);
        if (rs.next()) {
          String symbolKey = rs.getString("SYMBOL");
          String exchangeKey = rs.getString("EXCHANGE");
          Log.getLogWriter().info("gemfirexd - TXID:" + (Integer)SQLDistTxTest.curTxId.get()+ " should hold the unique key "
              + getTableName()+"_unique_"+symbolKey+"_"+exchangeKey);
          modifiedKeysByOp.put(getTableName()+"_unique_"+symbolKey+"_"+exchangeKey, txId);
        }
        rs.close();
      } catch (SQLException se) {
        if (!SQLHelper.checkGFXDException(gConn, se)) {
          Log.getLogWriter().info("gemfirexd - TXID:" + (Integer)SQLDistTxTest.curTxId.get()+ "node failure in HA test, abort this operation");
          return false;
        } else SQLHelper.handleSQLException(se);
      }
    }

    //update will hold the keys to track foreign key constraint conflict
    if (batchingWithSecondaryData && !ticket42672fixed && isSecuritiesPartitionedOnPKOrReplicate()) {
      HashSet<String> holdParentKeyByThisTx = (HashSet<String>) SQLDistTxTest.parentKeyHeldWithBatching.get();
      holdParentKeyByThisTx.addAll(modifiedKeysByOp.keySet());
      SQLDistTxTest.parentKeyHeldWithBatching.set(holdParentKeyByThisTx);

      SQLBB.getBB().getSharedMap().put(parentKeyHeldTxid + txId, holdParentKeyByThisTx);
      //used to track actual parent keys hold by update, work around #42672 & #50070
    }

    for (int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Updating " + i + " times.");
        updateGfxdTable(gConn, sec_id, symbol, exchange, price,
            lowEnd, highEnd, whichUpdate, updateCount, size);

        //handles get stmt failure conditions -- node failure or unsupported update on partition field
        if (isHATest && (Boolean)SQLDistTxTest.failedToGetStmtNodeFailure.get()) {
          SQLDistTxTest.failedToGetStmtNodeFailure.set(false); //reset flag
          return false; //due to node failure, assume txn rolled back
        }
        if ((Boolean)SQLDistTxTest.updateOnPartitionCol.get()) {
          SQLDistTxTest.updateOnPartitionCol.set(false); //reset flag
          return true; //assume 0A000 exception does not cause txn to rollback,
        }

        if (expectConflict[0] && !batchingWithSecondaryData) {
          throw new TestException("expected to get conflict exception due to foreign key constraint, but does not." +
              " Please check the logs for more information");
        }
      } catch (SQLException se) {
        SQLHelper.printSQLException(se);
        if (se.getSQLState().equalsIgnoreCase("X0Z02")) {
          if (expectConflict[0]) {
            Log.getLogWriter().info("got conflict exception due to #42672, continuing testing"); //if conflict caused by foreign key, this is a workaround due to #42672
          } else {
            try {
              if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, se, true);
              else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, se, hasSecondary, true);
            }
            catch(TestException te){
              if (te.getMessage().contains("but got conflict exception") && i < 9) {
                Log.getLogWriter().info("RR: got conflict, retrying the operations ");
                continue;
              }
              else throw te;
            }
          }
          return false;
        } else if (gfxdtxHANotReady && isHATest &&
            SQLHelper.gotTXNodeFailureException(se)) {
          SQLHelper.printSQLException(se);
          Log.getLogWriter().info("gemfirexd - TXID:" + (Integer)SQLDistTxTest.curTxId.get() + "got node failure exception during Tx with HA support, continue testing");
          return false;
        } else {
          gfxdse = se;
        }
      }
      break;
    }

    if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, gfxdse, false);
    else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, gfxdse, hasSecondary, false);

    //add this operation for derby
    addUpdateToDerbyTx(sec_id, symbol, exchange, price,
        lowEnd, highEnd, whichUpdate, updateCount, gfxdse);

    if (gfxdse == null) {
      modifiedKeysByTx.putAll(modifiedKeysByOp);
      SQLDistTxTest.curTxModifiedKeys.set(modifiedKeysByTx);
    } //add the keys if no SQLException is thrown during the operation
    return true;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean deleteGfxd(Connection gConn, boolean withDerby) {
    if (!withDerby) {
      return deleteGfxdOnly(gConn);
    }
    int whichDelete = rand.nextInt(delete.length);
    whichDelete = getWhichDelete(whichDelete);
    int minLen = 2;
    int maxLen = 3;
    String symbol = getSymbol(minLen, maxLen) + '%';
    String exchange = getExchange();
    int[] sec_id = new int[1];
    int[] updateCount = new int[1];
    boolean[] expectConflict = new boolean[1];
    int tid = getMyTid();
    Connection nonTxConn = (Connection)SQLDistTxTest.gfxdNoneTxConn.get();
    SQLException gfxdse = null;
    getExistingSidFromSecurities(nonTxConn, sec_id);

    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    try {
      getKeysForDelete(nonTxConn, modifiedKeysByOp, whichDelete, sec_id[0], symbol,
          exchange, tid, expectConflict); //track foreign key constraints and key conflict

      /* no longer needed as foreign key lock check will be check at
       * the commit time or a later op time when batching is enabled
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

    //add modified parent keys to threadlocal
    if (batchingWithSecondaryData) {
      HashSet<String> holdParentKeyByThisTx = (HashSet<String>) SQLDistTxTest.parentKeyHeldWithBatching.get();
      holdParentKeyByThisTx.addAll(modifiedKeysByOp.keySet());
      SQLDistTxTest.parentKeyHeldWithBatching.set(holdParentKeyByThisTx);

      int txId = (Integer)SQLDistTxTest.curTxId.get();
      SQLBB.getBB().getSharedMap().put(parentKeyHeldTxid + txId, holdParentKeyByThisTx);
      //used to track actual parent keys hold by delete/update, work around #42672 & #50070
    }

    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();

    for( int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Deleting " + i + " times");
        deleteFromGfxdTable(gConn, sec_id[0], symbol, exchange, tid, whichDelete, updateCount);
      } catch (SQLException se) {
        SQLHelper.printSQLException(se);
        if (se.getSQLState().equalsIgnoreCase("X0Z02")) {
          if (expectConflict[0]) {
            Log.getLogWriter().info("got expected conflict exception due to foreign key constraint, continuing testing");  //if conflict caused by foreign key
          } else {
            try {
              if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, se, true);
              else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, se, hasSecondary, true);
            } catch (TestException te) {
              if (te.getMessage().contains("but got conflict exception") && i < 9) {
                Log.getLogWriter().info("Got conflict exception, retrying the op");
                continue;
              } else throw te;
            }
            //check if conflict caused by multiple inserts on the same keys
          }
          for (String key : modifiedKeysByOp.keySet()) {
            Log.getLogWriter().info("key is " + key);
            if (!key.contains("unique")) {
              int sid = Integer.parseInt(key.substring(key.indexOf("_") + 1));
              removeWholeRangeForeignKeys(sid);
              if (batchingWithSecondaryData) cleanUpFKHolds(); //got the exception, ops are rolled back due to #43170
            }
          }
          return false;
        } else if (SQLHelper.gotTXNodeFailureException(se)) {
          if (gfxdtxHANotReady && isHATest) {
            SQLHelper.printSQLException(se);
            Log.getLogWriter().info("got node failure exception during Tx with HA support, continue testing");

            for (String key : modifiedKeysByOp.keySet()) {
              Log.getLogWriter().info("key is " + key);
              if (!key.contains("unique")) {
                int sid = Integer.parseInt(key.substring(key.indexOf("_") + 1));
                removeWholeRangeForeignKeys(sid);
                if (batchingWithSecondaryData) cleanUpFKHolds(); //got the exception, ops are rolled back due to #43170
              }
            }

            return false;
          } else throw new TestException("got node failure exception in a non HA test" + TestHelper.getStackTrace(se));
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
          for (String key : modifiedKeysByOp.keySet()) {
            if (key != null && !key.contains("_unique_")) {
              String s = key.substring(key.indexOf("_") + 1);
              int sid = Integer.parseInt(s);
              removeWholeRangeForeignKeys(sid); //operation failed, does not hold foreign key
              if (batchingWithSecondaryData) cleanUpFKHolds(); //got the exception, ops are rolled back due to #43170
            }
          }
        }
      }
      break;
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
    //add this operation for derby
    addDeleteToDerbyTx(sec_id[0], symbol, exchange, tid,
        whichDelete, updateCount[0], gfxdse);

    modifiedKeysByTx.putAll(modifiedKeysByOp);
    SQLDistTxTest.curTxModifiedKeys.set(modifiedKeysByTx);
    return true;
  }

  protected boolean verifyConflict(HashMap<String, Integer> modifiedKeysByOp,
      HashMap<String, Integer>modifiedKeysByThisTx, SQLException gfxdse,
      boolean getConflict) {
    return verifyConflictForRR(modifiedKeysByOp, modifiedKeysByThisTx, gfxdse, getConflict);
  }

  //query database using a randomly chosen select statement
  public void query(Connection dConn, Connection gConn) {
    int numOfNonUniq = 4; //how many select statement is for non unique keys, non uniq query must be at the end
    int whichQuery = getWhichOne(numOfNonUniq, select.length); //randomly select one query sql based on test uniq or not

    int sec_id = rand.nextInt((int)SQLBB.getBB().getSharedCounters().read(SQLBB.tradeSecuritiesPrimary));
    String symbol = getSymbol();
    BigDecimal price = getPrice();
    String exchange = getExchange();
    int tid = getMyTid();
    ResultSet discRS = null;
    ResultSet gfeRS = null;
    ArrayList<SQLException> exceptionList = new ArrayList<SQLException>();
    for (int i = 0; i < 10; i++) {
      Log.getLogWriter().info("RR: executing query " + i + "times");
      if (dConn != null) {
        try {
          discRS = query(dConn, whichQuery, sec_id, symbol, price, exchange, tid);
          if (discRS == null) {
            Log.getLogWriter().info("could not get the derby result set after retry, abort this query");
            if (alterTableDropColumn && SQLTest.alterTableException.get() != null && (Boolean)SQLTest.alterTableException.get() == true)
              ; //do nothing, expect gfxd fail with the same reason due to alter table
            else return;
          }
        } catch (SQLException se) {
          SQLHelper.handleDerbySQLException(se, exceptionList);
        }
        try {
          gfeRS = query(gConn, whichQuery, sec_id, symbol, price, exchange, tid);
          if (gfeRS == null) {
            if (isHATest) {
              Log.getLogWriter().info("Testing HA and did not get GFXD result set after retry");
              return;
            } else if (setCriticalHeap) {
              Log.getLogWriter().info("got XCL54 and does not get query result");
              return; //prepare stmt may fail due to XCL54 now
            } else
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
          Log.getLogWriter().info("Not able to compare results, continuing test");
        } //not able to compare results due to derby server error

      }// we can verify resultSet
      else {
        try {
          Log.getLogWriter().info("RR: executing query " + i + " times.");
          gfeRS = query(gConn, whichQuery, sec_id, symbol, price, exchange, tid);
        } catch (SQLException se) {
          if (se.getSQLState().equals("42502") && SQLTest.testSecurity) {
            Log.getLogWriter().info("Got expected no SELECT permission, continuing test");
            return;
          } else if (alterTableDropColumn && se.getSQLState().equals("42X04")) {
            Log.getLogWriter().info("Got expected column not found exception, continuing test");
            return;
          } else if (se.getSQLState().equals("X0Z02") && (i < 9)) {
            Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
            continue;
          }
          else SQLHelper.handleSQLException(se);
        }

        if (gfeRS != null) {
          try {
            ResultSetHelper.asList(gfeRS, false);
          } catch (TestException te) {
            if (te.getMessage().contains("Conflict detected in transaction operation and it will abort") && (i <9)) {
              Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
              continue;
            } else throw te;
          }
        } else if (isHATest)
          Log.getLogWriter().info("could not get gfxd query results after retry due to HA");
        else if (setCriticalHeap)
          Log.getLogWriter().info("could not get gfxd query results after retry due to XCL54");
        else
          throw new TestException("gfxd query returns null and not a HA test");
      }
      break;
    }

    SQLHelper.closeResultSet(gfeRS, gConn);
  }

  public boolean queryGfxd(Connection gConn, boolean withDerby){
    if (!withDerby) {
      return queryGfxdOnly(gConn);
    }    
    
    int whichQuery = rand.nextInt(select.length); //randomly select one query sql
    
    if (whichQuery > 3) {
      whichQuery = whichQuery-4; //avoid to hold all the keys to block other tx in the RR tests
    }
    
    int sec_id = rand.nextInt((int)SQLBB.getBB().getSharedCounters().read(SQLBB.tradeSecuritiesPrimary));
    String symbol = getSymbol();
    BigDecimal price = getPrice();
    BigDecimal price1 = price.add(new BigDecimal(rangePrice));
    String exchange = getExchange();
    int tid = testUniqueKeys ? getMyTid() : getRandomTid();
    String sql = null;
    
    ResultSet gfxdRS = null;
    SQLException gfxdse = null;
    List<Struct> noneTxGfxdList = null;
    
    if (!reproduce39455 && whichQuery == 1) whichQuery--; //hitting #39455, need to remove this line and uncomment the following once #39455 is fixed
    
    try {
      gfxdRS = query (gConn, whichQuery, sec_id, symbol, price, exchange, tid);   
      if (gfxdRS == null) {
        if (isHATest) {
          Log.getLogWriter().info("Testing HA and did not get GFXD result set");
          return true;
        }
        else     
          throw new TestException("Not able to get gfxd result set");
      }
    } catch (SQLException se) {
      if (isHATest &&
        SQLHelper.gotTXNodeFailureException(se) ) {
        SQLHelper.printSQLException(se);
        Log.getLogWriter().info("got node failure exception during Tx with HA support, continue testing");
        return false; //assume node failure exception causes the tx to rollback 
      }
      
      SQLHelper.printSQLException(se);
      gfxdse = se;
    }
    
    List<Struct> gfxdList = ResultSetHelper.asList(gfxdRS, false);
    if (gfxdList == null && isHATest) {
      Log.getLogWriter().info("Testing HA and did not get GFXD result set");
      return true; //do not compare query results as gemfirexd does not get any
    }    
    boolean[] success = new boolean[1];
    success[0] = false;
    
    
    if (whichQuery == 1) { //after #39455 is fixed
      //"select avg( distinct price) as avg_distinct_price from trade.securities where tid=? and symbol >?", 
      sql = "select sec_id from trade.securities where tid=" +tid + " and symbol > '" + symbol +"'" ;  
      while (!success[0]) { 
        noneTxGfxdList = getKeysForQuery(sql, success);
      } 
    } else if (whichQuery == 2 ) {
      // "select price, symbol, exchange from trade.securities where (price<? or price >=?) and tid =?",
      sql = "select sec_id from trade.securities where (price<" + price +
      		" or price >= " + price1 + ") and tid =" + tid;
      while (!success[0]) { 
        noneTxGfxdList = getKeysForQuery(sql, success);
      } 
    } 
    
    if (whichQuery == 1 ||  whichQuery == 2) {
      Log.getLogWriter().info("noneTxGfxdList size is " + noneTxGfxdList.size());
      addReadLockedKeys(noneTxGfxdList);
    }
    else
      addReadLockedKeys(gfxdList);
    
    addQueryToDerbyTx(whichQuery, sec_id, symbol, price, exchange, 
        tid, gfxdList, gfxdse);
    //only the first thread to commit the tx in this round could verify results
    //to avoid phantom read
    //this is handled in the SQLDistTxTest doDMLOp
    
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
      int sid = (Integer) gfxdList.get(i).get("SEC_ID");
      String key = getTableName()+"_"+sid;
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
