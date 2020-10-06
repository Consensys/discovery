/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.type.Bytes12;

/**
 * In memory repository with nonces, corresponding sessions {@link NodeSession} and 2-way getters:
 * {@link #get(Bytes12)} and {@link #getNonce(NodeSession)}
 *
 * <p>Expired authTags should be manually removed with {@link #expire(NodeSession)}
 */
public class NonceRepository {
  private static final Logger logger = LogManager.getLogger(NonceRepository.class);
  private Map<Bytes12, NodeSession> authTags = new ConcurrentHashMap<>();
  private Map<NodeSession, Bytes12> sessions = new ConcurrentHashMap<>();

  public synchronized void put(Bytes12 authTag, NodeSession session) {
    logger.trace(
        () -> String.format("PUT: authTag[%s] => nodeSession[%s]", authTag, session.getNodeId()));
    authTags.put(authTag, session);
    sessions.put(session, authTag);
  }

  public Optional<NodeSession> get(Bytes12 nonce) {
    logger.trace(() -> String.format("GET: nonce[%s]", nonce));
    NodeSession session = authTags.get(nonce);
    return session == null ? Optional.empty() : Optional.of(session);
  }

  public Optional<Bytes12> getNonce(NodeSession session) {
    logger.trace(() -> String.format("GET: session %s", session));
    Bytes12 authTag = sessions.get(session);
    return authTag == null ? Optional.empty() : Optional.of(authTag);
  }

  public synchronized void expire(NodeSession session) {
    logger.trace(() -> String.format("REMOVE: session %s", session));
    Bytes authTag = sessions.remove(session);
    logger.trace(
        () ->
            authTag == null
                ? "Session %s not found, was not removed"
                : String.format("Session %s removed with authTag[%s]", session, authTag));
    if (authTag != null) {
      authTags.remove(authTag);
    }
  }
}
