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
import java.util.HashMap;
import java.util.List;

import com.gemstone.gemfire.cache.query.Struct;

import sql.SQLHelper;
import sql.dmlDistTxStatements.TradeNetworthDMLDistTxStmt;
import sql.sqlTx.ReadLockedKey;
import sql.sqlTx.SQLDistRRTxTest;
import sql.sqlTx.SQLDistTxTest;
import sql.sqlTx.SQLTxRRReadBB;
import sql.sqlutil.ResultSetHelper;
import util.TestException;

public class TradeNetworthDMLDistTxRRStmt extends TradeNetworthDMLDistTxStmt {
  protected boolean verifyConflict(HashMap<String, Integer> modifiedKeysByOp, 
      HashMap<String, Integer>modifiedKeysByThisTx, SQLException gfxdse,
      boolean getConflict) {
    return verifyConflictForRR(modifiedKeysByOp, modifiedKeysByThisTx, gfxdse, getConflict);
  }

  @SuppressWarnings("unchecked")
  public boolean insertGfxd(Connection gConn, boolean withDerby, int cid){
    if (!withDerby) {
      return insertGfxdOnly(gConn, cid);
    }
    int size = 1;
    BigDecimal[] cash = new BigDecimal[size];
    int[] loanLimit = new int[size];
    BigDecimal[] availLoan = new BigDecimal[size];
    int[] updateCount = new int[size];
    getDataForInsert(cash, loanLimit, availLoan, size);
    BigDecimal securities = new BigDecimal(Integer.toString(0));
    SQLException gfxdse = null;

    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    modifiedKeysByOp.put(getTableName()+"_"+cid, (Integer)SQLDistTxTest.curTxId.get());
    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();
    for(int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Inserting " + i + " times.");
        insertToGfxdTable(gConn, cid, cash, securities, loanLimit, availLoan, updateCount, size);
        break;
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
          gfxdse = se; //added the test case for duplicate cid
        }
      }
    }

    if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, gfxdse, false);
    else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, gfxdse, hasSecondary, false);

    //add this operation for derby
    addInsertToDerbyTx(cid, cash, loanLimit, availLoan, securities, updateCount, gfxdse);

    modifiedKeysByTx.putAll(modifiedKeysByOp);
    SQLDistTxTest.curTxModifiedKeys.set(modifiedKeysByTx);
    return true;
  }

  @SuppressWarnings("unchecked")
  public boolean updateGfxd(Connection gConn, boolean withDerby){
    if (!withDerby) {
      return updateGfxdOnly(gConn);
    }
    int size =1;
    int[] whichUpdate = new int[size];
    int[] cid = new int[size];
    BigDecimal[] availLoanDelta = new BigDecimal[size];
    BigDecimal[] sec = new BigDecimal[size];
    BigDecimal[] cashDelta = new BigDecimal[size];
    int[] newLoanLimit = new int[size];
    int[] updateCount = new int[size];
    SQLException gfxdse = null;

    Connection nonTxConn = (Connection)SQLDistTxTest.gfxdNoneTxConn.get();
    getDataForUpdate(nonTxConn, cid,
        availLoanDelta, sec, cashDelta, newLoanLimit, whichUpdate, size);
    //no update on a newly inserted cid in the same round of tx
    int tid = rand.nextInt(SQLDistTxTest.numOfWorkers);
    cid[0] = getCidFromQuery((Connection)SQLDistTxTest.gfxdNoneTxConn.get(),
        (rand.nextBoolean()? getMyTid() : tid)); //get random cid

    //Separate update to two groups, commit earlier could update certain sql.
    whichUpdate[0] = getWhichUpdate(whichUpdate[0]);
    /* needs to be handed in actual dml op later
    if (SQLTest.testPartitionBy) {
      PreparedStatement stmt = getCorrectTxStmt(gConn, whichUpdate[0], null);
      if (stmt == null) {
        if (SQLTest.isEdge && isHATest && !isTicket48176Fixed &&
            batchingWithSecondaryData &&(Boolean) SQLDistTxTest.failedToGetStmt.get()) {
          SQLDistTxTest.failedToGetStmt.set(false);
          return false; //due to node failure, need to rollback tx
        }
        else return true; //due to unsupported exception
      }
    } */

    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();

    try {
      getKeysForUpdate(nonTxConn, modifiedKeysByOp, whichUpdate[0], cid[0], sec[0]);
    } catch (SQLException se) {
      SQLHelper.printSQLException(se);
      if (se.getSQLState().equals("X0Z01") && isHATest) { // handles HA issue for #41471
        Log.getLogWriter().warning("Not able to process the keys for this op due to HA, this insert op does not proceed");
        return true; //not able to process the keys due to HA, it is a no op
      } else SQLHelper.handleSQLException(se);
    }

    for( int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: Updating " + i + " times.");
        updateGfxdTable(gConn, cid, availLoanDelta, sec, cashDelta,
            newLoanLimit, whichUpdate, updateCount, size);

        if (isHATest && (Boolean)SQLDistTxTest.failedToGetStmtNodeFailure.get()) {
          SQLDistTxTest.failedToGetStmtNodeFailure.set(false); //reset flag
          return false; //due to node failure, assume txn rolled back
        }

        //will not update on partition column in txn unless is specifically set
        if ((Boolean)SQLDistTxTest.updateOnPartitionCol.get()) {
          SQLDistTxTest.updateOnPartitionCol.set(false); //reset flag
          return true;
          //assume 0A000 exception does not cause txn to rollback when allowUpdateOnPartitionColumn set to true.
          //
          //when allowUpdateOnPartitionColumn set to  false, the get stmt for update is not executed
          //needs to return true here.
        }
        break;
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
    }
    if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, gfxdse, false);
    else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, gfxdse, hasSecondary, false);

    //add this operation for derby
    addUpdateToDerbyTx(cid, availLoanDelta, sec, cashDelta,
        newLoanLimit, whichUpdate, updateCount, gfxdse);

    if (gfxdse == null) {
      modifiedKeysByTx.putAll(modifiedKeysByOp);
      SQLDistTxTest.curTxModifiedKeys.set(modifiedKeysByTx);
    } //add the keys if no SQLException is thrown during the operation
    return true;
  }

  @SuppressWarnings("unchecked")
  public boolean deleteGfxd(Connection gConn, boolean withDerby){
    if (!withDerby) {
      return deleteGfxdOnly(gConn);
    }

    int whichDelete = rand.nextInt(delete.length);

    int cid = getExistingCid();
    int cid1 = getExistingCid();
    int[] updateCount = new int[1];
    SQLException gfxdse = null;

    HashMap<String, Integer> modifiedKeysByOp = new HashMap<String, Integer>();
    HashMap<String, Integer> modifiedKeysByTx = (HashMap<String, Integer>)
        SQLDistTxTest.curTxModifiedKeys.get();
    Connection nonTxConn = (Connection)SQLDistTxTest.gfxdNoneTxConn.get();

    try {
      getKeysForDelete(nonTxConn, modifiedKeysByOp, whichDelete, cid, cid1);
    } catch (SQLException se) {
      SQLHelper.printSQLException(se);
      if (se.getSQLState().equals("X0Z01") && isHATest) { // handles HA issue for #41471
        Log.getLogWriter().warning("Not able to process the keys for this op due to HA, this insert op does not proceed");
        return true; //not able to process the keys due to HA, it is a no op
      } else SQLHelper.handleSQLException(se);
    }

    for(int i=0; i< 10; i++) {
      try {
        Log.getLogWriter().info("RR: deleting "+ i + " times");
        deleteFromGfxdTable(gConn, cid, cid1, whichDelete, updateCount);
        break;
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
          gfxdse = se; //security testing may get exception
        }
      }
    }

    if (!batchingWithSecondaryData) verifyConflict(modifiedKeysByOp, modifiedKeysByTx, gfxdse, false);
    else verifyConflictWithBatching(modifiedKeysByOp, modifiedKeysByTx, gfxdse, hasSecondary, false);

    //add this operation for derby
    addDeleteToDerbyTx(cid, cid1, whichDelete, updateCount[0], gfxdse);

    modifiedKeysByTx.putAll(modifiedKeysByOp);
    SQLDistTxTest.curTxModifiedKeys.set(modifiedKeysByTx);
    return true;

  }

  public boolean queryGfxd(Connection gConn, boolean withDerby){
    if (!withDerby) {
      return queryGfxdOnly(gConn);
    }
    
    int whichQuery = rand.nextInt(select.length-numOfNonUniq); //only uses with tid condition
    int cash = 100000;
    int sec = 100000;
    int tid = testUniqueKeys ? getMyTid() : getRandomTid();
    int loanLimit = loanLimits[rand.nextInt(loanLimits.length)];
    BigDecimal loanAmount = new BigDecimal (Integer.toString(rand.nextInt(loanLimit)));
    BigDecimal queryCash = new BigDecimal (Integer.toString(rand.nextInt(cash)));
    BigDecimal querySec= new BigDecimal (Integer.toString(rand.nextInt(sec)));
    ResultSet gfxdRS = null;
    SQLException gfxdse = null;

    for (int i = 0; i < 10; i++) {
      try {
        Log.getLogWriter().info("RR: executing query " + i + "times");
        gfxdRS = query(gConn, whichQuery, queryCash, querySec, loanLimit, loanAmount, tid);
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
        }else if (se.getSQLState().equals("X0Z02") && (i <= 9)) {
          Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
          continue;
        }

        SQLHelper.printSQLException(se);
        gfxdse = se;
      }

      try {
        List<Struct> gfxdList = ResultSetHelper.asList(gfxdRS, false);
        if (gfxdList == null) {
          if (isHATest) {
            Log.getLogWriter().info("Testing HA and did not get GFXD result set");
            return true; //do not compare query results as gemfirexd does not get any
          } else
            throw new TestException("Did not get gfxd results and it is not HA test");
        }

        addReadLockedKeys(gfxdList);

        addQueryToDerbyTx(whichQuery, queryCash, querySec, loanLimit, loanAmount,
            tid, gfxdList, gfxdse);
      } catch (TestException te) {
        if (te.getMessage().contains("Conflict detected in transaction operation and it will abort") && (i <=9)) {
          Log.getLogWriter().info("RR: Retrying the query as we got conflicts");
          continue;
        } else throw te;
      }
      //only the first thread to commit the tx in this round could verify results
      //this is handled in the SQLDistTxTest doDMLOp
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

}
