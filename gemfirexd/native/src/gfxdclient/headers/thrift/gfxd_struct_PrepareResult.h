/**
 * Autogenerated by Thrift Compiler (0.9.1)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */

#ifndef GFXD_STRUCT_PREPARERESULT_H
#define GFXD_STRUCT_PREPARERESULT_H


#include "gfxd_types.h"

#include "gfxd_struct_FieldDescriptor.h"
#include "gfxd_struct_Decimal.h"
#include "gfxd_struct_Timestamp.h"
#include "gfxd_struct_FieldValue.h"
#include "gfxd_struct_PDXNode.h"
#include "gfxd_struct_PDXObject.h"
#include "gfxd_struct_PDXSchemaNode.h"
#include "gfxd_struct_PDXSchema.h"
#include "gfxd_struct_JSONField.h"
#include "gfxd_struct_JSONNode.h"
#include "gfxd_struct_JSONObject.h"
#include "gfxd_struct_BlobChunk.h"
#include "gfxd_struct_ClobChunk.h"
#include "gfxd_struct_ServiceMetaData.h"
#include "gfxd_struct_ServiceMetaDataArgs.h"
#include "gfxd_struct_OpenConnectionArgs.h"
#include "gfxd_struct_ConnectionProperties.h"
#include "gfxd_struct_HostAddress.h"
#include "gfxd_struct_GFXDExceptionData.h"
#include "gfxd_struct_StatementAttrs.h"
#include "gfxd_struct_DateTime.h"
#include "gfxd_struct_ColumnValue.h"
#include "gfxd_struct_ColumnDescriptor.h"
#include "gfxd_struct_Row.h"
#include "gfxd_struct_OutputParameter.h"
#include "gfxd_struct_RowSet.h"

namespace com { namespace pivotal { namespace gemfirexd { namespace thrift {

typedef struct _PrepareResult__isset {
  _PrepareResult__isset() : resultSetMetaData(false), warnings(false) {}
  bool resultSetMetaData;
  bool warnings;
} _PrepareResult__isset;

class PrepareResult {
 public:

  static const char* ascii_fingerprint; // = "2F1FE28D9BFC914F8BCAA0BEE5DF909F";
  static const uint8_t binary_fingerprint[16]; // = {0x2F,0x1F,0xE2,0x8D,0x9B,0xFC,0x91,0x4F,0x8B,0xCA,0xA0,0xBE,0xE5,0xDF,0x90,0x9F};

  PrepareResult() : statementId(0) {
  }

  virtual ~PrepareResult() throw() {}

  int32_t statementId;
  std::vector<ColumnDescriptor>  parameterMetaData;
  std::vector<ColumnDescriptor>  resultSetMetaData;
  GFXDExceptionData warnings;

  _PrepareResult__isset __isset;

  void __set_statementId(const int32_t val) {
    statementId = val;
  }

  void __set_parameterMetaData(const std::vector<ColumnDescriptor> & val) {
    parameterMetaData = val;
  }

  void __set_resultSetMetaData(const std::vector<ColumnDescriptor> & val) {
    resultSetMetaData = val;
    __isset.resultSetMetaData = true;
  }

  void __set_warnings(const GFXDExceptionData& val) {
    warnings = val;
    __isset.warnings = true;
  }

  bool operator == (const PrepareResult & rhs) const
  {
    if (!(statementId == rhs.statementId))
      return false;
    if (!(parameterMetaData == rhs.parameterMetaData))
      return false;
    if (__isset.resultSetMetaData != rhs.__isset.resultSetMetaData)
      return false;
    else if (__isset.resultSetMetaData && !(resultSetMetaData == rhs.resultSetMetaData))
      return false;
    if (__isset.warnings != rhs.__isset.warnings)
      return false;
    else if (__isset.warnings && !(warnings == rhs.warnings))
      return false;
    return true;
  }
  bool operator != (const PrepareResult &rhs) const {
    return !(*this == rhs);
  }

  bool operator < (const PrepareResult & ) const;

  uint32_t read(::apache::thrift::protocol::TProtocol* iprot);
  uint32_t write(::apache::thrift::protocol::TProtocol* oprot) const;

};

void swap(PrepareResult &a, PrepareResult &b);

}}}} // namespace

#endif