/**
 * Autogenerated by Thrift Compiler (0.9.1)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */

#include <thrift/Thrift.h>
#include <thrift/TApplicationException.h>
#include <thrift/protocol/TProtocol.h>
#include <thrift/transport/TTransport.h>

#include <thrift/cxxfunctional.h>

#include "gfxd_struct_PDXNode.h"

#include <algorithm>

namespace com { namespace pivotal { namespace gemfirexd { namespace thrift {

const char* PDXNode::ascii_fingerprint = "5BAE4780BBF340F68755ECE4E12FB8B2";
const uint8_t PDXNode::binary_fingerprint[16] = {0x5B,0xAE,0x47,0x80,0xBB,0xF3,0x40,0xF6,0x87,0x55,0xEC,0xE4,0xE1,0x2F,0xB8,0xB2};

uint32_t PDXNode::read(::apache::thrift::protocol::TProtocol* iprot) {

  uint32_t xfer = 0;
  std::string fname;
  ::apache::thrift::protocol::TType ftype;
  int16_t fid;

  xfer += iprot->readStructBegin(fname);

  using ::apache::thrift::protocol::TProtocolException;


  while (true)
  {
    xfer += iprot->readFieldBegin(fname, ftype, fid);
    if (ftype == ::apache::thrift::protocol::T_STOP) {
      break;
    }
    switch (fid)
    {
      case 1:
        if (ftype == ::apache::thrift::protocol::T_STRUCT) {
          xfer += this->singleField.read(iprot);
          this->__isset.singleField = true;
        } else {
          xfer += iprot->skip(ftype);
        }
        break;
      case 2:
        if (ftype == ::apache::thrift::protocol::T_LIST) {
          {
            this->fields.clear();
            uint32_t _size55;
            ::apache::thrift::protocol::TType _etype58;
            xfer += iprot->readListBegin(_etype58, _size55);
            this->fields.resize(_size55);
            uint32_t _i59;
            for (_i59 = 0; _i59 < _size55; ++_i59)
            {
              xfer += this->fields[_i59].read(iprot);
            }
            xfer += iprot->readListEnd();
          }
          this->__isset.fields = true;
        } else {
          xfer += iprot->skip(ftype);
        }
        break;
      case 3:
        if (ftype == ::apache::thrift::protocol::T_I32) {
          xfer += iprot->readI32(this->refId);
          this->__isset.refId = true;
        } else {
          xfer += iprot->skip(ftype);
        }
        break;
      default:
        xfer += iprot->skip(ftype);
        break;
    }
    xfer += iprot->readFieldEnd();
  }

  xfer += iprot->readStructEnd();

  return xfer;
}

uint32_t PDXNode::write(::apache::thrift::protocol::TProtocol* oprot) const {
  uint32_t xfer = 0;
  xfer += oprot->writeStructBegin("PDXNode");

  if (this->__isset.singleField) {
    xfer += oprot->writeFieldBegin("singleField", ::apache::thrift::protocol::T_STRUCT, 1);
    xfer += this->singleField.write(oprot);
    xfer += oprot->writeFieldEnd();
  }
  if (this->__isset.fields) {
    xfer += oprot->writeFieldBegin("fields", ::apache::thrift::protocol::T_LIST, 2);
    {
      xfer += oprot->writeListBegin(::apache::thrift::protocol::T_STRUCT, static_cast<uint32_t>(this->fields.size()));
      std::vector<FieldValue> ::const_iterator _iter60;
      for (_iter60 = this->fields.begin(); _iter60 != this->fields.end(); ++_iter60)
      {
        xfer += (*_iter60).write(oprot);
      }
      xfer += oprot->writeListEnd();
    }
    xfer += oprot->writeFieldEnd();
  }
  if (this->__isset.refId) {
    xfer += oprot->writeFieldBegin("refId", ::apache::thrift::protocol::T_I32, 3);
    xfer += oprot->writeI32(this->refId);
    xfer += oprot->writeFieldEnd();
  }
  xfer += oprot->writeFieldStop();
  xfer += oprot->writeStructEnd();
  return xfer;
}

void swap(PDXNode &a, PDXNode &b) {
  using ::std::swap;
  swap(a.singleField, b.singleField);
  swap(a.fields, b.fields);
  swap(a.refId, b.refId);
  swap(a.__isset, b.__isset);
}

}}}} // namespace