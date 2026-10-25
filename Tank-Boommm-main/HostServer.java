import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class HostServer {
    private final int maxPlayers;
    private final Consumer<NetworkManager.LobbySnapshot> onSnapshot;
    private final NetworkManager.PlayerInfo[] players;
    private final List<ClientConnection> clients = new ArrayList<>();
    private ServerSocket serverSocket;
    private boolean running;

    public HostServer(int port, String hostName, int maxPlayers,
                      Consumer<NetworkManager.LobbySnapshot> onSnapshot) {
        if (maxPlayers < 2 || maxPlayers > 4) {
            throw new IllegalArgumentException("Maximum players must be between 2 and 4.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        if (hostName == null || hostName.trim().isEmpty()) {
            throw new IllegalArgumentException("Host name cannot be empty.");
        }
        this.configuredPort = port;
        this.maxPlayers = maxPlayers;
        this.onSnapshot = onSnapshot;
        this.players = new NetworkManager.PlayerInfo[maxPlayers];
        this.players[0] = new NetworkManager.PlayerInfo(hostName, 1);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        try {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new java.net.InetSocketAddress(configuredPort));
        } catch (IOException e) {
            serverSocket.close();
            serverSocket = null;
            throw e;
        }
        synchronized (this) {
            running = true;
        }
        publishSnapshot();
        Thread acceptThread = new Thread(this::acceptConnections, "lobby-host-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private final int configuredPort;

    private void acceptConnections() {
        while (isRunning()) {
            try {
                Socket socket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handleClient(socket), "lobby-host-client");
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (IOException e) {
                if (isRunning()) {
                    stop();
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        ClientConnection connection = null;
        try {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            socket.setSoTimeout(10000);
            String request = input.readUTF();
            String[] fields = request.split("\t", -1);
            if (fields.length != 2 || !"JOIN".equals(fields[0])) {
                sendError(output, "Invalid connection request.");
                socket.close();
                return;
            }
            String playerName = NetworkManager.decode(fields[1]).trim();
            if (playerName.isEmpty()) {
                sendError(output, "Player name cannot be empty.");
                socket.close();
                return;
            }

            synchronized (this) {
                int slot = findOpenSlot();
                if (!running) {
                    sendError(output, "The host is no longer accepting players.");
                    socket.close();
                    return;
                }
                if (slot < 0) {
                    sendError(output, "The lobby is full.");
                    socket.close();
                    return;
                }
                connection = new ClientConnection(socket, output, slot);
                players[slot] = new NetworkManager.PlayerInfo(playerName, 1);
                clients.add(connection);
                socket.setSoTimeout(0);
                output.writeUTF("WELCOME\t" + slot + "\t" + NetworkManager.encodeSnapshot(snapshot()));
                output.flush();
                publishSnapshot();
            }

            while (isRunning() && !socket.isClosed()) {
                String message = input.readUTF();
                handleClientMessage(connection, message);
            }
        } catch (IOException e) {
            // A disconnected client is removed in the cleanup block below.
        } finally {
            if (connection != null) {
                removeClient(connection);
            } else {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // The socket was not accepted into the lobby.
                }
            }
        }
    }

    private void handleClientMessage(ClientConnection connection, String message) throws IOException {
        String[] fields = message.split("\t", -1);
        synchronized (this) {
            if (!clients.contains(connection)) {
                return;
            }
            if (fields.length == 2 && "TANK".equals(fields[0])) {
                int tankNum;
                try {
                    tankNum = Integer.parseInt(fields[1]);
                } catch (NumberFormatException e) {
                    return;
                }
                if (tankNum >= 1 && tankNum <= 6) {
                    NetworkManager.PlayerInfo current = players[connection.slot];
                    players[connection.slot] = new NetworkManager.PlayerInfo(current.name, tankNum);
                    publishSnapshot();
                }
            } else if ("LEAVE".equals(message)) {
                removeClient(connection);
            }
        }
    }

    public synchronized void updateHostTank(int tankNum) {
        if (tankNum < 1 || tankNum > 6) {
            throw new IllegalArgumentException("Tank number must be between 1 and 6.");
        }
        if (players[0] != null) {
            players[0] = new NetworkManager.PlayerInfo(players[0].name, tankNum);
            publishSnapshot();
        }
    }

    public synchronized void stop() {
        if (!running && serverSocket == null) {
            return;
        }
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException ignored) {
            // Server shutdown is best-effort.
        }
        for (ClientConnection client : new ArrayList<>(clients)) {
            closeQuietly(client.socket);
        }
        clients.clear();
    }

    private synchronized boolean isRunning() {
        return running;
    }

    private int findOpenSlot() {
        for (int i = 1; i < players.length; i++) {
            if (players[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private synchronized void removeClient(ClientConnection connection) {
        if (clients.remove(connection)) {
            players[connection.slot] = null;
            closeQuietly(connection.socket);
            publishSnapshot();
        }
    }

    private synchronized NetworkManager.LobbySnapshot snapshot() {
        return new NetworkManager.LobbySnapshot(maxPlayers, players);
    }

    private void publishSnapshot() {
        NetworkManager.LobbySnapshot current = snapshot();
        if (onSnapshot != null) {
            onSnapshot.accept(current);
        }
        String message = NetworkManager.encodeSnapshot(current);
        for (ClientConnection client : new ArrayList<>(clients)) {
            try {
                synchronized (client.output) {
                    client.output.writeUTF(message);
                    client.output.flush();
                }
            } catch (IOException e) {
                removeClient(client);
            }
        }
    }

    private static void sendError(DataOutputStream output, String message) throws IOException {
        output.writeUTF("ERROR\t" + NetworkManager.encode(message));
        output.flush();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // The connection is already unavailable.
        }
    }

    private static final class ClientConnection {
        private final Socket socket;
        private final DataOutputStream output;
        private final int slot;

        private ClientConnection(Socket socket, DataOutputStream output, int slot) {
            this.socket = socket;
            this.output = output;
            this.slot = slot;
        }
    }
}
