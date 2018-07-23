package de.hasenburg.geofencebroker.model.connections;

import de.hasenburg.geofencebroker.communication.ControlPacketType;
import de.hasenburg.geofencebroker.communication.ReasonCode;
import de.hasenburg.geofencebroker.communication.RouterCommunicator;
import de.hasenburg.geofencebroker.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConnectionManager {

	private static final Logger logger = LogManager.getLogger();

	private final HashMap<String, Connection> connections = new HashMap<>();
	private RouterCommunicator routerCommunicator;

	/*****************************************************************
	 * Constructor and Co
	 ****************************************************************/

	public ConnectionManager(RouterCommunicator router) {
		this.routerCommunicator = router;
	}

	public List<Connection> getActiveConnections() {
		return connections.values().stream().filter(c -> c.isActive()).collect(Collectors.toList());
	}

	public List<Connection> getInactiveConnections() {
		return connections.values().stream().filter(c -> !c.isActive()).collect(Collectors.toList());
	}

	/*****************************************************************
	 * Message Processing
	 ****************************************************************/

	public boolean notExpectedMessage(RouterMessage message, ControlPacketType expectedType) {
		if (message.getControlPacketType() != expectedType) {
			logger.warn("Stopping processing of message from client {} as it has not expected ControlPacketType {}, instead {}",
					message.getClientIdentifier(), expectedType, message.getControlPacketType());
			return true;
		}
		logger.trace("Processing {} message from client {}", expectedType, message.getClientIdentifier());
		return false;
	}

	public void processCONNECT(RouterMessage message) {
		if (notExpectedMessage(message, ControlPacketType.CONNECT)) {
			return;
		}

		// get connection or create new
		Connection connection = connections.get(message.getClientIdentifier());
		if (connection == null) {
			connection = new Connection(message.getClientIdentifier());
		}

		RouterMessage response;

		if (connection.isActive()) {
			logger.debug("Connection already active, so protocol error. Closing now.");
			connection.setActive(false);
			response = new RouterMessage(
					message.getClientIdentifier(), ControlPacketType.DISCONNECT,
					new PayloadDISCONNECT(ReasonCode.ProtocolError));
		} else {
			logger.debug("Connection is now active, acknowledging.");
			connection.setActive(true);
			response = new RouterMessage(message.getClientIdentifier(), ControlPacketType.CONNACK);
		}

		routerCommunicator.sendRouterMessage(response);

		connections.put(connection.getClientIdentifier(), connection);
	}

	public void processDISCONNECT(RouterMessage message) {
		if (notExpectedMessage(message, ControlPacketType.DISCONNECT)) {
			return;
		}

		// get connection
		Connection connection = connections.get(message.getClientIdentifier());
		if (connection == null) {
			logger.debug("Connection of {} did not exist", message.getClientIdentifier());
			return;
		}

		// set to inactive
		logger.debug("Connection of {} is now inactive", message.getClientIdentifier());
		connection.setActive(false);

		connections.put(connection.getClientIdentifier(), connection);
	}

	public void processPINGREQ(RouterMessage message) {
		if (notExpectedMessage(message, ControlPacketType.PINGREQ)) {
			return;
		}

		Connection connection = connections.get(message.getClientIdentifier());
		RouterMessage response;

		if (Connection.isConnected(connection)) {
			response = new RouterMessage(message.getClientIdentifier(), ControlPacketType.PINGRESP);

			// update location
			PayloadPINGREQ payloadPINGREQ = (PayloadPINGREQ) message.getPayload();
			payloadPINGREQ.getLocation().ifPresentOrElse(location -> {
				connection.updateLocation(location);
				connections.put(connection.getClientIdentifier(), connection);
				logger.debug("Updated location to {}", location.toString());
			}, () -> logger.warn("Message misses location"));

		} else {
			response = new RouterMessage(message.getClientIdentifier(), ControlPacketType.PINGRESP,
					new PayloadPINGRESP(ReasonCode.NotConnected));
		}

		routerCommunicator.sendRouterMessage(response);

	}

	public void processSUBSCRIBEforConnection(RouterMessage message) {
		// TODO Implement
	}

	public void processUNSUBSCRIBEforConnection(RouterMessage message) {
		// TODO Implement
	}

	/*****************************************************************
	 * Generated Code
	 ****************************************************************/

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder("\n");
		for (Map.Entry<String, Connection> entry : connections.entrySet()) {
			s.append("\t").append(entry.getKey()).append(", is active: ").append(entry.getValue().isActive());
			//noinspection ConstantConditions
			s.append("\t\tLocation: ").append(entry.getValue().getLocation().get().toString());
			s.append("\n");
		}
		return s.toString();
	}
}
