SET CURRENT SCHEMA=SEC_OWNER;

INSERT
INTO SECM_TXN_MANAGEMENT
  (
    BACKOFFICE_CODE,
    TXN_TYPE,
    DESCRIPTION,
    TXN_RETENTION,
    TXN_ORPHAN,
    TXN_HOUSEKEEPING,
    ONBOARD_DATE,
	END_DATE,
    LAST_UPDATE_BY,
    LAST_UPDATE_DATE
  )
  VALUES
  (
    'IPAY',
    'UNKNOWN',
    'General data management for iPay data whose transaction type is N/A (UNKNOWN).',
    100,
    30,
    8,
    CURRENT TIMESTAMP,
	TIMESTAMP('2020-03-19', '00:00:00'),
    'SEC_SYS',
    CURRENT TIMESTAMP
  );

INSERT
INTO SECM_TXN_MANAGEMENT
  (
    BACKOFFICE_CODE,
    TXN_TYPE,
    DESCRIPTION,
    TXN_RETENTION,
    TXN_ORPHAN,
    TXN_HOUSEKEEPING,
    ONBOARD_DATE,
	END_DATE,
    LAST_UPDATE_BY,
    LAST_UPDATE_DATE
  )
  VALUES
  (
    'IPAY',
    'DEBIT',
    'If pay type is in DBT, BCD, BCI, EZD, SDD, CPD, CUD, ITD, ITI, BKD, MXD, NOD, NSD, ZAD',
    100,
    10, 
    8,
    CURRENT TIMESTAMP,
	TIMESTAMP('2020-03-19', '00:00:00'),
    'SEC_SYS',
    CURRENT TIMESTAMP
  );
 
INSERT
INTO SECM_TXN_MANAGEMENT
  (
    BACKOFFICE_CODE,
    TXN_TYPE,
    DESCRIPTION,
    TXN_RETENTION,
    TXN_ORPHAN,
    TXN_HOUSEKEEPING,
    ONBOARD_DATE,
	END_DATE,
    LAST_UPDATE_BY,
    LAST_UPDATE_DATE
  )
  VALUES
  (
    'IPAY',
    'CREDIT',
    'If pay type is in CRD, BCC, EZC, FCP, CPC, CUC, MXS, MXT, ZAH, NOH,  BKP, ITC, NOC, NOS, ZAC',
    100,
    20, 
    8,
    CURRENT TIMESTAMP,
	TIMESTAMP('2020-03-19', '00:00:00'),
    'SEC_SYS',
    CURRENT TIMESTAMP
  );

INSERT
INTO SECM_TXN_MANAGEMENT
  (
    BACKOFFICE_CODE,
    TXN_TYPE,
    DESCRIPTION,
    TXN_RETENTION,
    TXN_ORPHAN,
    TXN_HOUSEKEEPING,
    ONBOARD_DATE,
	END_DATE,
    LAST_UPDATE_BY,
    LAST_UPDATE_DATE
  )
  VALUES
  (
    'PAY METHOD DEFAULT',
    'UNKNOWN',
    'General data management for PAY METHOD DEFAULT backoffice code whose transaction type is N/A.',
    100,
    30,
    8,
    CURRENT TIMESTAMP,
	TIMESTAMP('2020-03-19', '00:00:00'),
    'SEC_SYS',
    CURRENT TIMESTAMP
  );
  
INSERT
INTO SECM_TXN_MANAGEMENT
  (
    BACKOFFICE_CODE,
    TXN_TYPE,
    DESCRIPTION,
    TXN_RETENTION,
    TXN_ORPHAN,
    TXN_HOUSEKEEPING,
    ONBOARD_DATE,
	END_DATE,
    LAST_UPDATE_BY,
    LAST_UPDATE_DATE
  )
  VALUES
  (
    'Unknown BackOffice System',
    'UNKNOWN',
    'General data management for Unknown BackOffice System whose transaction type is N/A (UNKNOWN).',
    100,
    30,
    8,
    CURRENT TIMESTAMP,
	TIMESTAMP('2020-03-19', '00:00:00'),
    'SEC_SYS',
    CURRENT TIMESTAMP
  ); 