/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.util;

import static org.web3j.rlp.RlpDecoder.OFFSET_LONG_LIST;
import static org.web3j.rlp.RlpDecoder.OFFSET_SHORT_LIST;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/**
 * Handy utilities used for RLP encoding and decoding and not fulfilled by {@link
 * org.web3j.rlp.RlpEncoder} and {@link RlpDecoder}
 */
public class RlpUtil {
  public interface BytesConstraint extends Predicate<Bytes> {}

  public static final int ANY_LEN = -1;

  public static final BytesConstraint CONS_ANY = b -> true;
  public static final BytesConstraint CONS_UINT64 = maxSize(8);

  public static BytesConstraint strictSize(int size) {
    return b -> b.size() == size;
  }

  public static BytesConstraint maxSize(int maxSize) {
    return b -> b.size() <= maxSize;
  }

  public static BytesConstraint minMaxSize(int minSize, int maxSize) {
    return b -> b.size() <= maxSize && b.size() >= minSize;
  }

  public static BytesConstraint enumSizes(int... sizes) {
    return b -> Arrays.stream(sizes).anyMatch(s -> s == b.size());
  }

    /**
     * Calculates length of list beginning from the start of the data. So, there could everything else
     * after first list in data, method helps to cut data in this case.
     */
  private static int calcListLen(Bytes data) {
    int prefix = data.get(0) & 0xFF;
    int prefixAddon = 1;
    if (prefix >= OFFSET_SHORT_LIST && prefix <= OFFSET_LONG_LIST) {

      // 4. the data is a list if the range of the
      // first byte is [0xc0, 0xf7], and the concatenation of
      // the RLP encodings of all items of the list which the
      // total payload is equal to the first byte minus 0xc0 follows the first byte;

      byte listLen = (byte) (prefix - OFFSET_SHORT_LIST);
      return listLen & 0xFF + prefixAddon;
    } else if (prefix > OFFSET_LONG_LIST) {

      // 5. the data is a list if the range of the
      // first byte is [0xf8, 0xff], and the total payload of the
      // list which length is equal to the
      // first byte minus 0xf7 follows the first byte,
      // and the concatenation of the RLP encodings of all items of
      // the list follows the total payload of the list;

      int lenOfListLen = (prefix - OFFSET_LONG_LIST) & 0xFF;
      prefixAddon += lenOfListLen;
      return UInt64.fromBytes(Utils.leftPad(data.slice(1, lenOfListLen & 0xFF), 8)).intValue()
          + prefixAddon;
    } else {
      throw new RuntimeException("Not a start of RLP list!!");
    }
  }

  /**
   * @return first rlp list in provided data, plus remaining data starting from the end of this list
   */
  public static DecodedList decodeFirstList(Bytes data) {
    int len = RlpUtil.calcListLen(data);
    return new DecodedList(RlpDecoder.decode(data.slice(0, len).toArray()), data.slice(len));
  }

  /**
   * Decodes strictly the list of byte strings of specified lengths The list should contain strictly
   * {@code lengths.length} strings
   *
   * @param constraints constrains for every string in the list
   * @throws RlpDecodeException if this rlp doesn't represent a single list of strings of specified
   *     lengths
   */
  public static List<Bytes> decodeListOfStrings(Bytes rlp, BytesConstraint... constraints)
      throws RlpDecodeException {

    List<Bytes> bytesList = decodeListOfStrings(rlp);
    if (bytesList.size() != constraints.length) {
      throw new RlpDecodeException("Expected RLP list of " + constraints.length + " items: " + rlp);
    }

    for (int i = 0; i < constraints.length; i++) {
//      if (lengths[i] >= 0 && bytesList.get(i).size() != lengths[i]) {
      if (constraints[i].test(bytesList.get(i))) {
        throw new RlpDecodeException(
            "Failed constraints check (" + constraints[i] + ") for item #" + i + ": " + rlp);
      }
    }
    return bytesList;
  }

