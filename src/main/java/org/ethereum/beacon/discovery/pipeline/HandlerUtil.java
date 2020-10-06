/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline;

import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.schema.NodeSession;

public class HandlerUtil {
  private static final Logger logger = LogManager.getLogger(HandlerUtil.class);

  public static boolean requireField(Field field, Envelope envelope) {
    if (envelope.contains(field)) {
      return true;
    } else {
      logger.trace(
          () ->
              String.format(
                  "Requirement not satisfied: field %s not exists in envelope %s",
                  field, envelope.getId()));
      return false;
    }
  }

  public static boolean requireNodeRecord(Envelope envelope) {
    if (!requireField(Field.SESSION, envelope)) {
      return false;
    }
    if ((envelope.get(Field.SESSION)).getNodeRecord().isEmpty()) {
      logger.trace(
          () ->
              String.format(
                  "Requirement not satisfied: node record unknown in envelope %s",
                  envelope.getId()));
      return false;
    }
    return true;
  }

  public static boolean requireCondition(
      Function<Envelope, Boolean> conditionFunction, Envelope envelope) {
    if (conditionFunction.apply(envelope)) {
      return true;
    } else {
      logger.trace(
          () ->
              String.format(
                  "Requirement not satisfied: condition %s not met for envelope %s",
                  conditionFunction, envelope.getId()));
      return false;
    }
  }
}