  /**
   * Decodes strictly the list of byte strings
   *
   * @throws RlpDecodeException if this rlp doesn't represent a single list of strings
   */
  public static List<Bytes> decodeListOfStrings(Bytes rlp) throws RlpDecodeException {
    RlpList items = decodeSingleList(rlp);
    List<RlpType> values = items.getValues();

    List<Bytes> ret = new ArrayList<>();
    for (RlpType val : values) {
      if (!(val instanceof RlpString)) {
        throw new RlpDecodeException("Expected RLP list of strings only: " + rlp);
      }
      Bytes string = Bytes.wrap(((RlpString) val).getBytes());
      ret.add(string);
    }
    return ret;
  }

  /**
   * Decodes strictly one list from rlp bytes
   *
   * @throws RlpDecodeException if this rlp doesn't represent a single list
   */
  public static RlpList decodeSingleList(Bytes rlp) throws RlpDecodeException {
    RlpType item = decodeSingleItem(rlp);
    if (!(item instanceof RlpList)) {
      throw new RlpDecodeException("Expected RLP list, but got a string from bytes: " + rlp);
    }
    return (RlpList) item;
  }

  /**
   * Decodes strictly one bytes string item from rlp bytes
   *
   * @throws RlpDecodeException if this rlp doesn't represent a single bytes string
   */
  public static Bytes decodeSingleString(Bytes rlp, int expectedSize) throws RlpDecodeException {
    Bytes ret = decodeSingleString(rlp);
    if (ret.size() != expectedSize) {
      throw new RlpDecodeException("Expected string size doesn't match: " + rlp);
    }
    return ret;
  }

  /**
   * Decodes strictly one bytes string item from rlp bytes
   *
   * @throws RlpDecodeException if this rlp doesn't represent a single bytes string
   */
  public static Bytes decodeSingleString(Bytes rlp) throws RlpDecodeException {
    RlpType item = decodeSingleItem(rlp);
    if (!(item instanceof RlpString)) {
      throw new RlpDecodeException("Expected RLP bytes string, but got a list from bytes: " + rlp);
    }
    return Bytes.wrap(((RlpString) item).getBytes());
  }

  /**
   * Decodes strictly one item from rlp bytes
   *
   * @throws RlpDecodeException if more that item encoded in this rlp
   */
  public static RlpType decodeSingleItem(Bytes rlp) throws RlpDecodeException {
    try {
      List<RlpType> rlpList = RlpDecoder.decode(rlp.toArray()).getValues();
      if (rlpList.size() != 1) {
        throw new RlpDecodeException("Only a single RLP item expected from bytes: " + rlp);
      }
      return rlpList.get(0);
    } catch (Exception e) {
      throw new RlpDecodeException("Error decoding RLP: " + rlp, e);
    }
  }

  /**
   * Encodes object to {@link RlpString}. Supports numbers, {@link Bytes} etc.
   *
   * @throws RuntimeException with errorMessageFunction applied with `object` when encoding is not
   *     possible
   */
  public static RlpString encode(Object object, Function<Object, String> errorMessageFunction) {
    if (object instanceof Bytes) {
      return fromBytesValue((Bytes) object);
    } else if (object instanceof Number) {
      return fromNumber((Number) object);
    } else if (object == null) {
      return RlpString.create(new byte[0]);
    } else if (object instanceof IdentitySchema) {
      return RlpString.create(((IdentitySchema) object).stringName());
    } else {
      throw new RuntimeException(errorMessageFunction.apply(object));
    }
  }

  private static RlpString fromNumber(Number number) {
    if (number instanceof BigInteger) {
      return RlpString.create((BigInteger) number);
    } else if (number instanceof Long) {
      return RlpString.create((Long) number);
    } else if (number instanceof Integer) {
      return RlpString.create((Integer) number);
    } else {
      throw new RuntimeException(
          String.format("Couldn't serialize number %s : no serializer found.", number));
    }
  }

  private static RlpString fromBytesValue(Bytes bytes) {
    return RlpString.create(bytes.toArray());
  }

  public static class DecodedList {
    private final RlpList list;
    private final Bytes remainingData;

    public DecodedList(final RlpList list, final Bytes remainingData) {
      this.list = list;
      this.remainingData = remainingData;
    }

    public RlpList getList() {
      return list;
    }

    public Bytes getRemainingData() {
      return remainingData;
    }
  }
}
